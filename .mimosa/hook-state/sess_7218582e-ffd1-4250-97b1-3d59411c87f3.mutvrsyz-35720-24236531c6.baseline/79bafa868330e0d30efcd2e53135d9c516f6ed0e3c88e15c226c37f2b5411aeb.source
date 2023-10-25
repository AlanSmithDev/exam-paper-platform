// 商业化路由：交易/计费/营销（docs/17 §4-§5、§7）
const express = require('express');
const trade = require('../trade');
const { required, requireRole, audit } = require('../auth');

const r = express.Router();

// GET /trade/billing?paperId= 或 ?questionCount=&paperHash= —— 下载判价（D-1）
r.get('/billing', required, (req, res) => {
  let qCount = Number(req.query.questionCount) || 0;
  let hash = req.query.paperHash ? String(req.query.paperHash) : null;
  if (req.query.paperId) {
    const ids = trade.paperQuestionIds(req.query.paperId);
    if (!ids.length) return res.status(404).json({ code: 40400, message: '试卷不存在', data: null });
    hash = trade.paperHashOf(ids);
    qCount = ids.length;
  }
  if (!qCount || !hash) return res.status(422).json({ code: 42200, message: '需要 paperId 或 questionCount+paperHash', data: null });
  res.json({ code: 0, message: 'ok', data: trade.billing(req.user.uid, qCount, hash, req.query.paperId ? Number(req.query.paperId) : null) });
});

// POST /trade/consume —— 导出成功后扣费（D-2，服务端复算）
r.post('/consume', required, (req, res) => {
  const b = req.body || {};
  if (!b.paperHash || !Number(b.questionCount)) {
    return res.status(422).json({ code: 42200, message: '缺少 paperHash 或 questionCount', data: null });
  }
  try {
    res.json({ code: 0, message: 'ok', data: trade.consume(req.user.uid, b.paperId ? Number(b.paperId) : null, String(b.paperHash), Number(b.questionCount)) });
  } catch (e) {
    if (e.code === 42900) return res.status(429).json({ code: 42900, message: e.message, data: { needPoints: e.needPoints } });
    throw e;
  }
});

// GET /trade/points —— 点数余额与流水
r.get('/points', required, (req, res) => {
  const pageNo = Math.max(1, Number(req.query.pageNo) || 1);
  const pageSize = Math.min(100, Number(req.query.pageSize) || 20);
  res.json({ code: 0, message: 'ok', data: trade.pointsLog(req.user.uid, pageNo, pageSize) });
});

// POST /trade/points/adjust —— 人工调整（仅 SUPER_ADMIN，入审计）
r.post('/points/adjust', requireRole('SUPER_ADMIN'), (req, res) => {
  const b = req.body || {};
  const uid = Number(b.userId);
  const delta = Number(b.delta);
  if (!Number.isInteger(uid) || !Number.isInteger(delta) || delta === 0) {
    return res.status(422).json({ code: 42200, message: 'userId/delta 非法（delta 非 0 整数）', data: null });
  }
  try {
    const out = trade.adminPointsAdjust(uid, delta, b.note, req.user.nickname);
    audit(req, 'TRADE_POINTS_ADJUST', { userId: uid, delta, note: b.note || '' });
    res.json({ code: 0, message: 'ok', data: out });
  } catch (e) {
    if (e.code === 42900) return res.status(429).json({ code: 42900, message: '回收点数超过该用户当前余额', data: null });
    throw e;
  }
});

// POST /trade/signin —— 签到（唯一索引防重复）
r.post('/signin', required, (req, res) => {
  try {
    res.json({ code: 0, message: 'ok', data: trade.signIn(req.user.uid) });
  } catch (e) {
    if (e.code === 42900) return res.status(429).json({ code: 42900, message: e.message, data: { reason: e.reason } });
    throw e;
  }
});

// GET /trade/signin —— 当月日历与连续天数
r.get('/signin', required, (req, res) => {
  res.json({ code: 0, message: 'ok', data: trade.signInInfo(req.user.uid) });
});

// GET /trade/invite —— 邀请码与记录
r.get('/invite', required, (req, res) => {
  res.json({ code: 0, message: 'ok', data: trade.inviteInfo(req.user.uid) });
});

// GET /trade/downloads —— 我的下载记录（下载中心，docs/25 TJ-92）
r.get('/downloads', required, (req, res) => {
  const db = require('../db');
  const pageNo = Math.max(1, Number(req.query.pageNo) || 1);
  const pageSize = Math.min(50, Number(req.query.pageSize) || 15);
  const total = db.prepare('SELECT COUNT(*) AS c FROM download_record WHERE user_id=?').get(req.user.uid).c;
  const rows = Array.from(db.prepare(`SELECT d.id, d.paper_id, d.question_count, d.charge_mode, d.points_charged, d.order_no, d.created_at,
      COALESCE(p.title, '试卷 #' || d.paper_id) AS paper_title
    FROM download_record d LEFT JOIN papers p ON p.id = d.paper_id
    WHERE d.user_id=? ORDER BY d.id DESC LIMIT ? OFFSET ?`).iterate(req.user.uid, pageSize, (pageNo - 1) * pageSize));
  res.json({ code: 0, message: 'ok', data: { total, rows } });
});

module.exports = r;
