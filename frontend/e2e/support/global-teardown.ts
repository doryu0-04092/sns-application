import { stopStack } from "./stack";

/**
 * 全テストの後にスタックを破棄する。
 * 永続ボリュームを持たないため、破棄した時点でDBもS3のオブジェクトも消える。
 */
function globalTeardown(): void {
  stopStack();
}

export default globalTeardown;
