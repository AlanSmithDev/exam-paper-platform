// WP-3: 试题篮（登录，上限 100）
const express = require('express');
const store = require('../store');
const { required } = require('../auth');

const r = express.Router();
r.use(required);

r.get('/', (req, res) => {
  const ids = store.basketIds(req.user.uid).map(x => x.id);
  res.json({ code: 0, message: 'ok', data: { count: ids.length, ids } });
});

r.post('/items', (req, res) => {
  const body = req.body || {};
  const qids = Array.isArray(body.questionIds) ? body.questionIds.map(Number).filter(Number.isInteger) : [];
  const added = store.basketAdd(req.user.uid, qids);
  res.json({ code: 0, message: 'ok', data: { added } });
});

r.delete('/items', (req, res) => {
  const body = req.body || {};
  const qids = Array.isArray(body.questionIds) ? body.questionIds.map(Number).filter(Number.isInteger) : [];
  res.json({ code: 0, message: 'ok', data: { removed: store.basketRemove(req.user.uid, qids) } });
});

module.exports = r;
