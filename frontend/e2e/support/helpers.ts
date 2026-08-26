import { expect, type Locator, type Page } from "@playwright/test";

/**
 * E2Eテストは実際の開発用DBに対して実行するため、テストごとにデータを作り分ける。
 *
 * テストDBの初期化は行っていない。各テストが自分専用のユーザーを作り、
 * 自分が投稿した本文でしか要素を絞り込まないことで、他のテストや
 * 手動で作った既存データと干渉しないようにしている。
 * 代償として、実行するたびに開発用DBにデータが残る。
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
