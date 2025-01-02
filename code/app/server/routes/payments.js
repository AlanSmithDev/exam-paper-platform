// 商业化路由：支付（docs/17 §2）
const express = require('express');
const trade = require('../trade');
const { required, requireRole, audit } = require('../auth');

const r = express.Router();

// POST /payments/mock-notify —— 沙箱回调（生产环境禁用，替换为渠道验签回调）
r.post('/mock-notify', required, (req, res) => {
  if (process.env.NODE_ENV === 'production') {
    return res.status(403).json({ code: 40301, message: '沙箱回调已在生产环境禁用', data: null });
  }
  const b = req.body || {};
  if (!b.orderNo || !b.callbackId) return res.status(422).json({ code: 42200, message: '缺少 orderNo 或 callbackId', data: null });
  const clientIp = (req.headers['x-forwarded-for'] || req.socket.remoteAddress || '-').toString().split(',')[0].trim().slice(0, 64);
  try {
    const out = trade.payNotify(String(b.orderNo), String(b.callbackId), Number(b.amountCents), 'MOCK', clientIp);
    res.json({ code: out.duplicated ? 42901 : 0, message: out.duplicated ? '重复回调已忽略' : 'ok', data: out.detail });
  } catch (e) {
    if (e.code === 40001) return res.status(409).json({ code: e.code, message: e.message, data: null });
    if (e.code === 40400) return res.status(404).json({ code: e.code, message: e.message, data: null });
    if (e.code === 42200) return res.status(422).json({ code: e.code, message: e.message, data: null });
    throw e;
  }
});

// POST /payments/refund/:orderNo —— 退款（仅 SUPER_ADMIN，固定回滚顺序 + 审计）
r.post('/refund/:orderNo', requireRole('SUPER_ADMIN'), (req, res) => {
  try {
    const out = trade.refund(req.params.orderNo);
    audit(req, 'TRADE_REFUND', out);
    res.json({ code: 0, message: 'ok', data: out });
  } catch (e) {
    if (e.code === 40001) return res.status(409).json({ code: e.code, message: e.message, data: null });
    if (e.code === 40400) return res.status(404).json({ code: e.code, message: e.message, data: null });
    throw e;
  }
});

module.exports = r;
