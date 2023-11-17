// 消息中心路由（docs/17 §7E：站内信）
const express = require('express');
const message = require('../message');
const { required } = require('../auth');

const r = express.Router();
r.use(required);

// GET /messages?pageNo&pageSize —— 我的消息 + 未读数
r.get('/', (req, res) => {
  const pageNo = Math.max(1, Number(req.query.pageNo) || 1);
  const pageSize = Math.min(50, Number(req.query.pageSize) || 15);
  res.json({ code: 0, message: 'ok', data: message.myMessages(req.user.uid, pageNo, pageSize) });
});

// GET /messages/unread-count —— 导航铃铛角标
r.get('/unread-count', (req, res) => {
  const d = message.myMessages(req.user.uid, 1, 1);
  res.json({ code: 0, message: 'ok', data: { unread: d.unread } });
});

// POST /messages/:id/read —— 标记已读
r.post('/:id/read', (req, res) => {
  try {
    res.json({ code: 0, message: 'ok', data: message.markRead(req.user.uid, req.params.id) });
  } catch (e) {
    if (e.code === 40400) return res.status(404).json({ code: e.code, message: e.message, data: null });
    throw e;
  }
});

// POST /messages/read-all —— 全部已读
r.post('/read-all', (req, res) => {
  res.json({ code: 0, message: 'ok', data: message.markAllRead(req.user.uid) });
});

module.exports = r;
