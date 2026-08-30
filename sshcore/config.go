// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package main

import (
	"encoding/json"
	"fmt"
	"os"
	"strconv"
)

// SshTunnelMode selects how the SSH TCP connection is established.
type SshTunnelMode string

const (
	ModeDirect    SshTunnelMode = "direct"     // plain TCP to the SSH server
	ModeProxy     SshTunnelMode = "proxy"      // HTTP CONNECT proxy -> SSH server
	ModeTLS       SshTunnelMode = "tls"        // TCP -> TLS (SNI) -> SSH server
	ModeTLSProxy  SshTunnelMode = "tls_proxy"  // HTTP CONNECT proxy -> TLS (SNI) -> SSH server
)

// PayloadSplitMode selects how the injected payload is written.
type PayloadSplitMode string

const (
	SplitNone        PayloadSplitMode = "none"
	SplitInstant     PayloadSplitMode = "instant"
	SplitDelay       PayloadSplitMode = "delay"
	SplitSplit       PayloadSplitMode = "split"
	SplitSplitDelay  PayloadSplitMode = "split_delay"
)

// Config is the flat JSON configuration consumed by the sshcore daemon.
// Field names deliberately mirror the NPV Tunnel SshConfig model so that
// existing SSH provider setups carry over cleanly.
type Config struct {
	// Listen is the local SOCKS5 listen address, e.g. "127.0.0.1:10808".
	Listen string `json:"listen"`

	// SSH server.
	SshAddress  string `json:"ssh_address"`
	SshPort     int    `json:"ssh_port"`
	SshUsername string `json:"ssh_username"`
	SshPassword string `json:"ssh_password"`

	// Connection mode: direct | proxy | tls | tls_proxy.
	TunnelMode string `json:"tunnel_mode"`

	// HTTP CONNECT proxy (used by proxy and tls_proxy modes).
	HttpProxy        string `json:"http_proxy"`
	HttpProxyPort    int    `json:"http_proxy_port"`
	ProxyUsername    string `json:"proxy_username"`
	ProxyPassword    string `json:"proxy_password"`
	AuthenticateProxy bool  `json:"authenticate_proxy"`

	// TLS wrapping (used by tls and tls_proxy modes).
	Sni            string `json:"sni"`
	TLSVersion     string `json:"tls_version"`
	TLSAllowInsecure bool `json:"tls_allow_insecure"`

	// Payload injection.
	PayloadEnabled   bool   `json:"payload_enabled"`
	Payload          string `json:"payload"`
	PayloadSplitMode string `json:"payload_split_mode"`
	PayloadDelayMs   int    `json:"payload_delay_ms"`

	// DNS through tunnel (UDP helper), optional.
	UdpgwPort           int  `json:"udpgw_port"`
	UdpgwTransparentDNS bool `json:"udpgw_transparent_dns"`

	// Logging.
	LogLevel string `json:"log_level"`
}

func (c *Config) validate() error {
	if c.Listen == "" {
		return fmt.Errorf("listen address is required")
	}
	if c.SshAddress == "" {
		return fmt.Errorf("ssh_address is required")
	}
	if c.SshPort <= 0 || c.SshPort > 65535 {
		return fmt.Errorf("invalid ssh_port: %d", c.SshPort)
	}
	switch SshTunnelMode(c.TunnelMode) {
	case ModeDirect, ModeProxy, ModeTLS, ModeTLSProxy:
	default:
		return fmt.Errorf("unsupported tunnel_mode: %q", c.TunnelMode)
	}
	switch PayloadSplitMode(c.PayloadSplitMode) {
	case SplitNone, SplitInstant, SplitDelay, SplitSplit, SplitSplitDelay:
	default:
		return fmt.Errorf("unsupported payload_split_mode: %q", c.PayloadSplitMode)
	}
	mode := SshTunnelMode(c.TunnelMode)
	if mode == ModeProxy || mode == ModeTLSProxy {
		if c.HttpProxy == "" {
			return fmt.Errorf("http_proxy is required for mode %s", mode)
		}
	}
	return nil
}

func (c *Config) tlsVersion() uint16 {
	switch c.TLSVersion {
	case "1.0":
		return 0x0301
	case "1.1":
		return 0x0302
	case "1.3":
		return 0x0304
	default:
		return 0x0303 // TLS 1.2
	}
}

func loadConfig(path string) (*Config, error) {
	raw, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("failed to read config: %w", err)
	}
	var cfg Config
	if err := json.Unmarshal(raw, &cfg); err != nil {
		return nil, fmt.Errorf("failed to parse config: %w", err)
	}
	if cfg.LogLevel == "" {
		cfg.LogLevel = "info"
	}
	if cfg.SshPort == 0 {
		cfg.SshPort = 22
	}
	if cfg.HttpProxyPort == 0 {
		cfg.HttpProxyPort = 8080
	}
	if cfg.PayloadSplitMode == "" {
		cfg.PayloadSplitMode = string(SplitNone)
	}
	if cfg.Listen == "" {
		cfg.Listen = "127.0.0.1:10808"
	}
	if err := cfg.validate(); err != nil {
		return nil, err
	}
	return &cfg, nil
}

func (c *Config) udpgwPortOrDefault() int {
	if c.UdpgwPort > 0 {
		return c.UdpgwPort
	}
	if c.UdpgwTransparentDNS {
		return 7300
	}
	return 0
}

func portInt(s string, fallback int) int {
	if n, err := strconv.Atoi(s); err == nil && n > 0 && n <= 65535 {
		return n
	}
	return fallback
}
