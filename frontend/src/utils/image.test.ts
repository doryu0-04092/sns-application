import { afterEach, describe, expect, it, vi } from "vitest";
import { downscaleImage } from "./image";

/**
 * アップロード前の縮小。
 *
 * 要点は「縮小されること」より **「縮小してはいけないものに触らないこと」**。
 * アニメーションGIFを canvas に通すと1コマ目だけになり、
 * 失敗時に例外を投げると投稿そのものができなくなる。
 */
describe("downscaleImage", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  const fileOf = (type: string, size = 1_000_000) => {
    const file = new File(["x"], "photo", { type });
    // File のサイズは中身から決まるため、テストでは size を差し替える
    Object.defineProperty(file, "size", { value: size });
    return file;
  };

  /** createImageBitmap を、指定した寸法を返すスタブに差し替える。 */
  const stubBitmap = (width: number, height: number) => {
    const close = vi.fn();
    vi.stubGlobal(
      "createImageBitmap",
      vi.fn(async () => ({ width, height, close })),
    );
    return { close };
  };

  it.each([
    ["アニメーションGIF", "image/gif"],
    ["SVG", "image/svg+xml"],
  ])("%s は canvas を通さずそのまま返す", async (_label, type) => {
    const createImageBitmap = vi.fn();
    vi.stubGlobal("createImageBitmap", createImageBitmap);
    const file = fileOf(type);

    await expect(downscaleImage(file)).resolves.toBe(file);
    expect(createImageBitmap).not.toHaveBeenCalled();
  });

  it("長辺が上限以下なら再エンコードしない", async () => {
    const { close } = stubBitmap(1600, 1200);
    const file = fileOf("image/jpeg");

    await expect(downscaleImage(file)).resolves.toBe(file);
    expect(close).toHaveBeenCalled();
  });

  it("画像の読み込みに失敗しても例外にせず元のファイルを返す", async () => {
    vi.stubGlobal(
      "createImageBitmap",
      vi.fn(async () => {
        throw new Error("decode failed");
      }),
    );
    const file = fileOf("image/jpeg");

    await expect(downscaleImage(file)).resolves.toBe(file);
  });

  it("長辺が上限を超えたら縮小したファイルを返す", async () => {
    stubBitmap(4000, 3000);
    const file = fileOf("image/jpeg", 5_000_000);
    const drawImage = vi.fn();
    const canvas = {
      width: 0,
      height: 0,
      getContext: () => ({ drawImage }),
      toBlob: (cb: (b: Blob | null) => void, type: string) => {
        const blob = new Blob(["small"], { type });
        Object.defineProperty(blob, "size", { value: 200_000 });
        cb(blob);
      },
    };
    vi.spyOn(document, "createElement").mockReturnValue(canvas as unknown as HTMLElement);

    const result = await downscaleImage(file);

    expect(result).not.toBe(file);
    expect(result.type).toBe("image/jpeg");
    // 4000x3000 の長辺を 1600 に合わせるので 1600x1200 になる
    expect(canvas.width).toBe(1600);
    expect(canvas.height).toBe(1200);
    expect(drawImage).toHaveBeenCalled();
  });

  it("縮小しても小さくならない場合は元のファイルを使う", async () => {
    stubBitmap(4000, 3000);
    const file = fileOf("image/jpeg", 100_000);
    const canvas = {
      width: 0,
      height: 0,
      getContext: () => ({ drawImage: vi.fn() }),
      toBlob: (cb: (b: Blob | null) => void, type: string) => {
        const blob = new Blob(["bigger"], { type });
        Object.defineProperty(blob, "size", { value: 150_000 });
        cb(blob);
      },
    };
    vi.spyOn(document, "createElement").mockReturnValue(canvas as unknown as HTMLElement);

    await expect(downscaleImage(file)).resolves.toBe(file);
  });
});
