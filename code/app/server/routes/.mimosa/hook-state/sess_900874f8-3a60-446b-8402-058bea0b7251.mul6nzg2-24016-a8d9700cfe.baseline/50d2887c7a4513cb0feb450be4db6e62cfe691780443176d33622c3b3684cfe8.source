// AI 域路由（docs/15 §2：AI-1~AI-5，MVP MOCK/OPENAI_COMPAT）
const express = require('express');
const ai = require('../ai');
const { required } = require('../auth');

const r = express.Router();
r.use(required);

// POST /ai/variants {questionId} 或 {stem} —— AI 变式出题（草稿，aigc=1 需人工审核）
r.post('/variants', async (req, res) => {
  try {
    res.json({ code: 0, message: 'ok', data: await ai.variants(req.user.uid, req.body || {}) });
  } catch (e) {
    if (e.code === 42900) return res.status(429).json({ code: e.code, message: e.message, data: { reason: e.reason, quota: e.quota } });
    if (e.code === 40400) return res.status(404).json({ code: e.code, message: e.message, data: null });
    if (e.code === 42200) return res.status(422).json({ code: e.code, message: e.message, data: null });
    throw e;
  }
});

// POST /ai/explain {questionId} 或 {stem, answer} —— AI 分步讲题
r.post('/explain', async (req, res) => {
  try {
    res.json({ code: 0, message: 'ok', data: await ai.explain(req.user.uid, req.body || {}) });
  } catch (e) {
    if (e.code === 42900) return res.status(429).json({ code: e.code, message: e.message, data: { reason: e.reason, quota: e.quota } });
    if (e.code === 40400) return res.status(404).json({ code: e.code, message: e.message, data: null });
    if (e.code === 42200) return res.status(422).json({ code: e.code, message: e.message, data: null });
    throw e;
  }
});

// GET /ai/quota —— 我的 AI 配额
r.get('/quota', (req, res) => {
  res.json({ code: 0, message: 'ok', data: { ...ai.quotaOf(req.user.uid), logs: ai.myLogs(req.user.uid, 10) } });
});

module.exports = r;
