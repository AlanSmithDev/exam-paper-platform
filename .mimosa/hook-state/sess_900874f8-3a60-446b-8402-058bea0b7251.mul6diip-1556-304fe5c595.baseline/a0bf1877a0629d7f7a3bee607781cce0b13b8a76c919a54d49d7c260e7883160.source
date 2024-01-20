// WP-0: 服务器入口（上线标准版：traceId 日志、登录限流、登录审计、优雅停机、健康检查增强）
// 安全说明：路由层不直接触碰数据库；用户输入经 store.js / trade.js（静态 SQL + 参数绑定）落库。
const express = require('express');
const cors = require('cors');
const path = require('path');
const crypto = require('crypto');
const bcrypt = require('bcryptjs');

const store = require('./store');
const trade = require('./trade');
const { clientIp } = require('./netutil');
const { sign, optional } = require('./auth');

const app = express();
app.use(cors());
app.use(express.json({ limit: '2mb' }));

// ---- traceId（docs/19 §7 结构化日志约定）：响应头 X-Trace-Id，错误日志携带 ----
app.use((req, res, next) => {
  req.traceId = String(req.headers['x-trace-id'] || crypto.randomUUID()).slice(0, 64);
  res.set('X-Trace-Id', req.traceId);
  next();
});

app.use(optional);

// ---- 登录限流（docs/20 SEC：同 IP 5 次/分，内存滑动窗口；生产接 Redis 计数器）----
const loginHits = new Map(); // ip -> [timestamps]
function loginLimiter(req, res, next) {
  const ip = clientIp(req);
  const now = Date.now();
  const wins = (loginHits.get(ip) || []).filter(t => now - t < 60_000);
  if (wins.length >= 5) {
    return res.status(429).json({ code: 42900, message: '登录尝试过于频繁，请 1 分钟后重试', data: null });
  }
  wins.push(now);
  loginHits.set(ip, wins);
  if (loginHits.size > 10_000) { // 防内存膨胀：清理过期项
    for (const [k, v] of loginHits) { if (v.every(t => now - t >= 60_000)) loginHits.delete(k); }
  }
  next();
}

// ---- 认证 ----
app.post('/api/v1/auth/register', (req, res) => {
  const b = req.body || {};
  const mobile = String(b.mobile || '');
  const password = String(b.password || '');
  const role = b.role === 'STUDENT' ? 'STUDENT' : 'TEACHER';
  if (!/^1\d{10}$/.test(mobile) || password.length < 8) {
    return res.status(422).json({ code: 42200, message: '手机号或密码格式错误（密码≥8位）', data: null });
  }
  if (store.userByMobile(mobile)) return res.status(422).json({ code: 42200, message: '手机号已注册', data: null });
  const id = store.userCreate(mobile, bcrypt.hashSync(password, 10), String(b.nickname || mobile.slice(-4)), role);
  // 新人礼包 + 邀请绑定（K-1/K-2，注册事务内一次性发放；无效邀请码不阻断注册）
  try { trade.registerGift(id, b.inviteCode ? String(b.inviteCode) : null); } catch (_) { /* 赠礼失败不阻断注册 */ }
  const user = store.userById(id);
  res.json({ code: 0, message: 'ok', data: { accessToken: sign(user), profile: { id: user.id, mobile: user.mobile, nickname: user.nickname, role: user.role } } });
});

app.post('/api/v1/auth/login', loginLimiter, (req, res) => {
  const b = req.body || {};
  const user = store.userByMobile(String(b.mobile || ''));
  if (!user || !bcrypt.compareSync(String(b.password || ''), user.password_hash)) {
    return res.status(401).json({ code: 40100, message: '手机号或密码错误', data: null });
  }
  if (user.status === 0) return res.status(403).json({ code: 40301, message: '账号已封禁', data: null });
  try { store.userTouchLogin(user.id, clientIp(req)); } catch (_) { /* 登录审计失败不阻断 */ }
  res.json({ code: 0, message: 'ok', data: { accessToken: sign(user), profile: { id: user.id, mobile: user.mobile, nickname: user.nickname, role: user.role } } });
});

