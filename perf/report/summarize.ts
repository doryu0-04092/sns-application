// k6 の summary JSON からレポート用の表(Markdown)を組み立てる。
//
// なぜ必要か: 数字を手で書き写すと、写し間違いが起きても誰も気づけない。
// レポートの全ての数値が perf/results/*.json に対応していることを機械的に保証する。
//
// 使い方(Node 22.6以降は .ts をそのまま実行できる。この環境は Node 24):
//   node perf/report/summarize.ts perf/results/load-*.json
//   node perf/report/summarize.ts perf/results/stress-mixed.json

import fs from 'node:fs';
import path from 'node:path';

/** k6 の trend メトリクス(応答時間など)。閾値を設定したものだけ thresholds を持つ。 */
interface TrendMetric {
  avg?: number;
  min?: number;
  med?: number;
  max?: number;
  'p(90)'?: number;
  'p(95)'?: number;
  'p(99)'?: number;
  /** k6 の仕様上、値が true なら「閾値を破った」を意味する。表示時は反転させる。 */
  thresholds?: Record<string, boolean>;
}

/** カウンタ・レート系メトリクス。k6 のバージョンで値の置き場所が異なる。 */
interface CountMetric {
  count?: number;
  rate?: number;
  value?: number;
  values?: { count?: number; rate?: number };
}

type Metric = TrendMetric & CountMetric;

interface Summary {
  metrics: Record<string, Metric>;
}

const files = process.argv.slice(2);
if (files.length === 0) {
  console.error('usage: node perf/report/summarize.ts <summary.json> [...]');
  process.exit(1);
}

const fmt = (v: number | undefined | null, digits = 2): string =>
  v === undefined || v === null ? '-' : Number(v).toFixed(digits);

// k6 の thresholds は「破られたら true」。合否表示は反転させる必要がある。
const verdict = (thresholds: Record<string, boolean> | undefined): string => {
  if (!thresholds || Object.keys(thresholds).length === 0) return '-';
  const crossed = Object.entries(thresholds).filter(([, v]) => v === true);
  return crossed.length === 0 ? '合格' : `不合格 (${crossed.map(([k]) => k).join(', ')})`;
};

for (const file of files) {
  const data = JSON.parse(fs.readFileSync(file, 'utf8')) as Summary;
  const m = data.metrics ?? {};
  const name = path.basename(file, '.json');

  console.log(`\n### ${name}\n`);

  // ---- 全体 ----
  const reqs = m.http_reqs?.count ?? m.http_reqs?.values?.count;
  const rate = m.http_reqs?.rate ?? m.http_reqs?.values?.rate;
  const iters = m.iterations?.count ?? m.iterations?.values?.count;
  const failed = m.http_req_failed?.value ?? m.http_req_failed?.values?.rate;
  const unexpected = m.unexpected_status?.value ?? m.unexpected_status?.values?.rate;
  const received = m.data_received?.count ?? m.data_received?.values?.count;
  const dur: TrendMetric = m.http_req_duration ?? {};

  console.log('| 指標 | 値 |');
  console.log('|---|---|');
  console.log(`| リクエスト数 | ${reqs ?? '-'} (${fmt(rate, 1)} req/s) |`);
  console.log(`| イテレーション数 | ${iters ?? '-'} |`);
  console.log(`| HTTPエラー率 | ${fmt((failed ?? 0) * 100, 2)}% |`);
  console.log(`| 想定外ステータス率 | ${fmt((unexpected ?? 0) * 100, 2)}% |`);
  console.log(`| 全体 p95 / p99 | ${fmt(dur['p(95)'])}ms / ${fmt(dur['p(99)'])}ms |`);
  if (received && reqs) {
    console.log(`| 平均レスポンスサイズ | ${fmt(received / reqs / 1024, 1)} KB/req |`);
  }

  // ---- エンドポイント別 ----
  const subs = Object.keys(m)
    .filter((k) => k.startsWith('http_req_duration{') && k.includes('name:'))
    .sort();

  if (subs.length > 0) {
    console.log('\n| エンドポイント | avg | med | p95 | p99 | max | 閾値 |');
    console.log('|---|---|---|---|---|---|---|');
    for (const key of subs) {
      const label = key.replace('http_req_duration{', '').replace(/}$/, '');
      const s: TrendMetric = m[key];
      console.log(
        `| \`${label}\` | ${fmt(s.avg)} | ${fmt(s.med)} | **${fmt(s['p(95)'])}** | ${fmt(s['p(99)'])} | ${fmt(s.max)} | ${verdict(s.thresholds)} |`,
      );
    }
    console.log('\n(単位: ms)');
  }
}
