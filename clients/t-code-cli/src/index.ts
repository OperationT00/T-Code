import { createInterface } from "node:readline/promises";
import { stdin as input, stdout as output } from "node:process";
import { RuntimeClient, type RuntimeEvent } from "./runtime-client.ts";
import { RuntimeEventCursor } from "./runtime-event-cursor.ts";
import { formatRuntimeEvent } from "./runtime-event-renderer.ts";

const apiKey = process.env.TCODE_RUNTIME_API_KEY;
if (!apiKey) {
  console.error("TCODE_RUNTIME_API_KEY is required.");
  process.exit(1);
}

const baseUrl = process.env.T_CODE_RUNTIME_URL ?? "http://127.0.0.1:8080";
const client = new RuntimeClient(baseUrl, apiKey);
const threadId = await client.createThread();
const cursor = new RuntimeEventCursor();
const prompt = createInterface({ input, output });

console.log(`t-code connected to ${baseUrl}`);
while (true) {
  const message = (await prompt.question("> ")).trim();
  if (!message) continue;
  if (message === "/exit" || message === "/quit") break;
  await client.submitTurn(threadId, message);
  await printTurn(client, threadId, cursor, prompt);
}
prompt.close();

async function printTurn(
  runtime: RuntimeClient,
  activeThreadId: string,
  cursor: RuntimeEventCursor,
  prompt: ReturnType<typeof createInterface>,
): Promise<void> {
  while (true) {
    const events = cursor.consume(await runtime.events(activeThreadId, cursor.after));
    for (const event of events) {
      render(event);
      if (event.type === "hitl.requested") await reviewPendingApproval(runtime, prompt);
      if (event.type === "turn.completed" || event.type === "turn.failed") return;
    }
    await new Promise(resolve => setTimeout(resolve, 150));
  }
}

async function reviewPendingApproval(
  runtime: RuntimeClient,
  prompt: ReturnType<typeof createInterface>,
): Promise<void> {
  for (let attempt = 0; attempt < 10; attempt++) {
    const approvals = await runtime.pendingApprovals();
    if (approvals.length === 0) {
      await new Promise(resolve => setTimeout(resolve, 100));
      continue;
    }
    for (const approval of approvals) {
      const answer = (await prompt.question(`approve ${approval.tool}? [y/N] `)).trim().toLowerCase();
      await runtime.resolveApproval(approval.id, answer === "y" || answer === "yes" ? "APPROVED" : "REJECTED");
    }
    return;
  }
  console.error("approval request was not available");
}

function render(event: RuntimeEvent): void {
  const line = formatRuntimeEvent(event);
  if (line === null) return;
  if (event.type === "turn.failed") console.error(line);
  else console.log(line);
}
