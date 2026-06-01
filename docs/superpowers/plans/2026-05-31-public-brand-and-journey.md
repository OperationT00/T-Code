# Public Brand And Journey Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish `t-code v1.0.0` with a large `T` startup logo and a GitHub-friendly capability journey.

**Architecture:** Keep package names, persistent directories, environment variables, system properties and the legacy API header unchanged for compatibility. Update only presentation code, bundled resource text and public documentation.

**Tech Stack:** Java 17, Maven, JUnit 5, Markdown, TypeScript.

---

### Task 1: Lock The Startup Banner Contract

**Files:**
- Modify: `src/test/java/com/tcode/cli/MainInputNormalizationTest.java`
- Modify: `src/main/java/com/tcode/cli/CliPresentation.java`

- [x] Require `t-code`, `v1.0.0`, a five-line uppercase `T` logo and no `π`.
- [x] Run `mvn test -Dtest=MainInputNormalizationTest -DskipTests=false` and confirm the old banner fails.
- [x] Update `CliPresentation` and rerun the test until it passes.

### Task 2: Clean Bundled Resource Text

**Files:**
- Modify: `src/main/resources/prompts/base.md`
- Modify: `src/main/resources/skills/web-access/**`

- [x] Replace bundled user-visible `TCode` references with `t-code`.
- [x] Run `rg -n "TCode" src/main/resources` and confirm no matches.

### Task 3: Publish The Capability Journey

**Files:**
- Create: `CHANGELOG.md`
- Create: `docs/journey/*.md`
- Modify: `README.md`
- Modify: `AGENTS.md`

- [x] Document versions `v0.1.0` through `v1.0.0`.
- [x] Update active documentation to describe the `T` logo and public release.
- [x] Run targeted and full verification.
