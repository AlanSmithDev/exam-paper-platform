// AI 域服务（docs/15 §2 AI-1~AI-5）：Provider 抽象（MOCK / OPENAI_COMPAT）、变式出题、分步讲题、ai_log 治理、会员配额。
// 安全说明：外部调用目标仅限运营通过环境变量配置的 OPENAI 兼容端点，且经 SSRF 白名单校验
//（仅 http/https、默认禁止环回/内网/链路本地地址，内网自建 vLLM 需显式 AI_ALLOW_PRIVATE=1）；
// 15s 超时、失败降级 MOCK 并标记 degraded；生成内容不直接入库，草稿一律进人工审核流程。
const db = require('./db');
const trade = require('./trade');
const net = require('net');

const TIMEOUT_MS = 15000;

// ---------- SSRF 防护：校验运营配置的 AI 端点（等同 admin.js assertSafeUrl 策略）----------
function isPrivateIp(ip) {
  return ip === '::1' || ip.startsWith('127.') || ip.startsWith('10.') || ip.startsWith('192.168.') ||
    /^172\.(1[6-9]|2\d|3[01])\./.test(ip) || ip.startsWith('169.254.') || ip === '0.0.0.0' || ip.endsWith('.local');
}
function assertSafeAiBase(rawUrl) {
  let parsed;
  try { parsed = new URL(String(rawUrl)); } catch (_) { throw new Error('AI_BASE 格式非法'); }
  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') throw new Error('AI_BASE 仅允许 http/https');
  const allowPrivate = process.env.AI_ALLOW_PRIVATE === '1';
  if (net.isIP(parsed.hostname)) {
    if (isPrivateIp(parsed.hostname) && !allowPrivate) throw new Error('AI_BASE 禁止指向内网/环回地址');
    return parsed;
  }
  if ((parsed.hostname === 'localhost' || parsed.hostname.endsWith('.local')) && !allowPrivate) {
    throw new Error('AI_BASE 禁止指向本机域名');
  }
  return parsed; // 公网域名放行；企业版上线前追加 DNS 解析复核（docs/19 §9）
}

// ---------- 配额（AI-4，对接会员权益 M-1/D16）----------
function quotaOf(uid) {
  const ent = trade.entitlement(uid);
  const key = ent.member.active ? 'ai_quota_member' : 'ai_quota_free';
  const quota = Number(db.prepare('SELECT value FROM settings WHERE key=?').get(key)?.value || (ent.member.active ? 50 : 3));
  const used = db.prepare("SELECT COUNT(*) AS c FROM ai_log WHERE user_id=? AND created_at >= date('now','localtime')").get(Number(uid)).c;
  return { quota, used, left: Math.max(0, quota - used), member: ent.member.active };
}
function assertQuota(uid) {
  const q = quotaOf(uid);
  if (q.used >= q.quota) { const e = new Error(`今日 AI 配额已用完（${q.quota} 次）`); e.code = 42900; e.reason = 'AI_QUOTA_EXCEEDED'; e.quota = q; throw e; }
  return q;
}

// ---------- Provider 抽象（AI-1/AI-5）----------
let cachedBase = null;
function aiBase() {
  if (cachedBase !== null) return cachedBase;
  const raw = process.env.AI_BASE;
  if (!raw) { cachedBase = ''; return cachedBase; }
  try { cachedBase = assertSafeAiBase(raw).toString().replace(/\/$/, ''); } catch (e) {
    console.error('[ai] AI_BASE 校验失败，降级 MOCK：', e.message);
    cachedBase = '';
  }
  return cachedBase;
}
async function chat(system, user) {
  const provider = process.env.AI_PROVIDER || 'MOCK';
  const base = aiBase();
  const started = Date.now();
  if (provider === 'OPENAI_COMPAT' && base && process.env.AI_KEY) {
    try {
      const ctrl = new AbortController();
      const timer = setTimeout(() => ctrl.abort(), TIMEOUT_MS);
      const r = await fetch(base + '/chat/completions', {
        method: 'POST', signal: ctrl.signal,
        headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + process.env.AI_KEY },
        body: JSON.stringify({ model: process.env.AI_MODEL || 'gpt', messages: [{ role: 'system', content: system }, { role: 'user', content: user }] })
      });
      clearTimeout(timer);
      if (!r.ok) throw new Error('HTTP ' + r.status);
      const j = await r.json();
      return { text: j.choices?.[0]?.message?.content || '', provider, model: process.env.AI_MODEL || 'gpt', degraded: false, costMs: Date.now() - started, tokens: j.usage?.total_tokens || 0 };
    } catch (_) { /* AI-5：失败降级 MOCK 并标记 degraded */ }
  }
  return { text: mockChat(system, user), provider: 'MOCK', model: 'mock-1', degraded: provider !== 'MOCK', costMs: Date.now() - started, tokens: Math.ceil((system.length + user.length) / 4) };
}
// MOCK 通道：离线确定性输出（开发/测试/降级用）
function mockChat(system, user) {
  if (system.includes('变式')) return mockVariantText(user);
  return mockExplainText(user);
}
// 数值缩放构造变式题干（演示级：保持考点/题型，替换情境数据）
function scaleNumbers(text) {
  let n = 0;
  return String(text).replace(/\d+(?:\.\d+)?/g, (m) => {
    n++;
    const v = parseFloat(m);
    if (!isFinite(v) || v === 0) return m;
    return String(n % 2 === 0 ? v * 2 : v + 1);
  });
}
function mockVariantText(stem) {
  return `[AI-草稿] 变式题干：${scaleNumbers(String(stem).slice(0, 600))}\n（保持原考点与题型，情境数据已更换；草稿需编辑审核后方可入库，aigc=1）`;
}
function mockExplainText(payload) {
  let q = {};
  try { q = JSON.parse(payload); } catch (_) { q = { stem: payload }; }
  const steps = [];
  steps.push(`第 1 步·审题：抓住题干条件「${String(q.stem || '').slice(0, 60)}…」，明确所求。`);
  if (q.analysis && q.analysis.brief) steps.push(`第 2 步·思路：${q.analysis.brief}`);
  if (q.analysis && q.analysis.solve) steps.push(`第 3 步·解答：${q.analysis.solve}`);
  if (!q.analysis) steps.push('第 3 步·解答：按知识点常规方法推导（MOCK 演示）。');
  if (q.analysis && q.analysis.comment) steps.push(`第 4 步·点评：${q.analysis.comment}`);
  steps.push(`参考答案：${q.answer || '见解析'}`);
  return steps.join('\n');
}

