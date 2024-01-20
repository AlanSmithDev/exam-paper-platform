// 商业化服务层（WP-Trade）：会员/点数/优惠券/订单支付/下载计费/营销。
// 安全说明：全部 SQL 静态语句 + 参数绑定；金额与点数一律整数（分/点）；
// 状态跃迁与限量扣减均使用"条件 UPDATE + 影响行数判断"保证幂等与防超发；
// 履约/关单/退款使用 better-sqlite3 事务（同步单写，语义等价于 MySQL 行锁事务）。
// 需求编号见 docs/14（v1.1）；表结构见 docs/16；接口契约见 docs/17。
const crypto = require('crypto');
const db = require('./db');

const ORDER_EXPIRE_MINUTES = 30;
const POINTS_PER_YUAN = 100;

// ---------- 基础工具 ----------
function pad(n) { return String(n).padStart(2, '0'); }
function nowStr(d = new Date()) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}
function dateStr(d = new Date()) { return nowStr(d).slice(0, 10); }
function addDays(base, days) {
  const d = typeof base === 'string' ? new Date(base.replace(' ', 'T')) : new Date(base);
  d.setDate(d.getDate() + Number(days));
  return d;
}
function addMinutes(base, minutes) {
  const d = typeof base === 'string' ? new Date(base.replace(' ', 'T')) : new Date(base);
  d.setMinutes(d.getMinutes() + Number(minutes));
  return d;
}
function genOrderNo() {
  const d = new Date();
  const rand = crypto.randomInt(100000, 1000000); // 订单号随机段用 crypto 随机（不可预测、防撞号）
  return `ZJ${String(d.getFullYear()).slice(2)}${pad(d.getMonth() + 1)}${pad(d.getDate())}${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}${rand}`;
}
function genInviteCode() {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  let s = '';
  for (let i = 0; i < 6; i++) s += chars[crypto.randomInt(0, chars.length)]; // 邀请码用 crypto 随机，防枚举
  return 'ZJ' + s;
}
function parseSnapshot(c) {
  try { return JSON.parse(c.snapshot); } catch (_) { return {}; }
}
function normStr(v) { return v === undefined || v === null || v === '' ? null : String(v); }

// ---------- 会员与套餐（REQ-M）----------
// 序列化为 docs/17 §1.1 契约（camelCase，benefits 解析为数组）
function serializePlan(p) {
  let benefits = [];
  try { benefits = p.benefits ? JSON.parse(p.benefits) : []; } catch (_) { benefits = []; }
  return { id: p.id, code: p.code, name: p.name, priceCents: p.price_cents, durationDays: p.duration_days,
    subjectId: p.subject_id, benefits, purchasable: !!p.purchasable, sort: p.sort };
}
function serializePackage(k) {
  return { id: k.id, points: k.points, bonusPoints: k.bonus_points, priceCents: k.price_cents, label: k.label, sort: k.sort };
}
function plans() {
  return {
    plans: Array.from(db.prepare('SELECT * FROM member_plan WHERE status=1 ORDER BY sort').iterate()).map(serializePlan),
    pointPackages: Array.from(db.prepare('SELECT * FROM points_package WHERE status=1 ORDER BY sort').iterate()).map(serializePackage)
  };
}

// 权益判定唯一入口（M-3 entitlement）：会员态 + 点数 + 今日免费额度
function entitlement(uid) {
  const u = db.prepare('SELECT id, member_until, plan_code, certify FROM users WHERE id=?').get(Number(uid));
  if (!u) return null;
  const today = dateStr();
  const memberActive = !!u.member_until && u.member_until >= today;
  const acct = db.prepare('SELECT balance FROM point_account WHERE user_id=?').get(Number(uid));
  // 免费额度：基础 3 次/日（settings 可配），教师认证 +2（与前台权益宣传一致）
  const baseFree = Number(db.prepare('SELECT value FROM settings WHERE key=?').get('free_downloads_per_day')?.value || 3);
  const freePerDay = baseFree + (u.certify ? 2 : 0);
  const freeUsed = db.prepare("SELECT COUNT(*) AS c FROM download_record WHERE user_id=? AND charge_mode='FREE_QUOTA' AND created_at >= ?")
    .get(Number(uid), today + ' 00:00:00').c;
  const unusedCoupons = db.prepare("SELECT COUNT(*) AS c FROM user_coupon WHERE user_id=? AND status='UNUSED'").get(Number(uid)).c;
  return {
    member: { active: memberActive, planCode: memberActive ? u.plan_code : null, expireAt: u.member_until || null,
      remainDays: memberActive ? Math.round((new Date(u.member_until + 'T23:59:59') - new Date()) / 86400000) : 0 },
    points: { balance: acct ? acct.balance : 0, freeDownloadsPerDay: freePerDay, freeUsedToday: freeUsed, certifyBonus: u.certify ? 2 : 0 },
    coupons: { unused: unusedCoupons }
  };
}

// ---------- 点数账户（REQ-P）----------
// 增减点数（调用方需在事务内）：负 delta 用条件 UPDATE 防透支，changes=0 抛错回滚
function creditPoint(uid, delta, bizType, note, orderNo) {
  const uidN = Number(uid);
  db.prepare('INSERT INTO point_account(user_id, balance, version) VALUES(?,0,0) ON CONFLICT(user_id) DO NOTHING').run(uidN);
  let balanceAfter;
  if (delta >= 0) {
    db.prepare('UPDATE point_account SET balance=balance+?, version=version+1 WHERE user_id=?').run(delta, uidN);
  } else {
    const r = db.prepare('UPDATE point_account SET balance=balance+?, version=version+1 WHERE user_id=? AND balance>=?')
      .run(delta, uidN, -delta);
    if (r.changes === 0) { const e = new Error('点数余额不足'); e.code = 42900; e.needPoints = -delta; throw e; }
  }
  balanceAfter = db.prepare('SELECT balance FROM point_account WHERE user_id=?').get(uidN).balance;
  db.prepare('INSERT INTO point_log(user_id, order_no, biz_type, delta, balance_after, note, created_at) VALUES(?,?,?,?,?,?,?)')
    .run(uidN, orderNo || null, bizType, delta, balanceAfter, note || null, nowStr());
  if (bizType === 'RECHARGE') {
    db.prepare('UPDATE point_account SET total_recharge = total_recharge + ? WHERE user_id=?').run(delta, uidN);
  }
  return balanceAfter;
}

