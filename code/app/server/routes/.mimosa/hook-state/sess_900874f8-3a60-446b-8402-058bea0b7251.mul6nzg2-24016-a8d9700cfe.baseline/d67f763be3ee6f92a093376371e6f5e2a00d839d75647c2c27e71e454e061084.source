// 商业化路由：会员与订单（docs/17 §1）
const express = require('express');
const trade = require('../trade');
const { required } = require('../auth');

const r = express.Router();

// GET /member/plans —— 档位与点数套餐（公开）
r.get('/plans', (_req, res) => {
  res.json({ code: 0, message: 'ok', data: trade.plans() });
});

// GET /member/me —— 权益唯一入口（entitlement，M-3）
r.get('/me', required, (req, res) => {
  res.json({ code: 0, message: 'ok', data: trade.entitlement(req.user.uid) });
});

// POST /member/orders —— 下单（O-2 幂等；X-Idempotency-Key 头与 body.idempotencyKey 等价，头优先）
r.post('/orders', required, (req, res) => {
  const b = req.body || {};
  const idemKey = String(req.headers['x-idempotency-key'] || b.idempotencyKey || '').trim();
  if (!idemKey) return res.status(422).json({ code: 42200, message: '缺少幂等键 idempotencyKey', data: null });
  if (!['MEMBER', 'POINTS', 'PAPER'].includes(b.skuType)) {
    return res.status(422).json({ code: 42200, message: 'skuType 必须为 MEMBER/POINTS/PAPER', data: null });
  }
  try {
    const { order, replay } = trade.createOrder(req.user.uid, b, idemKey);
    if (replay) res.set('X-Idempotent-Replay', 'true');
    res.json({ code: 0, message: 'ok', data: trade.orderDetail(order.order_no, req.user.uid) });
  } catch (e) {
    const map = { 40001: 409, 40400: 404, 42200: 422, 42900: 429 };
    if (e.code) return res.status(map[e.code] || 422).json({ code: e.code, message: e.message, data: e.needCents ? { needCents: e.needCents } : (e.needPoints ? { needPoints: e.needPoints } : (e.reason ? { reason: e.reason } : null)) });
    throw e;
  }
});

// GET /member/orders —— 我的订单
r.get('/orders', required, (req, res) => {
  const pageNo = Math.max(1, Number(req.query.pageNo) || 1);
  const pageSize = Math.min(50, Number(req.query.pageSize) || 10);
  res.json({ code: 0, message: 'ok', data: trade.myOrders(req.user.uid, pageNo, pageSize) });
});

// GET /member/orders/:orderNo —— 订单详情（本人或管理角色）
r.get('/orders/:orderNo', required, (req, res) => {
  const adminRoles = ['SUPER_ADMIN', 'OP'];
  const d = trade.orderDetail(req.params.orderNo, adminRoles.includes(req.user.role) ? null : req.user.uid);
  if (!d) return res.status(404).json({ code: 40400, message: '订单不存在', data: null });
  res.json({ code: 0, message: 'ok', data: d });
});

// POST /member/orders/:orderNo/close —— 主动关单（本人；管理员走 /admin/orders/:orderNo/close）
r.post('/orders/:orderNo/close', required, (req, res) => {
  try {
    const o = trade.closeOrder(req.params.orderNo, req.user.uid);
    res.json({ code: 0, message: 'ok', data: trade.serializeOrder(o) });
  } catch (e) {
    if (e.code === 40001) return res.status(409).json({ code: e.code, message: e.message, data: null });
    if (e.code === 40400) return res.status(404).json({ code: e.code, message: e.message, data: null });
    throw e;
  }
});

module.exports = r;
