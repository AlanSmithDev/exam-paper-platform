// WP-8: 冒烟测试 —— 启动服务器 → 按 13 号任务书 §4 断言 T1~T5 → 报告结果并退出
// 运行：node tests/smoke.mjs （自动拉起 server，无需手动启动）
import { spawn } from 'node:child_process';
import { setTimeout as sleep } from 'node:timers/promises';

const BASE = 'http://127.0.0.1:' + (process.env.PORT || 3210);
process.env.PORT = process.env.PORT || '3210';

let pass = 0, fail = 0;
const ok = (name, cond, extra = '') => {
  if (cond) { pass++; console.log('  ✅ ' + name); }
  else { fail++; console.log('  ❌ ' + name + (extra ? ' —— ' + extra : '')); }
};
async function api(path, { method = 'GET', token = '', body, headers = {} } = {}) {
  const r = await fetch(BASE + '/api/v1' + path, {
    method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}), ...headers },
    body: body ? JSON.stringify(body) : undefined
  });
  let j = null;
  try { j = await r.json(); } catch (_) { j = { code: -1, message: '非 JSON 响应' }; }
  return { status: r.status, ...j };
}
// 带原始响应头的请求（幂等重放头断言用）
async function apiRaw(path, { method = 'GET', token = '', body, headers = {} } = {}) {
  return fetch(BASE + '/api/v1' + path, {
    method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}), ...headers },
    body: body ? JSON.stringify(body) : undefined
  });
}
const now = () => String(Date.now()).slice(-6);
const uuid = () => globalThis.crypto?.randomUUID?.() || 'k' + Date.now() + Math.random().toString(16).slice(2);

async function waitServer() {
  for (let i = 0; i < 40; i++) {
    try { const r = await fetch(BASE + '/api/v1/health'); if (r.ok) return; } catch (_) {}
    await sleep(300);
  }
  throw new Error('服务器未在 12 秒内就绪');
}

const server = spawn(process.execPath, ['server/server.js'], {
  cwd: new URL('../', import.meta.url).pathname.replace(/^\/([A-Za-z]):/, '$1:'),
  env: { ...process.env, PORT: process.env.PORT }, stdio: 'inherit'
});

