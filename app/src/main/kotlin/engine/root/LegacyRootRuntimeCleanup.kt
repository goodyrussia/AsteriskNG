// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package engine.root

import android.content.Context
import features.logs.AndroidAppLogger
import system.AndroidRootShellGateway
import system.ShellExecOptions
import java.io.File

/**
 * Removes artifacts left by the pre-TPROXY rooted transport during an in-place upgrade.
 *
 * This is intentionally one-way cleanup: it does not configure or start the retired
 * runtime. It is gated on old PID/config/interface/firewall/boot-script signatures.
 */
internal class LegacyRootRuntimeCleaner(
    context: Context,
    private val rootAccess: AndroidRootShellGateway,
) {
    private val appContext = context.applicationContext

    suspend fun clean() {
        if (!rootAccess.hasRootAccess()) {
            return
        }
        val result = rootAccess.exec(
            appContext.prepareRootRuntimeLayout().buildLegacyRootRuntimeCleanupCommand(),
            ShellExecOptions(logFailure = false),
        )
        if (result.errno != 0) {
            val details = result.stderr.ifBlank { result.stdout }
            AndroidAppLogger.warn(LogTag, "Failed to remove retired rooted runtime artifacts:\n$details")
            error("Failed to remove retired rooted runtime artifacts")
        }
    }
}