function pointsLog(uid, pageNo, pageSize) {
  const total = db.prepare('SELECT COUNT(*) AS c FROM point_log WHERE user_id=?').get(Number(uid)).c;
  const rows = Array.from(db.prepare('SELECT id, biz_type, delta, balance_after, note, order_no, created_at FROM point_log WHERE user_id=? ORDER BY id DESC LIMIT ? OFFSET ?')
    .iterate(Number(uid), Number(pageSize), (Number(pageNo) - 1) * Number(pageSize)));
  const balance = db.prepare('SELECT balance FROM point_account WHERE user_id=?').get(Number(uid))?.balance || 0;
  return { balance, total, rows };
}

// ---------- 优惠券（REQ-C）----------
function couponVisible(t, now) {
  if (t.status !== 1) return false;
  if (t.claim_start && t.claim_start > now) return false;
  if (t.claim_end && t.claim_end < now) return false;
  return true;
}
function claimStateOf(t, claimedByUser, now) {
  if (!couponVisible(t, now)) return t.claim_start && t.claim_start > now ? 'NOT_STARTED' : 'ENDED';
  if (t.total > 0 && t.claimed >= t.total) return 'SOLD_OUT';
  if (t.per_limit > 0 && claimedByUser >= t.per_limit) return 'LIMIT_REACHED';
  return 'CLAIMABLE';
}
function couponsAvailable(uid) {
  const now = nowStr();
  const rows = Array.from(db.prepare('SELECT * FROM coupon_template WHERE status=1 ORDER BY id DESC').iterate());
  return rows.map(t => {
    const mine = db.prepare('SELECT COUNT(*) AS c FROM user_coupon WHERE template_id=? AND user_id=?').get(t.id, Number(uid)).c;
    return {
      id: t.id, code: t.code, name: t.name, type: t.type, amountCents: t.amount_cents, discountRate: t.discount_rate,
      maxDiscountCents: t.max_discount_cents, thresholdCents: t.threshold_cents, total: t.total, claimed: t.claimed,
      perLimit: t.per_limit, validDays: t.valid_days, fixedEnd: t.fixed_end, scope: t.scope,
      claimState: claimStateOf(t, mine, now)
    };
  });
}
// 领取（C-3'）：模板条件 UPDATE 防超发 + 每人全状态限领；有效期按模式在发券瞬间计算（C-6'）
const claimCouponTx = db.transaction((uid, templateId) => {
  const t = db.prepare('SELECT * FROM coupon_template WHERE id=?').get(Number(templateId));
  const now = nowStr();
  if (!t || !couponVisible(t, now)) { const e = new Error('券不在可领取时间内'); e.code = 42200; throw e; }
  const mine = db.prepare('SELECT COUNT(*) AS c FROM user_coupon WHERE template_id=? AND user_id=?').get(t.id, Number(uid)).c;
  if (t.per_limit > 0 && mine >= t.per_limit) { const e = new Error('已达每人限领上限'); e.code = 42900; e.reason = 'PER_LIMIT'; throw e; }
  const inc = db.prepare('UPDATE coupon_template SET claimed=claimed+1 WHERE id=? AND status=1 AND (total=0 OR claimed<total) AND (claim_start IS NULL OR claim_start<=?) AND (claim_end IS NULL OR claim_end>=?)')
    .run(t.id, now, now);
  if (inc.changes === 0) { const e = new Error('券已被领完'); e.code = 42900; e.reason = 'COUPON_SOLD_OUT'; throw e; }
  const expireAt = t.valid_days ? nowStr(addDays(now, t.valid_days)) : (t.fixed_end || nowStr(addDays(now, 30)));
  const snap = JSON.stringify({ type: t.type, name: t.name, amountCents: t.amount_cents, discountRate: t.discount_rate,
    maxDiscountCents: t.max_discount_cents, thresholdCents: t.threshold_cents, scope: t.scope });
  const info = db.prepare("INSERT INTO user_coupon(template_id, user_id, status, snapshot, source, claimed_at, expire_at) VALUES(?,?, 'UNUSED', ?, 'CLAIM', ?, ?)")
    .run(t.id, Number(uid), snap, now, expireAt);
  return { couponId: info.lastInsertRowid, expireAt };
});
function claimCoupon(uid, templateId) { return claimCouponTx(uid, templateId); }

function myCoupons(uid, status) {
  const valid = ['UNUSED', 'FROZEN', 'USED', 'EXPIRED', 'CLOSED'];
  const hasFilter = status && status !== 'ALL' && valid.includes(status);
  const sql = `SELECT * FROM user_coupon WHERE user_id=? ${hasFilter ? 'AND status=?' : ''} ORDER BY id DESC LIMIT 100`;
  const rows = hasFilter
    ? Array.from(db.prepare(sql).iterate(Number(uid), status))
    : Array.from(db.prepare(sql).iterate(Number(uid)));
  return rows.map(c => {
    const s = parseSnapshot(c);
    return { id: c.id, templateCode: null, name: s.name, type: s.type, amountCents: s.amountCents, discountRate: s.discountRate,
      maxDiscountCents: s.maxDiscountCents, thresholdCents: s.thresholdCents, scope: s.scope,
      status: c.status, expireAt: c.expire_at, usedOrderNo: c.used_order_no, claimedAt: c.claimed_at, usedAt: c.used_at };
  });
}

// 惰性过期（C-7'）：查询/判价前调用，把该用户已过期未置状态的券补置 EXPIRED
function lazyExpire(uid) {
  db.prepare("UPDATE user_coupon SET status='EXPIRED' WHERE status IN ('UNUSED','FROZEN') AND expire_at < ?").run(nowStr());
  if (uid) db.prepare("UPDATE user_coupon SET status='EXPIRED' WHERE user_id=? AND status IN ('UNUSED','FROZEN') AND expire_at < ?").run(Number(uid), nowStr());
}
// 定时任务（每日）：全量过期扫描（双保险）
function expireCouponsJob() { lazyExpire(null); }

// 券对订单的抵扣计算（门槛按订单原价、折扣封顶、不为负）
function couponDiscount(coupon, originCents) {
  const s = parseSnapshot(coupon);
  if (s.type === 'FULL_REDUCTION') {
    if (originCents < (s.thresholdCents || 0)) { const e = new Error('未达到优惠券使用门槛'); e.code = 42200; e.needCents = s.thresholdCents; throw e; }
    return Math.min(s.amountCents, originCents);
  }
  if (s.type === 'DISCOUNT') {
    const raw = Math.floor(originCents * (100 - (s.discountRate || 100)) / 100);
    const capped = s.maxDiscountCents > 0 ? Math.min(raw, s.maxDiscountCents) : raw;
    return Math.min(capped, originCents);
  }
  if (s.type === 'POINTS') return 0; // 点数券在履约时兑换点数，不抵扣金额
  return 0;
}

