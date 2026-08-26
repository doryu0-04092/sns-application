// 耐久テスト: 一定負荷を30分かけ続け、劣化(リーク)が起きないかを見る。
//
// VU20 に抑えているのは、飽和させることが目的ではないため。
// 飽和した状態で30分回しても「過負荷だから遅い」としか分からず、
// リークによる単調増加と区別がつかなくなる。
//
// 合格条件は docs/perf-test-plan.md の B-2
// 「メモリ・DBコネクション数・応答時間が単調増加しない」。
// 単調増加の判定は perf/monitor の観測CSVを見て行う。
export const stages = [
  { duration: '2m',  target: 20 },
  { duration: '26m', target: 20 },
  { duration: '2m',  target: 0 },
];

export const applyEndpointThresholds = true;

export const thresholds = {
  unexpected_status: ['rate==0'],
  http_req_failed: ['rate<0.01'],
  checks: ['rate>0.99'],
};

// 30分でアクセストークンが失効するため、各VUが自前の refresh_token を持つ必要がある。
export const preAuth = false;
