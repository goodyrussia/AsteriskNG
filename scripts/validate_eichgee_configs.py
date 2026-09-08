#!/usr/bin/env python3
"""Validate AsteriskNG's retained config shapes against the pinned core binary.

The core is now the Xray-core SSH fork (goodyrussia/Xray-core, v25.5.16-ssh)
built on eichgee/Xray-core (v25.5.16) with the native `ssh` outbound ported in.
This validates all transport modes + payload fields of the ssh outbound.
"""

import copy
import json
import subprocess
import sys
import tempfile
from pathlib import Path

# Accept either the core binary or the original xray binary.
CORE = Path(sys.argv[1] if len(sys.argv) > 1 else "/tmp/xray-ssh-linux")
UUID = "11111111-1111-1111-1111-111111111111"

TPROXY = {
    "tag": "tproxy-in",
    "port": 12345,
    "protocol": "dokodemo-door",
    "settings": {"network": "tcp,udp", "followRedirect": True, "userLevel": 0},
    "streamSettings": {"sockopt": {"tproxy": "tproxy"}},
}
TLS_WS = {
    "network": "websocket",
    "security": "tls",
    "tlsSettings": {"serverName": "example.com", "allowInsecure": True},
    "wsSettings": {"path": "/", "host": "example.com"},
}

# Native SSH outbound: all 4 modes + payload.
SSH = {
    "protocol": "ssh",
    "settings": {
        "address": "example.com",
        "port": 22,
        "user": "test",
        "password": "test",
        "tunnelMode": "tls_proxy",
        "sni": "example.com",
        "tlsVersion": "1.2",
        "tlsAllowInsecure": True,
        "httpProxy": "proxy.example.com",
        "httpProxyPort": 8080,
        "proxyUsername": "u",
        "proxyPassword": "p",
        "authenticateProxy": True,
        "payloadEnabled": True,
        "payload": "GET http://[host_port]/ HTTP/1.1\r\nHost: [host]\r\n\r\n[split]SSH-2.0-OpenSSH_8.2p1",
        "payloadSplit": "split_delay",
        "payloadDelayMs": 100,
    },
}

OUTBOUNDS = {
    "vless": {
        "protocol": "vless",
        "settings": {"vnext": [{"address": "example.com", "port": 443, "users": [{"id": UUID, "encryption": "none"}]}]},
        "streamSettings": TLS_WS,
    },
    "vmess": {
        "protocol": "vmess",
        "settings": {"vnext": [{"address": "example.com", "port": 443, "users": [{"id": UUID, "alterId": 0, "security": "auto"}]}]},
        "streamSettings": TLS_WS,
    },
    "trojan": {
        "protocol": "trojan",
        "settings": {"servers": [{"address": "example.com", "port": 443, "password": "test"}]},
        "streamSettings": TLS_WS,
    },
    "shadowsocks": {
        "protocol": "shadowsocks",
        "settings": {"servers": [{"address": "example.com", "port": 8388, "method": "aes-256-gcm", "password": "test"}]},
    },
    "socks": {
        "protocol": "socks",
        "settings": {"servers": [{"address": "example.com", "port": 1080, "users": [{"user": "u", "pass": "p"}]}]},
    },
    "http": {
        "protocol": "http",
        "settings": {"servers": [{"address": "example.com", "port": 8080, "users": [{"user": "u", "pass": "p"}]}]},
    },
    "wireguard": {
        "protocol": "wireguard",
        "settings": {
            "secretKey": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "address": ["172.16.0.2/32"],
            "peers": [{"endpoint": "example.com:51820", "publicKey": "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB="}],
            "mtu": 1420,
        },
    },
    "ssh": SSH,
}


def config(outbound: dict) -> dict:
    selected = copy.deepcopy(outbound)
    selected["tag"] = "proxy"
    return {
        "log": {"loglevel": "warning"},
        "dns": {
            "servers": [
                {
                    "address": "https+local://1.1.1.1/dns-query",
                    "domains": ["full:proxy-bootstrap.example"],
                    "skipFallback": True,
                    "tag": "dns-direct",
                },
                "8.8.8.8",
                "8.8.4.4",
            ],
            "queryStrategy": "UseIPv4",
            "disableFallbackIfMatch": True,
        },
        "inbounds": [TPROXY],
        "outbounds": [selected, {"tag": "dns-out", "protocol": "dns"}],
        "routing": {
            "domainStrategy": "AsIs",
            "rules": [
                {"inboundTag": ["tproxy-in"], "network": "tcp,udp", "port": "53", "outboundTag": "dns-out"},
                {"network": "tcp,udp", "outboundTag": "proxy"},
            ],
        },
    }


def main() -> None:
    if not CORE.is_file():
        raise SystemExit(f"Core binary not found: {CORE}")
    with tempfile.TemporaryDirectory(prefix="asteriskng-configs-") as temp:
        for name, outbound in OUTBOUNDS.items():
            path = Path(temp, f"{name}.json")
            cfg = config(outbound)
            # xray-core requires an explicit rule type.
            for rule in cfg.get("routing", {}).get("rules", []):
                rule.setdefault("type", "field")
            path.write_text(json.dumps(cfg), encoding="utf-8")
            result = subprocess.run(
                [str(CORE), "run", "-test", "-c", str(path)],
                text=True,
                capture_output=True,
            )
            if result.returncode:
                print(result.stdout)
                print(result.stderr, file=sys.stderr)
                raise SystemExit(f"{name} config failed validation")
            print(f"validated: {name}")


if __name__ == "__main__":
    main()
