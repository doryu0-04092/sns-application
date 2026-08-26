import { expect, test } from "@playwright/test";
import { acceptConfirmDialogs, commentNode, createPost, newBody, postCard, signUp } from "./support/helpers";

/**
 * 投稿 → コメント → 返信 → ツームストーンまでの状態遷移。
 *
 * ここを最優先で資産化しているのは、機能の中で状態遷移が最も複雑で、
 * 壊れたときの影響が他機能へ波及するためである(docs/e2e-test-report.md 9章)。
 *
 * 特に確認したいのは「削除しても返信は残る」という仕様である。
 * 親を物理削除してしまうと返信が道連れになるが、
 * その事故は投稿とコメントを跨いだ実データでしか再現しない。
 */
test.describe("投稿・コメント・返信・ツームストーン", () => {
  test("投稿するとタイムラインに現れ、詳細画面を開ける", async ({ page }) => {
    await signUp(page);
    const body = newBody("投稿");

    await createPost(page, body);

    await expect(postCard(page, body)).toBeVisible();

    await postCard(page, body).getByText(body).click();
    await expect(page).toHaveURL(/\/posts\/\d+$/);
    await expect(page.getByText(body)).toBeVisible();
  });

  test("コメントを削除してもツームストーンになり、返信は残る", async ({ page }) => {
    acceptConfirmDialogs(page);
    await signUp(page);

    const postBody = newBody("投稿");
    await createPost(page, postBody);
    await postCard(page, postBody).getByText(postBody).click();
    await expect(page).toHaveURL(/\/posts\/\d+$/);

    // 投稿へのコメント。画面下部のフォームは parentCommentId が null のもの。
    const commentBody = newBody("コメント");
    await page.getByPlaceholder("返信をポスト").fill(commentBody);
    await page.getByRole("button", { name: "返信する" }).click();
    await expect(commentNode(page, commentBody)).toBeVisible();

    // そのコメントへの返信。「返信」(開閉)と「返信する"」(送信)は
    // 部分一致では区別できないため、開閉ボタンは exact 指定で選ぶ。
    const parent = commentNode(page, commentBody);
    await parent.getByRole("button", { name: "返信", exact: true }).click();
    const replyBody = newBody("返信");
    await parent.getByPlaceholder("返信をポスト").fill(replyBody);
    await parent.getByRole("button", { name: "返信する" }).click();

    // 返信が親コメントの内側に入れ子で描画されていること。
    // 平坦に並んでしまう不具合は、件数だけを見ていると気づけない。
    await expect(parent.getByTestId("comment-node").filter({ hasText: replyBody })).toBeVisible();

    // 親コメントを削除する。親の操作ボタンは子コメントより前に描画されるため、
    // 先頭の「削除」が親自身のものになる。
    await parent.getByRole("button", { name: "削除" }).first().click();

    // 削除の前後で親ノードを特定し直す。
    // parent は「本文が commentBody であること」で絞り込んでいるが、
    // 削除するとその本文自体がツームストーンに置き換わり、絞り込みが何にも一致しなくなる。
    // 削除しても消えない値(返信の本文)で特定すれば、前後で同じノードを指し続けられる。
    // 返信を含むノードは「親」と「返信自身」の2つで、先に現れるのが親である。
    const deletedParent = page.getByTestId("comment-node").filter({ hasText: replyBody }).first();

    await expect(deletedParent.getByText("このコメントは削除されました")).toBeVisible();
    await expect(page.getByText(commentBody)).toHaveCount(0);
    // 本題。親が消えても返信は残っている。
    await expect(page.getByText(replyBody)).toBeVisible();
  });

  test("投稿を削除するとツームストーンになり、コメントは残る", async ({ page }) => {
    acceptConfirmDialogs(page);
    await signUp(page);

    const postBody = newBody("投稿");
    await createPost(page, postBody);
    await postCard(page, postBody).getByText(postBody).click();
    await expect(page).toHaveURL(/\/posts\/\d+$/);
    const detailUrl = page.url();

    const commentBody = newBody("コメント");
    await page.getByPlaceholder("返信をポスト").fill(commentBody);
    await page.getByRole("button", { name: "返信する" }).click();
    await expect(commentNode(page, commentBody)).toBeVisible();

    await page.getByRole("button", { name: "削除" }).first().click();

    // 削除後はタイムラインへ戻され、その投稿は一覧から消える。
    await expect(page).toHaveURL("/home");
    await expect(postCard(page, postBody)).toHaveCount(0);

    // 直接URLを開くと、本文の代わりにツームストーンが出てコメントは保持されている。
    await page.goto(detailUrl);
    await expect(page.getByText("この投稿は削除されました(返信は保持されています)")).toBeVisible();
    await expect(page.getByText(postBody)).toHaveCount(0);
    await expect(commentNode(page, commentBody)).toBeVisible();
  });
});