private fun RootRuntimeLayout.buildLegacyRootRuntimeCleanupCommand(): String {
    val legacyPidPath = File(dataDir, LegacyPidFileName).absolutePath
    val legacyConfigPath = File(dataDir, LegacyConfigFileName).absolutePath
    return buildString {
        appendScript(
            $$"""
            cleanup_failed=0
            legacy_present=0
            legacy_core_config=0
            legacy_boot_script=0
            test -s $${legacyPidPath.shellQuote()} && legacy_present=1
            test -f $${legacyConfigPath.shellQuote()} && legacy_present=1
            grep -F $${LegacyInboundTag.shellQuote()} $${configPath.shellQuote()} >/dev/null 2>&1 && {
                legacy_present=1
                legacy_core_config=1
            }
            ip link show dev $${LegacyInterfaceName.shellQuote()} >/dev/null 2>&1 && legacy_present=1
            iptables -t mangle -S $${LegacyIpv4PreroutingChain.shellQuote()} >/dev/null 2>&1 && legacy_present=1
            ip6tables -t mangle -S $${LegacyIpv6PreroutingChain.shellQuote()} >/dev/null 2>&1 && legacy_present=1
            grep -F $${LegacyInterfaceName.shellQuote()} $${RootBootScriptPath.shellQuote()} $${startupScriptPath.shellQuote()} >/dev/null 2>&1 && {
                legacy_present=1
                legacy_boot_script=1
            }
            [ "$$legacy_present" = 1 ] || exit 0

            if [ "$$legacy_core_config" = 1 ]; then
                for proc_dir in /proc/[0-9]*; do
                    pid="$${proc_dir##*/}"
                    cmdline="$(tr '\0' ' ' < "$$proc_dir/cmdline" 2>/dev/null || true)"
                    case "$$cmdline" in
                        *$${configPath.shellQuoteForCase()}*)
                            kill "$$pid" 2>/dev/null || true
                            sleep 0.2
                            kill -9 "$$pid" 2>/dev/null || true
                            kill -0 "$$pid" 2>/dev/null && cleanup_failed=1
                            ;;
                    esac
                done
                rm -f $${pidPath.shellQuote()} $${configPath.shellQuote()} 2>/dev/null || cleanup_failed=1
            fi

            for proc_dir in /proc/[0-9]*; do
                pid="$${proc_dir##*/}"
                cmdline="$(tr '\0' ' ' < "$$proc_dir/cmdline" 2>/dev/null || true)"
                executable="$(readlink "$$proc_dir/exe" 2>/dev/null || true)"
                case "$$cmdline $$executable" in
                    *$${LegacyHelperLibraryName.shellQuote()}*)
                        kill "$$pid" 2>/dev/null || true
                        sleep 0.2
                        kill -9 "$$pid" 2>/dev/null || true
                        kill -0 "$$pid" 2>/dev/null && cleanup_failed=1
                        ;;
                esac
            done

            clean_chain() {
                command="$$1"
                table="$$2"
                chain="$$3"
                parent="$$4"
                if $$command -t "$$table" -S "$$chain" >/dev/null 2>&1; then
                    while $$command -t "$$table" -D "$$parent" -j "$$chain" 2>/dev/null; do :; done
                    $$command -t "$$table" -F "$$chain" 2>/dev/null || cleanup_failed=1
                    $$command -t "$$table" -X "$$chain" 2>/dev/null || cleanup_failed=1
                    $$command -t "$$table" -S "$$chain" >/dev/null 2>&1 && cleanup_failed=1
                fi
            }
            clean_chain iptables mangle $${LegacyIpv4PreroutingChain.shellQuote()} PREROUTING
            clean_chain iptables mangle $${LegacyIpv4OutputChain.shellQuote()} OUTPUT
            clean_chain iptables filter $${LegacyIpv4ForwardChain.shellQuote()} FORWARD
            clean_chain ip6tables mangle $${LegacyIpv6PreroutingChain.shellQuote()} PREROUTING
            clean_chain ip6tables mangle $${LegacyIpv6OutputChain.shellQuote()} OUTPUT
            clean_chain ip6tables filter $${LegacyIpv6ForwardChain.shellQuote()} FORWARD
            while ip6tables -t filter -D OUTPUT -p udp --dport 53 -j REJECT 2>/dev/null; do :; done

            while ip rule del fwmark $${LegacyFwmark.shellQuote()} lookup $${LegacyRouteTable.shellQuote()} 2>/dev/null; do :; done
            while ip -6 rule del fwmark $${LegacyFwmark.shellQuote()} lookup $${LegacyRouteTable.shellQuote()} 2>/dev/null; do :; done
            while ip -6 rule del from all lookup $${LegacyRouteTable.shellQuote()} prio 31999 2>/dev/null; do :; done
            ip route flush table $${LegacyRouteTable.shellQuote()} 2>/dev/null || cleanup_failed=1
            ip -6 route flush table $${LegacyRouteTable.shellQuote()} 2>/dev/null || cleanup_failed=1
            ip link delete dev $${LegacyInterfaceName.shellQuote()} 2>/dev/null || true
            ip link show dev $${LegacyInterfaceName.shellQuote()} >/dev/null 2>&1 && cleanup_failed=1
            rm -f $${legacyPidPath.shellQuote()} $${legacyConfigPath.shellQuote()} 2>/dev/null || cleanup_failed=1
            test -e $${legacyPidPath.shellQuote()} && cleanup_failed=1
            test -e $${legacyConfigPath.shellQuote()} && cleanup_failed=1

            if [ "$$legacy_boot_script" = 1 ]; then
                rm -f $${RootBootScriptPath.shellQuote()} $${startupScriptPath.shellQuote()} 2>/dev/null || cleanup_failed=1
                test -e $${RootBootScriptPath.shellQuote()} && cleanup_failed=1
                test -e $${startupScriptPath.shellQuote()} && cleanup_failed=1
            fi
            [ "$$cleanup_failed" = 0 ] || exit 1
            """,
        )
    }
}

private const val LogTag = "LegacyRootRuntimeCleanup"
private const val LegacyInboundTag = "\"tun2socks-in\""
private const val LegacyHelperLibraryName = "libhev-socks5-tunnel.so"
private const val LegacyPidFileName = "tun2socks.pid"
private const val LegacyConfigFileName = "tun2socks.yml"
private const val LegacyInterfaceName = "asterisk0"
private const val LegacyFwmark = "0x4000000/0x4000000"
private const val LegacyRouteTable = "168"
private const val LegacyIpv4PreroutingChain = "ASTERISK_TUN_PREROUTING"
private const val LegacyIpv4OutputChain = "ASTERISK_TUN_OUTPUT"
private const val LegacyIpv4ForwardChain = "ASTERISK_TUN_FORWARD"
private const val LegacyIpv6PreroutingChain = "ASTERISK_TUN6_PREROUTING"
private const val LegacyIpv6OutputChain = "ASTERISK_TUN6_OUTPUT"
private const val LegacyIpv6ForwardChain = "ASTERISK_TUN6_FORWARD"
