import { RuntimeRequestRetrier } from "./runtime-request-retrier.ts";

export type RuntimeEvent = {
  id: number;
  type: string;
  schemaVersion: number;
  data: Record<string, unknown>;
};

export type PendingApproval = {
  id: string;
  tool: string;
  arguments: string;
  danger_level?: string;
  risk_description?: string;
  suggestion?: string;
};

export class RuntimeApiError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(status: number, code: string, message: string) {
    super(`Runtime API ${status} [${code}]: ${message}`);
    this.name = "RuntimeApiError";
    this.status = status;
    this.code = code;
  }
}

export class RuntimeClient {
  private readonly baseUrl: string;
  private readonly apiKey: string;
  private readonly retrier = new RuntimeRequestRetrier();

  constructor(baseUrl: string, apiKey: string) {
    this.baseUrl = baseUrl;
    this.apiKey = apiKey;
  }

  async createThread(): Promise<string> {
    const body = await this.requestJson("/v1/threads", { method: "POST" });
    return requiredString(body, "id");
  }

  async submitTurn(threadId: string, input: string): Promise<string> {
    const body = await this.requestJson(`/v1/threads/${threadId}/turns`, {
      method: "POST",
      body: JSON.stringify({ input }),
    });
    return requiredString(body, "id");
  }

  async events(threadId: string, after = 0): Promise<RuntimeEvent[]> {
    const response = await this.retrier.run(() => fetch(
      `${this.baseUrl}/v1/threads/${threadId}/events?after=${after}`,
      { headers: this.headers() },
    ));
    if (!response.ok) {
      throw await runtimeApiError(response);
    }
    return parseSse(await response.text());
  }

  async pendingApprovals(): Promise<PendingApproval[]> {
    const body = await this.retrier.run(() => this.requestJson("/v1/approvals", { method: "GET" }));
    return Array.isArray(body.data) ? body.data as PendingApproval[] : [];
  }

  async resolveApproval(id: string, decision: "APPROVED" | "REJECTED"): Promise<void> {
    await this.requestJson(`/v1/approvals/${id}/decision`, {
      method: "POST",
      body: JSON.stringify({ decision }),
    });
  }

  private async requestJson(path: string, init: RequestInit): Promise<Record<string, unknown>> {
    const response = await fetch(`${this.baseUrl}${path}`, {
      ...init,
      headers: this.headers(),
    });
    if (!response.ok) {
      throw await runtimeApiError(response);
    }
    return await response.json() as Record<string, unknown>;
  }

  private headers(): Record<string, string> {
    return {
      "Authorization": `Bearer ${this.apiKey}`,
      "Content-Type": "application/json",
    };
  }
}

async function runtimeApiError(response: Response): Promise<RuntimeApiError> {
  const text = await response.text();
  try {
    const body = JSON.parse(text) as { error?: unknown };
    if (typeof body.error === "string") {
      return new RuntimeApiError(response.status, body.error, body.error);
    }
    if (isRecord(body.error)) {
      const code = typeof body.error.code === "string" ? body.error.code : "unknown_error";
      const message = typeof body.error.message === "string" ? body.error.message : code;
      return new RuntimeApiError(response.status, code, message);
    }
  } catch {
    // Preserve useful server text when a non-conforming response reaches the client.
  }
  return new RuntimeApiError(response.status, "unknown_error", text || response.statusText);
}

export function parseSse(body: string): RuntimeEvent[] {
  return body
    .split(/\r?\n\r?\n/)
    .map(block => block.trim())
    .filter(Boolean)
    .map(block => {
      let id = 0;
      let type = "message";
      const data: string[] = [];
      for (const line of block.split(/\r?\n/)) {
        if (line.startsWith("id:")) id = Number(line.slice(3).trim());
        if (line.startsWith("event:")) type = line.slice(6).trim();
        if (line.startsWith("data:")) data.push(line.slice(5).trimStart());
      }
      const payload = JSON.parse(data.join("\n")) as Record<string, unknown>;
      const schemaVersion = typeof payload.schema_version === "number" ? payload.schema_version : 1;
      return { id, type, schemaVersion, data: payload };
    });
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function requiredString(body: Record<string, unknown>, key: string): string {
  const value = body[key];
  if (typeof value !== "string" || value.length === 0) {
    throw new Error(`Runtime API response is missing '${key}'`);
  }
  return value;
}
