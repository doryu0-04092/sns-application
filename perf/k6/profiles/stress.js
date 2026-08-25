// ストレステスト: 壊れるまで上げ、何が先に飽和するか・どう壊れるか・回復するかを見る。
//
// 数値の閾値では合否を決められない(壊すことが目的なので)。
// 合格条件は docs/perf-test-plan.md の B-1「飽和したらエラーを返し、負荷を戻せば性能も戻る」。
// 判定はレポート作成時に、応答時間と perf/monitor の観測CSVを突き合わせて行う。
export const stages = [
  { duration: '1m', target: 50 },
  { duration: '1m', target: 100 },
  { duration: '1m', target: 150 },
  { duration: '1m', target: 200 },
  { duration: '1m', target: 250 },
  { duration: '1m', target: 300 },
  // ここから回復の観測。壊れた後に負荷を戻して性能が戻るかを見る区間で、
  // このテストの本題はむしろこちら。
  { duration: '30s', target: 50 },
  { duration: '2m',  target: 50 },
  { duration: '30s', target: 0 },
];

// 壊すことが目的なので、エンドポイント別の応答時間閾値は適用しない。
export const applyEndpointThresholds = false;

export const thresholds = {
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

// VU が300まで増えるため、各VUのログインで BCrypt が300回走るのを避ける。
export const preAuth = true;
