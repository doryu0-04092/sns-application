import { expect, test } from "@playwright/test";
import { createPost, newBody, postCard, signUp } from "./support/helpers";
import { createPng } from "./support/png";
import { S3_PUBLIC_URL } from "./support/stack";

/** アップロード前に縮小される長辺の上限(frontend/src/utils/image.ts の MAX_EDGE と対応)。 */
const MAX_EDGE = 1600;

const SOURCE_WIDTH = 1800;
const SOURCE_HEIGHT = 1200;

/**
 * 画像投稿。presign → S3への直接PUT → promote の3者連携。
 *
 * ここは jsdom で代替できない箇所が最も多い。
 *   - S3への PUT はクロスオリジンであり、CORS プリフライトが通るかは実ブラウザでしか分からない
 *   - 署名は Content-Type に対して行われるため、送信時のヘッダが1文字違えば 403 になる
 *   - アップロード前の縮小は canvas と createImageBitmap を使う。jsdom にはどちらも無い
 *
 * つまり単体テストが何件緑でも、この経路が通っている保証はまったく無い。
 */
test.describe("画像投稿", () => {
  test("S3へ直接アップロードされ、縮小された画像が表示される", async ({ page }) => {
    await signUp(page);
    const body = newBody("画像投稿");
    const png = createPng(SOURCE_WIDTH, SOURCE_HEIGHT);

    // 通信は投稿ボタンを押した後に発生するため、待ち受けを先に用意しておく。
    const presignResponse = page.waitForResponse((res) => res.url().includes("/uploads/presign"));
    const putResponse = page.waitForResponse(
      (res) => res.request().method() === "PUT" && res.url().startsWith(S3_PUBLIC_URL),
    );

    await createPost(page, body, { name: "e2e.png", mimeType: "image/png", buffer: png });

    // 署名付きURLの発行(バックエンド経由)。
    expect((await presignResponse).status()).toBe(200);

    // 画像本体のS3への直接PUT。バックエンドを経由していない。
    // ここが200であることが、CORSプリフライトの通過と署名の一致の両方を示す。
    expect((await putResponse).status()).toBe(200);

    const image = postCard(page, body).getByTestId("post-image");
    await expect(image).toBeVisible();

    // naturalWidth は「実際に復号できた画像」の寸法。0 でないことが読み込み成功の証拠になり、
    // 値そのものが縮小の結果を示す。1800x1200 は長辺 1600 に収まるよう縮小される。
    const expectedHeight = Math.round(SOURCE_HEIGHT * (MAX_EDGE / SOURCE_WIDTH));
    await expect
      .poll(() => image.evaluate((el) => (el as HTMLImageElement).naturalWidth))
      .toBe(MAX_EDGE);
    expect(await image.evaluate((el) => (el as HTMLImageElement).naturalHeight)).toBe(expectedHeight);

    // 実際に配信されるバイト数を測る。
    //
    // 送信リクエストの本体(postDataBuffer)は見ない。File をそのまま本体にした PUT では
    // Playwright が本体を保持せず undefined になるためで、これはアプリの挙動とは関係ない。
    // また、利用者の回線を消費するのは「配信される側」なので、そちらを測る方が主張として正確でもある。
    // 原寸配信のままだと1投稿で約921KBを転送していた(docs/e2e-test-report.md OBS-1)。
    const src = await image.getAttribute("src");
    expect(src).toContain(`${S3_PUBLIC_URL}/`);
    const served = await page.request.get(src as string);
    expect(served.status()).toBe(200);
    expect((await served.body()).length).toBeLessThan(png.length);

    // 詳細画面でも同じ画像が表示される(一覧と詳細で別の経路を通るため両方見る)。
    await postCard(page, body).getByText(body).click();
    await expect(page).toHaveURL(/\/posts\/\d+$/);
    await expect
      .poll(() => page.getByTestId("post-image").evaluate((el) => (el as HTMLImageElement).naturalWidth))
      .toBe(MAX_EDGE);
  });
});
