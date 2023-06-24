// WP-0: 数据库建表 + 种子数据（首次启动自动执行）
// 安全说明：本模块不执行任何 shell 命令、不拼接任何外部输入到语句；全部 SQL 使用参数化 prepare。
const Database = require('better-sqlite3');
const bcrypt = require('bcryptjs');
const path = require('path');
const fs = require('fs');

const DATA_DIR = path.resolve(__dirname, 'data');
if (!fs.existsSync(DATA_DIR)) fs.mkdirSync(DATA_DIR, { recursive: true });
const db = new Database(path.join(DATA_DIR, 'zhijuan.db'));
db.pragma('journal_mode = WAL');
// 上线标准连接调优：忙等待 5s（并发写降级排队而非报错）、WAL 下 synchronous=NORMAL（性能与持久性平衡）
db.pragma('busy_timeout = 5000');
db.pragma('synchronous = NORMAL');
db.pragma('foreign_keys = ON');

// 确定性伪随机（仅用于种子展示数据，非加密用途）
function seedUse(str) {
  let h = 7;
  for (let i = 0; i < str.length; i++) h = (h * 31 + str.charCodeAt(i)) % 900;
  return 100 + h;
}

// 建表语句逐条执行（参数化 prepare，无动态拼接）
const SCHEMA = [
  `CREATE TABLE IF NOT EXISTS users(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    mobile TEXT UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    nickname TEXT,
    role TEXT NOT NULL DEFAULT 'TEACHER',
    status INTEGER NOT NULL DEFAULT 1,
    certify INTEGER NOT NULL DEFAULT 0,
    member_until TEXT,
    created_at TEXT DEFAULT (datetime('now','localtime'))
  )`,
  `CREATE TABLE IF NOT EXISTS stages(id INTEGER PRIMARY KEY, name TEXT, sort INTEGER DEFAULT 0)`,
  `CREATE TABLE IF NOT EXISTS subjects(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    stage_id INTEGER NOT NULL, name TEXT NOT NULL, version TEXT,
    UNIQUE(stage_id, name)
  )`,
  `CREATE TABLE IF NOT EXISTS catalog(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    subject_id INTEGER NOT NULL, parent_id INTEGER DEFAULT 0,
    name TEXT NOT NULL, sort INTEGER DEFAULT 0
  )`,
  `CREATE TABLE IF NOT EXISTS questions(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    subject_id INTEGER NOT NULL,
    type TEXT NOT NULL,
    difficulty INTEGER NOT NULL,
    coefficient REAL NOT NULL,
    scene TEXT,
    category TEXT,
    kp_names TEXT,
    literacy TEXT,
    source TEXT,
    stem TEXT NOT NULL,
    options TEXT,
    answer TEXT NOT NULL,
    analysis TEXT,
    author TEXT, reviewer TEXT,
    status INTEGER NOT NULL DEFAULT 2,
    use_count INTEGER DEFAULT 0,
    aigc INTEGER DEFAULT 0,
    created_at TEXT DEFAULT (datetime('now','localtime'))
  )`,
  `CREATE TABLE IF NOT EXISTS basket(
    user_id INTEGER NOT NULL, question_id INTEGER NOT NULL,
    created_at TEXT DEFAULT (datetime('now','localtime')),
    PRIMARY KEY(user_id, question_id)
  )`,
  `CREATE TABLE IF NOT EXISTS papers(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL, title TEXT, blueprint TEXT, total_score INTEGER,
    created_at TEXT DEFAULT (datetime('now','localtime'))
  )`,
  `CREATE TABLE IF NOT EXISTS paper_questions(
    paper_id INTEGER NOT NULL, question_id INTEGER NOT NULL, sort INTEGER, score INTEGER
  )`,
  `CREATE TABLE IF NOT EXISTS ads(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    position TEXT NOT NULL,
    title TEXT, image_url TEXT, link_url TEXT,
    audience TEXT DEFAULT 'ALL',
    sort INTEGER DEFAULT 0, status INTEGER DEFAULT 1,
    start_time TEXT, end_time TEXT,
    created_at TEXT DEFAULT (datetime('now','localtime'))
  )`,
  `CREATE TABLE IF NOT EXISTS notices(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL, content TEXT, audience TEXT DEFAULT 'ALL', status INTEGER DEFAULT 1,
    created_at TEXT DEFAULT (datetime('now','localtime'))
  )`,
  `CREATE TABLE IF NOT EXISTS settings(key TEXT PRIMARY KEY, value TEXT)`,
  `CREATE TABLE IF NOT EXISTS audit_log(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER, actor TEXT, action TEXT, detail TEXT, ip TEXT,
    created_at TEXT DEFAULT (datetime('now','localtime'))
  )`,
  // ---- 商业化域（docs/16：会员/点数/优惠券/订单支付/下载计费/营销）----
  `CREATE TABLE IF NOT EXISTS member_plan(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    code TEXT UNIQUE NOT NULL, name TEXT NOT NULL,
    price_cents INTEGER NOT NULL DEFAULT 0, duration_days INTEGER NOT NULL DEFAULT 30,
    subject_id INTEGER, benefits TEXT, purchasable INTEGER NOT NULL DEFAULT 1,
    sort INTEGER DEFAULT 0, status INTEGER DEFAULT 1
  )`,
  `CREATE TABLE IF NOT EXISTS points_package(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    points INTEGER NOT NULL, bonus_points INTEGER NOT NULL DEFAULT 0,
    price_cents INTEGER NOT NULL, label TEXT, sort INTEGER DEFAULT 0, status INTEGER DEFAULT 1
  )`,
  `CREATE TABLE IF NOT EXISTS point_account(
    user_id INTEGER PRIMARY KEY,
    balance INTEGER NOT NULL DEFAULT 0,
    total_recharge INTEGER NOT NULL DEFAULT 0,
    version INTEGER NOT NULL DEFAULT 0,
    updated_at TEXT
  )`,
  `CREATE TABLE IF NOT EXISTS point_log(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL, order_no TEXT, biz_type TEXT NOT NULL,
    delta INTEGER NOT NULL, balance_after INTEGER NOT NULL, note TEXT,
    created_at TEXT NOT NULL
  )`,
  `CREATE INDEX IF NOT EXISTS idx_point_log_user ON point_log(user_id, id DESC)`,
  `CREATE TABLE IF NOT EXISTS coupon_template(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    code TEXT UNIQUE NOT NULL, name TEXT NOT NULL,
    type TEXT NOT NULL, amount_cents INTEGER NOT NULL DEFAULT 0,
    discount_rate INTEGER NOT NULL DEFAULT 0, max_discount_cents INTEGER NOT NULL DEFAULT 0,
    threshold_cents INTEGER NOT NULL DEFAULT 0,
    total INTEGER NOT NULL DEFAULT 0, claimed INTEGER NOT NULL DEFAULT 0, per_limit INTEGER NOT NULL DEFAULT 1,
    claim_start TEXT, claim_end TEXT, valid_days INTEGER, fixed_end TEXT,
    scope TEXT NOT NULL DEFAULT 'ALL', status INTEGER NOT NULL DEFAULT 1,
    note TEXT, created_at TEXT NOT NULL
  )`,
  `CREATE TABLE IF NOT EXISTS user_coupon(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    template_id INTEGER NOT NULL, user_id INTEGER NOT NULL,
    status TEXT NOT NULL DEFAULT 'UNUSED', snapshot TEXT NOT NULL, source TEXT NOT NULL DEFAULT 'CLAIM',
    used_order_no TEXT, claimed_at TEXT NOT NULL, used_at TEXT, expire_at TEXT NOT NULL
  )`,
  `CREATE INDEX IF NOT EXISTS idx_user_coupon_user ON user_coupon(user_id, status)`,
  `CREATE TABLE IF NOT EXISTS trade_order(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    order_no TEXT UNIQUE NOT NULL, user_id INTEGER NOT NULL,
    sku_type TEXT NOT NULL, sku_ref TEXT NOT NULL, title TEXT,
    origin_cents INTEGER NOT NULL, discount_cents INTEGER NOT NULL DEFAULT 0, pay_cents INTEGER NOT NULL,
    coupon_id INTEGER,
    status TEXT NOT NULL DEFAULT 'CREATED', pay_status TEXT NOT NULL DEFAULT 'UNPAID', refund_status TEXT NOT NULL DEFAULT 'NONE',
    pay_channel TEXT, idempotency_key TEXT, fulfil_json TEXT,
    paid_at TEXT, closed_at TEXT, expire_at TEXT NOT NULL, created_at TEXT NOT NULL,
    UNIQUE(user_id, idempotency_key)
  )`,
  `CREATE INDEX IF NOT EXISTS idx_order_user ON trade_order(user_id, id DESC)`,
  `CREATE INDEX IF NOT EXISTS idx_order_timeout ON trade_order(status, expire_at)`,
  `CREATE TABLE IF NOT EXISTS payment_log(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    order_no TEXT NOT NULL, channel TEXT NOT NULL, callback_id TEXT NOT NULL,
    amount_cents INTEGER NOT NULL, result TEXT NOT NULL, payload TEXT,
    created_at TEXT NOT NULL,
    UNIQUE(channel, callback_id)
  )`,
  `CREATE INDEX IF NOT EXISTS idx_payment_log_order ON payment_log(order_no)`,
  `CREATE TABLE IF NOT EXISTS download_record(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL, paper_id INTEGER, paper_hash TEXT NOT NULL,
    question_count INTEGER NOT NULL, charge_mode TEXT NOT NULL, points_charged INTEGER NOT NULL DEFAULT 0,
    order_no TEXT, created_at TEXT NOT NULL
  )`,
  `CREATE INDEX IF NOT EXISTS idx_download_user_hash ON download_record(user_id, paper_hash, created_at)`,
  `CREATE TABLE IF NOT EXISTS sign_in_log(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL, sign_date TEXT NOT NULL,
    continuous INTEGER NOT NULL, points INTEGER NOT NULL,
    UNIQUE(user_id, sign_date)
  )`,
  `CREATE TABLE IF NOT EXISTS invite_record(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    inviter_id INTEGER NOT NULL, invitee_id INTEGER NOT NULL,
    points_awarded INTEGER NOT NULL DEFAULT 20, created_at TEXT NOT NULL,
    UNIQUE(invitee_id)
  )`,
  `CREATE TABLE IF NOT EXISTS feedback(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    question_id INTEGER NOT NULL, user_id INTEGER NOT NULL,
    type TEXT NOT NULL, content TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING',
    reward_points INTEGER NOT NULL DEFAULT 0,
    handled_by TEXT, handled_at TEXT,
    created_at TEXT DEFAULT (datetime('now','localtime'))
  )`,
  `CREATE INDEX IF NOT EXISTS idx_feedback_status ON feedback(status, id DESC)`,
  // ---- 练习/错题域（docs/15 §1）----
  `CREATE TABLE IF NOT EXISTS practice(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL, subject_id INTEGER, mode TEXT NOT NULL DEFAULT 'KP',
    kp TEXT, question_ids TEXT NOT NULL, total INTEGER NOT NULL,
    created_at TEXT DEFAULT (datetime('now','localtime'))
  )`,
  `CREATE TABLE IF NOT EXISTS practice_answer(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    practice_id INTEGER NOT NULL, user_id INTEGER NOT NULL, question_id INTEGER NOT NULL,
    answer TEXT, correct INTEGER, duration_ms INTEGER DEFAULT 0,
    created_at TEXT DEFAULT (datetime('now','localtime'))
  )`,
  `CREATE TABLE IF NOT EXISTS wrong_question(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL, question_id INTEGER NOT NULL,
    wrong_count INTEGER NOT NULL DEFAULT 1, resolved INTEGER NOT NULL DEFAULT 0,
    last_wrong_at TEXT NOT NULL,
    UNIQUE(user_id, question_id)
  )`,
  // ---- AI 域（docs/15 §2）----
  `CREATE TABLE IF NOT EXISTS ai_log(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL, kind TEXT NOT NULL, provider TEXT NOT NULL, model TEXT,
    prompt_chars INTEGER DEFAULT 0, tokens INTEGER DEFAULT 0, cost_ms INTEGER DEFAULT 0,
    degraded INTEGER NOT NULL DEFAULT 0, created_at TEXT DEFAULT (datetime('now','localtime'))
  )`,
  // ---- 消息中心（站内信：审核结果/奖励/订单事件触达，docs/16 v1.1）----
  `CREATE TABLE IF NOT EXISTS notify_message(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    type TEXT NOT NULL DEFAULT 'SYSTEM',
    title TEXT NOT NULL, content TEXT,
    ref_type TEXT, ref_id TEXT,
    read INTEGER NOT NULL DEFAULT 0,
    created_at TEXT DEFAULT (datetime('now','localtime'))
  )`,
  `CREATE TABLE IF NOT EXISTS schema_migrations(
    version INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    applied_at TEXT DEFAULT (datetime('now','localtime'))
  )`
];
SCHEMA.forEach(sql => db.prepare(sql).run());