// ---------- 订单与支付（REQ-O）----------
function orderDetail(orderNo, uid) {
  const o = db.prepare('SELECT * FROM trade_order WHERE order_no=?').get(String(orderNo));
  if (!o) return null;
  if (uid && o.user_id !== Number(uid)) return null;
  const coupon = o.coupon_id ? db.prepare('SELECT * FROM user_coupon WHERE id=?').get(o.coupon_id) : null;
  return { order: serializeOrder(o), coupon: coupon ? { id: coupon.id, name: parseSnapshot(coupon).name, status: coupon.status } : null,
    fulfil: o.fulfil_json ? JSON.parse(o.fulfil_json) : null };
}
function serializeOrder(o) {
  return { orderNo: o.order_no, userId: o.user_id, skuType: o.sku_type, skuRef: o.sku_ref, title: o.title,
    originCents: o.origin_cents, discountCents: o.discount_cents, payCents: o.pay_cents, couponId: o.coupon_id,
    status: o.status, payStatus: o.pay_status, refundStatus: o.refund_status, payChannel: o.pay_channel,
    idempotencyKey: o.idempotency_key, fulfil: o.fulfil_json ? JSON.parse(o.fulfil_json) : null,
    paidAt: o.paid_at, closedAt: o.closed_at, expireAt: o.expire_at, createdAt: o.created_at };
}

// 下单（O-2 幂等 + C-4' 券冻结），价格计算与建单同事务
const createOrderTx = db.transaction((uid, b, idemKey) => {
  // 幂等：命中返回原单
  if (idemKey) {
    const exists = db.prepare('SELECT * FROM trade_order WHERE user_id=? AND idempotency_key=?').get(Number(uid), idemKey);
    if (exists) return { order: exists, replay: true };
  }
  const now = nowStr();
  let origin = 0, title = '', skuRef = '';
  const plan = b.planCode ? db.prepare('SELECT * FROM member_plan WHERE code=? AND status=1').get(String(b.planCode)) : null;
  const pkg = b.packageId ? db.prepare('SELECT * FROM points_package WHERE id=? AND status=1').get(Number(b.packageId)) : null;
  const paper = b.paperId ? db.prepare('SELECT * FROM papers WHERE id=?').get(Number(b.paperId)) : null;

  if (b.skuType === 'MEMBER') {
    if (!plan || !plan.purchasable) { const e = new Error('会员档位不存在或不可售'); e.code = 42200; throw e; }
    origin = plan.price_cents; title = `会员·${plan.name}·${plan.duration_days}天`; skuRef = plan.code;
  } else if (b.skuType === 'POINTS') {
    if (!pkg) { const e = new Error('充值套餐不存在'); e.code = 42200; throw e; }
    origin = pkg.price_cents; title = `点数充值·${pkg.points}点${pkg.bonus_points ? `(含赠${pkg.bonus_points})` : ''}`; skuRef = String(pkg.id);
  } else if (b.skuType === 'PAPER') {
    if (!paper || paper.user_id !== Number(uid)) { const e = new Error('试卷不存在'); e.code = 40400; throw e; }
    const qCount = db.prepare('SELECT COUNT(*) AS c FROM paper_questions WHERE paper_id=?').get(paper.id).c;
    origin = paperNeedPoints(qCount); title = `单卷下载·${paper.title}·${qCount}题`; skuRef = String(paper.id);
  } else {
    const e = new Error('skuType 非法'); e.code = 42200; throw e;
  }

  // 券（可选）：本人 UNUSED + 范围匹配 + 门槛校验 → 冻结并计入折扣
  let coupon = null, discount = 0;
  if (b.couponId) {
    lazyExpire(uid);
    coupon = db.prepare("SELECT * FROM user_coupon WHERE id=? AND user_id=?").get(Number(b.couponId), Number(uid));
    const s = coupon ? parseSnapshot(coupon) : null;
    if (!coupon || coupon.status !== 'UNUSED' || (s.scope !== 'ALL' && s.scope !== b.skuType)) {
      const e = new Error('优惠券不可用'); e.code = 42200; e.reason = 'COUPON_UNAVAILABLE'; throw e;
    }
    discount = couponDiscount(coupon, origin); // 门槛不足在内部抛 42200
  }
  const payCents = Math.max(0, origin - discount);
  const expireAt = nowStr(addMinutes(now, ORDER_EXPIRE_MINUTES));
  const info = db.prepare(`INSERT INTO trade_order(order_no,user_id,sku_type,sku_ref,title,origin_cents,discount_cents,pay_cents,coupon_id,status,pay_status,refund_status,pay_channel,idempotency_key,expire_at,created_at)
    VALUES(?,?,?,?,?,?,?,?,?,'CREATED','UNPAID','NONE',?,?,?,?)`)
    .run(genOrderNo(), Number(uid), b.skuType, skuRef, title, origin, discount, payCents, coupon ? coupon.id : null,
         String(b.channel || 'MOCK'), idemKey || null, expireAt, now);
  if (coupon) {
    const fr = db.prepare("UPDATE user_coupon SET status='FROZEN', used_order_no=? WHERE id=? AND status='UNUSED'").run(String(info.lastInsertRowid), coupon.id);
    if (fr.changes === 0) { const e = new Error('优惠券状态冲突，请重试'); e.code = 40001; throw e; }
  }
  const order = db.prepare('SELECT * FROM trade_order WHERE id=?').get(info.lastInsertRowid);
  return { order, replay: false };
});

function createOrder(uid, body, idemKey) { return createOrderTx(uid, body, idemKey); }

// 关单（O-7）：仅 CREATED 可关；关单事务内返还 FROZEN 券（C-5'）
const closeOrderTx = db.transaction((orderNo, uid) => {
  const o = db.prepare('SELECT * FROM trade_order WHERE order_no=?').get(String(orderNo));
  if (!o || (uid && o.user_id !== Number(uid))) { const e = new Error('订单不存在'); e.code = 40400; throw e; }
  if (o.status !== 'CREATED') { const e = new Error('订单状态不允许关单'); e.code = 40001; throw e; }
  db.prepare("UPDATE trade_order SET status='CLOSED', closed_at=?, pay_status='UNPAID' WHERE order_no=? AND status='CREATED'").run(nowStr(), o.order_no);
  if (o.coupon_id) db.prepare("UPDATE user_coupon SET status='UNUSED', used_order_no=NULL WHERE id=? AND status='FROZEN'").run(o.coupon_id);
  return db.prepare('SELECT * FROM trade_order WHERE order_no=?').get(o.order_no);
});
function closeOrder(orderNo, uid) { return closeOrderTx(orderNo, uid); }

