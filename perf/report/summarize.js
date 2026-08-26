// k6 の summary JSON からレポート用の表(Markdown)を組み立てる。
//
// なぜ必要か: 数字を手で書き写すと、写し間違いが起きても誰も気づけない。
// レポートの全ての数値が perf/results/*.json に対応していることを機械的に保証する。
//
// 使い方:
//   node perf/report/summarize.js perf/results/load-*.json
//   node perf/report/summarize.js perf/results/stress-mixed.json

const fs = require('node:fs');
const path = require('node:path');

const files = process.argv.slice(2);
if (files.length === 0) {
  console.error('usage: node perf/report/summarize.js <summary.json> [...]');
  process.exit(1);
}

const fmt = (v, digits = 2) =>
  v === undefined || v === null ? '-' : Number(v).toFixed(digits);

// k6 の thresholds は「破られたら true」。合否表示は反転させる必要がある。
const verdict = (thresholds) => {
  if (!thresholds || Object.keys(thresholds).length === 0) return '-';
  const crossed = Object.entries(thresholds).filter(([, v]) => v === true);
  return crossed.length === 0 ? '合格' : `不合格 (${crossed.map(([k]) => k).join(', ')})`;
};

for (const file of files) {
  const data = JSON.parse(fs.readFileSync(file, 'utf8'));
  const m = data.metrics || {};
  const name = path.basename(file, '.json');

  console.log(`\n### ${name}\n`);

  // ---- 全体 ----
  const reqs = m.http_reqs?.count ?? m.http_reqs?.values?.count;
  const rate = m.http_reqs?.rate ?? m.http_reqs?.values?.rate;
  const iters = m.iterations?.count ?? m.iterations?.values?.count;
  const failed = m.http_req_failed?.value ?? m.http_req_failed?.values?.rate;
  const unexpected = m.unexpected_status?.value ?? m.unexpected_status?.values?.rate;
  const received = m.data_received?.count ?? m.data_received?.values?.count;
  const dur = m.http_req_duration || {};

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
      const s = m[key];
      console.log(
        `| \`${label}\` | ${fmt(s.avg)} | ${fmt(s.med)} | **${fmt(s['p(95)'])}** | ${fmt(s['p(99)'])} | ${fmt(s.max)} | ${verdict(s.thresholds)} |`,
      );
    }
    console.log('\n(単位: ms)');
  }
}
