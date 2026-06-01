export type RuntimeRequestRetrierOptions = {
  delaysMs?: number[];
};

export class RuntimeRequestRetrier {
  private readonly delaysMs: number[];

  constructor(options: RuntimeRequestRetrierOptions = {}) {
    this.delaysMs = options.delaysMs ?? [100, 250];
  }

  async run<T>(request: () => Promise<T>): Promise<T> {
    for (let attempt = 0; ; attempt++) {
      try {
        return await request();
      } catch (error) {
        if (!(error instanceof TypeError) || attempt >= this.delaysMs.length) {
          throw error;
        }
        await delay(this.delaysMs[attempt]);
      }
    }
  }
}

function delay(milliseconds: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, milliseconds));
}