function closeExpiredOrdersJob() {
  const expired = Array.from(db.prepare("SELECT order_no FROM trade_order WHERE status='CREATED' AND expire_at < ?").iterate(nowStr()));
  let n = 0;
  for (const r of expired) { try { closeOrderTx(r.order_no, null); n++; } catch (_) { /* 单笔失败不阻塞其余 */ } }
  return n;
}

// 支付回调履约（O-4' 幂等三板斧 / O-5' 同事务履约）
const fulfillTx = db.transaction((order, callbackId) => {
  const now = nowStr();
  // 履约：订单置 PAID（条件 UPDATE，防重复履约）
  const up = db.prepare("UPDATE trade_order SET status='PAID', pay_status='PAID', paid_at=? WHERE order_no=? AND status='CREATED'").run(now, order.order_no);
  if (up.changes === 0) return orderDetail(order.order_no, null); // 并发下已被履约 → 返回既有结果
  const fulfil = {};
  if (order.coupon_id) {
    const couponRow = db.prepare('SELECT * FROM user_coupon WHERE id=?').get(order.coupon_id);
    const u = db.prepare("UPDATE user_coupon SET status='USED', used_at=? WHERE id=? AND status='FROZEN' AND used_order_no=?")
      .run(now, order.coupon_id, String(order.id));
    if (u.changes === 0) { const e = new Error('优惠券核销失败（状态冲突）'); e.code = 40001; throw e; }
    // 点数券履约：核销同时兑换点数入账（C-1 POINTS 类型，docs/16 §4）
    const snap = couponRow ? parseSnapshot(couponRow) : {};
    if (snap.type === 'POINTS' && snap.amountCents > 0) {
      creditPoint(order.user_id, snap.amountCents, 'COUPON_EXCHANGE', `点数券兑换 ${snap.amountCents} 点`, order.order_no);
      fulfil.pointsFromCoupon = snap.amountCents;
    }
  }
  if (order.sku_type === 'MEMBER') {
    const plan = db.prepare('SELECT * FROM member_plan WHERE code=?').get(order.sku_ref);
    const u = db.prepare('SELECT member_until FROM users WHERE id=?').get(order.user_id);
    const base = u.member_until && u.member_until > dateStr() ? new Date(u.member_until.replace(' ', 'T')) : new Date();
    const newUntil = dateStr(addDays(base, plan ? plan.duration_days : 30));
    db.prepare('UPDATE users SET member_until=?, plan_code=? WHERE id=?').run(newUntil, order.sku_ref, order.user_id);
    fulfil.daysAdded = plan ? plan.duration_days : 30;
    fulfil.memberUntil = newUntil;
  } else if (order.sku_type === 'POINTS') {
    const pkg = db.prepare('SELECT * FROM points_package WHERE id=?').get(Number(order.sku_ref));
    if (pkg) {
      creditPoint(order.user_id, pkg.points - pkg.bonus_points, 'RECHARGE', `充值${pkg.points - pkg.bonus_points}点`, order.order_no);
      if (pkg.bonus_points > 0) creditPoint(order.user_id, pkg.bonus_points, 'RECHARGE_BONUS', `充值赠送${pkg.bonus_points}点`, order.order_no);
      fulfil.pointsCredited = pkg.points; fulfil.bonusPoints = pkg.bonus_points;
    }
  } else if (order.sku_type === 'PAPER') {
    const paper = db.prepare('SELECT * FROM papers WHERE id=?').get(Number(order.sku_ref));
    if (paper) {
      const ids = Array.from(db.prepare('SELECT question_id AS id FROM paper_questions WHERE paper_id=? ORDER BY sort').iterate(paper.id)).map(r => r.id);
      recordDownload(order.user_id, paper.id, paperHashOf(ids), ids.length, 'POINTS', 0, order.order_no);
      fulfil.paperId = paper.id;
    }
  }
  db.prepare('UPDATE trade_order SET fulfil_json=? WHERE order_no=?').run(JSON.stringify(fulfil), order.order_no);
  require('./message').notifySafe(order.user_id, 'ORDER', '支付成功',
    `订单 ${order.order_no}（${order.title}）支付成功${order.pay_cents ? `，实付 ${(order.pay_cents / 100).toFixed(2)} 元` : ''}。`, 'order', order.order_no);
  return orderDetail(order.order_no, null);
});

const payNotifyTx = db.transaction((orderNo, callbackId, amountCents, channel, clientIp) => {
  const now = nowStr();
  const o = db.prepare('SELECT * FROM trade_order WHERE order_no=?').get(String(orderNo));
  if (!o) { const e = new Error('订单不存在'); e.code = 40400; throw e; }
  // 已支付前置判断（幂等三板斧之二）：重复回调/重复支付一律返回首次结果
  if (o.status === 'PAID') { const e = new Error('重复回调已忽略'); e.code = 42901; e.duplicated = true; throw e; }
  if (o.status !== 'CREATED') { const e = new Error('订单当前状态不可支付'); e.code = 40001; throw e; }
  // 流水唯一索引防重放（三板斧之一）
  const dup = db.prepare('SELECT COUNT(*) AS c FROM payment_log WHERE channel=? AND callback_id=?').get(String(channel), String(callbackId)).c;
  if (dup > 0) { const e = new Error('重复回调已忽略'); e.code = 42901; e.duplicated = true; throw e; }
  // 三板斧之三：金额以服务端订单为准
  if (Number(amountCents) !== o.pay_cents) { const e = new Error('回调金额与订单不一致'); e.code = 42200; throw e; }
  db.prepare('INSERT INTO payment_log(order_no, channel, callback_id, channel_trade_no, client_ip, amount_cents, result, payload, created_at) VALUES(?,?,?,?,?,?,?,?,?)')
    .run(o.order_no, String(channel), String(callbackId), String(callbackId), clientIp || null,
         Number(amountCents), 'ACCEPTED', null, now);
  return fulfillTx(o, callbackId);
});

function payNotify(orderNo, callbackId, amountCents, channel, clientIp) {
  try {
    return { duplicated: false, detail: payNotifyTx(orderNo, callbackId, amountCents, channel, clientIp) };
  } catch (e) {
    if (e.code === 42901) return { duplicated: true, detail: orderDetail(orderNo, null) }; // 幂等：返回首次结果
    throw e;
  }
}

