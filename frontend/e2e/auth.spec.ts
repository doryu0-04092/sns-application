import { expect, test } from "@playwright/test";
import { logIn, logOut, newUser, signUp } from "./support/helpers";

/**
 * 認証の往復。
 *
 * jsdom では検証できない部分がここにある。認証は HttpOnly クッキーで持ち回っており、
 * JavaScript からは読めない。ブラウザがクッキーを保存し、以降のリクエストに
 * 自動で載せているかどうかは、実ブラウザで往復させないと確認できない。
 * 単体テストで検証しているのは「401 ならリダイレクトする」という分岐までである。
 */
test.describe("認証の往復", () => {
  test("未ログインで保護ルートを開くとログイン画面へ送られる", async ({ page }) => {
    await page.goto("/home");

    await expect(page).toHaveURL("/login");
    await expect(page.getByRole("heading", { name: "ログイン" })).toBeVisible();
  });

  test("新規登録するとログイン済みになり、再読み込みしても維持される", async ({ page }) => {
    const user = await signUp(page);

    await expect(page.getByRole("banner").getByText(user.displayName)).toBeVisible();

    // 再読み込みでクッキーが失われないこと。ログイン状態をメモリ上のみで持っていると、
    // ここでログイン画面へ戻される。
    await page.reload();
    await expect(page).toHaveURL("/home");
    await expect(page.getByRole("banner").getByText(user.displayName)).toBeVisible();
  });

  test("ログアウトするとセッションが切れ、保護ルートへ戻れない", async ({ page }) => {
    await signUp(page);

    await logOut(page);

    // 画面遷移だけでなく、サーバー側のセッションも切れていることを確かめる。
    // クッキーが残っていれば、ここで /home がそのまま表示されてしまう。
    await page.goto("/home");
    await expect(page).toHaveURL("/login");
  });

  test("ログアウト後、同じ資格情報でログインし直せる", async ({ page }) => {
    const user = await signUp(page);
    await logOut(page);

    await logIn(page, user);

    await expect(page.getByRole("banner").getByText(user.displayName)).toBeVisible();
  });

  test("誤ったパスワードではログインできず、理由が表示される", async ({ page }) => {
    const user = await signUp(page);
    await logOut(page);

    await page.goto("/login");
    await page.getByLabel("メールアドレス").fill(user.email);
    await page.getByLabel("パスワード").fill("wrong-password");
    await page.getByRole("button", { name: "ログイン" }).click();

    // メール未登録とパスワード不一致は同じ文言に統一されている(列挙攻撃の対策)。
    await expect(page.getByText("メールアドレスまたはパスワードが正しくありません")).toBeVisible();
    await expect(page).toHaveURL("/login");
  });

  test("未登録のメールアドレスでも同じ文言になる", async ({ page }) => {
    const unknown = newUser();

    await page.goto("/login");
    await page.getByLabel("メールアドレス").fill(unknown.email);
    await page.getByLabel("パスワード").fill(unknown.password);
    await page.getByRole("button", { name: "ログイン" }).click();

    await expect(page.getByText("メールアドレスまたはパスワードが正しくありません")).toBeVisible();
  });
});
