// 負荷プロファイル(ステージと閾値)をシナリオ本体から分離する。
//
// 分離する理由: 同じ利用シナリオを負荷・ストレス・耐久・スパイクの4種で使い回したいが、
// 「何を合格とするか」は種別ごとに違う。特にストレスとスパイクは
// 壊すことが目的なので、エラー率0%を要求すると必ず不合格になってしまう。
//
// 使い方: PERF_PROFILE=stress k6 run scenarios/mixed.js

import * as smoke from './smoke.js';
import * as load from './load.js';
import * as stress from './stress.js';
import * as soak from './soak.js';
import * as spike from './spike.js';

const PROFILES = { smoke, load, stress, soak, spike };

export function selectProfile() {
  const name = (__ENV.PERF_PROFILE || 'load').toLowerCase();
  const profile = PROFILES[name];
  if (!profile) {
    throw new Error(`unknown PERF_PROFILE: ${name} (expected one of ${Object.keys(PROFILES).join(', ')})`);
  }
  return profile;
}

/**
 * シナリオ固有のエンドポイント別閾値を受け取り、k6 の options を組み立てる。
 *
 * endpointThresholds は「想定内の負荷なら満たすべき応答時間」なので、
 * 壊すことが目的のプロファイル(stress / spike)では適用しない。
 * 適用してしまうと、意図通り壊れたのか閾値の付け方が悪かったのかが区別できなくなる。
 */
export function buildOptions(endpointThresholds = {}) {
  const p = selectProfile();

  const thresholds = p.applyEndpointThresholds
    ? { ...p.thresholds, ...endpointThresholds }
    : { ...p.thresholds };

  const options = {
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
