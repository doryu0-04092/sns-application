import { expect, type BrowserContext, type Locator, type Page } from "@playwright/test";

/** context.cookies() が返すクッキー1件の型。Playwright が名前付きで公開していないため実体から導く。 */
type Cookie = Awaited<ReturnType<BrowserContext["cookies"]>>[number];

/**
 * テストごとに一意な文字列を作る。
 *
 * スタックは実行のたびに作り直されるため、前回の実行のデータは残っていない
 * (docker-compose.e2e.yml は永続ボリュームを持たない)。
 * それでも一意にするのは、テストを並列実行しているためである。
 * 同時に走っている別のテストが作ったユーザーや投稿を取り違えないようにする。
 */
function unique(prefix: string): string {
  return `${prefix}-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

export interface TestUser {
  email: string;
  password: string;
  displayName: string;
}

export function newUser(): TestUser {
  const id = unique("e2e");
  return {
    email: `${id}@example.com`,
    // バックエンドの制約は8文字以上。
    password: "e2e-password",
    displayName: id,
  };
}

/** 投稿・コメント本文。他のテストと衝突しない一意な文字列を返す。 */
export function newBody(label: string): string {
  return `${label} ${unique("body")}`;
}

/** 新規登録する。成功するとサーバーが認証クッキーを発行し、タイムラインへ遷移する。 */
export async function signUp(page: Page, user: TestUser = newUser()): Promise<TestUser> {
  await page.goto("/signup");
  await page.getByLabel("表示名").fill(user.displayName);
  await page.getByLabel("メールアドレス").fill(user.email);
  await page.getByLabel("パスワード(8文字以上)").fill(user.password);
  await page.getByRole("button", { name: "登録する" }).click();
  await expect(page).toHaveURL("/home");
  return user;
}

export async function logIn(page: Page, user: TestUser): Promise<void> {
  await page.goto("/login");
  await page.getByLabel("メールアドレス").fill(user.email);
  await page.getByLabel("パスワード").fill(user.password);
  await page.getByRole("button", { name: "ログイン" }).click();
  await expect(page).toHaveURL("/home");
}

export async function logOut(page: Page): Promise<void> {
  await page.getByRole("button", { name: "ログアウト" }).click();
  await expect(page).toHaveURL("/login");
}

/** タイムライン上で投稿する。画像を添付する場合は files を渡す。 */
export async function createPost(
  page: Page,
  body: string,
  files: Parameters<Locator["setInputFiles"]>[0] | null = null,
): Promise<void> {
  await page.getByPlaceholder("いまどうしてる?").fill(body);
  if (files) {
    // ファイル入力は CSS で隠されている(見た目上は「画像投稿」ボタンから開く)。
    // setInputFiles は可視性を要求しないため、入力要素へ直接渡してよい。
    await page.locator("#post-composer-images").setInputFiles(files);
  }
  await page.getByRole("button", { name: "投稿する" }).click();
}

/** タイムライン上の投稿カードを本文で特定する。 */
export function postCard(page: Page, body: string): Locator {
  return page.getByTestId("post-card").filter({ hasText: body });
}

/** コメントのノードを本文で特定する。返信は入れ子になるため、最も内側のノードを返す。 */
export function commentNode(page: Page, body: string): Locator {
  return page.getByTestId("comment-node").filter({ hasText: body }).last();
}

/**
 * window.confirm を「OK」で応答させる。
 *
 * Playwright は既定でダイアログを自動的に「キャンセル」するため、
 * この登録をしないと削除操作が静かに無視され、
 * 「削除ボタンを押したのに消えない」という誤った失敗になる。
 */
export function acceptConfirmDialogs(page: Page): void {
  page.on("dialog", (dialog) => dialog.accept());
}

/**
 * 認証クッキーの名前。バックエンドが AuthController で発行する。
 *
 * refresh_token は Path が /api/auth に限定されており、認証系のリクエストにしか送られない。
 * 復元する際もこの Path を保つ必要がある(ずれると送信されず、失効したのと同じ挙動になる)。
 */
export const ACCESS_TOKEN_COOKIE = "auth_token";
export const REFRESH_TOKEN_COOKIE = "refresh_token";

/**
 * アクセストークンを失効させる。
 *
 * クッキーを削除する形にしているのは、これが実際の失効時に起きることだからである。
 * auth_token の Max-Age は JWT の有効期限と同じ値で発行されるため
 * (AuthController の buildAuthCookie)、期限が来た時点でブラウザがクッキー自体を捨てる。
 * つまり利用者に起きるのは「期限切れのトークンを送る」ではなく「トークンが無い状態で送る」であり、
 * ここでの削除はその状態を待ち時間ゼロで正確に再現している。
 *
 * refresh_token は消さない。残ったリフレッシュトークンで自力回復できるかが検証対象である。
 */
export async function expireAccessToken(context: BrowserContext): Promise<void> {
  await context.clearCookies({ name: ACCESS_TOKEN_COOKIE });
}

/** 認証クッキーを両方とも消す。リフレッシュもできない状態を作る。 */
export async function clearAuthCookies(context: BrowserContext): Promise<void> {
  await context.clearCookies({ name: ACCESS_TOKEN_COOKIE });
  await context.clearCookies({ name: REFRESH_TOKEN_COOKIE });
}

/**
 * クッキーを名前で取り出す。HttpOnly のクッキーも取得できる
 * (ブラウザコンテキストのAPIであり、ページ内のJavaScriptとは権限が違う)。
 */
export async function getCookie(context: BrowserContext, name: string): Promise<Cookie | undefined> {
  const cookies = await context.cookies();
  return cookies.find((cookie) => cookie.name === name);
}

/** 控えておいたクッキーをそのまま復元する。Path や HttpOnly を含めて元の値を保つ。 */
export async function restoreCookie(context: BrowserContext, cookie: Cookie): Promise<void> {
  await context.addCookies([cookie]);
}
