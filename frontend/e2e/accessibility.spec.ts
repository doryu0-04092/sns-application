import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";
import { createPost, newBody, signUp } from "./support/helpers";

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

  test("投稿の作成フォームに違反が無い", async ({ page }) => {
    await signUp(page);

    const results = await new AxeBuilder({ page }).withTags(TAGS).analyze();

    expect(results.violations).toEqual([]);
  });
});
