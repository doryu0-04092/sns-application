import { describe, expect, it } from "vitest";
import { buildCommentTree } from "./commentTree";
import { comment } from "../test/fixtures";

/**
 * 平坦な配列で返るコメント一覧を、parentCommentId をたどってツリーへ組み立てる関数のテスト。
 *
 * サーバーは「返信を持つ削除済みコメント」をツームストーンとして返すため、
 * 削除済みでも枝の接続点として残る必要がある。逆に、親が一覧に含まれない返信は
 * どこにもぶら下がれず結果から消える(サイレントに件数が減る)ため、その挙動も固定する。
 */
describe("buildCommentTree", () => {
  it("空配列なら空のツリーになる", () => {
    expect(buildCommentTree([])).toEqual([]);
  });

  it("トップレベルのコメントだけならフラットなまま返る", () => {
    const tree = buildCommentTree([
      comment({ id: 1, parentCommentId: null }),
      comment({ id: 2, parentCommentId: null }),
    ]);

    expect(tree).toHaveLength(2);
    expect(tree[0].comment.id).toBe(1);
    expect(tree[0].children).toEqual([]);
  });

  it("返信が親のchildrenにぶら下がる", () => {
    const tree = buildCommentTree([
      comment({ id: 1, parentCommentId: null }),
      comment({ id: 2, parentCommentId: 1 }),
    ]);

    expect(tree).toHaveLength(1);
    expect(tree[0].children).toHaveLength(1);
    expect(tree[0].children[0].comment.id).toBe(2);
  });

  it("3階層以上のネストも組み立てられる", () => {
    const tree = buildCommentTree([
      comment({ id: 1, parentCommentId: null }),
      comment({ id: 2, parentCommentId: 1 }),
      comment({ id: 3, parentCommentId: 2 }),
      comment({ id: 4, parentCommentId: 3 }),
    ]);

    expect(tree[0].comment.id).toBe(1);
    expect(tree[0].children[0].comment.id).toBe(2);
    expect(tree[0].children[0].children[0].comment.id).toBe(3);
    expect(tree[0].children[0].children[0].children[0].comment.id).toBe(4);
  });

  it("同じ親を持つ返信は入力の順序を保つ", () => {
    const tree = buildCommentTree([
      comment({ id: 1, parentCommentId: null }),
      comment({ id: 5, parentCommentId: 1 }),
      comment({ id: 3, parentCommentId: 1 }),
      comment({ id: 9, parentCommentId: 1 }),
    ]);

    expect(tree[0].children.map((node) => node.comment.id)).toEqual([5, 3, 9]);
  });

  it("複数のトップレベルがそれぞれ独立した枝になる", () => {
    const tree = buildCommentTree([
      comment({ id: 1, parentCommentId: null }),
      comment({ id: 2, parentCommentId: null }),
      comment({ id: 3, parentCommentId: 1 }),
      comment({ id: 4, parentCommentId: 2 }),
    ]);

    expect(tree[0].children[0].comment.id).toBe(3);
    expect(tree[1].children[0].comment.id).toBe(4);
  });

  /** 削除済みでも返信を持つコメントは、ツリーの接続点として残る必要がある。 */
  it("削除済みコメントも接続点として残り返信を保持する", () => {
    const tree = buildCommentTree([
      comment({ id: 1, parentCommentId: null, deleted: true, body: null }),
      comment({ id: 2, parentCommentId: 1 }),
    ]);

    expect(tree[0].comment.deleted).toBe(true);
    expect(tree[0].comment.body).toBeNull();
    expect(tree[0].children[0].comment.id).toBe(2);
  });

  /**
   * 親が一覧に含まれない返信はどこにもぶら下がれず、結果から消える。
   * 例外にはならず件数だけが減るため、気づきにくい挙動として明示的に固定する。
   */
  it("親が一覧に無い返信は結果から消える", () => {
    const tree = buildCommentTree([
      comment({ id: 1, parentCommentId: null }),
      comment({ id: 2, parentCommentId: 999 }),
    ]);

    expect(tree).toHaveLength(1);
    expect(tree[0].comment.id).toBe(1);
    expect(tree[0].children).toEqual([]);
  });

  it("トップレベルが1件も無ければ空になる", () => {
    const tree = buildCommentTree([comment({ id: 2, parentCommentId: 999 })]);

    expect(tree).toEqual([]);
  });

  it("入力の順序が親子で逆でも組み立てられる", () => {
    const tree = buildCommentTree([
      comment({ id: 2, parentCommentId: 1 }),
      comment({ id: 1, parentCommentId: null }),
    ]);

    expect(tree).toHaveLength(1);
    expect(tree[0].children[0].comment.id).toBe(2);
  });
});
