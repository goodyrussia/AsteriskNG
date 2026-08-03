English | [简体中文](README_zh_CN.md)

# AsteriskNG

A rooted Android Xray client powered by [Xray-core](https://github.com/XTLS/Xray-core).

## Telegram Channel

[Asterisk4Magisk](https://t.me/Asterisk4Magisk)

## Features

- Rooted TPROXY transparent-proxy runtime using Xray-core, iptables, and policy routing
- Per-app Global, Blacklist, and Whitelist proxy policy
- VMess, VLESS, Trojan, Shadowsocks, Socks, HTTP, Hysteria2, WireGuard, strategy group, and chain proxy support
- v2rayNG and mihomo subscription format support
- Resource file management for `geoip.dat`, `geosite.dat`, `geoip-only-cn-private.dat`, and the Xray executable
- ROOT start-on-boot script generation through Magisk `service.d`
- MIUIX Compose UI

## Screenshots

<p align="center">
  <img src="image/screenshot/5.jpg" width="24%" alt="Screenshot 1" />
  <img src="image/screenshot/6.jpg" width="24%" alt="Screenshot 2" />
  <img src="image/screenshot/7.jpg" width="24%" alt="Screenshot 3" />
  <img src="image/screenshot/8.jpg" width="24%" alt="Screenshot 4" />
</p>

## TPROXY (ROOT)

- Requires root permission.
- Runs the local Xray executable directly with libsu.
- Uses iptables and policy routing for transparent proxy traffic.
- Uses the configured transparent proxy port as the Xray inbound.

## Resource Files

- Runtime files are stored in the app private `files/xray` directory, commonly `/data/user/0/org.asterisk.zcc.ang/files/xray`.
- The bundled Xray executable is restored from native libraries and can be replaced manually with an `xray` executable file or a zip archive containing `xray`.
- `geoip.dat` and `geosite.dat` can be restored from bundled assets, updated from online sources, or replaced manually.
- Built-in update sources include [Loyalsoldier/v2ray-rules-dat](https://github.com/Loyalsoldier/v2ray-rules-dat), [v2fly/geoip](https://github.com/v2fly/geoip), [v2fly/domain-list-community](https://github.com/v2fly/domain-list-community), [Chocolate4U/Iran-v2ray-rules](https://github.com/Chocolate4U/Iran-v2ray-rules), and [runetfreedom/russia-v2ray-rules-dat](https://github.com/runetfreedom/russia-v2ray-rules-dat).

## Development

Open the project root in Android Studio, or build it with Gradle wrapper:

```powershell
.\gradlew.bat assembleDebug
```

On macOS or Linux:

```bash
./gradlew assembleDebug
```

The build:

- uses the Android SDK and NDK
- prepares the bundled Xray-core asset
- builds the native `setuidgid` helper
- packages native runtime components for `arm64-v8a`

If Gradle cannot find Android NDK, set `ndk.dir` in `local.properties`, set `ANDROID_NDK_HOME`, or install an NDK under the Android SDK.

## License

[GPL-3.0](LICENSE)

## Credits

- [@XTLS/Xray-core](https://github.com/XTLS/Xray-core)
- [@topjohnwu/libsu](https://github.com/topjohnwu/libsu)
- [@compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix)
- [@2dust/v2rayNG](https://github.com/2dust/v2rayNG)
- [@Loyalsoldier/v2ray-rules-dat](https://github.com/Loyalsoldier/v2ray-rules-dat)
- [@v2fly/geoip](https://github.com/v2fly/geoip)
- [@v2fly/domain-list-community](https://github.com/v2fly/domain-list-community)
- [@Chocolate4U/Iran-v2ray-rules](https://github.com/Chocolate4U/Iran-v2ray-rules)
- [@runetfreedom/russia-v2ray-rules-dat](https://github.com/runetfreedom/russia-v2ray-rules-dat)
