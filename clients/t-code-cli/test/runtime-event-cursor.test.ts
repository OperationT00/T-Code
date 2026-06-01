import assert from "node:assert/strict";
import test from "node:test";
import { RuntimeEventCursor } from "../src/runtime-event-cursor.ts";
import type { RuntimeEvent } from "../src/runtime-client.ts";

test("RuntimeEventCursor advances monotonically and filters historical events", () => {
  const cursor = new RuntimeEventCursor();

  assert.equal(cursor.after, 0);
  assert.deepEqual(cursor.consume([event(1), event(2)]).map(item => item.id), [1, 2]);
  assert.equal(cursor.after, 2);
  assert.deepEqual(cursor.consume([event(1), event(2), event(3)]).map(item => item.id), [3]);
  assert.equal(cursor.after, 3);
});

function event(id: number): RuntimeEvent {
  return {
    id,
    type: "status.updated",
    schemaVersion: 1,
    data: { phase: "running" },
  };
}
