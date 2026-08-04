#!/usr/bin/env python3
"""Static release gates for the simplified root-TPROXY policy."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(path: str, *needles: str) -> None:
    body = text(path)
    for needle in needles:
        if needle not in body:
            raise SystemExit(f"{path}: missing required policy: {needle}")


def forbid(path: str, *needles: str) -> None:
    body = text(path)
    for needle in needles:
        if needle in body:
            raise SystemExit(f"{path}: forbidden legacy UI/policy remains: {needle}")


require(
    "app/src/main/kotlin/app/AppState.kt",
    "val enableResolveProxyServerDomain: Boolean = true",
    "val enableSniffing: Boolean = false",
    "val shareHotspot: Boolean = true",
    "val enableMux: Boolean = false",
    "val enableFragment: Boolean = false",
    "val enableIpv6: Boolean = false",
    "val enableFakeDns: Boolean = false",
)
require(
    "app/src/main/kotlin/engine/xray/XrayDnsDefaults.kt",
    'DefaultPrimaryDnsServer = "8.8.8.8"',
    'DefaultSecondaryDnsServer = "8.8.4.4"',
    '"https+local://1.1.1.1/dns-query"',
    '"tcp+local://8.8.8.8"',
)
require(
    "app/src/main/kotlin/engine/xray/XrayDnsConfig.kt",
    'put("disableFallbackIfMatch", true)',
)
require(
    "app/src/main/kotlin/engine/xray/XrayOutboundJson.kt",
    'else -> "ForceIPv4"',
    "appState.xrayProxyOutboundDomainStrategy()",
)
require(
    "app/src/main/kotlin/engine/root/RootIptablesConfig.kt",
    'RootWifiHotspotInterfacePrefixes = listOf("wlan+", "swlan+", "ap+", "softap+")',
    "if (appState.shareHotspot) RootWifiHotspotInterfacePrefixes else emptyList()",
)
require(
    "app/src/main/kotlin/engine/tproxy/TproxyIptablesScript.kt",
    'RootIp6tablesCommand, "OUTPUT", "-j REJECT"',
    'RootIp6tablesCommand, "FORWARD", "-j REJECT"',
    "appendPreroutingDnsTproxyRule(variant, prefix, port, config.mark)",
)
require(
    "app/src/main/kotlin/data/AppSettingsPreferences.kt",
    "enableResolveProxyServerDomain = true",
    "enableSniffing = false",
    "enableMux = false",
    "enableFragment = false",
    "enableIpv6 = false",
    "enableFakeDns = false",
    ".putBoolean(KeyShareHotspot, state.shareHotspot)",
)
require(
    "app/src/main/kotlin/features/settings/SettingsSections.kt",
    "SettingsNetworkSection",
    "settings_share_hotspot",
    "settings_dns_summary_simple",
)
forbid(
    "app/src/main/kotlin/features/settings/SettingsSections.kt",
    "settings_sniffing",
    "settings_mux",
    "settings_fragment",
    "settings_ipv6",
    "settings_local_proxy",
    "settings_inbound",
    "settings_external_interfaces",
    "settings_ignored_interfaces",
    "settings_private_addresses",
    "settings_record_access_log",
)
for removed in (
    "FragmentSettingsBottomSheet.kt",
    "MuxSettingsBottomSheet.kt",
    "InboundProxySettingsBottomSheet.kt",
    "LocalProxySettingsBottomSheet.kt",
    "NetworkInterfaceBottomSheets.kt",
    "PrivateAddressBottomSheet.kt",
):
    if (ROOT / "app/src/main/kotlin/features/settings/sheets" / removed).exists():
        raise SystemExit(f"legacy settings source still exists: {removed}")

print("Simplified settings and hotspot policy verified")
