import { describe, expect, it } from "vitest";
import { useCharCount } from "./useCharCount";

/**
 * 残り文字数の計算。
 *
 * Reactのフックだがstateもエフェクトも持たない純粋な計算なので、renderHookを使わず直接呼ぶ。
 *
 * 文字数の数え方はサーバー側の @Size(max=280) と揃っている必要がある。
 * どちらも UTF-16 のコード単位で数えるため、絵文字などのサロゲートペアは2文字として数える。
 * 見た目の1文字と一致しないが、フロントで許した文字列がサーバーで弾かれる事態は避けられる。
 */
describe("useCharCount", () => {
  it("空文字なら残りは上限そのもの", () => {
    expect(useCharCount("")).toEqual({ remaining: 280, isOver: false });
  });

  it.each([
    [1, 279, false],
    [279, 1, false],
    [280, 0, false],
    [281, -1, true],
    [300, -20, true],
  ])("%i文字なら残り%iでisOverは%s", (length, remaining, isOver) => {
    expect(useCharCount("あ".repeat(length))).toEqual({ remaining, isOver });
  });

  /** 上限ちょうどはまだ超過ではない(サーバー側の @Size(max=280) と一致)。 */
  it("上限ちょうどは超過扱いにならない", () => {
    expect(useCharCount("a".repeat(280)).isOver).toBe(false);
  });

  it("上限を指定できる", () => {
    expect(useCharCount("a".repeat(500), 500)).toEqual({ remaining: 0, isOver: false });
    expect(useCharCount("a".repeat(501), 500)).toEqual({ remaining: -1, isOver: true });
  });

  /**
   * サロゲートペア(絵文字)は String.length で2として数えられる。
   * 見た目は1文字だが2文字分消費するという現挙動を明示的に固定する。
   */
  it("絵文字はサロゲートペアのため2文字として数えられる", () => {
    expect(useCharCount("😀").remaining).toBe(278);
    expect(useCharCount("😀".repeat(140)).remaining).toBe(0);
    expect(useCharCount("😀".repeat(141)).isOver).toBe(true);
  });

  it("改行も1文字として数えられる", () => {
    expect(useCharCount("a\nb").remaining).toBe(277);
  });

  /** 空白のみでも文字数としては数えられる(空判定は別の責務)。 */
  it("空白のみでも文字数として数えられる", () => {
    expect(useCharCount("   ").remaining).toBe(277);
  });
});