// 退款（O-8 / docs/16 §8 固定回滚顺序）
const refundTx = db.transaction((orderNo) => {
  const o = db.prepare("SELECT * FROM trade_order WHERE order_no=? AND status='PAID'").get(String(orderNo));
  if (!o) { const e = new Error('订单不存在或不可退款'); e.code = 40400; throw e; }
  // 流水去重：本单已存在 REFUND 流水则拒绝（防重复返还）
  const refunded = db.prepare("SELECT COUNT(*) AS c FROM point_log WHERE order_no=? AND biz_type='REFUND'").get(o.order_no).c;
  if (refunded > 0 && o.sku_type === 'POINTS') { const e = new Error('该订单已退款'); e.code = 40001; throw e; }
  db.prepare("UPDATE trade_order SET status='REFUNDED', refund_status='REFUNDED' WHERE order_no=? AND status='PAID'").run(o.order_no);
  const returned = { couponId: null, points: 0, days: 0 };
  const fulfil = o.fulfil_json ? JSON.parse(o.fulfil_json) : {};
  // ② 券返还（未过期才返还）
  if (o.coupon_id) {
    const back = db.prepare("UPDATE user_coupon SET status='UNUSED', used_order_no=NULL, used_at=NULL WHERE id=? AND status='USED' AND expire_at > ?")
      .run(o.coupon_id, nowStr());
    if (back.changes > 0) returned.couponId = o.coupon_id;
  }
  // ③ 点数回收（余额不足钳位到 0，差额记 note）
  if (o.sku_type === 'POINTS' && fulfil.pointsCredited) {
    const acct = db.prepare('SELECT balance FROM point_account WHERE user_id=?').get(o.user_id);
    const recover = Math.min(fulfil.pointsCredited, acct ? acct.balance : 0);
    if (recover > 0) creditPoint(o.user_id, -recover, 'REFUND', `订单退款回收点数`, o.order_no);
    if (recover < fulfil.pointsCredited) {
      creditPoint(o.user_id, 0, 'REFUND', `余额不足以全额回收：应收${fulfil.pointsCredited}实收${recover}`, o.order_no);
    }
    returned.points = recover;
  }
  // ④ 会员回收：直接扣减 daysAdded（叠加续费后退款仍正确），下限为今天（不追讨已享受天数）
  if (o.sku_type === 'MEMBER' && fulfil.daysAdded) {
    const u = db.prepare('SELECT member_until FROM users WHERE id=?').get(o.user_id);
    if (u && u.member_until) {
      const today = dateStr();
      const rolledDate = dateStr(addDays(u.member_until, -fulfil.daysAdded));
      const rolled = rolledDate > today ? rolledDate : null;
      db.prepare('UPDATE users SET member_until=?, plan_code=? WHERE id=?').run(rolled, rolled ? u.plan_code : null, o.user_id);
      returned.days = fulfil.daysAdded;
    }
  }
  // ⑤ 审计在路由层记录
  require('./message').notifySafe(o.user_id, 'ORDER', '订单已退款',
    `订单 ${o.order_no}（${o.title}）已完成退款。`, 'order', o.order_no);
  return { orderNo: o.order_no, status: 'REFUNDED', returned };
});

function refund(orderNo) { return refundTx(orderNo); }

// ---------- 下载计费（REQ-D）----------
function paperHashOf(questionIds) {
  return crypto.createHash('sha256').update(questionIds.join(',')).digest('hex');
}
// 试卷题目 ID（有序）——供判价与导出取 hash
function paperQuestionIds(paperId) {
  return Array.from(db.prepare('SELECT question_id AS id FROM paper_questions WHERE paper_id=? ORDER BY sort').iterate(Number(paperId))).map(r => r.id);
}
// 我的订单分页
function myOrders(uid, pageNo, pageSize) {
  const total = db.prepare('SELECT COUNT(*) AS c FROM trade_order WHERE user_id=?').get(Number(uid)).c;
  const rows = Array.from(db.prepare('SELECT * FROM trade_order WHERE user_id=? ORDER BY id DESC LIMIT ? OFFSET ?')
    .iterate(Number(uid), Number(pageSize), (Number(pageNo) - 1) * Number(pageSize))).map(serializeOrder);
  return { total, rows };
}
function paperNeedPoints(count) {
  if (count <= 10) return 100;
  if (count <= 20) return 200;
  if (count <= 50) return 400;
  if (count <= 100) return 600;
  return 600 + Math.ceil((count - 100) / 50) * 100;
}
function recordDownload(uid, paperId, hash, qCount, mode, pointsCharged, orderNo) {
  db.prepare('INSERT INTO download_record(user_id, paper_id, paper_hash, question_count, charge_mode, points_charged, order_no, created_at) VALUES(?,?,?,?,?,?,?,?)')
    .run(Number(uid), paperId ? Number(paperId) : null, hash, Number(qCount), mode, pointsCharged || 0, orderNo || null, nowStr());
}
// 限时免费（K-4）：运营在后台配置 {paperIds:[], start, end}，窗口内这些卷判价直接免费
function limitedFreePaper(paperId) {
  if (!paperId) return false;
  const raw = db.prepare('SELECT value FROM settings WHERE key=?').get('limited_free_papers')?.value;
  if (!raw) return false;
  try {
    const cfg = JSON.parse(raw);
    const now = nowStr();
    const inWindow = (!cfg.start || cfg.start <= now) && (!cfg.end || cfg.end >= now);
    return inWindow && Array.isArray(cfg.paperIds) && cfg.paperIds.map(Number).includes(Number(paperId));
  } catch (_) { return false; }
}
// 判价（D-1 顺序）：30天重复 → 已购单卷 → 限时免费 → 会员 → 免费额度 → 点数
function billing(uid, questionCount, hash, paperId) {
  lazyExpire(uid);
  const ent = entitlement(uid);
  const repeat = db.prepare('SELECT COUNT(*) AS c FROM download_record WHERE user_id=? AND paper_hash=? AND created_at >= ?')
    .get(Number(uid), hash, nowStr(addDays(new Date(), -30))).c;
  if (repeat > 0) return { mode: 'FREE_REPEAT', needPoints: 0, reason: 'REPEAT_30D', billing: { ...ent.points, memberActive: ent.member.active, repeat: true } };
  const paid = db.prepare('SELECT COUNT(*) AS c FROM download_record WHERE user_id=? AND paper_hash=? AND order_no IS NOT NULL').get(Number(uid), hash).c;
  if (paid > 0) return { mode: 'FREE_REPEAT', needPoints: 0, reason: 'PAID_ORDER', billing: { ...ent.points, memberActive: ent.member.active, repeat: false } };
  if (limitedFreePaper(paperId)) return { mode: 'LIMITED_FREE', needPoints: 0, reason: 'LIMITED_FREE', billing: { ...ent.points, memberActive: ent.member.active, repeat: false } };
  if (ent.member.active) return { mode: 'MEMBER', needPoints: 0, reason: 'MEMBER', billing: { ...ent.points, memberActive: true, repeat: false } };
  if (ent.points.freeUsedToday < ent.points.freeDownloadsPerDay) return { mode: 'FREE_QUOTA', needPoints: 0, reason: 'FREE_QUOTA', billing: { ...ent.points, memberActive: false, repeat: false } };
  return { mode: 'POINTS', needPoints: paperNeedPoints(questionCount), reason: `TIRED_${questionCount <= 10 ? '1_10' : questionCount <= 20 ? '11_20' : questionCount <= 50 ? '21_50' : '51_PLUS'}`, billing: { ...ent.points, memberActive: false, repeat: false } };
}
// 扣费（D-2，导出成功后调用；服务端复算）
const consumeTx = db.transaction((uid, paperId, hash, qCount) => {
  const verdict = billing(uid, qCount, hash, paperId);
  let charged = 0;
  if (verdict.mode === 'POINTS') {
    creditPoint(uid, -verdict.needPoints, 'CONSUME', `下载扣费·${qCount}题`, null);
    charged = verdict.needPoints;
  }
  recordDownload(uid, paperId, hash, qCount, verdict.mode, charged, null);
  const balance = db.prepare('SELECT balance FROM point_account WHERE user_id=?').get(Number(uid))?.balance || 0;
  return { charged, mode: verdict.mode, balance };
});
function consume(uid, paperId, hash, qCount) { return consumeTx(uid, paperId, hash, qCount); }

