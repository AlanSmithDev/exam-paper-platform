// 消息中心服务（docs/16 v1.1 notify_message）：站内信触达——审核结果/积分奖励/订单事件。
// 安全说明：全部静态 SQL + 参数绑定；写消息失败不得阻断主业务（调用方用 try/catch 包裹或本模块吞掉）。
const db = require('./db');

const TYPES = ['SYSTEM', 'ORDER', 'REVIEW', 'REWARD'];

function notify(userId, type, title, content, refType, refId) {
  if (!TYPES.includes(type)) type = 'SYSTEM';
  db.prepare('INSERT INTO notify_message(user_id, type, title, content, ref_type, ref_id) VALUES(?,?,?,?,?,?)')
    .run(Number(userId), type, String(title).slice(0, 64), content ? String(content).slice(0, 500) : null,
         refType ? String(refType) : null, refId !== undefined && refId !== null ? String(refId) : null);
}
// 业务侧安全封装：通知失败不影响主流程
function notifySafe(userId, type, title, content, refType, refId) {
  try { notify(userId, type, title, content, refType, refId); } catch (_) { /* 站内信失败仅记日志 */ }
}

function myMessages(uid, pageNo, pageSize) {
  const total = db.prepare('SELECT COUNT(*) AS c FROM notify_message WHERE user_id=?').get(Number(uid)).c;
  const unread = db.prepare('SELECT COUNT(*) AS c FROM notify_message WHERE user_id=? AND read=0').get(Number(uid)).c;
  const rows = Array.from(db.prepare('SELECT id, type, title, content, ref_type, ref_id, read, created_at FROM notify_message WHERE user_id=? ORDER BY id DESC LIMIT ? OFFSET ?')
    .iterate(Number(uid), Number(pageSize), (Number(pageNo) - 1) * Number(pageSize)));
  return { total, unread, rows };
}

function markRead(uid, id) {
  const r = db.prepare('UPDATE notify_message SET read=1 WHERE id=? AND user_id=?').run(Number(id), Number(uid));
  if (!r.changes) { const e = new Error('消息不存在'); e.code = 40400; throw e; }
  return { id: Number(id), read: 1 };
}

function markAllRead(uid) {
  const r = db.prepare('UPDATE notify_message SET read=1 WHERE user_id=? AND read=0').run(Number(uid));
  return { updated: r.changes };
}

module.exports = { notify, notifySafe, myMessages, markRead, markAllRead, TYPES };
