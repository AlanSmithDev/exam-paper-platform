// WP-4: 超管后台 API（SUPER_ADMIN / OP / EDITOR 分权 + 审计 + SSRF 校验）
const express = require('express');
const store = require('../store');
const { required, requireRole, audit } = require('../auth');

const r = express.Router();
r.use(required);

const ADMIN_ROLES = ['SUPER_ADMIN', 'OP'];
const CONTENT_ROLES = ['SUPER_ADMIN', 'EDITOR'];

// SSRF 防护（实现验收条件）：仅允许 http/https，且 host 不得解析到内网/环回/保留地址
const dns = require('dns').promises;
const net = require('net');
async function assertSafeUrl(u) {
  if (!u) return; // 空素材合法（前台占位）
  let parsed;
  try { parsed = new URL(String(u)); } catch (_) { throw Object.assign(new Error('URL 格式非法'), { code: 42200 }); }
  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
    throw Object.assign(new Error('仅允许 http/https 协议'), { code: 42200 });
  }
  const host = parsed.hostname;
  const isPrivateIp = (ip) => ip === '::1' || ip.startsWith('127.') || ip.startsWith('10.') ||
    ip.startsWith('192.168.') || /^172\.(1[6-9]|2\d|3[01])\./.test(ip) || ip.startsWith('169.254.') ||
    ip === '0.0.0.0' || ip.endsWith('.local');
  if (net.isIP(host)) {
    if (isPrivateIp(host)) throw Object.assign(new Error('禁止访问内网/环回地址'), { code: 42200 });
    return;
  }
  let addrs;
  try { addrs = await dns.lookup(host, { all: true }); } catch (_) {
    throw Object.assign(new Error('域名无法解析'), { code: 42200 });
  }
  for (const a of addrs) {
    if (isPrivateIp(a.address)) throw Object.assign(new Error('禁止访问解析到内网的域名'), { code: 42200 });
  }
}

// —— 数据看板 ——
r.get('/dashboard', requireRole(...ADMIN_ROLES, 'EDITOR'), (req, res) => {
  audit(req, 'VIEW_DASHBOARD', {});
  res.json({ code: 0, message: 'ok', data: store.dashboard() });
});

// —— 广告位管理（超管/运营）——
r.get('/ads', requireRole(...ADMIN_ROLES), (_req, res) => {
  res.json({ code: 0, message: 'ok', data: { list: store.allAds() } });
});
r.post('/ads', requireRole(...ADMIN_ROLES), async (req, res) => {
  const b = req.body || {};
  try { await assertSafeUrl(b.image_url); await assertSafeUrl(b.link_url); }
  catch (e) { return res.status(422).json({ code: e.code || 42200, message: e.message, data: null }); }
  const id = store.adCreate(b);
  audit(req, 'AD_CREATE', { id, position: b.position });
  res.json({ code: 0, message: 'ok', data: { id } });
});
r.put('/ads/:id', requireRole(...ADMIN_ROLES), async (req, res) => {
  const b = req.body || {};
  try { await assertSafeUrl(b.image_url); await assertSafeUrl(b.link_url); }
  catch (e) { return res.status(422).json({ code: e.code || 42200, message: e.message, data: null }); }
  const changes = store.adUpdate(req.params.id, b);
  audit(req, 'AD_UPDATE', { id: Number(req.params.id), changes });
  res.json({ code: 0, message: 'ok', data: { changes } });
});
r.delete('/ads/:id', requireRole(...ADMIN_ROLES), (req, res) => {
  const changes = store.adDelete(req.params.id);
  audit(req, 'AD_DELETE', { id: Number(req.params.id), changes });
  res.json({ code: 0, message: 'ok', data: { changes } });
});

// —— 题目管理（超管/编辑）——
r.get('/questions', requireRole(...CONTENT_ROLES), (req, res) => {
  const pageNo = Math.max(1, parseInt(req.query.pageNo || '1', 10) || 1);
  const pageSize = Math.min(50, Math.max(1, parseInt(req.query.pageSize || '20', 10) || 20));
  res.json({ code: 0, message: 'ok', data: store.adminQuestions(pageNo, pageSize) });
});
r.put('/questions/:id', requireRole(...CONTENT_ROLES), (req, res) => {
  const changes = store.adminQuestionUpdate(req.params.id, req.body || {});
  audit(req, 'QUESTION_UPDATE', { id: Number(req.params.id), changes });
  res.json({ code: 0, message: 'ok', data: { changes } });
});
r.put('/questions/:id/status', requireRole(...CONTENT_ROLES), (req, res) => {
  const status = Number((req.body || {}).status) || 0;
  const changes = store.adminQuestionStatus(req.params.id, status);
  audit(req, 'QUESTION_STATUS', { id: Number(req.params.id), status, changes });
  res.json({ code: 0, message: 'ok', data: { changes } });
});

// —— 用户管理（仅超管）——
r.get('/users', requireRole('SUPER_ADMIN'), (_req, res) => {
  res.json({ code: 0, message: 'ok', data: { list: store.adminUsers() } });
});
r.put('/users/:id', requireRole('SUPER_ADMIN'), (req, res) => {
  const changes = store.adminUserPatch(req.params.id, req.body || {});
  audit(req, 'USER_PATCH', { id: Number(req.params.id), patch: req.body, changes });
  res.json({ code: 0, message: 'ok', data: { changes } });
});

