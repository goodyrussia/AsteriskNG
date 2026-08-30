// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

// sshcore is the SSH tunneling daemon for AsteriskNG. It establishes an SSH
// connection to a provider server (direct / HTTP-proxy / TLS / TLS+proxy, with
// optional HTTP payload injection) and exposes a local SOCKS5 proxy that
// forwards connections through SSH direct-tcpip channels.
//
// Usage:
//
//	sshcore -config /path/to/config.json
package main

import (
	"context"
	"flag"
	"fmt"
	"net"
	"os/signal"
	"sync"
	"syscall"
	"time"

	"golang.org/x/crypto/ssh"
)

func main() {
	configPath := flag.String("config", "", "path to sshcore JSON config")
	flag.Parse()

	if *configPath == "" {
		Log.Fatalf("missing -config flag")
	}
	cfg, err := loadConfig(*configPath)
	if err != nil {
		Log.Fatalf("config error: %v", err)
	}
	Log.Configure(cfg.LogLevel)

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	if err := run(ctx, cfg); err != nil {
		Log.Fatalf("fatal: %v", err)
	}
	Log.Info("sshcore exited")
}

func run(ctx context.Context, cfg *Config) error {
	tunnel := newSshTunnel(cfg)

	// SOCKS5 listener binds immediately so Xray can always dial it; the SSH
	// tunnel is established in the background and connections are served only
	// once a live tunnel exists.
	listener, err := net.Listen("tcp", cfg.Listen)
	if err != nil {
		return fmt.Errorf("failed to listen on %s: %w", cfg.Listen, err)
	}
	Log.Info("socks5 listening on %s", cfg.Listen)

	// Supervisor: (re)connect with a short backoff.
	var mu sync.Mutex
	var current *sshConnState
	reconnect := make(chan struct{}, 1)
	signalReconnect := func() {
		select {
		case reconnect <- struct{}{}:
		default:
		}
	}

	connectLoop := func() error {
		for {
			select {
			case <-ctx.Done():
				return ctx.Err()
			default:
			}

			client, err := tunnel.connect(ctx)
			if err != nil {
				Log.Error("ssh connect failed: %v (retrying in 3s)", err)
				select {
				case <-ctx.Done():
					return ctx.Err()
				case <-time.After(3 * time.Second):
				}
				continue
			}

			Log.Info("ssh tunnel established (mode=%s)", cfg.TunnelMode)
			state := &sshConnState{client: client, done: make(chan struct{})}
			mu.Lock()
			current = state
			mu.Unlock()

			go func() {
				_ = client.Wait()
				close(state.done)
				Log.Warn("ssh connection lost")
				mu.Lock()
				if current == state {
					current = nil
				}
				mu.Unlock()
				client.Close()
				signalReconnect()
			}()
			return nil
		}
	}

	if err := connectLoop(); err != nil {
		listener.Close()
		return err
	}

	// Background reconnect supervisor.
	go func() {
		for {
			select {
			case <-ctx.Done():
				return
			case <-reconnect:
				Log.Info("reconnecting ssh tunnel...")
				if err := connectLoop(); err != nil {
					if ctx.Err() != nil {
						return
					}
					Log.Error("reconnect failed: %v", err)
				}
			}
		}
	}()

	var wg sync.WaitGroup
	wg.Add(1)
	go func() {
		defer wg.Done()
		<-ctx.Done()
		listener.Close()
		mu.Lock()
		if current != nil {
			current.client.Close()
		}
		mu.Unlock()
	}()

	for {
		conn, err := listener.Accept()
		if err != nil {
			select {
			case <-ctx.Done():
				wg.Wait()
				return nil
			default:
			}
			Log.Error("accept error: %v", err)
			continue
		}

		mu.Lock()
		client := current
		mu.Unlock()
		if client == nil || client.client == nil {
			Log.Warn("rejecting connection: ssh tunnel not ready")
			conn.Close()
			continue
		}

		go func(raw net.Conn, sshClient *sshConnState) {
			defer raw.Close()
			select {
			case <-sshClient.done:
				return
			default:
			}
			newSocks5Server(sshClient.client).serveConn(raw)
		}(conn, client)
	}
}

type sshConnState struct {
	client *ssh.Client
	done   chan struct{}
}
