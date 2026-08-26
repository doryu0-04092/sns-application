// 負荷プロファイル(ステージと閾値)をシナリオ本体から分離する。
//
// 分離する理由: 同じ利用シナリオを負荷・ストレス・耐久・スパイクの4種で使い回したいが、
// 「何を合格とするか」は種別ごとに違う。特にストレスとスパイクは
// 壊すことが目的なので、エラー率0%を要求すると必ず不合格になってしまう。
//
// 使い方: PERF_PROFILE=stress k6 run scenarios/mixed.ts

import type { Options, Threshold } from 'k6/options';
import type { Profile } from './types.ts';

import * as smoke from './smoke.ts';
import * as smokePreauth from './smoke-preauth.ts';
import * as load from './load.ts';
import * as stress from './stress.ts';
import * as soak from './soak.ts';
import * as spike from './spike.ts';
import * as saturate from './saturate.ts';

const PROFILES: Record<string, Profile> = {
  smoke,
  'smoke-preauth': smokePreauth,
  load,
  stress,
  soak,
  spike,
  saturate,
};

export function selectProfile(): Profile {
  const name = (__ENV.PERF_PROFILE || 'load').toLowerCase();
  const profile = PROFILES[name];
  if (!profile) {
    throw new Error(
      `unknown PERF_PROFILE: ${name} (expected one of ${Object.keys(PROFILES).join(', ')})`,
    );
  }
  return profile;
}

/**
 * シナリオ固有のエンドポイント別閾値を受け取り、k6 の options を組み立てる。
 *
 * endpointThresholds は「想定内の負荷なら満たすべき応答時間」なので、
 * 壊すことが目的のプロファイル(stress / spike / saturate)では適用しない。
 * 適用してしまうと、意図通り壊れたのか閾値の付け方が悪かったのかが区別できなくなる。
 */
export function buildOptions(
  endpointThresholds: Record<string, Threshold[]> = {},
): Options {
  const p = selectProfile();

  const thresholds = p.applyEndpointThresholds
    ? { ...p.thresholds, ...endpointThresholds }
    : { ...p.thresholds };

  const options: Options = {
    thresholds,
    // 既定では p(99) が出ないが、運用設計(docs/operations.md)の監視項目が
    // P99 を見ているため、レポートで突き合わせられるよう明示的に加える。
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
    noConnectionReuse: false,

    // k6 は既定でイテレーションごとに VU の cookie jar をリセットする。
    // このアプリの認証は httpOnly クッキー auth_token だけに依存しているため
    // (JwtAuthFilter は Authorization ヘッダを見ない)、既定のままだと
    // 各VUの2回目以降のイテレーションが全て 401 になる。
    //
    // 401 は認証フィルタが DB に触れずに即返すので応答が異常に速く、
    // ステータス検証をしていなければ「非常に高速」という誤った結果が出る。
    // 実際、この設定を入れる前のスモークテストがまさにその状態だった。
    noCookiesReset: true,
  };

  if (p.stages) {
    options.stages = p.stages;
  } else {
    options.vus = p.vus;
    options.iterations = p.iterations;
  }

  return options;
}
