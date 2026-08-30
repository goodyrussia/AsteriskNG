// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package main

import (
	"bufio"
	"context"
	"crypto/tls"
	"encoding/base64"
	"fmt"
	"net"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"

	"golang.org/x/crypto/ssh"
)

// sshTunnel establishes the SSH transport connection using the selected mode,
// optionally wraps it (HTTP CONNECT proxy / TLS), optionally injects the
// payload, and finally performs the SSH handshake. The resulting *ssh.Client
// is served by the local SOCKS5 server.
type sshTunnel struct {
	cfg *Config
}

func newSshTunnel(cfg *Config) *sshTunnel {
	return &sshTunnel{cfg: cfg}
}

func (t *sshTunnel) sshAddr() string {
	return net.JoinHostPort(t.cfg.SshAddress, strconv.Itoa(t.cfg.SshPort))
}

// dial establishes the transport connection per mode.
func (t *sshTunnel) dial(ctx context.Context) (net.Conn, error) {
	mode := SshTunnelMode(t.cfg.TunnelMode)
	switch mode {
	case ModeDirect:
		return t.dialDirect(ctx)
	case ModeProxy:
		return t.dialProxy(ctx)
	case ModeTLS:
		return t.dialTLS(ctx)
	case ModeTLSProxy:
		return t.dialTLSProxy(ctx)
	default:
		return nil, fmt.Errorf("unsupported tunnel mode: %q", mode)
	}
}

func (t *sshTunnel) dialDirect(ctx context.Context) (net.Conn, error) {
	d := net.Dialer{}
	return d.DialContext(ctx, "tcp", t.sshAddr())
}

func (t *sshTunnel) dialProxy(ctx context.Context) (net.Conn, error) {
	conn, err := t.httpConnect(ctx, t.sshAddr())
	if err != nil {
		return nil, err
	}
	return conn, nil
}

func (t *sshTunnel) dialTLS(ctx context.Context) (net.Conn, error) {
	raw, err := t.dialDirect(ctx)
	if err != nil {
		return nil, err
	}
	return t.wrapTLS(raw, t.sshAddr())
}

func (t *sshTunnel) dialTLSProxy(ctx context.Context) (net.Conn, error) {
	conn, err := t.httpConnect(ctx, t.sshAddr())
	if err != nil {
		return nil, err
	}
	return t.wrapTLS(conn, t.sshAddr())
}

// httpConnect establishes an HTTP CONNECT tunnel to target through the
// configured HTTP proxy. Returns the tunneled connection.
func (t *sshTunnel) httpConnect(ctx context.Context, target string) (net.Conn, error) {
	proxyAddr := net.JoinHostPort(t.cfg.HttpProxy, strconv.Itoa(t.cfg.HttpProxyPort))
	Log.Info("http connect proxy %s -> %s", proxyAddr, target)

	d := net.Dialer{}
	conn, err := d.DialContext(ctx, "tcp", proxyAddr)
	if err != nil {
		return nil, fmt.Errorf("connect to http proxy %s: %w", proxyAddr, err)
	}

	req := &http.Request{
		Method: http.MethodConnect,
		URL:    &url.URL{Opaque: target},
		Host:   target,
		Header: make(http.Header),
	}
	if t.cfg.AuthenticateProxy && t.cfg.ProxyUsername != "" {
		cred := base64.StdEncoding.EncodeToString(
			[]byte(t.cfg.ProxyUsername + ":" + t.cfg.ProxyPassword),
		)
		req.Header.Set("Proxy-Authorization", "Basic "+cred)
	}

	if err := req.Write(conn); err != nil {
		conn.Close()
		return nil, fmt.Errorf("write CONNECT request: %w", err)
	}

	br := bufio.NewReader(conn)
	resp, err := http.ReadResponse(br, req)
	if err != nil {
		conn.Close()
		return nil, fmt.Errorf("read CONNECT response: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		conn.Close()
		return nil, fmt.Errorf("http proxy CONNECT failed: %s", resp.Status)
	}
	return conn, nil
}

// wrapTLS wraps an established connection in a TLS client with the configured
// SNI and TLS version.
func (t *sshTunnel) wrapTLS(conn net.Conn, serverName string) (net.Conn, error) {
	sni := t.cfg.Sni
	if sni == "" {
		sni = t.cfg.SshAddress
	}
	tlsConfig := &tls.Config{
		ServerName:         sni,
		MinVersion:         t.cfg.tlsVersion(),
		MaxVersion:         t.cfg.tlsVersion(),
		InsecureSkipVerify: t.cfg.TLSAllowInsecure, //nolint:gosec // user-requested allowInsecure
	}
	tlsConn := tls.Client(conn, tlsConfig)
	if err := tlsConn.HandshakeContext(context.Background()); err != nil {
		conn.Close()
		return nil, fmt.Errorf("tls handshake (sni=%s): %w", sni, err)
	}
	Log.Info("tls handshake ok (sni=%s version=%s)", sni, tlsVersionName(tlsConn.ConnectionState().Version))
	return tlsConn, nil
}

// connect establishes the full SSH connection: transport + optional payload +
// handshake.
func (t *sshTunnel) connect(ctx context.Context) (*ssh.Client, error) {
	transport, err := t.dial(ctx)
	if err != nil {
		return nil, err
	}

	// Payload injection before the SSH banner.
	if t.cfg.PayloadEnabled && t.cfg.Payload != "" {
		tpl := newPayloadTemplate(t.cfg)
		Log.Info("injecting payload: %s", tpl.describe())
		written, err := tpl.write(transport)
		if err != nil {
			transport.Close()
			return nil, fmt.Errorf("write payload: %w", err)
		}
		Log.Info("payload written: %d bytes", written)
	}

	sshConfig := &ssh.ClientConfig{
		User: t.cfg.SshUsername,
		Auth: []ssh.AuthMethod{
			ssh.Password(t.cfg.SshPassword),
		},
		HostKeyCallback: ssh.InsecureIgnoreHostKey(), //nolint:gosec // tunnel app: no host key pinning
		Timeout:         15 * time.Second,
	}

	clientConn, chans, reqs, err := ssh.NewClientConn(transport, t.sshAddr(), sshConfig)
	if err != nil {
		transport.Close()
		return nil, fmt.Errorf("ssh handshake: %w", err)
	}
	client := ssh.NewClient(clientConn, chans, reqs)
	Log.Info("ssh connected: %s@%s mode=%s", t.cfg.SshUsername, t.sshAddr(), t.cfg.TunnelMode)
	return client, nil
}

func tlsVersionName(version uint16) string {
	switch version {
	case tls.VersionTLS10:
		return "1.0"
	case tls.VersionTLS11:
		return "1.1"
	case tls.VersionTLS12:
		return "1.2"
	case tls.VersionTLS13:
		return "1.3"
	default:
		return strconv.Itoa(int(version))
	}
}

// validatePlainString reports whether a value contains only sane characters
// (used when building configs from user input on the Android side).
func validatePlainString(value string) bool {
	return !strings.ContainsAny(value, "\r\n\x00")
}