// ---------- 营销（REQ-K）----------
const STREAK_BONUS = [{ day: 3, bonus: 2 }, { day: 7, bonus: 5 }, { day: 15, bonus: 10 }, { day: 30, bonus: 20 }];
const signInTx = db.transaction((uid) => {
  const today = dateStr();
  const dup = db.prepare('SELECT id FROM sign_in_log WHERE user_id=? AND sign_date=?').get(Number(uid), today);
  if (dup) { const e = new Error('今日已签到'); e.code = 42900; e.reason = 'ALREADY_SIGNED'; throw e; }
  const yest = dateStr(addDays(new Date(), -1));
  const prev = db.prepare('SELECT continuous FROM sign_in_log WHERE user_id=? AND sign_date=?').get(Number(uid), yest);
  const continuous = (prev ? prev.continuous : 0) + 1;
  const ent = entitlement(uid);
  const base = 1, mult = ent.member.active ? 2 : 1;
  const bonus = (STREAK_BONUS.filter(s => continuous % s.day === 0).reduce((a, s) => a + s.bonus, 0)) * mult;
  const points = (base * mult) + bonus;
  creditPoint(uid, points, 'SIGN', `签到${continuous}天连续`, null);
  db.prepare('INSERT INTO sign_in_log(user_id, sign_date, continuous, points) VALUES(?,?,?,?)').run(Number(uid), today, continuous, points);
  const balance = db.prepare('SELECT balance FROM point_account WHERE user_id=?').get(Number(uid))?.balance || 0;
  return { signedDate: today, continuous, pointsGained: points, memberMultiplier: mult, basePoints: base, streakBonus: bonus, balance };
});
function signIn(uid) { return signInTx(uid); }
function signInInfo(uid) {
  const monthStart = dateStr().slice(0, 7) + '-01';
  const calendar = Array.from(db.prepare('SELECT sign_date FROM sign_in_log WHERE user_id=? AND sign_date>=? ORDER BY sign_date').iterate(Number(uid), monthStart)).map(r => r.sign_date);
  const latest = db.prepare('SELECT * FROM sign_in_log WHERE user_id=? ORDER BY sign_date DESC LIMIT 1').get(Number(uid));
  const signedToday = !!(latest && latest.sign_date === dateStr());
  let continuous = 0;
  if (latest) {
    continuous = latest.sign_date === dateStr() ? latest.continuous : (latest.sign_date === dateStr(addDays(new Date(), -1)) ? latest.continuous : 0);
  }
  return { signedToday, continuous, calendar };
}