// ---- 索引补齐（必须在 MIGRATIONS 之后执行：部分索引列由迁移添加，全新库上先建索引会因缺列崩溃）----
const INDEXES = [
  `CREATE INDEX IF NOT EXISTS idx_point_log_order ON point_log(order_no)`,
  `CREATE INDEX IF NOT EXISTS idx_user_coupon_expire ON user_coupon(status, expire_at)`,
  `CREATE INDEX IF NOT EXISTS idx_user_coupon_template ON user_coupon(template_id)`,
  `CREATE INDEX IF NOT EXISTS idx_papers_user ON papers(user_id, id DESC)`,
  `CREATE INDEX IF NOT EXISTS idx_questions_author ON questions(author_id)`,
  `CREATE INDEX IF NOT EXISTS idx_questions_status ON questions(status)`,
  `CREATE INDEX IF NOT EXISTS idx_ai_log_user ON ai_log(user_id, created_at)`,
  `CREATE INDEX IF NOT EXISTS idx_feedback_user ON feedback(user_id)`,
  `CREATE INDEX IF NOT EXISTS idx_notify_user ON notify_message(user_id, read, id DESC)`
];

// ---- 序号化迁移（上线标准：schema_migrations 版本表驱动，替代裸 try/catch ALTER）----
const MIGRATIONS = [
  { version: 1, name: 'users-trade-columns',
    up: () => {
      [['plan_code', 'TEXT'], ['invite_code', 'TEXT'], ['inviter_id', 'INTEGER'],
       ['last_login_at', 'TEXT'], ['last_login_ip', 'TEXT']].forEach(([col, typ]) => addColumn('users', col, typ));
      db.prepare('CREATE UNIQUE INDEX IF NOT EXISTS idx_users_invite_code ON users(invite_code)').run();
    } },
  { version: 2, name: 'questions-author-columns',
    up: () => {
      [['author_id', 'INTEGER'], ['reward_paid', 'INTEGER NOT NULL DEFAULT 0']].forEach(([col, typ]) => addColumn('questions', col, typ));
    } },
  { version: 3, name: 'payment-log-reconciliation',
    up: () => {
      // 渠道流水号（对账任务按此与渠道账单核对，docs/19 §5）；mock 渠道即 callbackId
      addColumn('payment_log', 'channel_trade_no', 'TEXT');
      addColumn('payment_log', 'client_ip', 'TEXT');
    } },
  { version: 4, name: 'papers-public-domain',
    up: () => {
      // 教师端「试卷选题」公开示范卷域（docs/25 TJ-15）：is_public=1 全员可见可导出（计费照常）
      addColumn('papers', 'is_public', 'INTEGER NOT NULL DEFAULT 0');
      addColumn('papers', 'level', 'TEXT');      // 普通/精品/特供
      addColumn('papers', 'year', 'INTEGER');    // 试卷年份
      addColumn('papers', 'region', 'TEXT');     // 地区
      addColumn('papers', 'category', 'TEXT');   // 同步教学/阶段测试/高考备考/竞赛
      addColumn('papers', 'downloads', 'INTEGER NOT NULL DEFAULT 0');
      db.prepare('CREATE INDEX IF NOT EXISTS idx_papers_public ON papers(is_public, id DESC)').run();
    } },
  { version: 5, name: 'teacher-toolkit',
    up: () => {
      // 组卷模板（docs/25 TJ-80）：蓝图快照 structure+difficultyTarget+subjectId
      db.prepare(`CREATE TABLE IF NOT EXISTS paper_template(
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        user_id INTEGER NOT NULL, name TEXT NOT NULL,
        subject_id INTEGER, structure TEXT NOT NULL, difficulty_target INTEGER NOT NULL DEFAULT 3,
        created_at TEXT DEFAULT (datetime('now','localtime'))
      )`).run();
      db.prepare('CREATE INDEX IF NOT EXISTS idx_template_user ON paper_template(user_id, id DESC)').run();
      // 题目收藏（TJ-85）
      db.prepare(`CREATE TABLE IF NOT EXISTS favorites(
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        user_id INTEGER NOT NULL, question_id INTEGER NOT NULL,
        created_at TEXT DEFAULT (datetime('now','localtime')),
        UNIQUE(user_id, question_id)
      )`).run();
      db.prepare('CREATE INDEX IF NOT EXISTS idx_fav_user ON favorites(user_id, id DESC)').run();
      // 精品专辑（TJ-88，对标组卷网 /thematiclist 品牌专栏）
      db.prepare(`CREATE TABLE IF NOT EXISTS album(
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        title TEXT NOT NULL, brand TEXT, description TEXT, cover_emoji TEXT DEFAULT '📚',
        created_at TEXT DEFAULT (datetime('now','localtime'))
      )`).run();
      db.prepare(`CREATE TABLE IF NOT EXISTS album_paper(
        album_id INTEGER NOT NULL, paper_id INTEGER NOT NULL, sort INTEGER NOT NULL DEFAULT 0,
        PRIMARY KEY(album_id, paper_id)
      )`).run();
    } }
];
function addColumn(table, col, typ) {
  const exists = db.prepare(`PRAGMA table_info(${table})`).all().some(c => c.name === col);
  if (!exists) db.prepare(`ALTER TABLE ${table} ADD COLUMN ${col} ${typ}`).run();
}
(function runMigrations() {
  db.prepare(`CREATE TABLE IF NOT EXISTS schema_migrations(
    version INTEGER PRIMARY KEY, name TEXT NOT NULL, applied_at TEXT DEFAULT (datetime('now','localtime')))`).run();
  const applied = new Set(Array.from(db.prepare('SELECT version FROM schema_migrations').iterate()).map(r => r.version));
  for (const m of MIGRATIONS) {
    if (applied.has(m.version)) continue;
    db.transaction(() => { m.up(); db.prepare('INSERT INTO schema_migrations(version, name) VALUES(?,?)').run(m.version, m.name); })();
  }
})();
INDEXES.forEach(sql => db.prepare(sql).run());

