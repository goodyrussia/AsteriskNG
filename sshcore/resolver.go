package main

import (
	"context"
	"crypto/rand"
	"encoding/binary"
	"fmt"
	"net"
	"os/exec"
	"strings"
	"sync"
	"time"
)

const (
	resolverCacheTTL = 5 * time.Minute
	resolverTimeout  = 4 * time.Second
)

type cachedHost struct {
	ips     []string
	expires time.Time
}

var hostCache = struct {
	sync.Mutex
	entries map[string]cachedHost
}{entries: make(map[string]cachedHost)}

// resolveAndroidHost never calls net.DefaultResolver. Android rejects the
// pure-Go resolver's raw UDP writes to dnsproxyd at [::1]:53 for this daemon.
// It uses real carrier DNS addresses, shell fallbacks, and an in-process cache.
func resolveAndroidHost(ctx context.Context, host string, fallbackIPs []string) ([]string, string, error) {
	host = strings.TrimSpace(strings.TrimSuffix(host, "."))
	if host == "" {
		return nil, "", fmt.Errorf("empty hostname")
	}
	if ip := net.ParseIP(host); ip != nil {
		return []string{ip.String()}, "literal_ip", nil
	}

	hostCache.Lock()
	if entry, ok := hostCache.entries[strings.ToLower(host)]; ok && time.Now().Before(entry.expires) {
		ips := append([]string(nil), entry.ips...)
		hostCache.Unlock()
		Log.Info("resolver cache: %s -> %s", host, strings.Join(ips, ","))
		return ips, "cache", nil
	}
	hostCache.Unlock()

	servers := androidCarrierDNSServers(ctx)
	if len(servers) > 0 {
		if ips := resolveViaCarrierDNS(ctx, host, servers); len(ips) > 0 {
			cacheHost(host, ips)
			return ips, "android_carrier_dns", nil
		}
		Log.Warn("carrier DNS failed for %s (servers=%s)", host, strings.Join(servers, ","))
	} else {
		Log.Warn("no Android carrier DNS servers found for %s", host)
	}

	if ips := shellResolve(ctx, host); len(ips) > 0 {
		cacheHost(host, ips)
		return ips, "android_shell_dns", nil
	}

	for _, candidate := range fallbackIPs {
		if ip := net.ParseIP(strings.TrimSpace(candidate)); ip != nil {
			ips := []string{ip.String()}
			cacheHost(host, ips)
			return ips, "profile_fallback_ip", nil
		}
	}
	return nil, "failed", fmt.Errorf("Android DNS failed for %q; no usable fallback IP", host)
}

func cacheHost(host string, ips []string) {
	hostCache.Lock()
	hostCache.entries[strings.ToLower(host)] = cachedHost{
		ips: append([]string(nil), ips...), expires: time.Now().Add(resolverCacheTTL),
	}
	hostCache.Unlock()
	Log.Info("resolver: %s -> %s", host, strings.Join(ips, ","))
}

func androidCarrierDNSServers(ctx context.Context) []string {
	properties := []string{
		"net.dns1", "net.dns2", "net.dns3", "net.dns4",
		"net.rmnet_data0.dns1", "net.rmnet_data0.dns2",
		"net.rmnet_data1.dns1", "net.rmnet_data1.dns2",
		"net.rmnet0.dns1", "net.rmnet0.dns2",
		"dhcp.wlan0.dns1", "dhcp.wlan0.dns2",
		"dhcp.rmnet_data0.dns1", "dhcp.rmnet_data0.dns2",
	}
	var servers []string
	seen := make(map[string]bool)
	for _, property := range properties {
		cmdCtx, cancel := context.WithTimeout(ctx, 300*time.Millisecond)
		out, err := runResolverCommand(cmdCtx, []string{"/system/bin/getprop", property}, []string{"getprop", property})
		cancel()
		if err != nil {
			continue
		}
		ip := net.ParseIP(strings.TrimSpace(string(out)))
		if ip == nil || ip.To4() == nil {
			continue
		}
		server := ip.String()
		if !seen[server] {
			seen[server] = true
			servers = append(servers, server)
		}
	}
	return servers
}

func resolveViaCarrierDNS(ctx context.Context, host string, servers []string) []string {
	result := make(chan []string, len(servers))
	for _, server := range servers {
		server := server
		go func() {
			queryCtx, cancel := context.WithTimeout(ctx, resolverTimeout)
			defer cancel()
			ips := queryDNSA(queryCtx, server, host)
			result <- ips
		}()
	}
	for range servers {
		select {
		case ips := <-result:
			if len(ips) > 0 {
				return ips
			}
		case <-ctx.Done():
			return nil
		}
	}
	return nil
}

