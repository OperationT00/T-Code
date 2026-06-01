import assert from "node:assert/strict";
import test from "node:test";
import { RuntimeApiError } from "../src/runtime-client.ts";
import { RuntimeRequestRetrier } from "../src/runtime-request-retrier.ts";

test("RuntimeRequestRetrier retries transient network failures", async () => {
  let attempts = 0;
  const retrier = new RuntimeRequestRetrier({ delaysMs: [0, 0] });

  const result = await retrier.run(async () => {
    attempts++;
    if (attempts < 3) throw new TypeError("fetch failed");
    return "ok";
  });

  assert.equal(result, "ok");
  assert.equal(attempts, 3);
});

test("RuntimeRequestRetrier does not retry HTTP API errors", async () => {
  let attempts = 0;
  const retrier = new RuntimeRequestRetrier({ delaysMs: [0, 0] });

  await assert.rejects(
    () => retrier.run(async () => {
      attempts++;
      throw new RuntimeApiError(503, "unavailable", "unavailable");
    }),
    RuntimeApiError,
  );
  assert.equal(attempts, 1);
});