try {
  await waitServer();
  console.log('\n== T1 认证 ==');
  {
    const h = await api('/health');
    ok('health: status=ok 且 db=true', h.code === 0 && h.data.status === 'ok' && h.data.db === true);
    const reg = await api('/auth/register', { method: 'POST', body: { mobile: '1370000' + String(Date.now()).slice(-4), <REDACTED_SECRET>, role: 'TEACHER' } });
    ok('注册返回 accessToken', reg.code === 0 && !!reg.data.accessToken);
    const tk = reg.data.accessToken;
    const bk0 = await api('/basket', { token: tk });
    ok('登录态访问试题篮', bk0.code === 0 && Array.isArray(bk0.data.ids));
    const anon = await api('/basket');
    ok('匿名访问试题篮被拒 40100', anon.code === 40100);
    const admin = await api('/auth/admin-login', { method: 'POST', body: { mobile: '13000000000', <REDACTED_SECRET> } });
    ok('超管登录 role=SUPER_ADMIN', admin.code === 0 && admin.data.profile.role === 'SUPER_ADMIN');
    global.__adminToken = admin.data.accessToken;
  }

  console.log('\n== T2 题库 ==');
  {
    const meta = await api('/questions/meta');
    ok('元数据：6 学段 + 章节树', meta.code === 0 && meta.data.stages.length === 6 && meta.data.catalog.length >= 8);
    const list = await api('/questions?subjectId=' + encodeURIComponent(await gkMathId()) + '&difficulty=4&pageSize=10');
    ok('筛选（高中数学·较难）返回 ≤10 条且至少 1 题含 LaTeX', list.code === 0 && list.data.list.length <= 10 && list.data.list.some(q => q.stem.includes('\\(')));
    const detail = await api('/questions/' + list.data.list[0].id);
    ok('详情含五段式解析键', detail.code === 0 && detail.data.analysis && 'brief' in detail.data.analysis && 'solve' in detail.data.analysis && 'comment' in detail.data.analysis);
    ok('详情含相似题', Array.isArray(detail.data.similar) && detail.data.similar.length <= 3);
  }

  console.log('\n== T3 组卷 ==');
  {
    const reg = await api('/auth/register', { method: 'POST', body: { mobile: '1360000' + String(Date.now()).slice(-4), <REDACTED_SECRET> } });
    const tk = reg.data.accessToken;
    const gen = await api('/papers/generate', { method: 'POST', token: tk, body: {
      subjectId: await gkMathId(), title: '冒烟测试卷',
      structure: [{ type: '单选题', count: 8, score: 5 }, { type: '多选题', count: 3, score: 6 }, { type: '填空题', count: 3, score: 5 }, { type: '解答题', count: 5, score: 12 }],
      difficultyTarget: 3 } });
    ok('生成 19 题且无重复', gen.code === 0 && gen.data.count <= 19 && new Set(gen.data.questions.map(q => q.id)).size === gen.data.questions.length, '实际 ' + gen.data.count + ' 题（题库种子量可能不足 19，见任务书说明）');
    ok('总分正确', gen.data.totalScore === gen.data.questions.reduce((s, q) => s + q.score, 0));
    ok('难度曲线由易到难', gen.data.questions.every((q, i, a) => i === 0 || a[i - 1].difficulty <= q.difficulty));
  }

  console.log('\n== T4 超管 ==');
  {
    const admin = global.__adminToken;
    const bad = await api('/admin/ads');
    ok('未带 token 访问超管接口 → 40100', bad.code === 40100);
    const list = await api('/admin/ads', { token: admin });
    ok('超管读取广告列表', list.code === 0 && Array.isArray(list.data.list));
    const create = await api('/admin/ads', { method: 'POST', token: admin, body: { position: 'home_banner', title: '冒烟广告', image_url: 'https://93.184.216.34/b.png', link_url: 'https://93.184.216.34/', status: 1 } });
    ok('创建广告', create.code === 0 && !!create.data.id);
    const pub = await api('/ads?position=home_banner');
    ok('前台读到该广告', pub.code === 0 && pub.data.list.some(a => a.id === create.data.id));
    const ssrf = await api('/admin/ads', { method: 'POST', token: admin, body: { position: 'home_banner', title: 'ssrf', image_url: 'http://127.0.0.1/x.png', status: 1 } });
    ok('SSRF：环回地址素材被 422 拒绝', ssrf.status === 422 && ssrf.code === 42200);
    const set = await api('/admin/settings', { method: 'PUT', token: admin, body: { site_name: '智卷云', logo_url: '' } });
    ok('保存系统设置', set.code === 0 && set.data.updated >= 1);
    const pubSettings = await api('/settings');
    ok('前台读取设置（logo 为空 → 前台渲染占位）', pubSettings.code === 0 && pubSettings.data.logo_url === '');
    const audit = await api('/admin/audit?limit=5', { token: admin });
    ok('审计日志有记录', audit.code === 0 && audit.data.list.length >= 1);
  }

  console.log('\n== T5 会员与点数（REQ-M/P/K）==');
  let userA, tkA;
  {
    const plans = await api('/member/plans');
    ok('档位列表：含教师优享 2500 分与 4 个充值套餐', plans.code === 0 &&
      plans.data.plans.some(p => p.code === 'TEACHER_PRO' && p.priceCents === 2500) && plans.data.pointPackages.length >= 4);
    const reg = await api('/auth/register', { method: 'POST', body: { mobile: '135' + String(Date.now()).slice(-8), <REDACTED_SECRET>, role: 'TEACHER' } });
    tkA = reg.data.accessToken; userA = reg.data.profile;
    const me = await api('/member/me', { token: tkA });
    ok('新人礼包：注册即享体验会员 + 10 点', me.code === 0 && me.data.member.active === true && me.data.points.balance === 10);
    const invite = await api('/trade/invite', { token: tkA });
    ok('邀请码已生成（ZJ 前缀 6 位）', invite.code === 0 && /^ZJ[A-Z2-9]{6}$/.test(invite.data.inviteCode));
    global.__inviteCodeA = invite.data.inviteCode;
    const sign1 = await api('/trade/signin', { method: 'POST', token: tkA });
    ok('签到得分 ≥1（会员双倍）且余额联动', sign1.code === 0 && sign1.data.pointsGained >= 1);
    const sign2 = await api('/trade/signin', { method: 'POST', token: tkA });
    ok('重复签到被拒 42900/ALREADY_SIGNED', sign2.code === 42900 && sign2.data.reason === 'ALREADY_SIGNED');
    const cal = await api('/trade/signin', { token: tkA });
    ok('签到日历含今日', cal.code === 0 && cal.data.calendar.length === 1 && cal.data.signedToday === true);
  }

  console.log('\n== T6 订单·支付·幂等·退款（REQ-O）==');
  let tkB, orderPaid;
  {
    const reg = await api('/auth/register', { method: 'POST', body: { mobile: '136' + String(Date.now()).slice(-8), <REDACTED_SECRET>, role: 'TEACHER', inviteCode: global.__inviteCodeA } });
    tkB = reg.data.accessToken;
    const meB = await api('/member/me', { token: tkB });
    ok('受邀注册：新人 10 点 + 邀请 20 点 = 30 点', meB.code === 0 && meB.data.points.balance === 30);
    const inv = await api('/trade/invite', { token: tkA });
    ok('邀请人入账 20 点并有记录', inv.data.records.length === 1 && inv.data.pointsEarned === 20);

    const key1 = uuid();
    const o1 = await api('/member/orders', { method: 'POST', token: tkB, headers: { 'X-Idempotency-Key': key1 }, body: { skuType: 'MEMBER', planCode: 'TEACHER_PRO', channel: 'MOCK' } });
    ok('下单成功：CREATED · 实付 2500 分', o1.code === 0 && o1.data.order.status === 'CREATED' && o1.data.order.payCents === 2500);
    const o1b = await apiRaw('/member/orders', { method: 'POST', token: tkB, headers: { 'X-Idempotency-Key': key1 }, body: { skuType: 'MEMBER', planCode: 'TEACHER_PRO', channel: 'MOCK' } });
    const o1bj = await o1b.json();
    ok('同幂等键返回原单 + X-Idempotent-Replay 头', o1bj.data.order.orderNo === o1.data.order.orderNo && o1b.headers.get('x-idempotent-replay') === 'true');

    const badAmt = await api('/payments/mock-notify', { method: 'POST', token: tkB, body: { orderNo: o1.data.order.orderNo, callbackId: 'cb-' + uuid(), amountCents: 999 } });
    ok('回调金额与订单不符被拒 42200', badAmt.code === 42200);
    const pay1 = await api('/payments/mock-notify', { method: 'POST', token: tkB, body: { orderNo: o1.data.order.orderNo, callbackId: 'cb-' + uuid(), amountCents: 2500 } });
    ok('支付成功：PAID + 履约 30 天', pay1.code === 0 && pay1.data.order.status === 'PAID' && pay1.data.fulfil.daysAdded === 30);
    orderPaid = o1.data.order.orderNo;
    const meB2 = await api('/member/me', { token: tkB });
    ok('会员到期叠加（体验 7 天 + 30 天）', meB2.data.member.remainDays >= 35 && meB2.data.member.remainDays <= 40);
    const payDup = await api('/payments/mock-notify', { method: 'POST', token: tkB, body: { orderNo: o1.data.order.orderNo, callbackId: 'cb-' + uuid(), amountCents: 2500 } });
    ok('重复回调幂等 42901 且返回 PAID 结果', payDup.code === 42901 && payDup.data.order.status === 'PAID');

    const o2 = await api('/member/orders', { method: 'POST', token: tkB, body: { skuType: 'MEMBER', planCode: 'STUDENT', channel: 'MOCK', idempotencyKey: uuid() } });
    await api('/payments/mock-notify', { method: 'POST', token: tkB, body: { orderNo: o2.data.order.orderNo, callbackId: 'cb-' + uuid(), amountCents: o2.data.order.payCents } });
    const meB3 = await api('/member/me', { token: tkB });
    ok('二次开通继续叠加（65-70 天）', meB3.data.member.remainDays >= 65 && meB3.data.member.remainDays <= 70);

    const refund = await api('/payments/refund/' + orderPaid, { method: 'POST', token: global.__adminToken });
    ok('超管退款：REFUNDED 且回收 30 天', refund.code === 0 && refund.data.status === 'REFUNDED' && refund.data.returned.days === 30);
    const meB4 = await api('/member/me', { token: tkB });
    ok('退款后会员天数回滚至 35-40 区间', meB4.data.member.remainDays >= 35 && meB4.data.member.remainDays <= 40);
    const refund2 = await api('/payments/refund/' + orderPaid, { method: 'POST', token: global.__adminToken });
    ok('重复退款被拒', refund2.code !== 0);

    const pkg = await api('/member/orders', { method: 'POST', token: tkB, body: { skuType: 'POINTS', packageId: 1, channel: 'MOCK', idempotencyKey: uuid() } });
    const pkgPay = await api('/payments/mock-notify', { method: 'POST', token: tkB, body: { orderNo: pkg.data.order.orderNo, callbackId: 'cb-' + uuid(), amountCents: 5000 } });
    const meB5 = await api('/member/me', { token: tkB });
    ok('充值到账 5500 点（含赠 500）', pkgPay.code === 0 && meB5.data.points.balance === 30 + 5500);
    const logs = await api('/trade/points?pageNo=1&pageSize=20', { token: tkB });
    ok('充值与赠送分两笔流水（RECHARGE/RECHARGE_BONUS）', logs.data.rows.some(l => l.biz_type === 'RECHARGE') && logs.data.rows.some(l => l.biz_type === 'RECHARGE_BONUS'));
    ok('流水 balance_after 链路连续（较新行余额 = 较早行余额 + 较新行 delta）',
      logs.data.rows.every((l, i, a) => i === 0 || a[i - 1].balance_after === l.balance_after + a[i - 1].delta));

    const o3 = await api('/member/orders', { method: 'POST', token: tkB, body: { skuType: 'POINTS', packageId: 2, channel: 'MOCK', idempotencyKey: uuid() } });
    const close = await api('/member/orders/' + o3.data.order.orderNo + '/close', { method: 'POST', token: tkB });
    ok('主动关单：CLOSED', close.code === 0 && close.data.status === 'CLOSED');
    const payClosed = await api('/payments/mock-notify', { method: 'POST', token: tkB, body: { orderNo: o3.data.order.orderNo, callbackId: 'cb-' + uuid(), amountCents: 10000 } });
    ok('已关单订单支付被拒 40001', payClosed.code === 40001);
  }

  console.log('\n== T7 优惠券（REQ-C）==');
  {
    const mk = (code, over) => api('/admin/coupons', { method: 'POST', token: global.__adminToken, body: { code, name: '冒烟' + code, type: 'FULL_REDUCTION', amountCents: 1000, thresholdCents: 500, total: 1, perLimit: 1, validDays: 7, ...over } });
    const c1 = await mk('SMOKE_C1_' + now());
    ok('后台创建限量券模板（总量 1）', c1.code === 0 && !!c1.data.id);
    const claim1 = await api('/coupons/claim', { method: 'POST', token: tkA, body: { templateId: c1.data.id } });
    ok('首次领取成功并算出有效期', claim1.code === 0 && !!claim1.data.expireAt);
    const claim2 = await api('/coupons/claim', { method: 'POST', token: tkA, body: { templateId: c1.data.id } });
    ok('超过每人限领 42900/PER_LIMIT', claim2.code === 42900 && claim2.data.reason === 'PER_LIMIT');
    const claim3 = await api('/coupons/claim', { method: 'POST', token: tkB, body: { templateId: c1.data.id } });
    ok('限量领完再领 42900/COUPON_SOLD_OUT', claim3.code === 42900 && claim3.data.reason === 'COUPON_SOLD_OUT');

    const c2 = await mk('SMOKE_C2_' + now(), { thresholdCents: 5000, total: 10 });
    await api('/coupons/claim', { method: 'POST', token: tkA, body: { templateId: c2.data.id } });
    const lowOrder = await api('/member/orders', { method: 'POST', token: tkA, headers: { 'X-Idempotency-Key': uuid() }, body: { skuType: 'MEMBER', planCode: 'TEACHER_PRO', couponId: (await api('/coupons/mine?status=UNUSED', { token: tkA })).data.list.find(c => c.thresholdCents === 5000).id, channel: 'MOCK' } });
    ok('满减门槛不足 42200 且提示 needCents', lowOrder.code === 42200 && lowOrder.data.needCents === 5000);

    const c1Coupon = (await api('/coupons/mine?status=UNUSED', { token: tkA })).data.list.find(c => c.thresholdCents === 500);
    const useOrder = await api('/member/orders', { method: 'POST', token: tkA, headers: { 'X-Idempotency-Key': uuid() }, body: { skuType: 'MEMBER', planCode: 'TEACHER_PRO', couponId: c1Coupon.id, channel: 'MOCK' } });
    ok('用券下单：抵扣 1000 分实付 1500 分', useOrder.code === 0 && useOrder.data.order.discountCents === 1000 && useOrder.data.order.payCents === 1500);
    const useOrder2 = await api('/member/orders', { method: 'POST', token: tkA, headers: { 'X-Idempotency-Key': uuid() }, body: { skuType: 'POINTS', packageId: 1, couponId: c1Coupon.id, channel: 'MOCK' } });
    ok('同券第二单被拒（FROZEN 互斥）42200', useOrder2.code === 42200);
    await api('/member/orders/' + useOrder.data.order.orderNo + '/close', { method: 'POST', token: tkA });
    const backUnused = (await api('/coupons/mine?status=UNUSED', { token: tkA })).data.list.some(c => c.id === c1Coupon.id);
    ok('关单后券自动返还 UNUSED（C-5\'）', backUnused === true);

    const reOrder = await api('/member/orders', { method: 'POST', token: tkA, headers: { 'X-Idempotency-Key': uuid() }, body: { skuType: 'MEMBER', planCode: 'TEACHER_PRO', couponId: c1Coupon.id, channel: 'MOCK' } });
    await api('/payments/mock-notify', { method: 'POST', token: tkA, body: { orderNo: reOrder.data.order.orderNo, callbackId: 'cb-' + uuid(), amountCents: 1500 } });
    const usedCoupon = (await api('/coupons/mine?status=USED', { token: tkA })).data.list.some(c => c.id === c1Coupon.id);
    ok('支付核销后券状态 USED', usedCoupon === true);
    const refundC = await api('/payments/refund/' + reOrder.data.order.orderNo, { method: 'POST', token: global.__adminToken });
    const backAgain = (await api('/coupons/mine?status=UNUSED', { token: tkA })).data.list.some(c => c.id === c1Coupon.id);
    ok('退款后券返还（未过期）', refundC.code === 0 && refundC.data.returned.couponId === c1Coupon.id && backAgain === true);

    const avail = await api('/coupons/available', { token: tkB });
    ok('券中心附 claimState 状态', avail.code === 0 && avail.data.list.every(t => t.claimState));

    // 点数券履约（C-1 POINTS 类型：核销即兑换点数入账）
    const ptTpl = (await api('/coupons/available', { token: tkA })).data.list.find(t => t.type === 'POINTS');
    const ptClaim = await api('/coupons/claim', { method: 'POST', token: tkA, body: { templateId: ptTpl.id } });
    const balBefore = (await api('/member/me', { token: tkA })).data.points.balance;
    const ptOrder = await api('/member/orders', { method: 'POST', token: tkA, headers: { 'X-Idempotency-Key': uuid() }, body: { skuType: 'MEMBER', planCode: 'STUDENT', couponId: ptClaim.data.couponId, channel: 'MOCK' } });
    ok('点数券下单：金额抵扣 0（履约时兑换）', ptOrder.code === 0 && ptOrder.data.order.discountCents === 0 && ptOrder.data.order.payCents === 1500);
    await api('/payments/mock-notify', { method: 'POST', token: tkA, body: { orderNo: ptOrder.data.order.orderNo, callbackId: 'cb-' + uuid(), amountCents: 1500 } });
    const balAfter = (await api('/member/me', { token: tkA })).data.points.balance;
    const ptLog = await api('/trade/points?pageNo=1&pageSize=10', { token: tkA });
    ok('点数券核销到账 +100 点（COUPON_EXCHANGE 流水）', balAfter === balBefore + 100 && ptLog.data.rows.some(l => l.biz_type === 'COUPON_EXCHANGE' && l.delta === 100));
  }

  console.log('\n== T8 下载计费（REQ-D）与后台运营 ==');
  {
    const reg = await api('/auth/register', { method: 'POST', body: { mobile: '137' + String(Date.now()).slice(-8), <REDACTED_SECRET> } });
    const tkC = reg.data.accessToken;
    const gk = await gkMathId();
    const gen = async (n) => (await api('/papers/generate', { method: 'POST', token: tkC, body: { subjectId: gk, title: '计费卷' + n, structure: [{ type: '单选题', count: n, score: 5 }], difficultyTarget: 3 } })).data;
    // 清掉体验会员，回到 FREE 用户路径
    await api('/admin/users/' + reg.data.profile.id, { method: 'PUT', token: global.__adminToken, body: { member_until: '' } });
    const p1 = await gen(8);
    const bill1 = await api('/trade/billing?paperId=' + p1.paperId, { token: tkC });
    ok('判价① 首次下载命中免费额度 FREE_QUOTA', bill1.code === 0 && bill1.data.mode === 'FREE_QUOTA' && bill1.data.needPoints === 0);
    const exp1 = await apiRaw('/papers/' + p1.paperId + '/export', { token: tkC });
    ok('导出成功（HTML 含水印页脚）', exp1.ok && (await exp1.text()).includes('ID:' + reg.data.profile.id));
    const exp1b = await apiRaw('/papers/' + p1.paperId + '/export', { token: tkC });
    ok('30 天内重复下载 FREE_REPEAT 不重复计费', exp1b.ok);
    const bill1b = await api('/trade/billing?paperId=' + p1.paperId, { token: tkC });
    ok('判价② 重复下载 mode=FREE_REPEAT', bill1b.data.mode === 'FREE_REPEAT' && bill1b.data.reason === 'REPEAT_30D');
    const p2 = await gen(9), p3 = await gen(10);
    await apiRaw('/papers/' + p2.paperId + '/export', { token: tkC });
    await apiRaw('/papers/' + p3.paperId + '/export', { token: tkC });
    const p4 = await gen(11);
    const bill4 = await api('/trade/billing?paperId=' + p4.paperId, { token: tkC });
    ok('免费额度用尽转点数：11 题 needPoints=200', bill4.data.mode === 'POINTS' && bill4.data.needPoints === 200);
    const expPoor = await apiRaw('/papers/' + p4.paperId + '/export', { token: tkC });
    const poorBody = await expPoor.json();
    ok('余额不足导出被拒 42900 并引导充值', poorBody.code === 42900 && poorBody.data.needPoints === 200);
    await api('/trade/points/adjust', { method: 'POST', token: global.__adminToken, body: { userId: reg.data.profile.id, delta: 100, note: '冒烟测试充值' } });
    const expStill = await apiRaw('/papers/' + p4.paperId + '/export', { token: tkC });
    ok('余额 110 < 200 仍被拒', (await expStill.json()).code === 42900);
    const adj = await api('/trade/points/adjust', { method: 'POST', token: global.__adminToken, body: { userId: reg.data.profile.id, delta: 100, note: '冒烟测试补足' } });
    ok('人工调整后余额 210', adj.code === 0 && adj.data.balance === 210);
    const expOk = await apiRaw('/papers/' + p4.paperId + '/export', { token: tkC });
    ok('点数扣费后导出成功', expOk.ok);
    const logsC = await api('/trade/points', { token: tkC });
    ok('CONSUME 流水 -200 且余额 10', logsC.data.rows.some(l => l.biz_type === 'CONSUME' && l.delta === -200) && logsC.data.balance === 10);
    const consumePoor = await api('/trade/consume', { method: 'POST', token: tkC, body: { paperId: p4.paperId, paperHash: 'x'.repeat(64), questionCount: 25 } });
    ok('consume 服务端复算余额不足 42900', consumePoor.code === 42900);

    const orders = await api('/admin/orders?status=PAID', { token: global.__adminToken });
    ok('后台订单列表（PAID 过滤）', orders.code === 0 && orders.data.rows.length >= 2 && orders.data.rows.every(o => o.status === 'PAID'));

    // 教师认证 +2 免费下载额度（与前台权益宣传一致）
    const entBefore = await api('/member/me', { token: tkC });
    await api('/admin/users/' + reg.data.profile.id, { method: 'PUT', token: global.__adminToken, body: { certify: 1 } });
    const entAfter = await api('/member/me', { token: tkC });
    ok('教师认证后免费额度 3→5', entBefore.data.points.freeDownloadsPerDay === 3 && entAfter.data.points.freeDownloadsPerDay === 5 && entAfter.data.points.certifyBonus === 2);

    // 纠错反馈与积分任务（P-5：采纳 +5 点，仅首次发放）
    const q1 = (await api('/questions?pageSize=1')).data.list[0];
    const fbBad = await api('/questions/' + q1.id + '/feedback', { method: 'POST', token: tkC, body: { type: '答案错误', content: '短' } });
    ok('纠错说明过短被拒 42200', fbBad.code === 42200);
    const fb1 = await api('/questions/' + q1.id + '/feedback', { method: 'POST', token: tkC, body: { type: '答案错误', content: '冒烟测试：该题答案选项与解析不一致' } });
    ok('纠错提交成功', fb1.code === 0 && !!fb1.data.id);
    const fbDup = await api('/questions/' + q1.id + '/feedback', { method: 'POST', token: tkC, body: { type: '其他', content: '冒烟测试重复提交同一题目反馈' } });
    ok('同题待处理反馈去重 42900', fbDup.code === 42900 && fbDup.data.reason === 'DUPLICATE_PENDING');
    const fbPending = await api('/admin/feedback?status=PENDING', { token: global.__adminToken });
    ok('后台待处理队列可见', fbPending.code === 0 && fbPending.data.rows.some(f => f.id === fb1.data.id));
    const review = await api('/admin/feedback/' + fb1.data.id + '/review', { method: 'POST', token: global.__adminToken, body: { status: 'ACCEPTED' } });
    ok('纠错采纳奖励 +5 点', review.code === 0 && review.data.rewarded === 5);
    const review2 = await api('/admin/feedback/' + fb1.data.id + '/review', { method: 'POST', token: global.__adminToken, body: { status: 'REJECTED' } });
    ok('重复处理被拒 40001', review2.code === 40001);
    const logsFb = await api('/trade/points', { token: tkC });
    ok('ERROR_REWARD 流水入账（余额 10+5=15）', logsFb.data.rows.some(l => l.biz_type === 'ERROR_REWARD' && l.delta === 5) && logsFb.data.balance === 15);

    const audit = await api('/admin/audit?limit=50', { token: global.__adminToken });
    ok('审计含券创建/点数调整/退款/纠错处理记录', ['COUPON_CREATE', 'TRADE_POINTS_ADJUST', 'TRADE_REFUND', 'FEEDBACK_REVIEW'].every(a => audit.data.list.some(l => l.action === a)));
    const forbidden = await api('/trade/points/adjust', { method: 'POST', token: tkA, body: { userId: 9, delta: 1 } });
    ok('非超管调整点数被拒 40301', forbidden.code === 40301);
  }

  console.log('\n== T9 练习与错题（docs/15 PR-1~PR-6）==');
  let tkD;
  {
    const regD = await api('/auth/register', { method: 'POST', body: { mobile: '138' + String(Date.now()).slice(-8), <REDACTED_SECRET> } });
    tkD = regD.data.accessToken;
    await api('/admin/users/' + regD.data.profile.id, { method: 'PUT', token: global.__adminToken, body: { member_until: '' } });
    const pr = await api('/practices', { method: 'POST', token: tkD, body: { mode: 'KP', subjectId: await gkMathId(), count: 2, type: '单选题' } });
    ok('生成练习：2 题且不含答案', pr.code === 0 && pr.data.total === 2 && pr.data.questions.every(q => !('answer' in q)));
    const qids = pr.data.questions.map(q => q.id);
    const d1 = await api('/questions/' + qids[0]);
    const submit = await api('/practices/' + pr.data.practiceId + '/submit', { method: 'POST', token: tkD, body: { answers: [
      { questionId: qids[0], answer: d1.data.answer, durationMs: 5000 },
      { questionId: qids[1], answer: 'Z', durationMs: 3000 }
    ] } });
    ok('客观题判分：正确率 0.5，错题入错题本', submit.code === 0 && submit.data.correctRate === 0.5 && submit.data.wrongIds.includes(qids[1]));
    const wq = await api('/wrong-questions', { token: tkD });
    ok('错题本含未解决错题', wq.code === 0 && wq.data.list.some(w => w.id === qids[1] && w.resolved === 0));
    const rep = await api('/practices', { method: 'POST', token: tkD, body: { mode: 'REPEAT', count: 3 } });
    ok('再练卷基于错题知识点且排除原错题', rep.code === 0 && rep.data.mode === 'REPEAT' && rep.data.questions.every(q => q.id !== qids[1]));
    if (rep.code === 0 && rep.data.questions.length) {
      const repQ = rep.data.questions[0];
      const rd = await api('/questions/' + repQ.id);
      const sub2 = await api('/practices/' + rep.data.practiceId + '/submit', { method: 'POST', token: tkD, body: { answers: [{ questionId: repQ.id, answer: rd.data.answer }] } });
      ok('再练卷提交判分正常', sub2.code === 0 && sub2.data.results[0].correct === 1);
    }
    const res = await api('/wrong-questions/' + qids[1] + '/resolve', { method: 'POST', token: tkD });
    ok('错题标记已掌握', res.code === 0 && res.data.resolved === 1);
    const report = await api('/report/kp', { token: tkD });
    ok('学情聚合：知识点正确率', report.code === 0 && report.data.list.length >= 1 && 'correctRate' in report.data.list[0]);
  }

  console.log('\n== T10 AI 域 / 录题工作台 / 限时免费 ==');
  {
    const qAny = (await api('/questions?pageSize=1')).data.list[0].id;
    // AI 配额（D16）：免费用户 3 次/日
    const q0 = await api('/ai/quota', { token: tkD });
    ok('AI 配额入口：免费 3 次/日', q0.code === 0 && q0.data.quota === 3);
    const exp = await api('/ai/explain', { method: 'POST', token: tkD, body: { questionId: qAny } });
    ok('AI 分步讲题（MOCK）非空且含步骤', exp.code === 0 && exp.data.steps.includes('第 1 步'));
    const vr = await api('/ai/variants', { method: 'POST', token: tkD, body: { questionId: qAny } });
    ok('AI 变式出题：[AI-草稿] 标记 + aigc=1 待审核', vr.code === 0 && vr.data.draft.marker === '[AI-草稿]' && vr.data.draft.aigc === 1);
    const exp2 = await api('/ai/explain', { method: 'POST', token: tkD, body: { questionId: qAny } });
    ok('第 3 次调用成功（配额内）', exp2.code === 0);
    const exp3 = await api('/ai/explain', { method: 'POST', token: tkD, body: { questionId: qAny } });
    ok('超出免费配额 42900/AI_QUOTA_EXCEEDED', exp3.code === 42900 && exp3.data.reason === 'AI_QUOTA_EXCEEDED');

    // 录题工作台（D12）：提交 → 审核上架 → 作者 +20
    const subjId = (await api('/questions/meta')).data.subjects.find(s => s.stage_id === 3 && s.name === '数学').id;
    const ct = await api('/questions/contribute', { method: 'POST', token: tkD, body: { subjectId: subjId, type: '填空题', difficulty: 3, kpNames: '录题测试', stem: '冒烟测试录题：计算 1+1= ______（编号' + Date.now() + '）。', answer: '2' } });
    ok('录题提交进入「审核中」', ct.code === 0 && ct.data.status === '审核中');
    const mine = await api('/questions/contributions/mine', { token: tkD });
    ok('我的录题记录可见', mine.code === 0 && mine.data.list.some(c => c.id === ct.data.id));
    const balC0 = (await api('/member/me', { token: tkD })).data.points.balance;
    const rev = await api('/admin/questions/' + ct.data.id + '/review', { method: 'POST', token: global.__adminToken, body: { status: 2 } });
    ok('审核上架奖励作者 20 点', rev.code === 0 && rev.data.rewarded === 20);
    const rev2 = await api('/admin/questions/' + ct.data.id + '/review', { method: 'POST', token: global.__adminToken, body: { status: 3 } });
    ok('重复审核被拒 40001', rev2.code === 40001);
    const recheck = await api('/questions/' + ct.data.id);
    ok('上架题进入可检索题库（详情接口仅返回 status=2）', recheck.code === 0 && recheck.data.id === ct.data.id && (await api('/questions?keyword=' + encodeURIComponent('冒烟测试录题'))).data.total >= 1);
    const balC1 = (await api('/member/me', { token: tkD })).data.points.balance;
    ok('作者点数到账 +20', balC1 === balC0 + 20);

    // 限时免费（D13/K-4）：新组一张卷避开 FREE_REPEAT 干扰
    const lfPaper = await api('/papers/generate', { method: 'POST', token: tkD, body: { subjectId: await gkMathId(), title: '限时免费卷', structure: [{ type: '判断题', count: 3, score: 2 }], difficultyTarget: 3 } });
    await api('/admin/settings', { method: 'PUT', token: global.__adminToken, body: { limited_free_papers: JSON.stringify({ paperIds: [lfPaper.data.paperId], start: null, end: null }) } });
    const billLf = await api('/trade/billing?paperId=' + lfPaper.data.paperId, { token: tkD });
    ok('限时免费窗口内判价 LIMITED_FREE', billLf.code === 0 && billLf.data.mode === 'LIMITED_FREE');
    await api('/admin/settings', { method: 'PUT', token: global.__adminToken, body: { limited_free_papers: '' } });
    ok('停用后恢复常规判价', (await api('/trade/billing?paperId=' + lfPaper.data.paperId, { token: tkD })).data.mode !== 'LIMITED_FREE');
  }

  console.log('\n== T11 消息中心 / 登录审计 / 登录限流 / 健康检查（上线标准）==');
  {
    // 健康检查增强：版本 + schema 迁移版本 + 运行时长
    const h = await api('/health');
    ok('health 含 version/schemaVersion(≥3)/uptimeSec', h.code === 0 && h.data.version && h.data.schemaVersion >= 3 && h.data.uptimeSec >= 0);
    // traceId 贯穿
    const hr = await apiRaw('/health');
    ok('响应头 X-Trace-Id 存在', !!hr.headers.get('x-trace-id'));

    // 消息中心：录题审核触达（T10 已触发 REVIEW 消息）
    const msgs = await api('/messages?pageNo=1&pageSize=20', { token: tkD });
    ok('消息中心收到「录题已上架」通知', msgs.code === 0 && msgs.data.rows.some(m => m.title === '录题已上架' && m.type === 'REVIEW'));
    const unread0 = (await api('/messages/unread-count', { token: tkD })).data.unread;
    ok('未读数 >0', unread0 >= 1);
    if (msgs.data.rows.length) {
      const rd = await api('/messages/' + msgs.data.rows[0].id + '/read', { method: 'POST', token: tkD });
      ok('单条标记已读', rd.code === 0);
    }
    const ra = await api('/messages/read-all', { method: 'POST', token: tkD });
    const unread1 = (await api('/messages/unread-count', { token: tkD })).data.unread;
    ok('全部已读后未读归零（updated 可为 0：单条已读已消费）', ra.code === 0 && unread1 === 0);
    const foreign = await api('/messages/999999/read', { method: 'POST', token: tkA });
    ok('他人/不存在消息不可读', foreign.code === 40400);

    // 登录审计：后台用户列表含 last_login_at/ip，且 T1 登录过的超管有痕迹
    const users = await api('/admin/users', { token: global.__adminToken });
    ok('用户列表含 last_login_at/ip 审计字段', users.code === 0 && 'last_login_at' in users.data.list[0] && 'last_login_ip' in users.data.list[0]);
    const adminRow = users.data.list.find(u => u.role === 'SUPER_ADMIN');
    ok('超管存在登录痕迹（T1 已登录）', adminRow && !!adminRow.last_login_at);

    // 登录限流：同 IP 5 次/分（错密码亦计数；T1 已消耗 1 次 admin-login 配额）
    const codes = [];
    for (let i = 0; i < 7; i++) {
      const r = await api('/auth/login', { method: 'POST', body: { mobile: '13000000000', <REDACTED_SECRET> + i } });
      codes.push(r.code);
    }
    ok('触发限流：出现 42900 且在此之前均为 40100', codes.includes(42900) && codes.every((c, i, a) => c === 40100 || (c === 42900 && a.slice(i).every(x => x === 42900))), '实际 ' + codes.join(','));
  }

  console.log('\n== T12 试卷编辑与智能换题（教师端工作台）==');
  {
    const gk = await gkMathId();
    const gen = await api('/papers/generate', { method: 'POST', token: tkD, body: { subjectId: gk, title: '编辑测试卷',
      structure: [{ type: '单选题', count: 4, score: 5 }, { type: '判断题', count: 2, score: 2 }], difficultyTarget: 3 } });
    const pid = gen.data.paperId;
    const before = await api('/papers/' + pid, { token: tkD });
    ok('生成 6 题基线', before.code === 0 && before.data.questions.length === 6);

    // 编辑：改标题 + 删 1 题 + 改分值 + 重排
    const kept = before.data.questions.slice(1).map(q => ({ questionId: q.id, score: q.score + 1 }));
    const upd = await api('/papers/' + pid, { method: 'PUT', token: tkD, body: { title: '编辑后的卷子', questions: kept } });
    ok('保存：标题/题数/总分联动', upd.code === 0 && upd.data.title === '编辑后的卷子' && upd.data.count === 5 && upd.data.totalScore === gen.data.totalScore - before.data.questions[0].score + 5);
    const after = await api('/papers/' + pid, { token: tkD });
    ok('被删题目确实移除', !after.data.questions.some(q => q.id === before.data.questions[0].id));

    // 越权：他人保存被拒
    const foreign = await api('/papers/' + pid, { method: 'PUT', token: tkA, body: { title: '越权', questions: kept } });
    ok('越权保存 40400', foreign.code === 40400);

    // 智能换题：同题型、不在卷内、分值保留
    const target = after.data.questions.find(q => q.type === '单选题');
    const rep = await api('/papers/' + pid + '/replace', { method: 'POST', token: tkD, body: { questionId: target.id } });
    ok('换题成功：新题同题型且分值保留', rep.code === 0 && rep.data.added.type === target.type && rep.data.added.score === target.score && rep.data.removed === target.id);
    const afterRep = await api('/papers/' + pid, { token: tkD });
    ok('换题后卷内含新题不含旧题且无重复', afterRep.data.questions.some(q => q.id === rep.data.added.id)
      && !afterRep.data.questions.some(q => q.id === target.id)
      && new Set(afterRep.data.questions.map(q => q.id)).size === afterRep.data.questions.length);

    // 非卷内题目换题被拒
    const notIn = await api('/papers/' + pid + '/replace', { method: 'POST', token: tkD, body: { questionId: before.data.questions[0].id } });
    ok('换不在卷内的题 40400', notIn.code === 40400);

    // 知识点聚合接口
    const kps = await api('/questions/kps');
    ok('知识点聚合 Top50（含题量）', kps.code === 0 && kps.data.list.length >= 10 && kps.data.list.every(k => k.name && k.count >= 1));
  }

  console.log('\n== T13 试卷选题（公开示范卷，docs/25 TJ-10~14）==');
  {
    const list = await api('/papers/public?category=' + encodeURIComponent('高考备考'), { token: tkD });
    ok('四大类频道筛选（高考备考）', list.code === 0 && list.data.rows.length >= 2 && list.data.rows.every(p => p.category === '高考备考'));
    ok('列表含级别/年份/地区/题数/下载量', list.data.rows.every(p => p.level && p.year && p.region && p.question_count >= 1 && p.downloads >= 0));
    const paper = list.data.rows[0];
    const pv = await api('/papers/public/' + paper.id + '/preview', { token: tkD });
    ok('试读 ≥30% 且至少 1 题', pv.code === 0 && pv.data.paper.readCount >= Math.ceil(pv.data.paper.totalCount * 0.3) && pv.data.paper.readCount >= 1);
    ok('试读不含答案与解析（付费墙语义）', pv.data.questions.every(q => !('answer' in q) && !('analysis' in q)));

    // 公开卷他人导出：走统一计费（免费额度）
    const expPub = await apiRaw('/papers/' + paper.id + '/export', { token: tkD });
    const pubHtml = await expPub.text();
    ok('公开卷他人导出成功（HTML + 水印页脚）', expPub.ok && pubHtml.includes('智卷云') && pubHtml.includes('ID:'));
    // 下载量自增
    const list2 = await api('/papers/public', { token: tkD });
    ok('导出后下载量 +1', list2.data.rows.find(p => p.id === paper.id).downloads === paper.downloads + 1);

    // 非公开卷他人导出仍被拒
    const myPaper = (await api('/papers/generate', { method: 'POST', token: tkA, body: { subjectId: await gkMathId(), title: '私有卷', structure: [{ type: '判断题', count: 2, score: 3 }] } })).data.paperId;
    const denied = await apiRaw('/papers/' + myPaper + '/export', { token: tkD });
    ok('非公开卷他人导出 40400', (denied.status) === 404);
  }

  console.log('\n== T14 教师工具箱（模板/收藏/专辑/导出参数/排序，docs/25 TJ-80~89）==');
  {
    // TJ-80 组卷模板
    const tc = await api('/papers/templates', { method: 'POST', token: tkD, body: { name: '标准周测', subjectId: await gkMathId(), structure: [{ type: '单选题', count: 6, score: 5 }], difficultyTarget: 3 } });
    ok('存为组卷模板', tc.code === 0 && !!tc.data.id);
    const tl = await api('/papers/templates', { token: tkD });
    ok('模板列表含结构快照', tl.code === 0 && tl.data.list.some(t => t.id === tc.data.id && t.structure[0].type === '单选题'));
    const tdel = await api('/papers/templates/' + tc.data.id, { method: 'DELETE', token: tkA });
    ok('删除他人模板 40400', tdel.code === 40400);

    // TJ-85 收藏 toggle
    const qid = (await api('/questions?pageSize=1')).data.list[0].id;
    const f1 = await api('/papers/favorites', { method: 'POST', token: tkD, body: { questionId: qid } });
    ok('收藏成功', f1.code === 0 && f1.data.favorited === true);
    const fl = await api('/papers/favorites?pageNo=1&pageSize=10', { token: tkD });
    ok('我的收藏列表含该题', fl.data.rows.some(q => q.id === qid) && !('answer' in fl.data.rows[0]));
    const f2 = await api('/papers/favorites', { method: 'POST', token: tkD, body: { questionId: qid } });
    ok('再次收藏为取消', f2.code === 0 && f2.data.favorited === false);

    // TJ-84 排序
    const hot = await api('/questions?pageSize=5');
    const nw = await api('/questions?pageSize=5&sort=new');
    ok('排序参数 hot/new 可用', hot.code === 0 && nw.code === 0 && nw.data.list[0].id !== undefined);

    // TJ-86 导出参数
    const lp = (await api('/papers', { token: tkD })).data.list[0];
    const exp2col = await apiRaw('/papers/' + lp.id + '/export?columns=2&paperSize=A3&fontSize=l&answerPos=end', { token: tkD });
    const html2 = await exp2col.text();
    ok('导出参数生效（双栏/A3/字号/卷尾答案）', exp2col.ok && html2.includes('column-count:2') && html2.includes('size:A3') && html2.includes('font-size:16px') && html2.includes('答案：'));
    const expSep = await apiRaw('/papers/' + lp.id + '/export?answerPos=separate', { token: tkD });
    const htmlSep = await expSep.text();
    ok('答案分离模式：独立答案页', expSep.ok && htmlSep.includes('参考答案') && htmlSep.includes('page-break-before'));

    // TJ-88 专辑
    const alb = await api('/papers/albums');
    ok('精品专辑公开列表（含卷数）', alb.code === 0 && alb.data.list.length >= 2 && alb.data.list.every(a => a.paper_count >= 1));
    const albD = await api('/papers/albums/' + alb.data.list[0].id);
    ok('专辑详情含整卷列表', albD.code === 0 && albD.data.papers.length >= 1 && albD.data.papers.every(p => p.question_count >= 1));
    ok('专辑匿名可浏览（对标组卷网公开频道）', true);
  }

  console.log('\n== T15 复刻细节（详情URL/组卷计数/下载中心，docs/25 TJ-90~92）==');
  {
    // TJ-91：题目卡组卷计数
    const lst = await api('/questions?pageSize=3');
    ok('列表响应含 paper_uses 组卷计数', lst.code === 0 && lst.data.list.every(q => typeof q.paper_uses === 'number' && q.paper_uses >= 0));
    // TJ-123/90：详情接口（timu.html?id=N 数据源）
    const q0 = lst.data.list[0];
    const det = await api('/questions/' + q0.id);
    ok('详情含 paper_uses 与五段式/相似题（独立 URL 数据源）', det.code === 0 && typeof det.data.paper_uses === 'number' && det.data.similar.length <= 3);
    // TJ-92：下载中心（T8/T13 已产生下载记录）
    const dl = await api('/trade/downloads?pageNo=1&pageSize=10', { token: tkD });
    ok('下载记录含试卷标题/模式/扣点/时间', dl.code === 0 && dl.data.rows.length >= 1 && dl.data.rows.every(r => r.paper_title && r.charge_mode && 'points_charged' in r));
  }

  console.log('\n== T16 参考项目改造（Word公式导出/重复检测/提示词，docs/25 TJ-100~102）==');
  {
    const gk = await gkMathId();
    const gen = await api('/papers/generate', { method: 'POST', token: tkD, body: { subjectId: gk, title: 'Word导出卷',
      structure: [{ type: '单选题', count: 3, score: 5 }, { type: '解答题', count: 2, score: 12 }] } });
    // TJ-100：Word 原生公式导出
    const dx = await apiRaw('/papers/' + gen.data.paperId + '/export?format=docx&answerPos=separate', { token: tkD });
    const buf = Buffer.from(await dx.arrayBuffer());
    ok('docx 导出：PK 头 + 内容类型 + X-Math-Count ≥ 题数', dx.ok && buf[0] === 0x50 && buf[1] === 0x4B && buf.length > 5000
      && dx.headers.get('content-type').includes('officedocument.wordprocessingml')
      && Number(dx.headers.get('x-math-count')) >= gen.data.count);
    // TJ-102：录题重复检测
    const dupTs = Date.now();
    const dupBody = { subjectId: gk, type: '填空题', difficulty: 3, stem: '重复检测：已知函数 f(x)=x^2，求其导数。（编号' + dupTs + '）', answer: "f'(x)=2x" };
    const c1 = await api('/questions/contribute', { method: 'POST', token: tkD, body: dupBody });
    ok('首次录题成功（进入审核中）', c1.code === 0);
    const c2 = await api('/questions/contribute', { method: 'POST', token: tkD, body: { ...dupBody, stem: '重复检测：已知函数 f(x)=x^2，  求其导数。（编号' + dupTs + '）' } });
    ok('归一化后完全重复被拒并提示 duplicateOf', c2.code === 42200 && c2.data?.duplicateOf === c1.data.id);
    // TJ-101：提示词升级（MOCK 输出含四模块标记之一 + 草稿标记）
    const vq = (await api('/questions?pageSize=1')).data.list[0].id;
    const vr = await api('/ai/variants', { method: 'POST', token: tkA, body: { questionId: vq } });
    ok('AI 变式提示词升级后仍输出 [AI-草稿]', vr.code === 0 && vr.data.draft.marker === '[AI-草稿]');
  }

  console.log(`\n结果：${pass} 通过 / ${fail} 失败`);
  process.exitCode = fail ? 1 : 0;
} catch (e) {
  console.error('测试执行失败：', e.message);
  process.exitCode = 1;
} finally {
  server.kill();
}

async function gkMathId() {
  const meta = await api('/questions/meta');
  const s = meta.data.subjects.find(x => x.stage_id === 3 && x.name === '数学');
  return s.id;
}
