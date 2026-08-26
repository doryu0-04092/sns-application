import type { Options, Threshold } from 'k6/options';

/**
 * 負荷プロファイルの形。
 *
 * 型を付けているのは、プロファイルを増やすときに項目の付け忘れを
 * コンパイル時に検出するため。実行時に気づくと、
 * 「9分走らせてから設定漏れが分かる」という壊れ方をする。
 */
export interface Profile {
  /** 段階的な負荷の台本。vus/iterations と排他。 */
  stages?: Options['stages'];
  /** 固定VU数での実行(スモーク用)。stages と排他。 */
  vus?: number;
  iterations?: number;
  /** エンドポイント別の応答時間閾値を適用するか。壊すのが目的のプロファイルでは false。 */
  applyEndpointThresholds: boolean;
  /** setup で先にログインし、cookie を各VUへ配るか。 */
  preAuth: boolean;
  thresholds: Record<string, Threshold[]>;
}
