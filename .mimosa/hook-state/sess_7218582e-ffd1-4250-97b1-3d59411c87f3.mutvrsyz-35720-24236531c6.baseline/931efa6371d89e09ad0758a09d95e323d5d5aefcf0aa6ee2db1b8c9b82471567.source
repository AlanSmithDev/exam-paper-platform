// 练习/错题域路由（docs/15 §1：PR-1~PR-6）
const express = require('express');
const practice = require('../practice');
const { required } = require('../auth');

const r = express.Router();
r.use(required);

// POST /practices {mode: KP|REPEAT, subjectId?, kp?, type?, count} —— 生成练习（题目不含答案）
r.post('/practices', (req, res) => {
  try {
    res.json({ code: 0, message: 'ok', data: practice.createPractice(req.user.uid, req.body || {}) });
  } catch (e) {
    if (e.code === 42200) return res.status(422).json({ code: e.code, message: e.message, data: null });
    throw e;
  }
});

// GET /practices/:id —— 练习详情（重做场景）
r.get('/practices/:id', (req, res) => {
  const p = practice.practiceById(req.user.uid, req.params.id);
  if (!p) return res.status(404).json({ code: 40400, message: '练习不存在', data: null });
  res.json({ code: 0, message: 'ok', data: p });
});

// POST /practices/:id/submit {answers:[{questionId, answer, durationMs}]} —— 提交判分
r.post('/practices/:id/submit', (req, res) => {
  const b = req.body || {};
  if (!Array.isArray(b.answers)) return res.status(422).json({ code: 42200, message: 'answers 必须为数组', data: null });
  const d = practice.submit(req.user.uid, req.params.id, b.answers);
  res.json({ code: 0, message: 'ok', data: d });
});

// GET /wrong-questions —— 错题本（未解决）
r.get('/wrong-questions', (req, res) => {
  res.json({ code: 0, message: 'ok', data: { list: practice.wrongQuestions(req.user.uid, req.query.subjectId) } });
});

// POST /wrong-questions/:questionId/resolve —— 标记已掌握
r.post('/wrong-questions/:questionId/resolve', (req, res) => {
  try {
    res.json({ code: 0, message: 'ok', data: practice.resolveWrong(req.user.uid, req.params.questionId) });
  } catch (e) {
    if (e.code === 40400) return res.status(404).json({ code: e.code, message: e.message, data: null });
    throw e;
  }
});

// GET /report/kp —— 知识点正确率聚合（学情热力图数据源）
r.get('/report/kp', (req, res) => {
  res.json({ code: 0, message: 'ok', data: { list: practice.kpReport(req.user.uid) } });
});

module.exports = r;
