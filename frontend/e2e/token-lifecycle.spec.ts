import { expect, test, type Page } from "@playwright/test";
import {
  ACCESS_TOKEN_COOKIE,
  REFRESH_TOKEN_COOKIE,
  clearAuthCookies,
  expireAccessToken,
  getCookie,
  restoreCookie,
  signUp,
} from "./support/helpers";

/**
 * 認証トークンのライフサイクル。失効からの自力回復・ローテーション・盗用検知を実ブラウザで通す。
 *
 * この経路には実在する穴があった。
 *   - バックエンドは期限切れの拒否(JwtServiceTest)とクッキー無しの401(JwtAuthFilterTest)を検証済み
 *   - フロントエンドは401→リフレッシュ→再試行の分岐を11件で網羅済み(api/client.test.ts)
 *   - しかし両者を繋いだ往復、つまり「クッキーが実際に消え、残ったリフレッシュトークンで
 *     自力回復し、利用者には何も起きていないように見える」ことは一度も検証されていなかった。
 *
 * 失効はクッキーの削除で作る。有効期限を短くして待つ方法は採らない。
 * auth_token の Max-Age は JWT の有効期限と同値で発行されるため、期限が来た時点で
 * ブラウザがクッキーごと捨てる。つまり削除こそが実際の失効時の状態であり、
 * 「期限切れのトークンが送られてくる」状況はブラウザ側では発生しない。
 *
 * 通信の差し替え(page.route)は使わない。すべて実際のAPIとの往復で検証する。
 */

/**
 * リフレッシュの発生回数を数える。
 *
 * 回数まで見るのは、api/client.ts が進行中のリフレッシュを共有して
 * 二重に走らせない作りになっているため。共有が壊れても画面は正常に見えるので、
 * 回数を数えない限り気づけない。
 */
function countRefreshRequests(page: Page): { count: number; trace: string[] } {
  const counter = { count: 0, trace: [] as string[] };
  const started = Date.now();
  page.on("request", (request) => {
    if (request.method() === "POST" && request.url().includes("/auth/refresh")) {
      counter.count += 1;
    }
  });
  // **このページが出した往復だけを記録する。** バックエンドのログは
  // 全ワーカー分が混ざるため、「その401はどのページのものか」を後から
  // 特定できない。CIの失敗を追った際、実際にそこで行き止まりになった。
  page.on("response", (response) => {
    const url = new URL(response.url());
    if (!url.pathname.startsWith("/api/")) return;
    const at = String(Date.now() - started).padStart(5);
    counter.trace.push(
      `${at}ms ${response.request().method()} ${url.pathname} ${response.status()}`,
    );
  });
  return counter;
}

/**
 * 復旧を待つ時間の上限。
 *
 * 既定の5秒では足りないことがある。復旧は「/auth/me が401 → リフレッシュ →
 * 元のリクエストを再試行 → 再描画」という逐次3往復で、通常は2秒ほどで終わるが、
 * Dockerスタックと計算資源を取り合うと5秒を超える。実際に、失敗するテストが
 * 実行のたびに入れ替わる形で表面化した(2026-08-28)。
 *
 * 延ばしても検出力は落ちない。復旧が壊れていればログイン画面へ送られ、
 * 表示名は何秒待っても現れないためである。長くなるのは失敗に気づくまでの時間だけ。
 *
 * playwright.config.ts が retries: 0 なので、揺らぎはそのまま失敗になる。
 * 再試行で隠すのではなく、待ち時間を実態に合わせる形で対処している。
 */
const RECOVERY_TIMEOUT_MS = 15_000;

/**
 * リフレッシュが完了し、画面が復旧しきったことを待つ。
 *
 * URL が /home になっただけでは足りない。goto の直後は認証がまだ進行中で、
 * ProtectedRoute が「読み込み中...」を出している段階でも URL は /home である。
 * そのタイミングでクッキーを読むと、まだ古い値が返る。
 *
 * ヘッダーに表示名が出るのは /auth/me が成功した後であり、
 * それはリフレッシュが新しいクッキーを保存し終えた後でしか起こらない。
 * ここまで待てば、クッキーの読み取りが競合しない。
 */
async function expectRecovered(page: Page, displayName: string): Promise<void> {
  await expect(page).toHaveURL("/home");
  await expect(page.getByRole("banner").getByText(displayName)).toBeVisible({
    timeout: RECOVERY_TIMEOUT_MS,
  });
}

