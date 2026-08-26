import type { Profile } from './types.ts';

// ストレステスト: 負荷を上げて何が先に飽和するかを見る。
//
// 注意: このプロファイルは PERF_SLEEP=1(既定)のままだと飽和に到達しない。
// 1VU あたり最大1req/s しか出ないため、VU300でも上限は300req/s になる。
// 実測では184req/s で PostgreSQL のCPUは上限の87%止まりだった。
// 飽和点を探すなら saturate プロファイル(PERF_SLEEP=0 と併用)を使うこと。
export const stages: Profile['stages'] = [
  { duration: '1m', target: 50 },
  { duration: '1m', target: 100 },
  { duration: '1m', target: 150 },
  { duration: '1m', target: 200 },
  { duration: '1m', target: 250 },
  { duration: '1m', target: 300 },
  // ここから回復の観測。壊れた後に負荷を戻して性能が戻るかを見る区間で、
  // このテストの本題はむしろこちら。
  { duration: '30s', target: 50 },
  { duration: '2m', target: 50 },
  { duration: '30s', target: 0 },
];

// 壊すことが目的なので、エンドポイント別の応答時間閾値は適用しない。
export const applyEndpointThresholds = false;

// VU が300まで増えるため、各VUのログインで BCrypt が300回走るのを避ける。
export const preAuth = true;

export const thresholds: Profile['thresholds'] = {
  // 自動中断ガード。無制限に上げ続けるとホストごと巻き込むため、
  // 「明らかに壊れた」状態を検知したら k6 側から止める。
  // delayAbortEval を入れているのは、立ち上がり直後の一瞬のブレで誤爆させないため。
  http_req_failed: [
    { threshold: 'rate<0.20', abortOnFail: true, delayAbortEval: '30s' },
  ],
  http_req_duration: [
    { threshold: 'p(95)<10000', abortOnFail: true, delayAbortEval: '30s' },
  ],
};
