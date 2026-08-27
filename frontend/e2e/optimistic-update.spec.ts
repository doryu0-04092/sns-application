import { expect, test, type Page } from "@playwright/test";
import { createPost, logOut, newBody, postCard, signUp } from "./support/helpers";

/**
 * いいね・フォローの楽観的更新とロールバック。
 *
 * これらは押した瞬間にキャッシュを直接書き換えて画面へ反映し、失敗したときだけ元に戻す。
 * サーバーへ反映されていないのに画面上は成功したまま残る、という壊れ方をしても
 * 例外は出ないため、実際に失敗させて巻き戻りを見ないと確認できない。
 *
 * <b>失敗は通信の差し替えで作る。</b> バックエンドのいいね・フォローは冪等で
 * (ON CONFLICT DO NOTHING / 存在チェック無し)、普通に操作しても失敗させられない。
 *
 * <b>2ユーザーが要る。</b> 自分の投稿にはいいねできない
 * (バックエンドが400を返し、フロントもボタンを出さない)。
 */

/** 応答に混ぜる文言。エラーエンベロープが画面まで正しく伝わることも同時に確かめる。 */
const FAILURE_MESSAGE = "サーバーが一時的に応答できません";

/**
 * 失敗させるまでの遅延。
 *
 * 即座に失敗させると、楽観的に反映された状態を観測する前に巻き戻ってしまい、
 * 「反映されなかった」のか「反映された後で戻った」のか区別できないテストになる。
 */
const FAILURE_DELAY_MS = 1500;

async function failAfterDelay(page: Page, urlPattern: string): Promise<void> {
  await page.route(urlPattern, async (route) => {
    await new Promise((resolve) => setTimeout(resolve, FAILURE_DELAY_MS));
    await route.fulfill({
      status: 500,
      contentType: "application/json",
      body: JSON.stringify({ error: { code: "INTERNAL_ERROR", message: FAILURE_MESSAGE } }),
    });
  });
}

/** 投稿一覧の取得回数を数える。いいねのPOSTは方式が違うので混ざらない。 */
function countPostFetches(page: Page): { count: number } {
  const counter = { count: 0 };
  page.on("request", (request) => {
    if (request.method() === "GET" && request.url().includes("/api/posts")) {
      counter.count += 1;
    }
  });
  return counter;
}

/**
 * 他人の投稿を1件用意し、それを見ている別のユーザーとしてログインした状態にする。
 * 戻り値はその投稿の本文(カードの特定に使う)。
 */
async function viewSomeoneElsesPost(page: Page): Promise<string> {
  const body = newBody("楽観的更新");

  await signUp(page);
  await createPost(page, body);
  await expect(postCard(page, body)).toBeVisible();
  await logOut(page);

  await signUp(page);
  await expect(postCard(page, body)).toBeVisible();

  return body;
}

test.describe("楽観的更新とロールバック", () => {
  test("いいねが失敗すると、いったん増えた数が元に戻り、理由が表示される", async ({ page }) => {
    const body = await viewSomeoneElsesPost(page);
    const card = postCard(page, body);
    const likeButton = card.getByTestId("like-button");

    await expect(likeButton).toHaveText("🤍0");

    await failAfterDelay(page, "**/api/posts/*/like");
    await likeButton.click();

    // サーバーの応答を待たずに反映されている。ここが本題の前半。
    await expect(likeButton).toHaveText("❤️1");

    // 失敗が確定したら、押す前の状態に戻る。ここが本題の後半。
    await expect(likeButton).toHaveText("🤍0");
    await expect(card.getByText(FAILURE_MESSAGE)).toBeVisible();
  });

  test("いいね成功では数が増えたまま残り、投稿の取り直しも起きない", async ({ page }) => {
    const body = await viewSomeoneElsesPost(page);
    const card = postCard(page, body);
    const likeButton = card.getByTestId("like-button");

    await expect(likeButton).toHaveText("🤍0");

    const postFetches = countPostFetches(page);
    const likeResponse = page.waitForResponse(
      (res) => res.request().method() === "POST" && /\/api\/posts\/\d+\/like$/.test(res.url()),
    );

    await likeButton.click();
    expect((await likeResponse).status()).toBe(200);

    await expect(likeButton).toHaveText("❤️1");

    // キャッシュを直接書き換えているので、投稿一覧を取り直していない。
    // ここが0でなくなったら、リクエスト数を削減した改善(PR #54)が巻き戻っている。
    expect(postFetches.count).toBe(0);
  });

  test("フォローが失敗すると表示が元に戻る", async ({ page }) => {
    const body = await viewSomeoneElsesPost(page);
    const card = postCard(page, body);

    await expect(card.getByRole("button", { name: "フォローする" })).toBeVisible();

    await failAfterDelay(page, "**/api/users/*/follow");
    await card.getByRole("button", { name: "フォローする" }).click();

    await expect(card.getByRole("button", { name: "フォロー中" })).toBeVisible();

    await expect(card.getByRole("button", { name: "フォローする" })).toBeVisible();
    await expect(card.getByText(FAILURE_MESSAGE)).toBeVisible();
  });
});
