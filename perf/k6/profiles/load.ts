import type { Profile } from './types.ts';

// 負荷テスト: 想定内の負荷での応答性を測る。
//
// 50VU に置いた根拠: backend を2コアに制限しているため、この規模で
// アプリ側の傾向が数字に現れる。
// これ以上上げるとホスト側の要因が混ざり始めるので、限界探索は saturate に分ける。
export const stages: Profile['stages'] = [
  { duration: '30s', target: 10 },  // 立ち上げ。JITとコネクションプールを温める
  { duration: '2m', target: 50 },   // 本計測区間
  { duration: '30s', target: 0 },   // 収束
];

export const applyEndpointThresholds = true;
export const preAuth = false;

export const thresholds: Profile['thresholds'] = {
  // 想定内の負荷でエラーが出ること自体が不合格(docs/perf-test-plan.md P-7)
  unexpected_status: ['rate==0'],
  http_req_failed: ['rate<0.01'],
  checks: ['rate>0.99'],
};
