// スパイクテスト: 瞬間的な急増に対する挙動と、負荷を戻した後の回復を見る。
//
// 合格条件は docs/perf-test-plan.md の B-3
// 「負荷を戻してから60秒以内に平常値へ戻る」。
// 急増中にエラーが出ること自体は不合格としない。
export const stages = [
  { duration: '30s', target: 10 },   // 平常時のベースラインを取る
  { duration: '10s', target: 200 },  // 急増
  { duration: '1m',  target: 200 },  // 高負荷を維持
  { duration: '10s', target: 10 },   // 急減
  { duration: '2m',  target: 10 },   // 回復の観測区間
  { duration: '10s', target: 0 },
];

export const applyEndpointThresholds = false;

export const thresholds = {
  // 急増中のエラーは許容するが、全滅していたら別問題なので上限だけ置く。
  http_req_failed: ['rate<0.50'],
};

// VU が200まで急増するため、ログインの BCrypt が急増分を支配するのを避ける。
export const preAuth = true;
