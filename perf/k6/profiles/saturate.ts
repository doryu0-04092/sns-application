import type { Profile } from './types.ts';

// 飽和点の探索。stress プロファイルで飽和に到達できなかったため追加した。
//
// なぜ必要になったか:
//   stress は各VUが1イテレーションごとに1秒待つ設計だったため、
//   VU300でも最大300req/s しか出せない。実測では184req/s で、
//   PostgreSQL のCPUは上限の87%までしか上がらず飽和に届かなかった。
//   つまり「壊れ方を見る」という当初の目的が果たせていなかった。
//
//   このプロファイルは PERF_SLEEP=0 と併用し、待ち時間を無くして
//   VUあたりのリクエスト密度を上げることで、実際にアプリを飽和させる。
//
// 合格条件は docs/perf-test-plan.md の B-1
// 「飽和したらエラーを返し、負荷を戻せば性能も戻る」。
export const stages: Profile['stages'] = [
  { duration: '1m', target: 50 },
  { duration: '1m', target: 150 },
  { duration: '1m', target: 300 },
  { duration: '2m', target: 500 },
  // 回復の観測。壊れた後に負荷を戻して性能が戻るかを見る区間。
  { duration: '30s', target: 50 },
  { duration: '2m', target: 50 },
  { duration: '30s', target: 0 },
];

export const applyEndpointThresholds = false;
export const preAuth = true;

export const thresholds: Profile['thresholds'] = {
  http_req_failed: [
    { threshold: 'rate<0.25', abortOnFail: true, delayAbortEval: '45s' },
  ],
  http_req_duration: [
    { threshold: 'p(95)<15000', abortOnFail: true, delayAbortEval: '45s' },
  ],
};