function inviteInfo(uid) {
  const u = db.prepare('SELECT invite_code FROM users WHERE id=?').get(Number(uid));
  if (!u.invite_code) {
    let code = genInviteCode();
    while (db.prepare('SELECT id FROM users WHERE invite_code=?').get(code)) code = genInviteCode();
    db.prepare('UPDATE users SET invite_code=? WHERE id=?').run(code, Number(uid));
    u.invite_code = code;
  }
  const records = Array.from(db.prepare(`SELECT i.invitee_id, i.points_awarded, i.created_at, u.mobile FROM invite_record i JOIN users u ON u.id=i.invitee_id WHERE i.inviter_id=? ORDER BY i.id DESC LIMIT 50`).iterate(Number(uid)));
  return { inviteCode: u.invite_code, invited: records.length, pointsEarned: records.reduce((s, r) => s + r.points_awarded, 0),
    records: records.map(r => ({ mobileMasked: String(r.mobile).replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2'), points: r.points_awarded, createdAt: r.created_at })) };
}

// 注册事务内调用：新人礼包（K-1）+ 邀请绑定（K-2）
const registerGiftTx = db.transaction((uid, inviteCode) => {
  const now = nowStr();
  // 新人：7 日体验会员 + 10 点
  const u = db.prepare('SELECT member_until FROM users WHERE id=?').get(Number(uid));
  if (!u.member_until) {
    const until = dateStr(addDays(new Date(), 7));
    db.prepare('UPDATE users SET member_until=?, plan_code=? WHERE id=?').run(until, 'NEWBIE_7D', Number(uid));
    creditPoint(uid, 10, 'GIFT_NEWBIE', '新人礼包·7日会员+10点', null);
    const tpl = db.prepare("SELECT * FROM coupon_template WHERE code='NEW10' AND status=1").get();
    if (tpl) {
      const snap = JSON.stringify({ type: tpl.type, name: tpl.name, amountCents: tpl.amount_cents, discountRate: tpl.discount_rate,
        maxDiscountCents: tpl.max_discount_cents, thresholdCents: tpl.threshold_cents, scope: tpl.scope });
      db.prepare("INSERT INTO user_coupon(template_id, user_id, status, snapshot, source, claimed_at, expire_at) VALUES(?,?, 'UNUSED', ?, 'GIFT_NEWBIE', ?, ?)")
        .run(tpl.id, Number(uid), snap, now, nowStr(addDays(now, 7)));
    }
  }
  // 邀请：一人仅一次（uk_invitee）；双方各 +20
  if (inviteCode) {
    const inviter = db.prepare('SELECT id FROM users WHERE invite_code=?').get(String(inviteCode));
    if (inviter && inviter.id !== Number(uid)) {
      try {
        db.prepare('INSERT INTO invite_record(inviter_id, invitee_id, points_awarded, created_at) VALUES(?,?,20,?)').run(inviter.id, Number(uid), now);
        db.prepare('UPDATE users SET inviter_id=? WHERE id=?').run(inviter.id, Number(uid));
        creditPoint(inviter.id, 20, 'GIFT_INVITE', '邀请好友注册奖励', null);
        creditPoint(uid, 20, 'GIFT_INVITE', '受邀注册奖励', null);
      } catch (_) { /* 唯一约束冲突=已被邀请过，忽略 */ }
    }
  }
});
function registerGift(uid, inviteCode) { return registerGiftTx(uid, inviteCode); }

// ---------- 后台运营 ----------
function adminOrders(status, pageNo, pageSize) {
  const valid = ['CREATED', 'PAID', 'CLOSED', 'REFUNDED'];
  const hasFilter = status && status !== 'ALL' && valid.includes(status);
  const where = hasFilter ? "WHERE status='" + status + "'" : '';
  const total = db.prepare(`SELECT COUNT(*) AS c FROM trade_order ${where}`).get().c;
  const rows = Array.from(db.prepare(`SELECT * FROM trade_order ${where} ORDER BY id DESC LIMIT ? OFFSET ?`)
    .iterate(Number(pageSize), (Number(pageNo) - 1) * Number(pageSize))).map(serializeOrder);
  return { total, rows };
}
function adminCouponTemplates(pageNo, pageSize) {
  const total = db.prepare('SELECT COUNT(*) AS c FROM coupon_template').get().c;
  const rows = Array.from(db.prepare('SELECT * FROM coupon_template ORDER BY id DESC LIMIT ? OFFSET ?').iterate(Number(pageSize), (Number(pageNo) - 1) * Number(pageSize)));
  return { total, rows: rows.map(t => ({ id: t.id, code: t.code, name: t.name, type: t.type, amountCents: t.amount_cents,
    discountRate: t.discount_rate, maxDiscountCents: t.max_discount_cents, thresholdCents: t.threshold_cents,
    total: t.total, claimed: t.claimed, perLimit: t.per_limit, validDays: t.valid_days, fixedEnd: t.fixed_end,
    scope: t.scope, status: t.status, note: t.note, createdAt: t.created_at })) };
}
const createCouponTx = db.transaction((b) => {
  const now = nowStr();
  return db.prepare(`INSERT INTO coupon_template(code,name,type,amount_cents,discount_rate,max_discount_cents,threshold_cents,total,per_limit,claim_start,claim_end,valid_days,fixed_end,scope,status,note,created_at)
    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)`)
    .run(String(b.code), String(b.name), String(b.type), Number(b.amountCents) || 0, Number(b.discountRate) || 0,
         Number(b.maxDiscountCents) || 0, Number(b.thresholdCents) || 0, Number(b.total) || 0, Number(b.perLimit ?? 1),
         b.claimStart || null, b.claimEnd || null, b.validDays ? Number(b.validDays) : null, b.fixedEnd || null,
         String(b.scope || 'ALL'), 1, b.note || null, now).lastInsertRowid;
});
function createCouponTemplate(b) { return createCouponTx(b); }
// status: 1 上架 / 0 下架 / 2 作废（连带用户券 CLOSED，C-7'）
const setCouponStatusTx = db.transaction((id, status) => {
  const r = db.prepare('UPDATE coupon_template SET status=? WHERE id=?').run(Number(status), Number(id));
  if (Number(status) === 2) db.prepare("UPDATE user_coupon SET status='CLOSED' WHERE template_id=? AND status IN ('UNUSED','FROZEN')").run(Number(id));
  return r.changes;
});
function setCouponStatus(id, status) { return setCouponStatusTx(id, status); }

function adminPointsAdjust(uid, delta, note, actor) {
  const r = db.transaction(() => creditPoint(uid, Number(delta), 'ADMIN_ADJUST', `${note || '人工调整'}（操作人：${actor}）`, null))();
  return { balance: r };
}

// ---------- 纠错反馈与积分任务（P-5：纠错采纳 +5 点）----------
const FEEDBACK_TYPES = ['内容错误', '答案错误', '解析错误', '图形缺失', '其他'];
function submitFeedback(uid, questionId, type, content) {
  const q = db.prepare('SELECT id FROM questions WHERE id=? AND status=2').get(Number(questionId));
  if (!q) { const e = new Error('题目不存在或未上架'); e.code = 40400; throw e; }
  if (!FEEDBACK_TYPES.includes(String(type))) { const e = new Error('纠错类型非法'); e.code = 42200; throw e; }
  const text = String(content || '').trim();
  if (text.length < 5 || text.length > 500) { const e = new Error('纠错说明需 5~500 字'); e.code = 42200; throw e; }
  const dup = db.prepare("SELECT id FROM feedback WHERE question_id=? AND user_id=? AND status='PENDING'").get(Number(questionId), Number(uid));
  if (dup) { const e = new Error('该题目已有待处理的纠错反馈'); e.code = 42900; e.reason = 'DUPLICATE_PENDING'; throw e; }
  return db.prepare('INSERT INTO feedback(question_id, user_id, type, content, created_at) VALUES(?,?,?,?,?)')
    .run(Number(questionId), Number(uid), String(type), text, nowStr()).lastInsertRowid;
}
function myFeedback(uid) {
  return Array.from(db.prepare('SELECT id, question_id, type, content, status, reward_points, handled_at, created_at FROM feedback WHERE user_id=? ORDER BY id DESC LIMIT 20').iterate(Number(uid)));
}
function adminFeedback(status, pageNo, pageSize) {
  const valid = ['PENDING', 'ACCEPTED', 'REJECTED'];
  const hasFilter = status && status !== 'ALL' && valid.includes(status);
  const where = hasFilter ? "WHERE status='" + status + "'" : '';
  const total = db.prepare(`SELECT COUNT(*) AS c FROM feedback ${where}`).get().c;
  const rows = Array.from(db.prepare(`SELECT * FROM feedback ${where} ORDER BY id DESC LIMIT ? OFFSET ?`)
    .iterate(Number(pageSize), (Number(pageNo) - 1) * Number(pageSize)));
  return { total, rows };
}
// 纠错处理：采纳→一次性奖励 +5 点（ERROR_REWARD 流水，reward_points 标记防重复发奖）
const reviewFeedbackTx = db.transaction((id, status, actor) => {
  const f = db.prepare('SELECT * FROM feedback WHERE id=?').get(Number(id));
  if (!f) { const e = new Error('反馈不存在'); e.code = 40400; throw e; }
  if (f.status !== 'PENDING') { const e = new Error('该反馈已处理'); e.code = 40001; throw e; }
  if (!['ACCEPTED', 'REJECTED'].includes(status)) { const e = new Error('处理结果非法'); e.code = 42200; throw e; }
  db.prepare('UPDATE feedback SET status=?, reward_points=?, handled_by=?, handled_at=? WHERE id=? AND status=?')
    .run(status, status === 'ACCEPTED' ? 5 : 0, String(actor), nowStr(), Number(id), 'PENDING');
  let rewarded = 0;
  if (status === 'ACCEPTED') {
    creditPoint(f.user_id, 5, 'ERROR_REWARD', `纠错采纳奖励·题目#${f.question_id}`, null);
    rewarded = 5;
    require('./message').notifySafe(f.user_id, 'REWARD', '纠错已采纳',
      `您对题目 #${f.question_id} 的纠错已被采纳，奖励 5 点已入账。`, 'feedback', f.id);
  }
  return { id: Number(id), status, rewarded };
});
function reviewFeedback(id, status, actor) { return reviewFeedbackTx(id, status, actor); }

// ---------- 录题工作台（P-5：录题上架 +20 点；TJ-102 重复检测）----------
const QUESTION_TYPES = ['单选题', '多选题', '填空题', '解答题', '判断题'];
// 题干归一化：去空白/全角半角/统一小写 —— 完全重复检测基准（BCTK 思路最小落地）
const normStem = (s) => String(s || '').replace(/\s+/g, '').replace(/（/g, '(').replace(/）/g, ')').replace(/：/g, ':').toLowerCase();
// 教师录题：落库为「审核中(status=1)」，上架由编辑/超管触发并发奖
function contributeQuestion(uid, b) {
  const subject = db.prepare('SELECT id FROM subjects WHERE id=?').get(Number(b.subjectId));
  if (!subject) { const e = new Error('学科不存在'); e.code = 42200; throw e; }
  if (!QUESTION_TYPES.includes(String(b.type))) { const e = new Error('题型非法'); e.code = 42200; throw e; }
  const diff = Number(b.difficulty);
  if (!(diff >= 1 && diff <= 5)) { const e = new Error('难度需 1~5'); e.code = 42200; throw e; }
  const stem = String(b.stem || '').trim();
  if (stem.length < 5 || stem.length > 5000) { const e = new Error('题干需 5~5000 字'); e.code = 42200; throw e; }
  const answer = String(b.answer || '').trim();
  if (!answer) { const e = new Error('答案不能为空'); e.code = 42200; throw e; }
  if (b.type === '单选题' && !Array.isArray(b.options)) { const e = new Error('单选题必须提供选项'); e.code = 42200; throw e; }
  // TJ-102 重复检测：题干归一化后完全一致即拒绝（跨上架/审核中所有题；长度窗口粗筛减少全表归一化）
  const normalized = normStem(stem);
  const dup = Array.from(db.prepare('SELECT id, status, stem FROM questions WHERE status IN (1,2) AND LENGTH(stem) BETWEEN ? AND ?')
    .iterate(stem.length - 4, stem.length + 4))
    .find(r => normStem(r.stem) === normalized);
  if (dup) {
    const e = new Error(`题干与已有题目 #${dup.id} 重复（${dup.status === 1 ? '审核中' : '已上架'}），请勿重复录题`);
    e.code = 42200; e.duplicateOf = dup.id; throw e;
  }
  return db.prepare(`INSERT INTO questions(subject_id,type,difficulty,coefficient,scene,category,kp_names,source,stem,options,answer,analysis,status,author_id,created_at)
    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,'1',?,?)`)
    .run(Number(b.subjectId), String(b.type), diff, Number(b.coefficient) || 0.65,
         normStr(b.scene) || '同步检测', normStr(b.category) || '教师录题', normStr(b.kpNames),
         '教师录题', stem,
         Array.isArray(b.options) ? JSON.stringify(b.options) : null,
         answer, normStr(b.analysis), Number(uid), nowStr()).lastInsertRowid;
}
function myContributions(uid) {
  return Array.from(db.prepare('SELECT id, type, difficulty, kp_names, stem, status, reward_paid, created_at FROM questions WHERE author_id=? ORDER BY id DESC LIMIT 50').iterate(Number(uid)));
}
// 录题审核：上架(2)→作者一次性 +20（CONTRIB_REWARD，reward_paid 防重复）；驳回(3)→不发奖
const reviewQuestionTx = db.transaction((id, status, actor) => {
  const q = db.prepare('SELECT * FROM questions WHERE id=?').get(Number(id));
  if (!q) { const e = new Error('题目不存在'); e.code = 40400; throw e; }
  if (q.status !== '1' && q.status !== 1) { const e = new Error('仅「审核中」的录题可处理'); e.code = 40001; throw e; }
  if (![2, 3].includes(Number(status))) { const e = new Error('处理结果非法（2 上架 / 3 驳回）'); e.code = 42200; throw e; }
  db.prepare('UPDATE questions SET status=? WHERE id=?').run(Number(status), Number(id));
  let rewarded = 0;
  if (Number(status) === 2 && q.author_id && !q.reward_paid) {
    creditPoint(q.author_id, 20, 'CONTRIB_REWARD', `录题上架奖励·题目#${q.id}`, null);
    db.prepare('UPDATE questions SET reward_paid=1 WHERE id=?').run(Number(id));
    rewarded = 20;
  }
  if (q.author_id) {
    const msg = require('./message');
    if (Number(status) === 2) msg.notifySafe(q.author_id, 'REVIEW', '录题已上架',
      `您的录题「${String(q.stem).slice(0, 30)}…」已通过审核并上架${rewarded ? `，奖励 20 点已入账` : ''}。`, 'question', q.id);
    else msg.notifySafe(q.author_id, 'REVIEW', '录题未通过',
      `您的录题「${String(q.stem).slice(0, 30)}…」未通过审核，可修改后重新提交。`, 'question', q.id);
  }
  return { id: Number(id), status: Number(status), rewarded };
});
function reviewQuestion(id, status, actor) { return reviewQuestionTx(id, status, actor); }

module.exports = {
  plans, entitlement, creditPoint, pointsLog,
  couponsAvailable, claimCoupon, myCoupons, lazyExpire, expireCouponsJob, couponDiscount,
  createOrder, orderDetail, serializeOrder, closeOrder, closeExpiredOrdersJob,
  payNotify, refund, billing, consume, paperHashOf, paperQuestionIds, paperNeedPoints, myOrders,
  signIn, signInInfo, inviteInfo, registerGift,
  adminOrders, adminCouponTemplates, createCouponTemplate, setCouponStatus, adminPointsAdjust,
  submitFeedback, myFeedback, adminFeedback, reviewFeedback, FEEDBACK_TYPES,
  contributeQuestion, myContributions, reviewQuestion, QUESTION_TYPES, limitedFreePaper,
  ORDER_EXPIRE_MINUTES, POINTS_PER_YUAN
};
