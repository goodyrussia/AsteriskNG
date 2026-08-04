# Simplified Settings and Wi-Fi Hotspot Sharing

## Scope

Reduce AsteriskNG to a fixed, reliable root-TPROXY policy while retaining user controls that materially change normal use.

## Visible settings

- Appearance: color mode, theme color, language.
- Network: DNS primary, DNS secondary, Share via Wi-Fi hotspot (default on), Start on boot.
- Diagnostics: core log and logcat viewers.
- About and licenses.
- Per-app filtering remains on its existing page.

## Fixed runtime policy

- Proxy endpoint hostnames are always resolved before Xray starts and pinned into Xray DNS hosts.
- Endpoint domains are always eligible for direct bootstrap DNS.
- Normal DNS uses the two user-entered DNS addresses and is routed through the selected proxy.
- FakeDNS, sniffing, routeOnly, Mux, Fragment, access logging, HTTP inbound, LAN SOCKS listening, dynamic proxy ports, custom DNS hosts, custom direct-DNS domain rules, ignored interfaces, and custom private CIDRs are disabled.
- Xray log level is fixed to error.
- IPv4-only operation is enforced while the tunnel runs; IPv6 OUTPUT and FORWARD traffic are rejected to prevent leaks.
- Internal ports remain fixed and hidden.

## Hotspot behavior

The `shareHotspot` preference defaults to true. When enabled, PREROUTING TPROXY rules capture traffic arriving from Wi-Fi hotspot interface families `wlan+`, `swlan+`, `ap+`, and `softap+`. USB, Bluetooth, and Ethernet tethering are not included. When disabled, no downstream hotspot interface prefixes are installed.

## Migration

Legacy advanced preference keys are ignored and removed during the next save. Existing server profiles, selected server, per-app choices, theme, language, DNS pair, and start-on-boot preference are retained. Existing installations gain hotspot sharing enabled by default unless the new preference has explicitly been saved as false.

## Verification

- Compile-gated GitHub Actions arm64 release build.
- Xray fixture validation using the pinned Eichgee Xray 25.5.16 Linux core.
- Static assertions for fixed config policy, hotspot prefix mapping, IPv6 leak prevention, package identity, ABI, manifest removals, signing certificate, and APK native payload.
