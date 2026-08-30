// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package main

import (
	"encoding/binary"
	"io"
	"net"
	"strconv"
	"strings"

	"golang.org/x/crypto/ssh"
)

// socks5Server implements a minimal RFC 1928 SOCKS5 server that forwards
// connections through an established SSH client using direct-tcpip channels.
type socks5Server struct {
	sshClient *ssh.Client
}

func newSocks5Server(client *ssh.Client) *socks5Server {
	return &socks5Server{sshClient: client}
}

func (s *socks5Server) serveConn(conn net.Conn) {
	defer conn.Close()

	// 1. Negotiate handshake.
	clientGreeting := make([]byte, 2)
	if _, err := io.ReadFull(conn, clientGreeting); err != nil {
		Log.Debug("socks5: read greeting: %v", err)
		return
	}
	if clientGreeting[0] != 0x05 {
		Log.Debug("socks5: unsupported version %d", clientGreeting[0])
		conn.Write([]byte{0x05, 0xFF})
		return
	}
	nMethods := int(clientGreeting[1])
	if nMethods < 1 || nMethods > 255 {
		conn.Write([]byte{0x05, 0xFF})
		return
	}
	methods := make([]byte, nMethods)
	if _, err := io.ReadFull(conn, methods); err != nil {
		return
	}

	// We support no-auth (0x00) and user-pass (0x02).
	authMethod := byte(0xFF)
	for _, m := range methods {
		if m == 0x00 && authMethod == 0xFF {
			authMethod = 0x00
		}
		if m == 0x02 {
			authMethod = 0x02
		}
	}
	if authMethod == 0xFF {
		conn.Write([]byte{0x05, 0xFF})
		return
	}
	conn.Write([]byte{0x05, authMethod})

	// 2. If user-pass auth, we accept anything (outbound proxy auth is in the SSH config).
	if authMethod == 0x02 {
		conn.Write([]byte{0x01, 0x00}) // accept
	}

	// 3. Read request.
	reqHeader := make([]byte, 4)
	if _, err := io.ReadFull(conn, reqHeader); err != nil {
		return
	}
	ver, cmd, _, atyp := reqHeader[0], reqHeader[1], reqHeader[2], reqHeader[3]
	if ver != 0x05 {
		Log.Debug("socks5: bad request version %d", ver)
		return
	}

	destHost := ""
	var destPort uint16

	switch atyp {
	case 0x01: // IPv4
		addr := make([]byte, 4)
		if _, err := io.ReadFull(conn, addr); err != nil {
			return
		}
		destHost = net.IP(addr).String()
	case 0x03: // Domain name
		lenByte := make([]byte, 1)
		if _, err := io.ReadFull(conn, lenByte); err != nil {
			return
		}
		domain := make([]byte, lenByte[0])
		if _, err := io.ReadFull(conn, domain); err != nil {
			return
		}
		destHost = string(domain)
	case 0x04: // IPv6
		addr := make([]byte, 16)
		if _, err := io.ReadFull(conn, addr); err != nil {
			return
		}
		destHost = net.IP(addr).String()
	default:
		Log.Debug("socks5: unsupported address type %d", atyp)
		conn.Write([]byte{0x05, 0x08, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00})
		return
	}

	portBytes := make([]byte, 2)
	if _, err := io.ReadFull(conn, portBytes); err != nil {
		return
	}
	destPort = binary.BigEndian.Uint16(portBytes)

	Log.Debug("socks5: request %s/%s %s:%d", cmdString(cmd), atypString(atyp), destHost, destPort)

	switch cmd {
	case 0x01: // CONNECT
		s.handleConnect(conn, destHost, destPort)
	case 0x03: // UDP ASSOCIATE
		s.handleUDP(conn, destHost, destPort)
	default:
		Log.Debug("socks5: unsupported command %d", cmd)
		conn.Write([]byte{0x05, 0x07, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00})
	}
}

func (s *socks5Server) handleConnect(conn net.Conn, host string, port uint16) {
	// Open SSH direct-tcpip channel.
	payload := struct {
		DestAddr string
		DestPort uint32
		SrcAddr  string
		SrcPort  uint32
	}{
		DestAddr: host,
		DestPort: uint32(port),
		SrcAddr:  "127.0.0.1",
		SrcPort:  0,
	}
	ch, reqs, err := s.sshClient.OpenChannel("direct-tcpip", ssh.Marshal(payload))
	if err != nil {
		Log.Debug("socks5: ssh direct-tcpip to %s:%d failed: %v", host, port, err)
		reply := []byte{0x05, 0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00}
		if strings.Contains(err.Error(), "connection refused") {
			reply[1] = 0x05 // connection refused
		} else if strings.Contains(err.Error(), "host unreachable") {
			reply[1] = 0x04 // host unreachable
		} else {
			reply[1] = 0x03 // network unreachable
		}
		conn.Write(reply)
		return
	}
	go ssh.DiscardRequests(reqs)

	// Send success reply.
	bndHost, bndPort := "0.0.0.0", uint16(0)
	reply := socksReply(0x00, bndHost, bndPort)
	conn.Write(reply)

	// Bidirectional relay.
	relay := func(dst io.Writer, src io.Reader, done chan struct{}) {
		io.Copy(dst, src)
		done <- struct{}{}
	}
	done := make(chan struct{}, 2)
	go relay(ch, conn, done)
	go relay(conn, ch, done)
	<-done
	ch.Close()
}

func (s *socks5Server) handleUDP(conn net.Conn, host string, port uint16) {
	// UDP ASSOCIATE: reply success and keep the association alive. The SSH
	// tunnel is TCP-only (direct-tcpip channels); UDP flows are not relayed
	// through SSH. Clients that need UDP (e.g. DNS) fall back to the direct
	// outbound configured in Xray.
	reply := socksReply(0x00, host, port)
	conn.Write(reply)
	buf := make([]byte, 256)
	for {
		_, err := conn.Read(buf)
		if err != nil {
			return
		}
	}
}

func socksReply(code byte, host string, port uint16) []byte {
	ip := net.ParseIP(host)
	var atyp byte
	var addr []byte
	if ip == nil || ip.To4() != nil {
		atyp = 0x01 // IPv4
		if ip == nil {
			addr = net.ParseIP("0.0.0.0").To4()
		} else {
			addr = ip.To4()
		}
	} else {
		atyp = 0x04 // IPv6
		addr = ip.To16()
	}
	b := make([]byte, 0, 10+len(addr))
	b = append(b, 0x05, code, 0x00, atyp)
	b = append(b, addr...)
	p := make([]byte, 2)
	binary.BigEndian.PutUint16(p, port)
	b = append(b, p...)
	return b
}

func cmdString(cmd byte) string {
	switch cmd {
	case 0x01:
		return "CONNECT"
	case 0x03:
		return "UDP"
	default:
		return strconv.Itoa(int(cmd))
	}
}

func atypString(atyp byte) string {
	switch atyp {
	case 0x01:
		return "IPv4"
	case 0x03:
		return "DOMAIN"
	case 0x04:
		return "IPv6"
	default:
		return strconv.Itoa(int(atyp))
	}
}