// 事前認証(preAuth)経路の動作確認用。
//
// なぜ別プロファイルが要るか:
//   通常の smoke は preAuth=false なので、setup で50人分ログインして
//   cookie jar に auth_token を配る経路がまったく実行されない。
//   その状態で本番のストレステスト(preAuth=true)に入ると、
//   9分かけて全リクエストが401になる、という事故が起こりうる。
//   ストレス・スパイクを走らせる前に必ずこれで1周させること。
export const vus = 2;
export const iterations = 6;
export const applyEndpointThresholds = false;
export const preAuth = true;

export const thresholds = {
  unexpected_status: ['rate==0'],
  checks: ['rate==1'],
};
