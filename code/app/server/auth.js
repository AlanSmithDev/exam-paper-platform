// WP-1: JWT 认证与角色鉴权 + 审计助手
const jwt = require('jsonwebtoken');
const db = require('./db');

const SECRET = process.env.JWT_SECRET || 'zhijuan-dev-secret-change-in-prod';

function sign(user) {
  return jwt.sign({ uid: user.id, role: user.role, nickname: user.nickname }, SECRET, { expiresIn: '12h' });
}

// 解析 Bearer token（可选登录：匿名放行）
function optional(req, _res, next) {
  const h = req.headers.authorization || '';
  if (h.startsWith('Bearer ')) {
    try { req.user = jwt.verify(h.slice(7), SECRET); } catch (_) { /* 无效 token 视为匿名 */ }
  }
  next();
}

// 强制登录
function required(req, res, next) {
  if (!req.user) return res.status(401).json({ code: 40100, message: '未登录或凭证过期', data: null });
  const row = db.prepare('SELECT status FROM users WHERE id=?').get(req.user.uid);
  if (!row || row.status === 0) return res.status(403).json({ code: 40301, message: '账号不可用', data: null });
  next();
}

// 角色鉴权（任一满足即可）
function requireRole(...roles) {
  return (req, res, next) => {
    if (!req.user) return res.status(401).json({ code: 40100, message: '未登录', data: null });
    if (!roles.includes(req.user.role)) return res.status(403).json({ code: 40301, message: '无权限执行该操作', data: null });
    next();
  };
}

// 超管/运营/编辑写操作审计（只记录，不阻断业务）
function audit(req, action, detail) {
  try {
    db.prepare('INSERT INTO audit_log(user_id,actor,action,detail,ip) VALUES(?,?,?,?,?)')
      .run(req.user ? req.user.uid : null, req.user ? req.user.nickname : '-',
           action, typeof detail === 'string' ? detail : JSON.stringify(detail),
           (req.headers['x-forwarded-for'] || req.socket.remoteAddress || '-').toString().slice(0, 64));
  } catch (_) { /* 审计失败不阻断 */ }
}

module.exports = { sign, optional, required, requireRole, audit, SECRET };