test.describe("トークンのライフサイクル", () => {
  test("アクセストークンが失われても、利用者には何も起きずに操作を続けられる", async ({ page, context }) => {
    const user = await signUp(page);
    const refreshes = countRefreshRequests(page);

    await expireAccessToken(context);
    expect(await getCookie(context, ACCESS_TOKEN_COOKIE)).toBeUndefined();
    // リフレッシュトークンは残っている。これが唯一の回復手段になる。
    expect(await getCookie(context, REFRESH_TOKEN_COOKIE)).toBeDefined();

    await page.goto("/home");

    // ログイン画面へ飛ばされず、そのまま中身が見えていること。
    await expectRecovered(page, user.displayName);

    // 落ちたときに「何回か」だけでなく「何が起きたか」が残るようにする。
    expect(refreshes.count, refreshes.trace.join(String.fromCharCode(10))).toBe(1);
    // 新しいアクセストークンが発行され、次のリクエストからは通常どおり動く。
    expect(await getCookie(context, ACCESS_TOKEN_COOKIE)).toBeDefined();
  });

  test("リフレッシュのたびにリフレッシュトークンがローテーションされる", async ({ page, context }) => {
    const user = await signUp(page);
    const before = await getCookie(context, REFRESH_TOKEN_COOKIE);
    expect(before).toBeDefined();

    await expireAccessToken(context);
    await page.goto("/home");
    await expectRecovered(page, user.displayName);

    const after = await getCookie(context, REFRESH_TOKEN_COOKIE);
    expect(after).toBeDefined();
    // 使い回していると、盗まれたトークンが失効しないまま残り続ける。
    expect(after?.value).not.toBe(before?.value);
  });

  test("同時に複数のリクエストが401になってもリフレッシュは1回だけ", async ({ page, context }) => {
    const user = await signUp(page);
    const refreshes = countRefreshRequests(page);

    await expireAccessToken(context);

    // 画面を再読み込みせず、その場でプロフィールへ遷移する。
    // 再読み込みだと /auth/me が単独で先に走ってしまい、同時に401になる状況が作れない。
    // プロフィール画面はユーザー情報と投稿一覧を並行して取得するため(ProfilePage の
    // profileQuery と useUserPosts は互いに待たない)、2本が同時に401になる。
    // ログイン中ユーザーの情報は staleTime(30秒)の内側なので再取得されない。
    await page.getByRole("banner").getByText(user.displayName).click();

    await expect(page).toHaveURL(/\/users\/\d+$/);
    await expect(page.getByRole("link", { name: "プロフィールを編集" })).toBeVisible();

    expect(refreshes.count).toBe(1);
  });

  test("リフレッシュトークンも無ければログイン画面へ送られる", async ({ page, context }) => {
    await signUp(page);

    await clearAuthCookies(context);
    await page.goto("/home");

    // 回復手段が無いので、粘らずにログインを求める。
    await expect(page).toHaveURL("/login");
    await expect(page.getByRole("heading", { name: "ログイン" })).toBeVisible();
  });

  test("失効済みのリフレッシュトークンを再提示すると全トークンが失効する", async ({ page, context }) => {
    const user = await signUp(page);

    // 盗まれた想定のトークンを控えておく。
    const stolen = await getCookie(context, REFRESH_TOKEN_COOKIE);
    expect(stolen).toBeDefined();

    // 正規の利用者がリフレッシュし、ローテーションが起きる。控えた方は失効済みになる。
    await expireAccessToken(context);
    await page.goto("/home");
    await expectRecovered(page, user.displayName);

    const rotated = await getCookie(context, REFRESH_TOKEN_COOKIE);
    expect(rotated).toBeDefined();
    expect(rotated?.value).not.toBe(stolen?.value);

    // **猶予を過ぎるまで待つ。**
    //
    // 失効した直後の再提示は「同時に飛んだ2本目」として通る仕様である
    // (RefreshTokenService の CONCURRENT_REFRESH_GRACE)。待たずに提示すると
    // 盗用ではなく並行リフレッシュとして扱われ、このテストの前提が崩れる。
    //
    // E2E用のスタックは猶予を1秒にしてある(docker-compose.e2e.yml)。
    // 本番の既定は10秒で、仕組みは同じである。
    await page.waitForTimeout(1500);

    // 失効済みのトークンを提示する。盗用の兆候として扱われる。
    await restoreCookie(context, stolen!);
    await expireAccessToken(context);
    await page.goto("/home");
    await expect(page).toHaveURL("/login");

    // ここが本題。拒否されるだけでなく、そのユーザーの全トークンが失効している。
    // ローテーション後の正規のトークンに戻しても回復しない。
    // (正規の利用者も再ログインを強いられる。影響が利用者に及ぶ設計であることを固定しておく)
    await restoreCookie(context, rotated!);
    await expireAccessToken(context);
    await page.goto("/home");
    await expect(page).toHaveURL("/login");
  });
});
