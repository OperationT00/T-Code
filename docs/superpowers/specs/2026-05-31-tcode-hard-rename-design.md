# tcode Hard Rename Design

## Goal

Publish a clean `tcode` namespace without compatibility aliases for the pre-release namespace.

## Rename Rules

| Surface | Published form |
|---|---|
| Java packages | `com.tcode` |
| Java product classes | `TCode*` |
| Persistent directories | `.tcode` |
| Environment variables | `TCODE_*` |
| System properties | `tcode.*` |
| Runtime API header | `X-TCode-API-Key` |
| Filenames and thread names | `tcode-*` |

## Compatibility Policy

The rename is intentionally breaking. The runtime does not read pre-release directories, environment variables, system properties or headers. Users upgrading a local checkout should manually move their prior configuration into `~/.tcode` before starting the new build.

## Scope

- Move Java production and test packages into `com.tcode`.
- Rename `TCodeConfig`, `TCodeHistory`, `TCodeCompleter` and `TCodeHighlighter`.
- Update Maven coordinates and main class.
- Update runtime directories, environment variables, properties, HTTP header, MCP client name, thread names and asset filenames.
- Update active and historical documentation so repository scans are clean.

## Verification

- Repository scan for pre-release namespace variants returns no matches outside generated output.
- TypeScript tests pass.
- Java full suite and TUI smoke pass.
- `mvn clean package` creates `target/t-code-1.0-SNAPSHOT.jar`.
