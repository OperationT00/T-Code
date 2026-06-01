# tcode Hard Rename Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish the `tcode` namespace and intentionally remove all pre-release compatibility reads.

**Architecture:** Apply one repository-wide rename map, move Java package directories, rename `TCode*` source files, then verify with zero-match scans and the full regression suite. The runtime exposes only new `tcode` identifiers.

**Tech Stack:** Java 17, Maven, JUnit 5, TypeScript, Markdown.

---

### Task 1: Lock Breaking Rename Behavior

- [x] Update auth tests to accept `X-TCode-API-Key` and reject the removed pre-release header.
- [x] Update history tests to require `.tcode`.
- [x] Run focused tests and confirm the old implementation fails.

### Task 2: Move Java Namespace

- [x] Move production and test packages into `src/**/java/com/tcode`.
- [x] Rename product-specific source files and symbols to `TCode*`.
- [x] Replace package references and Maven entry points.

### Task 3: Rename Runtime Configuration

- [x] Replace runtime directories, `TCODE_*`, `tcode.*`, headers, thread names and MCP client name.
- [x] Rename `tcode-*` asset files and references.

### Task 4: Rename Documentation

- [x] Replace old identifiers in active docs, historical docs, website and examples.
- [x] Add a breaking rename note to `CHANGELOG.md`.

### Task 5: Verify

- [x] Scan repository for forbidden old identifiers.
- [x] Run TypeScript tests.
- [x] Run Java full suite and TUI smoke.
- [x] Run `mvn clean package`.
