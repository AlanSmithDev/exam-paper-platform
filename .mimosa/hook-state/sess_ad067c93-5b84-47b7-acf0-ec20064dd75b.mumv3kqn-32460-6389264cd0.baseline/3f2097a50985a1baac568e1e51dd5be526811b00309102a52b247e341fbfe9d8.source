// WP-2: 题库 API —— 列表筛选 / 详情 / 相似题 / 元数据 / 纠错反馈
// 路由层只做参数归一化与响应组装；全部 SQL 在 server/store.js 与 server/trade.js（静态语句 + 参数绑定）。
const express = require('express');
const store = require('../store');
const trade = require('../trade');
const { optional, required } = require('../auth');

const r = express.Router();
r.use(optional);

// 学段/学科/章节树/题型枚举
r.get('/meta', (_req, res) => {
  const types = ['单选题', '多选题', '填空题', '解答题', '判断题'];
  const scenes = ['预习', '课堂', '作业', '单元测试', '阶段检测', '高考'];
  const categories = ['典型题', '压轴题', '同步题', '易错题', '常考题', '好题', '新定义'];
  res.json({ code: 0, message: 'ok', data: { stages: store.stages(), subjects: store.subjects(), catalog: store.catalog(), types, scenes, categories } });
});

// 知识点聚合（知识点选题视角，按题量降序 Top50）—— 必须定义在 GET /:id 之前
r.get('/kps', (_req, res) => {
  res.json({ code: 0, message: 'ok', data: { list: store.kpAggregation() } });
});

// 题目列表（多维筛选 + 分页 + 关键词）
r.get('/', (req, res) => {
  const pageNo = Math.max(1, parseInt(req.query.pageNo || '1', 10) || 1);
  const pageSize = Math.min(50, Math.max(1, parseInt(req.query.pageSize || '10', 10) || 10));
  const filter = {
    subjectId: req.query.subjectId, scene: req.query.scene, type: req.query.type,
    difficulty: req.query.difficulty, category: req.query.category,
    kp: req.query.kp, keyword: req.query.keyword
  };
  const total = store.qCount(filter);
  const list = store.qList(filter, pageSize, (pageNo - 1) * pageSize);
  res.json({
    code: 0, message: 'ok',
    data: {
      total, pageNo, pageSize,
      list: list.map(q => ({ ...q, options: q.options ? JSON.parse(q.options) : null }))
    }
  });
});

// 题目详情（五段式解析 + 相似题）
r.get('/:id', (req, res) => {
  const id = Number(req.params.id);
  if (!Number.isInteger(id)) return res.status(422).json({ code: 42200, message: '参数错误', data: null });
  const q = store.qById(id);
  if (!q) return res.status(404).json({ code: 40400, message: '题目不存在', data: null });
  res.json({
    code: 0, message: 'ok',
    data: {
      ...q,
      options: q.options ? JSON.parse(q.options) : null,
      analysis: q.analysis ? JSON.parse(q.analysis) : null,   // {brief(分析), solve(解答), comment(点评)}
      similar: store.qSimilar(id, q.kp_names, q.type)
    }
  });
});

// 提交纠错反馈（登录；P-5 采纳后 +5 点由后台处理触发）
r.post('/:id/feedback', required, (req, res) => {
  const b = req.body || {};
  try {
    const id = trade.submitFeedback(req.user.uid, req.params.id, b.type, b.content);
    res.json({ code: 0, message: 'ok', data: { id, tip: '感谢纠错，采纳后将获得 5 点奖励' } });
  } catch (e) {
    if (e.code === 40400) return res.status(404).json({ code: e.code, message: e.message, data: null });
    if (e.code === 42900) return res.status(429).json({ code: e.code, message: e.message, data: { reason: e.reason } });
    if (e.code === 42200) return res.status(422).json({ code: e.code, message: e.message, data: null });
    throw e;
  }
});

// 我的纠错记录（登录）
r.get('/feedback/mine', required, (req, res) => {
  res.json({ code: 0, message: 'ok', data: { list: trade.myFeedback(req.user.uid) } });
});

// 教师录题（P-5：录题落库为「审核中」，编辑/超管上架后作者 +20 点）
r.post('/contribute', required, (req, res) => {
  try {
    const id = trade.contributeQuestion(req.user.uid, req.body || {});
    res.json({ code: 0, message: 'ok', data: { id, status: '审核中', tip: '审核通过上架后奖励 20 点' } });
  } catch (e) {
    if (e.code === 42200) return res.status(422).json({ code: e.code, message: e.message, data: e.duplicateOf ? { duplicateOf: e.duplicateOf } : null });
    throw e;
  }
});

// 我的录题记录
r.get('/contributions/mine', required, (req, res) => {
  res.json({ code: 0, message: 'ok', data: { list: trade.myContributions(req.user.uid) } });
});

module.exports = r;
