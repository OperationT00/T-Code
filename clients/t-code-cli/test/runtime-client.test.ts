import assert from "node:assert/strict";
import { createServer } from "node:http";
import test from "node:test";
import { RuntimeApiError, RuntimeClient, parseSse } from "../src/runtime-client.ts";
import { formatRuntimeEvent } from "../src/runtime-event-renderer.ts";

test("parseSse reads runtime events", () => {
  const events = parseSse(
    "id: 1\nevent: status.updated\ndata: {\"phase\":\"running\"}\n\n"
      + "id: 2\nevent: message.delta\ndata: {\"content\":\"done\"}\n\n",
  );

  assert.deepEqual(events, [
    { id: 1, type: "status.updated", schemaVersion: 1, data: { phase: "running" } },
    { id: 2, type: "message.delta", schemaVersion: 1, data: { content: "done" } },
  ]);
});

test("parseSse exposes explicit runtime event schema version", () => {
  const events = parseSse(
    "id: 1\nevent: status.updated\ndata: {\"schema_version\":2,\"phase\":\"running\"}\n\n",
  );

  assert.deepEqual(events, [
    { id: 1, type: "status.updated", schemaVersion: 2, data: { schema_version: 2, phase: "running" } },
  ]);
});

test("RuntimeClient uses the Java Runtime API contract", async () => {
  const requests: string[] = [];
  const server = createServer((request, response) => {
    requests.push(`${request.method} ${request.url} ${request.headers.authorization}`);
    response.setHeader("Content-Type", request.url?.endsWith("/events?after=0")
      ? "text/event-stream"
      : "application/json");
    if (request.url === "/v1/threads") {
      response.end("{\"id\":\"thread_1\"}");
      return;
    }
    if (request.url === "/v1/threads/thread_1/turns") {
      response.statusCode = 202;
      response.end("{\"id\":\"turn_1\"}");
      return;
    }
    if (request.url === "/v1/approvals") {
      response.end("{\"data\":[{\"id\":\"approval_1\",\"tool\":\"write_file\",\"arguments\":\"{}\"}]}");
      return;
    }
    if (request.url === "/v1/approvals/approval_1/decision") {
      response.end("{\"id\":\"approval_1\",\"status\":\"resolved\",\"decision\":\"APPROVED\"}");
      return;
    }
    response.end("id: 3\nevent: turn.completed\ndata: {\"status\":\"completed\"}\n\n");
  });
  await new Promise<void>(resolve => server.listen(0, "127.0.0.1", resolve));
  const address = server.address();
  assert.notEqual(address, null);
  assert.equal(typeof address, "object");
  const client = new RuntimeClient(`http://127.0.0.1:${address.port}`, "secret");

  assert.equal(await client.createThread(), "thread_1");
  assert.equal(await client.submitTurn("thread_1", "hello"), "turn_1");
  assert.deepEqual(await client.events("thread_1"), [
    { id: 3, type: "turn.completed", schemaVersion: 1, data: { status: "completed" } },
  ]);
  assert.deepEqual(await client.pendingApprovals(), [
    { id: "approval_1", tool: "write_file", arguments: "{}" },
  ]);
  await client.resolveApproval("approval_1", "APPROVED");
  assert.deepEqual(requests, [
    "POST /v1/threads Bearer secret",
    "POST /v1/threads/thread_1/turns Bearer secret",
    "GET /v1/threads/thread_1/events?after=0 Bearer secret",
    "GET /v1/approvals Bearer secret",
    "POST /v1/approvals/approval_1/decision Bearer secret",
  ]);
  await new Promise<void>(resolve => server.close(() => resolve()));
});

test("RuntimeClient exposes structured Runtime API errors", async () => {
  const server = createServer((_request, response) => {
    response.statusCode = 404;
    response.setHeader("Content-Type", "application/json");
    response.end("{\"error\":{\"code\":\"thread_not_found\",\"message\":\"thread_not_found\"}}");
  });
  await new Promise<void>(resolve => server.listen(0, "127.0.0.1", resolve));
  const address = server.address();
  assert.notEqual(address, null);
  assert.equal(typeof address, "object");
  const client = new RuntimeClient(`http://127.0.0.1:${address.port}`, "secret");

  await assert.rejects(
    () => client.submitTurn("missing", "hello"),
    (error: unknown) => error instanceof RuntimeApiError
      && error.status === 404
      && error.code === "thread_not_found"
      && error.message === "Runtime API 404 [thread_not_found]: thread_not_found",
  );
  await new Promise<void>(resolve => server.close(() => resolve()));
});

test("formatRuntimeEvent renders status and tool lifecycle events", () => {
  assert.equal(formatRuntimeEvent({
    id: 1,
    type: "status.updated",
    schemaVersion: 1,
    data: { phase: "running" },
  }), "[running]");
  assert.equal(formatRuntimeEvent({
    id: 2,
    type: "tool.started",
    schemaVersion: 1,
    data: { name: "read_file" },
  }), "[tool] read_file");
  assert.equal(formatRuntimeEvent({
    id: 3,
    type: "tool.completed",
    schemaVersion: 1,
    data: { name: "read_file" },
  }), "[tool done] read_file");
  assert.equal(formatRuntimeEvent({
    id: 4,
    type: "hitl.requested",
    schemaVersion: 1,
    data: { tool: "write_file" },
  }), "[approval required] write_file");
  assert.equal(formatRuntimeEvent({
    id: 5,
    type: "hitl.resolved",
    schemaVersion: 1,
    data: { tool: "write_file", decision: "APPROVED" },
  }), "[approval APPROVED] write_file");
});
