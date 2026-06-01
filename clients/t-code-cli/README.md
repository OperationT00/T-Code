# t-code TypeScript CLI

This is the first thin client for the Java Core Runtime. It intentionally keeps
Agent, Memory, MCP, HITL and Snapshot behavior in Java.

The client renders Runtime API status, answer, tool lifecycle and HITL
events. Dangerous tool calls prompt for an approve or reject decision and
submit it to the Java Core Runtime over HTTP. Tool execution remains inside
the Java Core Runtime.

Runtime API failures use `{"error":{"code":"...","message":"..."}}`. The
client exposes them as `RuntimeApiError` with `status` and `code` fields, while
remaining compatible with the earlier string error shape.
Success and error JSON response bodies are both built by the Java
`RuntimeApiResponses` boundary.
The Java HTTP server is a thin adapter: thread and approval routing live in
`RuntimeThreadRoutes` and `RuntimeApprovalRoutes`.
Bearer authentication and the `X-TCode-API-Key` header are handled by
the Java `RuntimeApiAuthPolicy` boundary.

Core Runtime SSE event payloads include `schema_version: 1`. Parsed events
expose this as `schemaVersion`; older events without the field remain
compatible and are treated as version `1`.
The CLI keeps one `RuntimeEventCursor` for the active thread and requests only
events after the latest consumed id, so earlier turns are not replayed.
`RuntimeRequestRetrier` retries short-lived network failures for the idempotent
events and approvals GET requests only. Thread creation, turn submission and
approval decisions are never replayed automatically.

Start the Java runtime:

```powershell
$env:TCODE_RUNTIME_API_KEY='local-secret'
java -jar target/t-code-1.0-SNAPSHOT.jar serve --http --port 8080
```

Start the TypeScript CLI in another terminal:

```powershell
$env:TCODE_RUNTIME_API_KEY='local-secret'
node --experimental-strip-types clients/t-code-cli/src/index.ts
```
