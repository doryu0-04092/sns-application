import { defineConfig } from "@hey-api/openapi-ts";

/**
 * OpenAPI仕様から TypeScript の型を生成する設定。
 *
 * バックエンドの DTO とフロントエンドの型を手で二重定義していたため、
 * 片方だけ変えても双方のテストが通ってしまい、実行時まで壊れに気づけなかった。
 * 仕様を唯一の出所にして、ズレという状態自体を無くす(#39)。
 *
 * 入力の docs/openapi.json は実装から生成しており、
 * 実装と一致していることは OpenApiSnapshotTest が保証している。
 *
 * 生成するのは型だけで、APIクライアントは生成しない。
 * src/api/client.ts には 401 時のサイレントリフレッシュと多重実行防止という
 * 手で作り込んだロジックがあり、生成物で置き換えると失われるため。
 */
export default defineConfig({
  input: "../docs/openapi.json",
  output: {
    path: "src/api/generated",
    clean: true,
  },
  plugins: ["@hey-api/typescript"],
});
