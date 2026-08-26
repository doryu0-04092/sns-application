import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";

/**
 * E2E専用スタック(docker-compose.e2e.yml)の起動と破棄。
 *
 * 開発用スタックとはComposeプロジェクト名・ポート・DB名・バケット名がすべて別で、
 * 永続ボリュームを持たない。したがって破棄した時点でDBの中身もS3のオブジェクトも消える。
 * 「テストが作ったデータを後から削除する」形にしていないのは、
 * 削除対象の数え上げが保守の対象になり、テーブルが増えたときに静かに漏れるためである。
 */
const PROJECT_NAME = "snsapp-e2e";
const COMPOSE_FILE = fileURLToPath(new URL("../../../docker-compose.e2e.yml", import.meta.url));

/** E2E用バックエンド。開発用(8080)とは別ポート。 */
export const API_BASE_URL = "http://localhost:18081/api";

/** E2E用フロントエンド。開発用(5173)とは別ポートにして、開発サーバと同時に動かせるようにする。 */
export const FRONTEND_URL = "http://localhost:5273";

/** E2E用のS3(LocalStack)。署名付きURLのホストとしてブラウザから直接叩かれる。 */
export const S3_PUBLIC_URL = "http://localhost:44566";

/**
 * 開発中にスタックの起動・破棄を省きたい場合の逃げ道。
 *   E2E_SKIP_STACK=1 … 起動も破棄もしない(自分で起動済みのスタックを使う)
 *   E2E_KEEP_STACK=1 … 起動はするが破棄しない(失敗した状態のDBを調べたいとき)
 */
const skipStack = process.env.E2E_SKIP_STACK === "1";
const keepStack = process.env.E2E_KEEP_STACK === "1";

function compose(args: string[]): void {
  execFileSync("docker", ["compose", "-p", PROJECT_NAME, "-f", COMPOSE_FILE, ...args], {
    stdio: "inherit",
  });
}

export function startStack(): void {
  if (skipStack) {
    console.log("[e2e] E2E_SKIP_STACK=1 のためスタックの起動を省略します");
    return;
  }

  // 前回が異常終了して残っている場合に備え、先に必ず破棄する。
  // これがないと「前回のデータが残ったまま」という初期状態の揺れが生まれる。
  compose(["down", "-v", "--remove-orphans"]);

  // --build を毎回付ける。backend/src に変更が無ければ Dockerfile の層が
  // すべてキャッシュに当たるため実質無料で、変更があれば確実に取り込める。
  // 付けないと、バックエンドを直したのに古いイメージで検証してしまう。
  compose(["up", "-d", "--build", "--wait"]);
}

export function stopStack(): void {
  if (skipStack || keepStack) {
    console.log(`[e2e] スタックを残します(調べ終えたら: docker compose -p ${PROJECT_NAME} -f docker-compose.e2e.yml down -v)`);
    return;
  }
  compose(["down", "-v", "--remove-orphans"]);
}

/**
 * バックエンドがHTTPを返すようになるまで待つ。
 *
 * compose の --wait はヘルスチェックのある postgres / localstack までしか保証しない。
 * backend は「コンテナが起動した」時点で先へ進んでしまい、
 * Spring Boot の起動とFlywayのマイグレーションはまだ終わっていない。
 */
export async function waitForBackend(timeoutMs = 180_000): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  let lastError: unknown = null;

  while (Date.now() < deadline) {
    try {
      // 認証が要るエンドポイントを未認証で叩く。401 が返ればアプリは応答している。
      // ステータスの値は問わない。接続が成立するかだけを見ている。
      await fetch(`${API_BASE_URL}/auth/me`);
      return;
    } catch (error) {
      lastError = error;
      await new Promise((resolve) => setTimeout(resolve, 1000));
    }
  }

  throw new Error(
    [
      `バックエンドが ${timeoutMs / 1000} 秒以内に応答しませんでした (${API_BASE_URL})。`,
      "",
      "次でログを確認できます:",
      `  docker compose -p ${PROJECT_NAME} -f docker-compose.e2e.yml logs backend`,
      "",
      `最後のエラー: ${lastError instanceof Error ? lastError.message : String(lastError)}`,
    ].join("\n"),
  );
}