// ---------- 变式出题（AI-2，TJ-101 提示词升级：命题专家四模块 + 难度梯度，参考 ai-chuti）----------
async function variants(uid, b) {
  assertQuota(uid);
  let stem = String(b.stem || '').trim(), meta = {};
  if (b.questionId) {
    const q = db.prepare('SELECT * FROM questions WHERE id=? AND status=2').get(Number(b.questionId));
    if (!q) { const e = new Error('题目不存在或未上架'); e.code = 40400; throw e; }
    stem = q.stem; meta = { questionId: q.id, type: q.type, difficulty: q.difficulty, kpNames: q.kp_names };
  }
  if (stem.length < 5) { const e = new Error('题干过短，无法出题'); e.code = 42200; throw e; }
  const system = [
    '你是一位国家级命题组专家、高级教研员兼中高考考纲研究导师。',
    '请基于给定题目生成一道变式题，输出四个标准模块：',
    '1.【变式题干与核心考点标注】：表述严谨无歧义，保持原考点/题型/难度，替换情境与数据；',
    '2.【选项/填空设置与干扰项设计】：客观题给 ABCD 选项并设计高质量干扰项，主观题给分问拆解；',
    '3.【标准参考答案与得分要点】：给出标准答案与踩分关键词；',
    '4.【解析与易错陷阱提示】：解题思路、核心公式与常见思维盲点。',
    '输出以 [AI-草稿] 开头。生成的草稿仅进入人工审核队列（aigc=1），不直接上架。'
  ].join('\n');
  const out = await chat(system, stem);
  const draft = { kind: 'VARIANT', marker: '[AI-草稿]', stem: out.text, ...meta, aigc: 1, reviewStatus: 'PENDING_REVIEW' };
  logAi(uid, 'VARIANT', out, stem.length);
  return { draft, quota: quotaOf(uid) };
}

// ---------- 分步讲题（AI-3）----------
async function explain(uid, b) {
  assertQuota(uid);
  let payload;
  if (b.questionId) {
    const q = db.prepare('SELECT * FROM questions WHERE id=? AND status=2').get(Number(b.questionId));
    if (!q) { const e = new Error('题目不存在或未上架'); e.code = 40400; throw e; }
    payload = JSON.stringify({ stem: q.stem, answer: q.answer, analysis: q.analysis ? JSON.parse(q.analysis) : null });
  } else {
    if (!b.stem || String(b.stem).trim().length < 5) { const e = new Error('请提供题干或 questionId'); e.code = 42200; throw e; }
    payload = JSON.stringify({ stem: String(b.stem).slice(0, 2000), answer: b.answer || '' });
  }
  const out = await chat('你是数学老师。基于题目与解析输出分步讲题（审题→思路→解答→点评），逐步编号。', payload);
  logAi(uid, 'EXPLAIN', out, payload.length);
  return { kind: 'EXPLAIN', steps: out.text, provider: out.provider, degraded: out.degraded, quota: quotaOf(uid) };
}

// ---------- 治理（AI-4）----------
function logAi(uid, kind, out, promptChars) {
  const d = new Date();
  const p = (n) => String(n).padStart(2, '0');
  const now = `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
  db.prepare('INSERT INTO ai_log(user_id, kind, provider, model, prompt_chars, tokens, cost_ms, degraded, created_at) VALUES(?,?,?,?,?,?,?,?,?)')
    .run(Number(uid), kind, out.provider, out.model, promptChars || 0, out.tokens || 0, out.costMs || 0, out.degraded ? 1 : 0, now);
}
function myLogs(uid, limit) {
  return Array.from(db.prepare('SELECT id, kind, provider, model, degraded, created_at FROM ai_log WHERE user_id=? ORDER BY id DESC LIMIT ?')
    .iterate(Number(uid), Math.min(50, Number(limit) || 10)));
}

module.exports = { variants, explain, quotaOf, myLogs, scaleNumbers, TIMEOUT_MS };
