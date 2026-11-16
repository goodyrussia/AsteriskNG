English | [简体中文](README_zh_CN.md) | [Русский](README_ru.md) | [Tiếng Việt](README_vi.md)

# AsteriskNG

Android Xray GUI client.

> **This is a personal fork** of [Asterisk4Magisk/AsteriskNG](https://github.com/Asterisk4Magisk/AsteriskNG) (v1.4.26), customized for my own setup. Upstream stays the reference — this fork only changes what I need.

## What this fork has

- **Patched Xray core** — upstream Xray-core v26.11.9 with the restrictions lifted: `allowInsecure` works again, and plain (non-TLS) VLESS/Trojan are allowed. HTTP/QUIC transports and legacy XTLS were not restored (their implementations are gone upstream).
- **No routing, no China resources** — the routing tab, all built-in routing rules, and the geoip/geosite/China CIDR resource files are removed, along with the Resource Management screen and resource auto-update. All traffic goes through the proxy; only the Xray core file and custom resource files remain.
- **My defaults on a fresh install** — dark theme (fixed, no theme or language option), screenshots allowed, run mode TUN2SOCKS (ROOT), proxy DNS `tcp://1.1.1.1`, no direct-DNS domains, sniffing off, external interfaces `wlan+`, `ap+`, `softap+`. Restoring an old backup keeps TUN2SOCKS instead of forcing VPN Service.
- **arm64-v8a only** — no other ABIs, no universal APK.

## Releases

- [v1.4.28 (pre-release, current)](https://github.com/goodyrussia/AsteriskNG/releases/tag/v1.4.28) — the customized build above.
- [v1.4.26](https://github.com/goodyrussia/AsteriskNG/releases/tag/v1.4.26) — pure upstream 1.4.26 + patched core, no customization.
- [v1.4.27](https://github.com/goodyrussia/AsteriskNG/releases/tag/v1.4.27) — earlier build with ported settings, kept as a fallback.

v1.4.28 installs over v1.4.26 (same signing key). Coming from the old 1.6.2 build, uninstall first — it was signed with a different, lost key. Export **Backup & Restore** in the app first, import after reinstalling.

## The repos behind it

- [goodyrussia/Xray-core](https://github.com/goodyrussia/Xray-core) — the patched core. Branch `goody1`, tag `v26.11.9-goody1` (commit `a138fab`), based on upstream Xray-core v26.11.9.
- [goodyrussia/AndroidLibXrayLite](https://github.com/goodyrussia/AndroidLibXrayLite) — the Android library built from that core. Release `v26.11.9-p1` (`libv2ray.aar`), which is what this app downloads at build time.

## Telegram Channel

[Asterisk4Magisk](https://t.me/Asterisk4Magisk)

## Run Modes

### VPN Service

- Works without root permission.
- Uses Android `VpnService`.

### TPROXY(ROOT)

- Runs the local Xray executable directly with libsu.
- Uses iptables and policy routing for transparent proxy traffic.

### TUN2SOCKS(ROOT)

- Runs the local Xray executable directly with libsu.
- Uses `hev-socks5-tunnel` to create a TUN device and send traffic to the Xray SOCKS5 inbound.
- The default run mode in this fork.

### BPF2SOCKS(ROOT)

- Runs the local Xray executable directly with libsu.
- Uses `bpf2socks` to capture traffic and send it to the Xray SOCKS5 inbound.
- Availability depends on eBPF support in the device kernel.

### asteriskd

- Watches local IPv4/IPv6 addresses and tethering interfaces, then refreshes the relevant iptables rules or BPF maps.
- Cleans up networking rules owned by the active ROOT mode when the service stops.

## Enhancement Module

[AsteriskEnhance](https://github.com/Asterisk4Magisk/AsteriskEnhance) is an optional module that routes system DNS according to the application list and isolates resolver caches. It requires ROOT and Zygisk Next and supports TPROXY, TUN2SOCKS, and BPF2SOCKS. Enable **Application list enhancement** in settings and follow the installation prompts; reboot after installation or updates.

## Resource Files

- Resource files are stored in the app-private `files/xray` directory.
- There are no built-in geoip/geosite files and no auto-update in this fork — only the bundled Xray core file and custom resource files. Custom resources can be added or replaced locally and updated from configured URLs.

## Broadcast Control

Enable **Broadcast Control** in settings, then send an explicit broadcast to the receiver below. Actions use the `org.asterisk.zcc.ang.action.` prefix.

| Operation | Action suffix |
| --- | --- |
| Start proxy | `PROXY_START` |
| Stop proxy | `PROXY_STOP` |
| Toggle proxy | `PROXY_TOGGLE` |
| Update all URL subscriptions | `SUBSCRIPTION_UPDATE` |
| Cancel broadcast subscription update | `SUBSCRIPTION_UPDATE_CANCEL` |
| Update all resources | `RESOURCE_UPDATE` |
| Cancel resource updates | `RESOURCE_UPDATE_CANCEL` |

```sh
adb shell am broadcast -n org.asterisk.zcc.ang/features.automation.BroadcastControlReceiver -a org.asterisk.zcc.ang.action.SUBSCRIPTION_UPDATE
```

Subscription updates skip local entries; cancellation preserves completed results and scheduled update settings. In this fork, resource updates only cover custom resource files, and resource cancellation also clears its shared queue. Repeated update commands of the same kind are merged while running.

Updates run in the background without starting the proxy. Broadcast delivery does not mean the update has finished; check the `BroadcastControl` app logs for results.

## Development

Initialize submodules before building:

```bash
git submodule update --init --recursive
```

Open the project root in Android Studio, or build it with Gradle wrapper:

```powershell
.\gradlew.bat assembleDebug
```

On macOS or Linux:

```bash
./gradlew assembleDebug
```

The build downloads the pinned AndroidLibXrayLite AAR from [goodyrussia/AndroidLibXrayLite](https://github.com/goodyrussia/AndroidLibXrayLite), builds the native submodules, and produces an arm64-v8a APK.

Debug builds carry the application id `org.asterisk.zcc.ang.debug` and a DEV-badged launcher icon (`app/src/debug/res`), so a debug build installs alongside a release build.

If Gradle cannot find the Android NDK, configure it through Android Studio, `ndk.dir` in `local.properties`, or `ANDROID_NDK_HOME`.

## License

[GPL-3.0](LICENSE)

## Credits

- [@goodyrussia/Xray-core](https://github.com/goodyrussia/Xray-core) — patched core used by this fork
- [@goodyrussia/AndroidLibXrayLite](https://github.com/goodyrussia/AndroidLibXrayLite) — Android library built from that core
- [@XTLS/Xray-core](https://github.com/XTLS/Xray-core) — upstream core
- [@Asterisk4Magisk/AndroidLibXrayLite](https://github.com/Asterisk4Magisk/AndroidLibXrayLite) — upstream library
- [@Asterisk4Magisk/AsteriskNG](https://github.com/Asterisk4Magisk/AsteriskNG) — upstream app
- [@heiher/hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel)
- [@topjohnwu/libsu](https://github.com/topjohnwu/libsu)
- [@compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix)
- [@2dust/v2rayNG](https://github.com/2dust/v2rayNG)
- [@xchacha20-poly1305/husi](https://github.com/xchacha20-poly1305/husi)
