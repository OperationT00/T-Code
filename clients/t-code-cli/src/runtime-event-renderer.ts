import type { RuntimeEvent } from "./runtime-client.ts";

export function formatRuntimeEvent(event: RuntimeEvent): string | null {
  if (event.type === "message.delta") {
    return String(event.data.content ?? "");
  }
  if (event.type === "turn.failed") {
    return `Turn failed: ${String(event.data.error ?? "unknown error")}`;
  }
  if (event.type === "status.updated") {
    return `[${String(event.data.phase ?? "status")}]`;
  }
  if (event.type === "tool.started") {
    return `[tool] ${String(event.data.name ?? "unknown")}`;
  }
  if (event.type === "tool.completed") {
    return `[tool done] ${String(event.data.name ?? "unknown")}`;
  }
  if (event.type === "hitl.requested") {
    return `[approval required] ${String(event.data.tool ?? "unknown")}`;
  }
  if (event.type === "hitl.resolved") {
    return `[approval ${String(event.data.decision ?? "resolved")}] ${String(event.data.tool ?? "unknown")}`;
  }
  return null;
}
