import type { RuntimeEvent } from "./runtime-client.ts";

export class RuntimeEventCursor {
  private lastEventId = 0;

  get after(): number {
    return this.lastEventId;
  }

  consume(events: RuntimeEvent[]): RuntimeEvent[] {
    const unseen = events.filter(event => event.id > this.lastEventId);
    for (const event of unseen) {
      this.lastEventId = Math.max(this.lastEventId, event.id);
    }
    return unseen;
  }
}
