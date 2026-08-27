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
function countRefreshRequests(page: Page): { count: number } {
  const counter = { count: 0 };
  page.on("request", (request) => {
    if (request.method() === "POST" && request.url().includes("/auth/refresh")) {
      counter.count += 1;
    }
  });
  return counter;
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
    await expect(page).toHaveURL("/home");
    await expect(page.getByRole("banner").getByText(user.displayName)).toBeVisible();

    expect(refreshes.count).toBe(1);
    // 新しいアクセストークンが発行され、次のリクエストからは通常どおり動く。
    expect(await getCookie(context, ACCESS_TOKEN_COOKIE)).toBeDefined();
  });

  test("リフレッシュのたびにリフレッシュトークンがローテーションされる", async ({ page, context }) => {
    await signUp(page);
    const before = await getCookie(context, REFRESH_TOKEN_COOKIE);
    expect(before).toBeDefined();

    await expireAccessToken(context);
    await page.goto("/home");
    await expect(page).toHaveURL("/home");

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
    await signUp(page);

    // 盗まれた想定のトークンを控えておく。
    const stolen = await getCookie(context, REFRESH_TOKEN_COOKIE);
    expect(stolen).toBeDefined();

    // 正規の利用者がリフレッシュし、ローテーションが起きる。控えた方は失効済みになる。
    await expireAccessToken(context);
    await page.goto("/home");
    await expect(page).toHaveURL("/home");

    const rotated = await getCookie(context, REFRESH_TOKEN_COOKIE);
    expect(rotated).toBeDefined();
    expect(rotated?.value).not.toBe(stolen?.value);

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
