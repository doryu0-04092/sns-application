import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

// 各テストの後にDOMを片付ける。globals:false のため vitest の afterEach を明示的に import している。
afterEach(() => {
  cleanup();
});

/**
 * jsdom は IntersectionObserver を実装していない。
 * useInfiniteScrollSentinel が生成時に参照するため、テスト中は常に用意しておく。
 *
 * vi.stubGlobal ではなく globalThis へ直接入れているのは、既存の client.test.ts が
 * afterEach で vi.unstubAllGlobals() を呼んでおり、stubGlobal だと巻き添えで消えるため。
 *
 * observe/unobserve は何もしないので「sentinel が可視になった」状況は再現できない。
 * 無限スクロールの発火そのものはE2Eの領域で、ここでは監視の登録・解除までを見る。
 */
class IntersectionObserverStub implements IntersectionObserver {
  readonly root: Element | Document | null = null;
  readonly rootMargin: string = "";
  readonly scrollMargin: string = "";
  readonly thresholds: ReadonlyArray<number> = [];

  observe(): void {}
  unobserve(): void {}
  disconnect(): void {}
  takeRecords(): IntersectionObserverEntry[] {
    return [];
  }
}

globalThis.IntersectionObserver = IntersectionObserverStub;
