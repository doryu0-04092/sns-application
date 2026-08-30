import { startStack, waitForBackend, warmUpBackend } from "./stack";

/**
 * 全テストの前に、E2E専用スタックを作り直して応答するまで待つ。
 *
 * 毎回まっさらな状態から始まるため、テストは前回の実行結果にも
 * 開発中に手で作ったデータにも影響されない。
 */
async function globalSetup(): Promise<void> {
  const startedAt = Date.now();
  startStack();
  await waitForBackend();

  // **1本目のテストに起動コストを払わせない。**
  // 暖めないと、その回の1本目だけが時間切れで落ちることがある(stack.ts の説明)。
  await warmUpBackend();

  console.log(`[e2e] スタックの起動と暖機に ${((Date.now() - startedAt) / 1000).toFixed(1)} 秒かかりました`);
}

export default globalSetup;
