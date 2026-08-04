#!/usr/bin/env python3
"""Validate AsteriskNG's retained config shapes against the pinned Eichgee Xray."""

import copy
import json
import subprocess
import sys
import tempfile
from pathlib import Path

XRAY = Path(sys.argv[1] if len(sys.argv) > 1 else "/tmp/xray-linux")
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
}


def config(outbound: dict) -> dict:
    selected = copy.deepcopy(outbound)
    selected["tag"] = "proxy"
    return {
        "log": {"loglevel": "warning"},
        "dns": {"servers": ["1.1.1.1"], "queryStrategy": "UseIPv4"},
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
    if not XRAY.is_file():
        raise SystemExit(f"Xray binary not found: {XRAY}")
    with tempfile.TemporaryDirectory(prefix="asteriskng-configs-") as temp:
        for name, outbound in OUTBOUNDS.items():
            path = Path(temp, f"{name}.json")
            path.write_text(json.dumps(config(outbound)), encoding="utf-8")
            result = subprocess.run(
                [str(XRAY), "run", "-test", "-config", str(path)],
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
