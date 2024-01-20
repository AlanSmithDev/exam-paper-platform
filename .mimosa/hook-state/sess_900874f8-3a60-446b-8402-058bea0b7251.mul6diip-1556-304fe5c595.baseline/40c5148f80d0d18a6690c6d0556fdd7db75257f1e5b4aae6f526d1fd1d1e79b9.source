// 商业化路由：优惠券（docs/17 §3）
const express = require('express');
const trade = require('../trade');
const { required } = require('../auth');

const r = express.Router();

// GET /coupons/available —— 可领列表（附 claimState）
r.get('/available', required, (req, res) => {
  res.json({ code: 0, message: 'ok', data: { list: trade.couponsAvailable(req.user.uid) } });
});

// POST /coupons/claim —— 领取（条件 UPDATE 防超发）
r.post('/claim', required, (req, res) => {
  const b = req.body || {};
  if (!Number.isInteger(Number(b.templateId))) {
    return res.status(422).json({ code: 42200, message: 'templateId 非法', data: null });
  }
  try {
    res.json({ code: 0, message: 'ok', data: trade.claimCoupon(req.user.uid, Number(b.templateId)) });
  } catch (e) {
    if (e.code === 42900) return res.status(429).json({ code: 42900, message: e.message, data: { reason: e.reason } });
    if (e.code === 42200) return res.status(422).json({ code: 42200, message: e.message, data: null });
    throw e;
  }
});

// GET /coupons/mine?status= —— 我的券（状态过滤，查询前惰性过期）
r.get('/mine', required, (req, res) => {
  trade.lazyExpire(req.user.uid);
  res.json({ code: 0, message: 'ok', data: { list: trade.myCoupons(req.user.uid, req.query.status) } });
});

// GET /coupons/usable?skuType=&originCents= —— 收银台可用/不可用券与原因
r.get('/usable', required, (req, res) => {
  const skuType = ['MEMBER', 'POINTS', 'PAPER'].includes(req.query.skuType) ? req.query.skuType : null;
  const origin = Number(req.query.originCents) || 0;
  trade.lazyExpire(req.user.uid);
  const list = trade.myCoupons(req.user.uid, 'UNUSED').map(c => {
    const item = { ...c };
    if (!skuType || (c.scope !== 'ALL' && c.scope !== skuType)) { item.usable = false; item.blockedReason = 'SCOPE'; }
    else if (c.thresholdCents > origin) { item.usable = false; item.blockedReason = 'THRESHOLD'; }
    else {
      item.usable = true;
      item.discountCents = c.type === 'FULL_REDUCTION' ? Math.min(c.amountCents, origin)
        : c.type === 'DISCOUNT' ? Math.min(c.maxDiscountCents > 0 ? Math.min(Math.floor(origin * (100 - c.discountRate) / 100), c.maxDiscountCents) : Math.floor(origin * (100 - c.discountRate) / 100), origin)
        : 0;
    }
    return item;
  });
  res.json({ code: 0, message: 'ok', data: { list } });
});

module.exports = r;
