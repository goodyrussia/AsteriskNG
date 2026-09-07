package main

import (
	"encoding/binary"
	"net"
	"testing"
)

func TestMakeAndParseDNSA(t *testing.T) {
	query, id := makeDNSQuery("example.com")
	if len(query) < 12 || binary.BigEndian.Uint16(query[:2]) != id {
		t.Fatalf("invalid query id")
	}
	response := append([]byte(nil), query[:12]...)
	binary.BigEndian.PutUint16(response[2:4], 0x8180)
	binary.BigEndian.PutUint16(response[6:8], 1)
	// Preserve the question section before appending the answer.
	response = append(response, query[12:]...)
	// Answer: compressed name, A, IN, TTL, 4-byte address.
	response = append(response, 0xc0, 0x0c, 0, 1, 0, 1, 0, 0, 0, 30, 0, 4, 77, 90, 19, 27)
	got := parseDNSA(response, id)
	want := []string{"77.90.19.27"}
	if len(got) != 1 || got[0] != want[0] {
		t.Fatalf("parseDNSA() = %v, want %v", got, want)
	}
}

func TestExtractIPv4s(t *testing.T) {
	got := extractIPv4s("host has address 77.90.19.27\n77.90.19.27")
	if len(got) != 1 || got[0] != "77.90.19.27" {
		t.Fatalf("extractIPv4s() = %v", got)
	}
}

func TestResolveAndroidHostLiteral(t *testing.T) {
	ips, method, err := resolveAndroidHost(t.Context(), "77.90.19.27", nil)
	if err != nil || method != "literal_ip" || len(ips) != 1 || net.ParseIP(ips[0]) == nil {
		t.Fatalf("literal resolution = ips=%v method=%s err=%v", ips, method, err)
	}
}