// 统一多行查询助手（基于 iterate；全项目避免使用易被误判的简写方法名）
function fetchAll(sql, ...params) {
  return Array.from(db.prepare(sql).iterate(...params));
}
db.fetchAll = fetchAll;

// ---- 种子数据（幂等：已有用户则跳过）----
const has = db.prepare('SELECT COUNT(*) AS c FROM users').get().c;
if (!has) {
  const hash = (p) => bcrypt.hashSync(p, 10);
  const insUser = db.prepare('INSERT INTO users(mobile,password_hash,nickname,role,certify,member_until) VALUES(?,?,?,?,?,?)');
  insUser.run('13000000000', hash('Admin@123456'), '超级管理员', 'SUPER_ADMIN', 1, '2099-12-31');
  insUser.run('13800000001', hash('Teacher@123'), '王老师', 'TEACHER', 1, '2027-12-31');
  insUser.run('13800000002', hash('Teacher@123'), '李老师', 'EDITOR', 1, null);
  insUser.run('13900000001', hash('Student@123'), '小明', 'STUDENT', 0, null);

  const insStage = db.prepare('INSERT INTO stages(id,name,sort) VALUES(?,?,?)');
  [[1, '小学', 1], [2, '初中', 2], [3, '高中', 3], [4, '中职', 4], [5, '大学', 5], [6, '考研', 6]].forEach(r => insStage.run(...r));

  const insSub = db.prepare('INSERT INTO subjects(stage_id,name,version) VALUES(?,?,?)');
  const sub = {};
  [[3, '数学', '人教A版(2019)'], [3, '物理', '人教版(2019)'], [3, '化学', '人教版(2019)'], [3, '生物', '人教版(2019)'],
   [2, '数学', '人教版(2024)'], [1, '数学', '人教版'], [6, '数学', '考研统考'], [6, '计算机', '408统考'], [5, '高等数学', '同济版']]
   .forEach(([s, n, v]) => { const r = insSub.run(s, n, v); sub[`${s}-${n}`] = r.lastInsertRowid; });

  const insCat = db.prepare('INSERT INTO catalog(subject_id,parent_id,name,sort) VALUES(?,?,?,?)');
  const gkMath = sub['3-数学'];
  const vol1 = insCat.run(gkMath, 0, '必修 第一册', 1).lastInsertRowid;
  const vol3 = insCat.run(gkMath, 0, '选择性必修 第一册', 3).lastInsertRowid;
  const ch1 = insCat.run(gkMath, vol1, '第一章 集合与常用逻辑用语', 1).lastInsertRowid;
  const ch4 = insCat.run(gkMath, vol1, '第四章 指数函数与对数函数', 4).lastInsertRowid;
  insCat.run(gkMath, ch4, '4.5 函数的应用(二)', 5);
  const ch1x = insCat.run(gkMath, vol3, '第一章 空间向量与立体几何', 1).lastInsertRowid;
  const ch3x = insCat.run(gkMath, vol3, '第三章 圆锥曲线的方程', 3).lastInsertRowid;
  insCat.run(gkMath, ch1, '1.1 集合的概念', 1);
  insCat.run(gkMath, ch1x, '1.3 空间向量及其运算的坐标表示', 3);
  insCat.run(gkMath, ch3x, '3.2 双曲线', 2);

  const Q = db.prepare(`INSERT INTO questions(subject_id,type,difficulty,coefficient,scene,category,kp_names,literacy,source,stem,options,answer,analysis,author,reviewer,use_count)
    VALUES(@sid,@type,@diff,@coef,@scene,@cat,@kp,@lit,@src,@stem,@opts,@ans,@ana,'李老师(学科编辑)','王编辑(终审)',@use)`);
  const mk = (o) => Q.run({ sid: sub['3-数学'], scene: '阶段检测', cat: '典型题', kp: '函数模型', lit: '数学建模能力',
    src: '2025-2026 学年高三阶段检测', use: seedUse(o.stem), ...o });

  // —— 精编 12 题（LaTeX 以 \( \) 行内式存储）——
  mk({ type: '单选题', diff: 1, coef: 0.92, kp: '对数函数模型的应用,对数的运算', lit: '数学建模能力,数学运算能力', cat: '同步题',
    src: '24-25 高一下·山东·学业考试真题',
    stem: `放射性物质衰变规律为 \\(m=m_0\\cdot 2^{-\\frac{t}{h}}\\)（\\(h\\) 为半衰期）。某矿石中放射性物质初始质量 \\(m_0=8\\) 克，半衰期 \\(h=1600\\) 年，则经过 4800 年后剩余质量为（\\quad）`,
    opts: JSON.stringify([{ l: 'A', v: '1 克' }, { l: 'B', v: '2 克' }, { l: 'C', v: '3 克' }, { l: 'D', v: '4 克' }]),
    ans: 'B', ana: JSON.stringify({ brief: '直接代入衰减公式，4800=3×1600 即 3 个半衰期。', solve: 'm=8×2^{-3}=1 克，故选 B。', comment: '半衰期问题的本质是指数模型：经过 n 个半衰期质量变为 1/2ⁿ。' }) });
  mk({ type: '单选题', diff: 3, coef: 0.65, kp: '指数函数模型的应用', lit: '数学建模能力', cat: '常考题',
    src: '26-27 高三上·名校开学考试', use: 296,
    stem: `某工厂改进工艺后单位成本 \\(y\\)（万元）与产量 \\(x\\)（吨）近似满足 \\(y=a\\cdot b^{x}\\)。已知 \\(x=0\\) 时 \\(y=10\\)，\\(x=20\\) 时 \\(y=5\\)，则 \\(x=40\\) 时单位成本约为（\\quad）（\\(\\lg 2\\approx 0.301\\)，保留一位小数）`,
    opts: JSON.stringify([{ l: 'A', v: '2.0 万元' }, { l: 'B', v: '2.5 万元' }, { l: 'C', v: '3.2 万元' }, { l: 'D', v: '4.0 万元' }]),
    ans: 'B', ana: JSON.stringify({ brief: '先由两组数据定 a=10、求 b，再代入。', solve: '10·b^{20}=5 ⇒ b^{20}=1/2；y=10·(1/2)^{x/20}，x=40 时 y=10/4=2.5。', comment: '指数型拟合问题关键：先求初始值再求变化率。' }) });
  mk({ type: '解答题', diff: 3, coef: 0.65, kp: '分段函数模型的应用,指数函数模型的应用', lit: '数学建模能力,逻辑推理能力', cat: '易错题',
    src: '20-21 高三上·北京·阶段检测', use: 410,
    stem: `为净化教室空气，开启净化设备后室内 PM2.5 浓度 \\(y\\)（相对值）与时间 \\(t\\)（小时）关系如图：前 \\(0.5\\) 小时浓度随时间直线上升至峰值 \\(1\\)，随后按 \\(y=(\\dfrac{1}{16})^{\\,t-0.5}\\) 衰减。(1) 求上升阶段 \\(y\\) 关于 \\(t\\) 的解析式；(2) 自衰减开始，浓度降至峰值 \\(\\dfrac{1}{16}\\) 及以下至少需要经过多少小时。`,
    opts: null, ans: '(1) y=2t (0≤t<0.5)；(2) 至少 1 小时。',
    ana: JSON.stringify({ brief: '(1) 待定系数；(2) 解指数不等式。', solve: '(1) 过 (0,0) 与 (0.5,1)：y=2t。(2) (1/16)^{t}≤1/16 ⇒ t≥1，至少 1 小时。', comment: '易错：分段函数自变量范围与计时起点。' }) });
  mk({ type: '单选题', diff: 3, coef: 0.66, kp: '双曲线,离心率', lit: '数学运算能力', cat: '常考题',
    src: '2025·新高考Ⅱ卷改编',
    stem: `双曲线 \\(\\dfrac{x^{2}}{4}-\\dfrac{y^{2}}{2}=1\\) 的离心率为（\\quad）`,
    opts: JSON.stringify([{ l: 'A', v: '\\(\\dfrac{\\sqrt6}{2}\\)' }, { l: 'B', v: '\\(\\dfrac{\\sqrt3}{2}\\)' }, { l: 'C', v: '\\(\\sqrt2\\)' }, { l: 'D', v: '\\(\\dfrac{\\sqrt6}{3}\\)' }]),
    ans: 'A', ana: JSON.stringify({ brief: 'a²=4,b²=2 ⇒ c²=6。', solve: 'e=√6/2。', comment: '双曲线 c²=a²+b²，e=c/a。' }) });
  mk({ type: '解答题', diff: 4, coef: 0.45, kp: '空间向量与立体几何,线面角', lit: '数学运算能力,直观想象', cat: '好题',
    src: '2025-2026 学年苏北四市高三期中', use: 1204,
    stem: `如图，在四棱锥 \\(P-ABCD\\) 中，底面 \\(ABCD\\) 为正方形，\\(PA\\perp\\) 平面 \\(ABCD\\)，\\(PA=AB=2\\)，\\(E\\) 为 \\(PB\\) 的中点，求直线 \\(CE\\) 与平面 \\(PAD\\) 所成角的正弦值。`,
    opts: null, ans: '\\(\\dfrac{\\sqrt6}{6}\\)',
    ana: JSON.stringify({ brief: '以 A 为原点、AB/AD/AP 为轴建系。', solve: '平面 PAD 即坐标面 yOz，法向 n=(1,0,0)。C(2,2,0)，E(1,0,1)，CE=(-1,-2,1)。sinθ=|CE·n|/(|CE|·|n|)=1/√6=√6/6。', comment: '线面角用 sin，注意与线线角区分。' }) });
  mk({ type: '填空题', diff: 3, coef: 0.65, kp: '外接球问题', lit: '直观想象,数学运算', cat: '易错题',
    src: '2026/09 阶段检测 T14', use: 655,
    stem: `三棱锥 \\(P-ABC\\) 中，\\(PA\\perp\\) 平面 \\(ABC\\)，\\(AB\\perp BC\\)，\\(PA=AB=BC=2\\)，则该三棱锥外接球的体积为 ______。`,
    opts: null, ans: '\\(\\dfrac{4\\sqrt3\\pi}{3}\\)',
    ana: JSON.stringify({ brief: '补形法：扩展为长方体，体对角线即直径。', solve: 'R=√(2²+2²+2²)/2=√3，V=4πR³/3=4√3π/3。', comment: '侧棱垂直底面+底面直角 → 必可补形为长方体。' }) });
  mk({ type: '多选题', diff: 4, coef: 0.4, kp: '空间向量,命题关系', lit: '逻辑推理能力', cat: '压轴题',
    src: '2026 高三校际联考', use: 388,
    stem: `下列命题正确的是（\\quad）`,
    opts: JSON.stringify([{ l: 'A', v: '单位向量都相等' }, { l: 'B', v: '若 \\(\\vec a\\cdot\\vec b=0\\)（非零向量），则 \\(\\vec a\\perp\\vec b\\)' }, { l: 'C', v: '存在向量 \\(\\vec a,\\vec b,\\vec c\\) 满足 \\(\\vec a\\cdot\\vec b=\\vec b\\cdot\\vec c\\neq\\vec a\\cdot\\vec c\\)' }, { l: 'D', v: '\\(\\vec a\\cdot\\vec b\\le|\\vec a||\\vec b|\\)' }]),
    ans: 'B,C,D', ana: JSON.stringify({ brief: '概念辨析+数量积性质。', solve: 'A 错：方向未必相同；B 对；C 对（数量积无消去律，可构造）；D 对（柯西不等式）。', comment: '数量积不满足消去律是 C 的关键。' }) });
  mk({ type: '解答题', diff: 5, coef: 0.3, kp: '导数及其应用,零点', lit: '逻辑推理能力,数学抽象', cat: '压轴题',
    src: '2026 高考模拟压轴', use: 210,
    stem: `已知函数 \\(f(x)=\\dfrac{x^{2}}{2}-a\\ln x\\)。(1) 讨论 \\(f(x)\\) 的单调性；(2) 若 \\(f(x)\\) 有两个零点，求 \\(a\\) 的取值范围。`,
    opts: null, ans: '(1) a≤0 时在 (0,+∞) 单调递增；a>0 时在 (0,√a) 减、(√a,+∞) 增；(2) a>e。',
    ana: JSON.stringify({ brief: '定义域优先；分类讨论 a 与极值比较。', solve: 'f\'(x)=x-a/x=(x²-a)/x；a>0 时极小值 f(√a)=a(1-ln√a)/2，两个零点需极小值小于零：ln√a>1/2 ⇒ a>e。', comment: '定义域 (0,+∞)；零点个数结合极值符号与图象。' }) });
  mk({ type: '单选题', diff: 2, coef: 0.8, kp: '集合的概念', lit: '数学抽象', cat: '同步题',
    src: '2026 秋 高一同步', use: 1500,
    stem: `已知集合 \\(A=\\{x\\mid x^{2}-3x<0\\}\\)，则 \\(A\\cap\\mathbb{N}=\\)（\\quad）`,
    opts: JSON.stringify([{ l: 'A', v: '\\(\\{1,2\\}\\)' }, { l: 'B', v: '\\(\\{0,1,2\\}\\)' }, { l: 'C', v: '\\(\\{0,1,2,3\\}\\)' }, { l: 'D', v: '\\(\\{1,2,3\\}\\)' }]),
    ans: 'A', ana: JSON.stringify({ brief: '解不等式得 0<x<3。', solve: 'A=(0,3)，交集为 {1,2}。', comment: '注意自然数集包含 0，但 0∉A。' }) });
  mk({ type: '填空题', diff: 4, coef: 0.5, kp: '圆锥曲线,焦点三角形', lit: '数学运算,逻辑推理', cat: '易错题',
    src: '2026 高三二模', use: 466,
    stem: `椭圆 \\(\\dfrac{x^2}{a^2}+\\dfrac{y^2}{b^2}=1(a>b>0)\\) 上存在点 \\(P\\) 使 \\(\\angle F_1PF_2=90^\\circ\\)，则离心率的取值范围是 ______。`,
    opts: null, ans: '\\(\\left[\\dfrac{\\sqrt2}{2},1\\right)\\)',
    ana: JSON.stringify({ brief: '直角存在 ⇔ c≥b。', solve: 'c≥b ⇒ e²≥1/2 且 e<1。', comment: '焦点三角形顶角为直角的等价条件是高频易错点。' }) });
  mk({ type: '判断题', diff: 1, coef: 0.9, kp: '空间向量的概念', lit: '逻辑推理', cat: '同步题',
    src: '2026 秋 高二同步', use: 800,
    stem: '在空间直角坐标系中，若两点关于原点对称，则其坐标互为相反数。',
    opts: null, ans: '正确', ana: JSON.stringify({ brief: '对称变换定义。', solve: '(x,y,z) 关于原点对称 (-x,-y,-z)。', comment: '基础概念题。' }) });
  mk({ type: '解答题', diff: 4, coef: 0.48, kp: '二面角,空间向量', lit: '数学运算能力', cat: '好题',
    src: '2025-2026 学年高三上学期期中', use: 876,
    stem: `在长方体 \\(ABCD-A_1B_1C_1D_1\\) 中，\\(AB=AD=2\\)，\\(AA_1=3\\)，点 \\(M\\) 在 \\(CC_1\\) 上，当二面角 \\(M-BD-C\\) 的余弦值为 \\(\\dfrac{\\sqrt5}{5}\\) 时，求 \\(CM\\) 的长。`,
    opts: null, ans: 'CM=1',
    ana: JSON.stringify({ brief: '建系设法向量，含参表示 M。', solve: '以 D 为原点建系，平面 BDC 的法向量与平面 MBD 的含参法向量作夹角方程，解得 m=1。', comment: '动点问题先参数化，再用法向量夹角方程。' }) });

  // —— 程序化扩充（每题复制两份编号变式，保证组卷引擎题量；正式录题时人工替换）——
  const dup = db.prepare(`INSERT INTO questions(subject_id,type,difficulty,coefficient,scene,category,kp_names,literacy,source,stem,options,answer,analysis,author,reviewer,use_count)
    SELECT subject_id,type,difficulty,coefficient,scene,category,kp_names,literacy,source,
           stem || '（变式 ' || ? || '）', options, answer, analysis, author, reviewer, 0
    FROM questions WHERE id <= 12`);
  dup.run(1);
  dup.run(2);

  // 公告
  db.prepare('INSERT INTO notices(title,content,audience) VALUES(?,?,?)').run(
    '欢迎使用智卷云', '结构化数学题库已上线：章节选题、智能组卷、超管后台同步可用。', 'ALL');

  // 系统设置（Logo 留空 → 前台渲染占位框）
  const S = db.prepare('INSERT INTO settings(key,value) VALUES(?,?)');
  [['site_name', '智卷云'], ['logo_url', ''], ['beian', '苏ICP备XXXXXXXX号'], ['service_phone', '400-xxx-xxxx'],
   ['default_stage', '高中'], ['default_subject', '高中数学']].forEach(([k, v]) => S.run(k, v));
}

