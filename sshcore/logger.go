// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package main

import (
	"fmt"
	"log"
	"os"
)

type logger struct {
	level int
	l     *log.Logger
}

var Log = &logger{level: 1, l: log.New(os.Stdout, "", log.LstdFlags)}

const (
	levelDebug = 0
	levelInfo  = 1
	levelWarn  = 2
	levelError = 3
)

func parseLogLevel(name string) int {
	switch name {
	case "debug":
		return levelDebug
	case "info":
		return levelInfo
	case "warn", "warning":
		return levelWarn
	case "error":
		return levelError
	default:
		return levelInfo
	}
}

func (lg *logger) Configure(levelName string) {
	lg.level = parseLogLevel(levelName)
}

func (lg *logger) Debug(format string, args ...any) {
	if lg.level <= levelDebug {
		lg.l.Printf("[DEBUG] "+format, args...)
	}
}

func (lg *logger) Info(format string, args ...any) {
	if lg.level <= levelInfo {
		lg.l.Printf("[INFO] "+format, args...)
	}
}

func (lg *logger) Warn(format string, args ...any) {
	if lg.level <= levelWarn {
		lg.l.Printf("[WARN] "+format, args...)
	}
}

func (lg *logger) Error(format string, args ...any) {
	if lg.level <= levelError {
		lg.l.Printf("[ERROR] "+format, args...)
	}
}

func (lg *logger) Fatalf(format string, args ...any) {
	lg.l.Printf("[FATAL] "+format, args...)
	fmt.Fprintf(os.Stderr, "[FATAL] "+format+"\n", args...)
	os.Exit(1)
}
