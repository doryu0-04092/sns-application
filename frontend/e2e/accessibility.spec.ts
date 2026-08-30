import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";
import {
  acceptConfirmDialogs,
  createPost,
  newBody,
  postCard,
  signUp,
} from "./support/helpers";

/**
 * アクセシビリティの自動検証。
 *
 * <b>なぜ E2E に置くか</b>: axe は実際に描画された DOM を見る。
 * 単体テスト(jsdom)では、CSS が当たった状態のコントラスト比も、
 * ルーティングで組み上がった最終的な見出しの階層も再現できない。
 *
 * <b>何を保証しないか</b>: axe が検出できるのは自動で判定できる違反だけで、
 * 全体のおよそ3〜4割と言われている。「違反ゼロ = 使える」ではない。
 * それでも入れるのは、<b>崩れたときに気づける下限</b>を作るためである。
 *
 * <b>失敗したらテストではなく実装を直す。</b> 何のために検証しているかといえば、
 * 検出した内容に対応して品質を上げるためであり、
 * 閾値を緩めて緑にすることではない。
 */

/** 対象とする基準。WCAG 2.0/2.1 の A と AA。 */
const TAGS = ["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"];

test.describe("アクセシビリティ", () => {
  test("ログイン画面に違反が無い", async ({ page }) => {
    await page.goto("/login");
    await expect(page.getByRole("heading", { name: "ログイン" })).toBeVisible();

    const results = await new AxeBuilder({ page }).withTags(TAGS).analyze();

    expect(results.violations).toEqual([]);
  });

  test("新規登録画面に違反が無い", async ({ page }) => {
    await page.goto("/signup");
    await expect(page.getByRole("heading", { name: "新規登録" })).toBeVisible();

    const results = await new AxeBuilder({ page }).withTags(TAGS).analyze();

    expect(results.violations).toEqual([]);
  });

  /**
   * 投稿が並んだ状態のタイムライン。
   *
   * <b>空の一覧では意味が無い。</b> 画像・ボタン・リンクが並んで初めて、
   * 代替テキストやリンク名の欠落が現れる。
   */
  test("タイムラインに違反が無い", async ({ page }) => {
    await signUp(page);
    await createPost(page, newBody("アクセシビリティ検査"));

    const results = await new AxeBuilder({ page }).withTags(TAGS).analyze();

    expect(results.violations).toEqual([]);
  });

  /**
   * 削除済みの投稿(ツームストーン)。
   *
   * <b>通常の投稿だけでは通らない配色がある。</b> この表示の文字色は
   * text-gray-400 / bg-gray-50 で 2.48:1 しかなく、AA の 4.5:1 を満たしていなかった。
   *
   * それでも既存の検査は緑だった。並列実行で他のテストが消した投稿が
   * <b>偶然タイムラインに載った回にだけ</b>落ちる、という形でしか出なかったためである。
   * 「たまたま検出できた」を「必ず検出する」に変えるために、この状態を自分で作る。
   */
  test("削除済みの投稿の表示に違反が無い", async ({ page }) => {
    acceptConfirmDialogs(page);
    await signUp(page);

    const body = newBody("削除される投稿");
    await createPost(page, body);
    await postCard(page, body).getByText(body).click();
    await expect(page).toHaveURL(/\/posts\/\d+$/);
    const detailUrl = page.url();

    // 返信が残っている投稿だけがツームストーンとして残る(返信が無ければ消える)。
    await page.getByPlaceholder("返信をポスト").fill(newBody("残る返信"));
    await page.getByRole("button", { name: "返信する" }).click();
    await expect(page.getByTestId("comment-node")).toHaveCount(1);

    await page.getByRole("button", { name: "削除" }).first().click();
    await expect(page).toHaveURL("/home");

    await page.goto(detailUrl);
    await expect(page.getByText("この投稿は削除されました")).toBeVisible();

    const results = await new AxeBuilder({ page }).withTags(TAGS).analyze();

    expect(results.violations).toEqual([]);
  });

  test("投稿の作成フォームに違反が無い", async ({ page }) => {
    await signUp(page);

    const results = await new AxeBuilder({ page }).withTags(TAGS).analyze();

    expect(results.violations).toEqual([]);
  });
});