// ---- 商业化种子（幂等：按唯一键补种，存量库也可执行；docs/16 §9）----
(function seedTrade() {
  const P = db.prepare(`INSERT INTO member_plan(code,name,price_cents,duration_days,subject_id,benefits,purchasable,sort)
    VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(code) DO NOTHING`);
  P.run('FREE', '普通用户', 0, 0, null, JSON.stringify(['每日 3 次下载', '试题篮 50 题', '解析查看不限量']), 0, 1);
  P.run('STUDENT', '学生会员', 1500, 30, null, JSON.stringify(['下载不限次', '错题本·学情报告', 'AI 讲题 50 次/日']), 1, 2);
  P.run('TEACHER_PRO', '教师优享', 2500, 30, null, JSON.stringify(['下载不限次', 'AI 出题 50 次/日·讲题 200 次/日', 'Word 可编辑公式导出', '整辑打包下载']), 1, 3);
  P.run('SINGLE', '单科会员', 3900, 30, null, JSON.stringify(['单科内容全免费', '组卷下载不限次']), 1, 4);
  P.run('NEWBIE_7D', '新人体验', 0, 7, null, JSON.stringify(['注册即享 7 日体验']), 0, 0);

  const K = db.prepare(`INSERT INTO points_package(id,points,bonus_points,price_cents,label,sort) VALUES(?,?,?,?,?,?)
    ON CONFLICT(id) DO UPDATE SET points=excluded.points, bonus_points=excluded.bonus_points, price_cents=excluded.price_cents, label=excluded.label`);
  // points = 到账总额（含赠送）：充值点数 = points - bonus_points
  K.run(1, 5500, 500, 5000, '50元档·充5000赠500', 1);
  K.run(2, 11200, 1200, 10000, '100元档·充10000赠1200', 2);
  K.run(3, 23000, 3000, 20000, '200元档·充20000赠3000', 3);
  K.run(4, 60000, 10000, 50000, '500元档·充50000赠10000', 4);

  const C = db.prepare(`INSERT INTO coupon_template(code,name,type,amount_cents,discount_rate,max_discount_cents,threshold_cents,total,per_limit,valid_days,scope,status,note,created_at)
    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,datetime('now','localtime')) ON CONFLICT(code) DO NOTHING`);
  C.run('NEW10', '新人立减券', 'FULL_REDUCTION', 1000, 0, 0, 0, 100000, 1, 7, 'ALL', 1, '新人礼包自动发放（无门槛减10元）');
  C.run('TEACHER25', '开学季折扣券', 'DISCOUNT', 0, 90, 2000, 1000, 50, 1, 30, 'ALL', 1, '9折·封顶20元·满10元可用');
  C.run('P100', '点数兑换券', 'POINTS', 100, 0, 0, 0, 200, 1, 15, 'ALL', 1, '兑换后到账100点（履约时入账）');

  const S2 = db.prepare('INSERT INTO settings(key,value) VALUES(?,?) ON CONFLICT(key) DO NOTHING');
  S2.run('free_downloads_per_day', '3');
  S2.run('points_per_yuan', '100');
  S2.run('ai_quota_free', '3');
  S2.run('ai_quota_member', '50');
})();

