import type { Profile } from './types.ts';

// スクリプト自体の動作確認用。負荷はかけず、全経路が200を返すかだけを見る。
// 中データでの本番計測に入る前に、必ずこれで全シナリオを1周させる。
export const vus: Profile['vus'] = 1;
export const iterations: Profile['iterations'] = 3;
export const applyEndpointThresholds = false;

// 事前認証を使うか。理由は scenarios/mixed.ts の setup() のコメントを参照。
export const preAuth = false;

export const thresholds: Profile['thresholds'] = {
  // スモークでは「壊れていないこと」だけを厳密に見る。応答時間は問わない。
  unexpected_status: ['rate==0'],
  checks: ['rate==1'],
};
