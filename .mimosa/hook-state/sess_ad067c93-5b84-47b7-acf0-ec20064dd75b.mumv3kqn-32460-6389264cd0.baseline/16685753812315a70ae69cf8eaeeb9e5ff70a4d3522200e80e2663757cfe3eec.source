// WP-4: 前台公开接口 —— 广告位读取 / 系统设置 / 公告（无素材时前台渲染占位框）
const express = require('express');
const store = require('../store');
const { optional } = require('../auth');

const r = express.Router();
r.use(optional);

// 广告位：/api/v1/ads?position=home_banner  → 空列表时前台渲染占位框
r.get('/ads', (req, res) => {
  const position = String(req.query.position || '');
  const whitelist = ['home_hero', 'home_banner', 'sidebar_teacher', 'sidebar_student', 'list_inline', 'detail_footer', 'login_promo'];
  if (!whitelist.includes(position)) return res.status(422).json({ code: 42200, message: 'position 非法', data: null });
  res.json({ code: 0, message: 'ok', data: { position, list: store.activeAds(position) } });
});

// 系统设置（站名/Logo/备案号…；logo_url 为空 → 前台渲染 Logo 占位）
r.get('/settings', (_req, res) => {
  res.json({ code: 0, message: 'ok', data: store.settingsAll() });
});

// 公告
r.get('/notices', (_req, res) => {
  res.json({ code: 0, message: 'ok', data: { list: store.activeNotices() } });
});

module.exports = r;
