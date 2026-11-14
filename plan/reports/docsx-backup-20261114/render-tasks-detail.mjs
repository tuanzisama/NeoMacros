// Renders gap/tasks-detail.md: every task member plus the D/E exclusions as
// human-readable Markdown tables. Full machine data stays in tasks.json.
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const dumpRoot = resolve(here, '..', '..');
const data = JSON.parse(readFileSync(join(dumpRoot, 'gap', 'tasks.json'), 'utf8'));

const lines = [];
const out = (s = '') => lines.push(s);
const c = (s) => `\`${String(s ?? '').replace(/`/g, '\\`')}\``;
const esc = (s) => String(s ?? '').replace(/\|/g, '\\|').replace(/\n/g, ' ');

const STATUS = {
  ready: '可认领', blocked: '阻塞', 'design-first': '设计先行', 'no-op': '不实施',
};
const MEMBER_STATUS = {
  missing: '缺失', partial: '部分', 'permanent-gap': '永久缺口',
  covered: '已覆盖', renamed: '改名', 'not-applicable': '不适用',
};

out('# JsMacros 缺口开发任务 · 逐成员明细');
out();
out('> 由 `gap/tasks.json` 生成；每个任务列出全部成员（含签名/状态/目标/理由）。机器可读数据与依赖/验收见 `tasks.json`，任务说明见 `neomacros-docsx/15-jsmacros-gap-development-plan.md`。');
out();

for (const t of data.tasks) {
  out(`## ${t.id} · ${t.title}`);
  out();
  out(`- 阶段/优先级/规模/状态：${t.phase} / ${t.priority} / ${t.size} / ${STATUS[t.status] ?? t.status}`);
  out(`- 仓库：${t.repo}；依赖：${t.depends.length ? t.depends.map(c).join(' ') : '无'}；成员：${t.memberCount}`);
  out(`- 落点：${t.targets.length ? t.targets.map(c).join(' ') : '—'}`);
  out();
  if (!t.members.length) {
    out('无成员（设计/占位任务）。');
    out();
    continue;
  }
  out('| 类 | 成员 | 类型 | 签名 | 状态 | 目标 | 理由/证据 |');
  out('| --- | --- | --- | --- | --- | --- | --- |');
  for (const m of t.members) {
    out(`| ${c(m.class)} | ${c(m.name)} | ${m.kind} | ${c(m.signature || m.name)} | ${MEMBER_STATUS[m.status] ?? m.status} | ${m.target ? c(m.target) : '—'} | ${esc(m.reason || m.evidence || '')} |`);
  }
  out();
}

function exclusionSection(title, key, note) {
  const rows = data.exclusions[key] ?? [];
  out(`## ${title}（${rows.length}）`);
  out();
  out(note);
  out();
  if (!rows.length) return;
  out('| 类 | 成员 | 类型 | 签名 | 状态 | 理由/证据 |');
  out('| --- | --- | --- | --- | --- | --- |');
  for (const m of rows) {
    out(`| ${c(m.class)} | ${c(m.name)} | ${m.kind} | ${c(m.signature || m.name)} | ${MEMBER_STATUS[m.status] ?? m.status} | ${esc(m.reason || m.evidence || '')} |`);
  }
  out();
}

exclusionSection('D · 不映射 / 服务端-only / 既定反模式', 'EXCL-D',
  '> 这些成员不进入开发任务；完整判定见 13 号 §2.2/§2.3。');
exclusionSection('E · 永久缺口', 'EXCL-E',
  '> 运行时 Java 编译 / 动态类生成 / Jar 加载；按 13 号 §2.1 拍板不实施。');

writeFileSync(join(dumpRoot, 'gap', 'tasks-detail.md'), lines.join('\n') + '\n', 'utf8');
console.log('written gap/tasks-detail.md');
