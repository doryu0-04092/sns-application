import { describe, expect, it } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { Avatar } from "./Avatar";

/**
 * アイコン表示。分岐は「URLがあるか」と「読み込みに失敗したか」の2つ。
 *
 * 失敗時のフォールバックは E2E テストで見つかった不具合への対応
 * (丸い枠から alt テキストがはみ出して表示が崩れていた)。
 */
describe("Avatar", () => {
  it("avatarUrl があれば画像を表示する", () => {
    render(<Avatar avatarUrl="http://example.com/a.png" displayName="山田太郎" />);

    expect(screen.getByRole("img", { name: "山田太郎" })).toHaveAttribute(
      "src",
      "http://example.com/a.png",
    );
  });

  it.each([
    ["null", null],
    ["undefined", undefined],
  ])("avatarUrl が %s なら頭文字を表示する", (_label, avatarUrl) => {
    render(<Avatar avatarUrl={avatarUrl} displayName="山田太郎" />);

    expect(screen.queryByRole("img")).not.toBeInTheDocument();
    expect(screen.getByText("山")).toBeInTheDocument();
  });

  /** 署名付きURLの期限切れや、S3上のオブジェクト消失で実際に到達する。 */
  it("画像の読み込みに失敗したら頭文字表示へ切り替わる", () => {
    render(<Avatar avatarUrl="http://example.com/broken.png" displayName="山田太郎" />);

    fireEvent.error(screen.getByRole("img", { name: "山田太郎" }));

    expect(screen.queryByRole("img")).not.toBeInTheDocument();
    expect(screen.getByText("山")).toBeInTheDocument();
  });

  /**
   * 失敗を真偽値で覚えると、プロフィール編集で新しいアイコンに差し替えても
   * 頭文字のままになってしまう。URLごとに覚えていることを固定する。
   */
  it("別のURLに変わったら再び画像を表示する", () => {
    const { rerender } = render(
      <Avatar avatarUrl="http://example.com/broken.png" displayName="山田太郎" />,
    );
    fireEvent.error(screen.getByRole("img", { name: "山田太郎" }));
    expect(screen.queryByRole("img")).not.toBeInTheDocument();

    rerender(<Avatar avatarUrl="http://example.com/new.png" displayName="山田太郎" />);

    expect(screen.getByRole("img", { name: "山田太郎" })).toHaveAttribute(
      "src",
      "http://example.com/new.png",
    );
  });
});