// —— 公告 ——
r.get('/notices', requireRole(...ADMIN_ROLES), (_req, res) => {
  res.json({ code: 0, message: 'ok', data: { list: store.noticeList() } });
});
r.post('/notices', requireRole(...ADMIN_ROLES), (req, res) => {
  const id = store.noticeCreate(req.body || {});
  audit(req, 'NOTICE_CREATE', { id });
  res.json({ code: 0, message: 'ok', data: { id } });
});
r.delete('/notices/:id', requireRole(...ADMIN_ROLES), (req, res) => {
  res.json({ code: 0, message: 'ok', data: { changes: store.noticeDelete(req.params.id) } });
});

// —— 系统设置（仅超管）——
r.put('/settings', requireRole('SUPER_ADMIN'), (req, res) => {
  const n = store.settingsPut(req.body || {});
  audit(req, 'SETTINGS_PUT', { keys: Object.keys(req.body || {}) });
  res.json({ code: 0, message: 'ok', data: { updated: n } });
});

// —— 审计日志（只读，仅超管）——
r.get('/audit', requireRole('SUPER_ADMIN'), (req, res) => {
  res.json({ code: 0, message: 'ok', data: { list: store.auditList(req.query.limit) } });
});

// —— 录题审核（超管/编辑，P-5：上架奖励作者 +20 点）——
r.post('/questions/:id/review', requireRole(...CONTENT_ROLES), (req, res) => {
  const status = Number((req.body || {}).status);
  try {
    const out = trade.reviewQuestion(req.params.id, status, req.user.nickname);
    audit(req, 'QUESTION_REVIEW', out);
    res.json({ code: 0, message: 'ok', data: out });
  } catch (e) {
    if (e.code === 40001) return res.status(409).json({ code: e.code, message: e.message, data: null });
    if (e.code === 40400) return res.status(404).json({ code: e.code, message: e.message, data: null });
    if (e.code === 42200) return res.status(422).json({ code: e.code, message: e.message, data: null });
    throw e;
  }
});

// —— 商业化运营：优惠券模板（超管/运营，docs/17 §3.5）——
const trade = require('../trade');
r.get('/coupons', requireRole(...ADMIN_ROLES), (req, res) => {
  const pageNo = Math.max(1, Number(req.query.pageNo) || 1);
  const pageSize = Math.min(50, Number(req.query.pageSize) || 20);
  res.json({ code: 0, message: 'ok', data: trade.adminCouponTemplates(pageNo, pageSize) });
});
r.post('/coupons', requireRole(...ADMIN_ROLES), (req, res) => {
  const b = req.body || {};
  if (!b.code || !b.name || !['FULL_REDUCTION', 'DISCOUNT', 'POINTS'].includes(b.type)) {
    return res.status(422).json({ code: 42200, message: 'code/name/type 非法（type：FULL_REDUCTION/DISCOUNT/POINTS）', data: null });
  }
  try {
    const id = trade.createCouponTemplate(b);
    audit(req, 'COUPON_CREATE', { id, code: b.code, type: b.type, total: b.total });
    res.json({ code: 0, message: 'ok', data: { id } });
  } catch (e) {
    if (String(e.message).includes('UNIQUE')) return res.status(422).json({ code: 42200, message: '模板编码已存在', data: null });
    throw e;
  }
});
r.put('/coupons/:id/status', requireRole(...ADMIN_ROLES), (req, res) => {
  const status = Number((req.body || {}).status);
  if (![0, 1, 2].includes(status)) return res.status(422).json({ code: 42200, message: 'status 必须为 0 下架/1 上架/2 作废', data: null });
  const changes = trade.setCouponStatus(req.params.id, status);
  audit(req, 'COUPON_STATUS', { id: Number(req.params.id), status, changes });
  res.json({ code: 0, message: 'ok', data: { changes } });
});

// —— 商业化运营：订单（超管/运营，docs/17 §7）——
r.get('/orders', requireRole(...ADMIN_ROLES), (req, res) => {
  const pageNo = Math.max(1, Number(req.query.pageNo) || 1);
  const pageSize = Math.min(50, Number(req.query.pageSize) || 20);
  res.json({ code: 0, message: 'ok', data: trade.adminOrders(req.query.status, pageNo, pageSize) });
});
r.post('/orders/:orderNo/close', requireRole(...ADMIN_ROLES), (req, res) => {
  try {
    const o = trade.closeOrder(req.params.orderNo, null);
    audit(req, 'ORDER_CLOSE', { orderNo: req.params.orderNo });
    res.json({ code: 0, message: 'ok', data: trade.serializeOrder(o) });
  } catch (e) {
    if (e.code === 40001) return res.status(409).json({ code: e.code, message: e.message, data: null });
    if (e.code === 40400) return res.status(404).json({ code: e.code, message: e.message, data: null });
    throw e;
  }
});

// —— 纠错处理（超管/编辑，P-5：采纳奖励 +5 点）——
r.get('/feedback', requireRole(...ADMIN_ROLES, 'EDITOR'), (req, res) => {
  const pageNo = Math.max(1, Number(req.query.pageNo) || 1);
  const pageSize = Math.min(50, Number(req.query.pageSize) || 20);
  res.json({ code: 0, message: 'ok', data: trade.adminFeedback(req.query.status, pageNo, pageSize) });
});
r.post('/feedback/:id/review', requireRole(...ADMIN_ROLES, 'EDITOR'), (req, res) => {
  const status = (req.body || {}).status;
  try {
    const out = trade.reviewFeedback(req.params.id, status, req.user.nickname);
    audit(req, 'FEEDBACK_REVIEW', out);
    res.json({ code: 0, message: 'ok', data: out });
  } catch (e) {
    if (e.code === 40001) return res.status(409).json({ code: e.code, message: e.message, data: null });
    if (e.code === 40400) return res.status(404).json({ code: e.code, message: e.message, data: null });
    if (e.code === 42200) return res.status(422).json({ code: e.code, message: e.message, data: null });
    throw e;
  }
});

module.exports = r;