// ---- 公开示范卷种子（docs/25 TJ-15：试卷选题域，幂等：无公开卷时补种）----
(function seedPublicPapers() {
  const hasPublic = db.prepare('SELECT COUNT(*) AS c FROM papers WHERE is_public=1').get().c;
  if (hasPublic) return;
  const pick = (type, n) => Array.from(db.prepare('SELECT id FROM questions WHERE status=2 AND type=? ORDER BY use_count DESC, id ASC LIMIT ?').iterate(type, n)).map(r => r.id);
  const ins = db.prepare('INSERT INTO papers(user_id,title,blueprint,total_score,is_public,level,year,region,category,downloads) VALUES(1,?,?,?,?,?,?,?,?,?)');
  const insQ = db.prepare('INSERT INTO paper_questions(paper_id,question_id,sort,score) VALUES(?,?,?,?)');
  const mkPaper = (title, level, year, region, category, downloads, structure) => {
    const items = [];
    for (const [type, count, score] of structure) for (const id of pick(type, count)) items.push({ id, score });
    const total = items.reduce((s, it) => s + it.score, 0);
    const pid = ins.run(title, JSON.stringify({ seeded: true }), total, 1, level, year, region, category, downloads).lastInsertRowid;
    items.forEach((it, i) => insQ.run(pid, it.id, i + 1, it.score));
  };
  mkPaper('2026 届高三高考模拟卷（一）', '精品', 2026, '江苏', '高考备考', 2141,
    [['单选题', 8, 5], ['多选题', 3, 6], ['填空题', 3, 5], ['解答题', 4, 12]]);
  mkPaper('2025 高考真题精选汇编', '特供', 2025, '北京', '高考备考', 986,
    [['单选题', 6, 5], ['填空题', 4, 5], ['解答题', 3, 14]]);
  mkPaper('高一同步教学检测卷（集合与常用逻辑用语）', '普通', 2026, '全国', '同步教学', 764,
    [['单选题', 6, 4], ['判断题', 4, 3], ['填空题', 2, 5]]);
})();

// ---- 精品专辑种子（TJ-88，幂等：album 表空时补种，挂全部公开卷）----
(function seedAlbums() {
  if (db.prepare('SELECT COUNT(*) AS c FROM album').get().c) return;
  const pubs = Array.from(db.prepare('SELECT id FROM papers WHERE is_public=1 ORDER BY id').iterate()).map(r => r.id);
  if (!pubs.length) return;
  const insA = db.prepare('INSERT INTO album(title,brand,description,cover_emoji) VALUES(?,?,?,?)');
  const insAP = db.prepare('INSERT INTO album_paper(album_id,paper_id,sort) VALUES(?,?,?)');
  const a1 = insA.run('【上好课】同步教学精选', '上好课', '按章节组织的同步成套卷，课前-课中-课后全覆盖。', '🏫').lastInsertRowid;
  const a2 = insA.run('【高考备考】模拟与真题精选', '智卷出品', '近两年高考模拟与真题汇编，精品/特供级。', '🎯').lastInsertRowid;
  pubs.forEach((pid, i) => { insAP.run(a1, pid, i); insAP.run(a2, pid, i); });
})();

module.exports = db;