func queryDNSA(ctx context.Context, server, host string) []string {
	packet, id := makeDNSQuery(host)
	dialer := net.Dialer{}
	conn, err := dialer.DialContext(ctx, "udp", net.JoinHostPort(server, "53"))
	if err != nil {
		return nil
	}
	defer conn.Close()
	_ = conn.SetDeadline(time.Now().Add(resolverTimeout))
	if _, err := conn.Write(packet); err != nil {
		return nil
	}
	response := make([]byte, 4096)
	n, err := conn.Read(response)
	if err != nil {
		return nil
	}
	return parseDNSA(response[:n], id)
}

func makeDNSQuery(host string) ([]byte, uint16) {
	var idBytes [2]byte
	_, _ = rand.Read(idBytes[:])
	id := binary.BigEndian.Uint16(idBytes[:])
	packet := make([]byte, 12, 12+len(host)+2)
	binary.BigEndian.PutUint16(packet[0:2], id)
	binary.BigEndian.PutUint16(packet[2:4], 0x0100) // recursion desired
	binary.BigEndian.PutUint16(packet[4:6], 1)
	for _, label := range strings.Split(strings.TrimSuffix(host, "."), ".") {
		if len(label) > 63 {
			return packet, id
		}
		packet = append(packet, byte(len(label)))
		packet = append(packet, label...)
	}
	packet = append(packet, 0, 0, 1, 0, 1) // root, A, IN
	return packet, id
}

func parseDNSA(packet []byte, id uint16) []string {
	if len(packet) < 12 || binary.BigEndian.Uint16(packet[0:2]) != id {
		return nil
	}
	if binary.BigEndian.Uint16(packet[6:8]) == 0 {
		return nil
	}
	offset := 12
	if !skipDNSName(packet, &offset) || offset+4 > len(packet) {
		return nil
	}
	offset += 4 // question type/class
	answers := int(binary.BigEndian.Uint16(packet[6:8]))
	ips := make([]string, 0, answers)
	for i := 0; i < answers && offset+12 <= len(packet); i++ {
		if !skipDNSName(packet, &offset) || offset+10 > len(packet) {
			return ips
		}
		typ := binary.BigEndian.Uint16(packet[offset : offset+2])
		class := binary.BigEndian.Uint16(packet[offset+2 : offset+4])
		length := int(binary.BigEndian.Uint16(packet[offset+8 : offset+10]))
		offset += 10
		if offset+length > len(packet) {
			return ips
		}
		if typ == 1 && class == 1 && length == 4 {
			ips = append(ips, net.IP(packet[offset:offset+4]).String())
		}
		offset += length
	}
	return uniqueIPs(ips)
}

func skipDNSName(packet []byte, offset *int) bool {
	for *offset < len(packet) {
		length := int(packet[*offset])
		if length == 0 {
			*offset++
			return true
		}
		if length&0xc0 == 0xc0 {
			if *offset+1 >= len(packet) {
				return false
			}
			*offset += 2
			return true
		}
		if length > 63 || *offset+1+length > len(packet) {
			return false
		}
		*offset += 1 + length
	}
	return false
}

func runResolverCommand(ctx context.Context, primary []string, fallback []string) ([]byte, error) {
	out, err := exec.CommandContext(ctx, primary[0], primary[1:]...).Output()
	if err == nil {
		return out, nil
	}
	return exec.CommandContext(ctx, fallback[0], fallback[1:]...).Output()
}

func shellResolve(ctx context.Context, host string) []string {
	commands := [][]string{
		{"/system/bin/getent", "ahostsv4", host},
		{"getent", "ahostsv4", host},
		{"/system/bin/toybox", "getent", "ahostsv4", host},
		{"toybox", "getent", "ahostsv4", host},
		{"/system/bin/ping", "-c", "1", "-W", "2", host},
		{"ping", "-c", "1", "-W", "2", host},
	}
	for _, command := range commands {
		cmdCtx, cancel := context.WithTimeout(ctx, resolverTimeout)
		out, err := exec.CommandContext(cmdCtx, command[0], command[1:]...).CombinedOutput()
		cancel()
		if err == nil || len(out) > 0 {
			if ips := extractIPv4s(string(out)); len(ips) > 0 {
				Log.Info("resolver shell method=%s host=%s -> %s", command[0], host, strings.Join(ips, ","))
				return ips
			}
		}
	}
	return nil
}

func extractIPv4s(text string) []string {
	var ips []string
	for _, field := range strings.FieldsFunc(text, func(r rune) bool {
		return !(r == '.' || (r >= '0' && r <= '9'))
	}) {
		ip := net.ParseIP(field)
		if ip != nil && ip.To4() != nil {
			ips = append(ips, ip.To4().String())
		}
	}
	return uniqueIPs(ips)
}

func uniqueIPs(ips []string) []string {
	seen := make(map[string]bool, len(ips))
	out := make([]string, 0, len(ips))
	for _, ip := range ips {
		if net.ParseIP(ip) != nil && !seen[ip] {
			seen[ip] = true
			out = append(out, ip)
		}
	}
	return out
}