// 超管/运营/编辑登录（独立入口）
app.post('/api/v1/auth/admin-login', loginLimiter, (req, res) => {
  const b = req.body || {};
  const user = store.userByMobile(String(b.mobile || ''));
  const adminRoles = ['SUPER_ADMIN', 'OP', 'EDITOR'];
  if (!user || !adminRoles.includes(user.role) || !bcrypt.compareSync(String(b.password || ''), user.password_hash)) {
    return res.status(401).json({ code: 40100, message: '账号或密码错误', data: null });
  }
  try { store.userTouchLogin(user.id, clientIp(req)); } catch (_) { /* 登录审计失败不阻断 */ }
  res.json({ code: 0, message: 'ok', data: { accessToken: sign(user), profile: { id: user.id, mobile: user.mobile, nickname: user.nickname, role: user.role } } });
});

// ---- 业务路由 ----
const PKG = require('../package.json');
app.get('/api/v1/health', (_req, res) => {
  let schemaVersion = 0;
  try { schemaVersion = require('./db').prepare('SELECT COALESCE(MAX(version),0) AS v FROM schema_migrations').get().v; } catch (_) { /* 迁移表未建 */ }
  res.json({
    code: 0, message: 'ok',
    data: { status: 'ok', db: !!store.userById(1), version: PKG.version, schemaVersion, uptimeSec: Math.round(process.uptime()) }
  });
});
app.use('/api/v1/questions', require('./routes/questions'));
app.use('/api/v1/basket', require('./routes/basket'));
app.use('/api/v1/papers', require('./routes/papers'));
app.use('/api/v1', require('./routes/ads'));       // /ads /settings /notices
app.use('/api/v1/admin', require('./routes/admin'));
app.use('/api/v1/member', require('./routes/member'));
app.use('/api/v1/coupons', require('./routes/coupons'));
app.use('/api/v1/payments', require('./routes/payments'));
app.use('/api/v1/trade', require('./routes/trade'));
app.use('/api/v1', require('./routes/practice'));   // /practices /wrong-questions /report/kp
app.use('/api/v1/ai', require('./routes/ai'));
app.use('/api/v1/messages', require('./routes/messages'));

// ---- 定时任务（docs/19 §5）：超时关单每分钟 + 券过期惰性/定时双保险 ----
setInterval(() => {
  try { trade.closeExpiredOrdersJob(); } catch (e) { console.error('[job] closeExpiredOrders', e.message); }
  try { trade.expireCouponsJob(); } catch (e) { console.error('[job] expireCoupons', e.message); }
}, 60 * 1000).unref();

// ---- 前台静态页（prototype）与超管后台（admin）----
const ROOT = path.resolve(__dirname, '..', '..', '..');   // server → app → code → 项目根
app.use('/prototype', express.static(path.join(ROOT, 'prototype')));
app.use('/admin', express.static(path.join(__dirname, '..', 'admin')));
app.get('/', (_req, res) => res.redirect('/prototype/index.html'));

// 404 与错误兜底（traceId 贯穿日志）
app.use((_req, res) => res.status(404).json({ code: 40400, message: '接口不存在', data: null }));
app.use((err, req, res, _next) => {
  console.error(`[error][trace=${req.traceId}]`, err.message);
  res.status(500).json({ code: 50010, message: '系统错误', data: null });
});

const PORT = Number(process.env.PORT) || 3000;
const server = app.listen(PORT, () => console.log(`智卷云 MVP 已启动: http://localhost:${PORT}  (超管后台: /admin, 前台: /prototype/index.html)`));

// ---- 优雅停机（docs/19 §4）：摘流量 → 等待在途请求 → 关库 → 退出 ----
function shutdown(signal) {
  console.log(`[shutdown] 收到 ${signal}，开始优雅停机`);
  server.close(() => {
    try { require('./db').close(); } catch (_) { /* 已关闭 */ }
    process.exit(0);
  });
  setTimeout(() => { console.error('[shutdown] 10s 超时，强制退出'); process.exit(1); }, 10_000).unref();
}
process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT', () => shutdown('SIGINT'));
