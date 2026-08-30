// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package main

import (
	"fmt"
	"io"
	"net"
	"strconv"
	"strings"
	"time"
)

// Payload templates are sent on the SSH transport connection before the SSH
// banner (or after the TLS/HTTP-CONNECT wrapper is established). They let the
// client masquerade as a normal HTTPS/HTTP session in front of the SSH
// handshake, which is the classic HTTP Injector / SSH Custom technique.
//
// Supported template variables:
//
//	[host]        SSH server address
//	[host_port]   SSH server address:port
//	[port]        SSH server port
//	[method]      HTTP method used in the wrapper (GET by default)
//	[ssh]         SSH banner placeholder (client SSH version line)
//	[banner]      alias of [ssh]
//	[crlf]        carriage-return line-feed (\r\n)
//	[lf]          line-feed (\n)
//
// Supported split markers (written into the payload string):
//
//	[split]          split point, send second part immediately after a short gap
//	[split_delay]    split point, send second part after the configured delay
//	[instant_split]  alias of [split]
//	[delay_split]    alias of [split_delay]
//	||<sec>-split||  split with an explicit delay in seconds, e.g. ||1.5-split||
//
// The actual split is controlled by the selected PayloadSplitMode, which the
// app maps from the user's UI choice; markers in the payload string refine it.

const (
	crlf = "\r\n"
	lf   = "\n"
)

type payloadTemplate struct {
	raw          string
	host         string
	hostPort     string
	port         string
	method       string
	sshBanner    string
	splitDelayMs int
	splitMode    PayloadSplitMode
}

func newPayloadTemplate(cfg *Config) *payloadTemplate {
	host := cfg.SshAddress
	port := strconv.Itoa(cfg.SshPort)
	return &payloadTemplate{
		raw:          cfg.Payload,
		host:         host,
		hostPort:     net.JoinHostPort(host, port),
		port:         port,
		method:       "GET",
		sshBanner:    "SSH-2.0-OpenSSH_8.2p1 Ubuntu-4ubuntu0.3",
		splitDelayMs: cfg.PayloadDelayMs,
		splitMode:    PayloadSplitMode(cfg.PayloadSplitMode),
	}
}

// expand substitutes template variables. Split markers are preserved and
// handled during writing.
func (p *payloadTemplate) expand(input string) string {
	r := strings.NewReplacer(
		"[host]", p.host,
		"[host_port]", p.hostPort,
		"[port]", p.port,
		"[method]", p.method,
		"[ssh]", p.sshBanner,
		"[banner]", p.sshBanner,
		"[crlf]", crlf,
		"[lf]", lf,
		"[CRLF]", crlf,
	)
	return r.Replace(input)
}

// parts splits the expanded payload at split markers and returns the segments
// with the delay to use before writing each subsequent segment.
func (p *payloadTemplate) parts() [][]byte {
	expanded := p.expand(p.raw)
	var parts [][]byte
	var pending strings.Builder
	flush := func() {
		if pending.Len() > 0 {
			parts = append(parts, []byte(pending.String()))
			pending.Reset()
		}
	}

	i := 0
	for i < len(expanded) {
		// Try explicit ||<sec>-split|| markers first.
		if m := matchExplicitSplit(expanded[i:]); m != nil {
			flush()
			parts = append(parts, []byte(m.suffix)) // second half becomes a new part
			i += m.consumed
			continue
		}
		// Then bracket split markers.
		if strings.HasPrefix(expanded[i:], "[split]") {
			flush()
			i += len("[split]")
			continue
		}
		if strings.HasPrefix(expanded[i:], "[instant_split]") {
			flush()
			i += len("[instant_split]")
			continue
		}
		if strings.HasPrefix(expanded[i:], "[split_delay]") {
			flush()
			i += len("[split_delay]")
			continue
		}
		if strings.HasPrefix(expanded[i:], "[delay_split]") {
			flush()
			i += len("[delay_split]")
			continue
		}
		pending.WriteByte(expanded[i])
		i++
	}
	flush()
	return parts
}

type explicitSplitMatch struct {
	suffix   string
	consumed int
}

// matchExplicitSplit matches ||<sec>-split|| at the start of s.
func matchExplicitSplit(s string) *explicitSplitMatch {
	if !strings.HasPrefix(s, "||") {
		return nil
	}
	end := strings.Index(s, "-split||")
	if end < 0 {
		return nil
	}
	value := s[2:end]
	if _, err := strconv.ParseFloat(value, 64); err != nil {
		return nil
	}
	consumed := end + len("-split||")
	return &explicitSplitMatch{suffix: s[consumed:], consumed: consumed}
}

// write writes the expanded payload to the connection, applying the split
// delays. It returns the total bytes written.
func (p *payloadTemplate) write(w io.Writer) (int, error) {
	parts := p.parts()
	if len(parts) == 0 {
		return 0, nil
	}
	if len(parts) == 1 {
		n, err := w.Write(parts[0])
		return n, err
	}

	total := 0
	delayMs := p.splitDelayMs
	if delayMs <= 0 {
		delayMs = 100
	}
	for index, part := range parts {
		if index > 0 {
			// [split]/[instant_split] write the second half after a short gap;
			// [delay_split]/[split_delay] use the configured delay.
			gap := time.Duration(delayMs) * time.Millisecond
			if p.splitMode == SplitInstant || p.splitMode == SplitSplit {
				gap = 20 * time.Millisecond
			}
			if p.splitMode == SplitNone {
				gap = 0
			}
			if gap > 0 {
				time.Sleep(gap)
			}
		}
		n, err := w.Write(part)
		total += n
		if err != nil {
			return total, err
		}
	}
	return total, nil
}

func (p *payloadTemplate) describe() string {
	parts := p.parts()
	lengths := make([]string, len(parts))
	for i, part := range parts {
		lengths[i] = strconv.Itoa(len(part))
	}
	return fmt.Sprintf("parts=%d lengths=[%s]", len(parts), strings.Join(lengths, ","))
}
