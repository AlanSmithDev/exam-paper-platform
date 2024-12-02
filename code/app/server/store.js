// 数据访问层（WP-2）：全部 SQL 集中于此，静态语句 + 参数绑定。
// 入参均为已归一化的普通值（Number/String/null），不引入任何 HTTP 请求对象。
const db = require('./db');

function normStr(v) { return v === undefined || v === null || v === '' ? null : String(v); }

function qCount(f) {
  const order = f.sort === 'new' ? 'created_at DESC, id DESC' : 'use_count DESC, id ASC';
  return db.prepare(`SELECT COUNT(*) AS c FROM questions
    WHERE status = 2
      AND (@sid IS NULL OR subject_id = @sid)
      AND (@scene IS NULL OR scene = @scene)
      AND (@type IS NULL OR type = @type)
      AND (@diff IS NULL OR difficulty = @diff)
      AND (@cat IS NULL OR category = @cat)
      AND (@kpLike IS NULL OR kp_names LIKE @kpLike)
      AND (@kwLike IS NULL OR stem LIKE @kwLike OR answer LIKE @kwLike)`).get({
    sid: normStr(f.subjectId), scene: normStr(f.scene), type: normStr(f.type),
    diff: f.difficulty ? Number(f.difficulty) : null, cat: normStr(f.category),
    kpLike: f.kp ? '%' + String(f.kp) + '%' : null,
    kwLike: f.keyword ? '%' + String(f.keyword) + '%' : null
  }).c;
}

function qList(f, limit, offset) {
  const rows = [];
  const stmt = db.prepare(`SELECT id, type, difficulty, coefficient, scene, category, kp_names, literacy, source, stem, options, use_count, aigc,
    (SELECT COUNT(*) FROM paper_questions pq WHERE pq.question_id = questions.id) AS paper_uses
    FROM questions
    WHERE status = 2
      AND (@sid IS NULL OR subject_id = @sid)
      AND (@scene IS NULL OR scene = @scene)
      AND (@type IS NULL OR type = @type)
      AND (@diff IS NULL OR difficulty = @diff)
      AND (@cat IS NULL OR category = @cat)
      AND (@kpLike IS NULL OR kp_names LIKE @kpLike)
      AND (@kwLike IS NULL OR stem LIKE @kwLike OR answer LIKE @kwLike)
    ORDER BY ${f.sort === 'new' ? 'created_at DESC, id DESC' : 'use_count DESC, id ASC'}
    LIMIT @limit OFFSET @offset`);
  for (const row of stmt.iterate({
    sid: normStr(f.subjectId), scene: normStr(f.scene), type: normStr(f.type),
    diff: f.difficulty ? Number(f.difficulty) : null, cat: normStr(f.category),
    kpLike: f.kp ? '%' + String(f.kp) + '%' : null,
    kwLike: f.keyword ? '%' + String(f.keyword) + '%' : null,
    limit: Number(limit), offset: Number(offset)
  })) rows.push(row);
  return rows;
}

function qById(id) {
  return db.prepare(`SELECT q.*, (SELECT COUNT(*) FROM paper_questions pq WHERE pq.question_id = q.id) AS paper_uses
    FROM questions q WHERE q.id = ? AND q.status = 2`).get(Number(id));
}

function qSimilar(id, kpNames, type) {
  const rows = [];
  const stmt = db.prepare(`SELECT id, type, difficulty, coefficient, stem FROM questions
    WHERE status = 2 AND id != ? AND (kp_names = ? OR type = ?) LIMIT 3`);
  for (const row of stmt.iterate(Number(id), normStr(kpNames), normStr(type))) rows.push(row);
  return rows;
}

function stages() { return Array.from(db.prepare('SELECT * FROM stages ORDER BY sort').iterate()); }
function subjects() { return Array.from(db.prepare('SELECT * FROM subjects').iterate()); }
function catalog() { return Array.from(db.prepare('SELECT * FROM catalog ORDER BY sort').iterate()); }

// ---------- 试题篮 ----------
function basketIds(uid) {
  return Array.from(db.prepare('SELECT question_id AS id FROM basket WHERE user_id = ? ORDER BY created_at DESC').iterate(Number(uid)));
}
function basketAdd(uid, qids) {
  const ins = db.prepare('INSERT OR IGNORE INTO basket(user_id, question_id) VALUES(?, ?)');
  const count = db.prepare('SELECT COUNT(*) AS c FROM basket WHERE user_id = ?').get(Number(uid)).c;
  let added = 0;
  for (const qid of qids) {
    if (count + added >= 100) break;
    if (!Number.isInteger(qid)) continue;
    added += ins.run(Number(uid), qid).changes;
  }
  return added;
}
function basketRemove(uid, qids) {
  const del = db.prepare('DELETE FROM basket WHERE user_id = ? AND question_id = ?');
  let removed = 0;
  for (const qid of qids) { if (Number.isInteger(qid)) removed += del.run(Number(uid), qid).changes; }
  return removed;
}

// ---------- 试卷与组卷引擎 ----------
function savePaper(uid, title, blueprintJson, totalScore, items) {
  const info = db.prepare('INSERT INTO papers(user_id,title,blueprint,total_score) VALUES(?,?,?,?)')
    .run(Number(uid), String(title || '智能组卷'), String(blueprintJson), Number(totalScore));
  const pid = info.lastInsertRowid;
  const ins = db.prepare('INSERT INTO paper_questions(paper_id,question_id,sort,score) VALUES(?,?,?,?)');
  items.forEach((it, i) => ins.run(pid, Number(it.id), i + 1, Number(it.score)));
  return pid;
}
// 工作台保存（教师端编辑）：整卷覆盖（题目顺序=数组顺序，分值逐题），归属校验内聚
const paperUpdateTx = db.transaction((pid, uid, title, items) => {
  const paper = db.prepare('SELECT * FROM papers WHERE id=?').get(Number(pid));
  if (!paper || paper.user_id !== Number(uid)) { const e = new Error('试卷不存在'); e.code = 40400; throw e; }
  if (!Array.isArray(items) || !items.length) { const e = new Error('试卷至少保留 1 道题'); e.code = 42200; throw e; }
  const ids = items.map(it => Number(it.questionId));
  if (new Set(ids).size !== ids.length || ids.some(id => !Number.isInteger(id))) {
    const e = new Error('题目列表重复或非法'); e.code = 42200; throw e;
  }
  // 题目必须真实存在且上架（防挂载草稿题）
  const known = db.prepare('SELECT id FROM questions WHERE status=2 AND id=?');
  for (const id of ids) { if (!known.get(id)) { const e = new Error(`题目 ${id} 不存在或未上架`); e.code = 42200; throw e; } }
  db.prepare('DELETE FROM paper_questions WHERE paper_id=?').run(Number(pid));
  const ins = db.prepare('INSERT INTO paper_questions(paper_id,question_id,sort,score) VALUES(?,?,?,?)');
  let total = 0;
  items.forEach((it, i) => { const s = Math.max(0, Math.min(100, Number(it.score) || 0)); total += s; ins.run(Number(pid), ids[i], i + 1, s); });
  db.prepare('UPDATE papers SET title=?, total_score=? WHERE id=?').run(String(title || paper.title), total, Number(pid));
  return Number(pid);
});
function paperUpdate(pid, uid, title, items) { return paperUpdateTx(pid, uid, title, items); }

// 智能换题（教师端）：同学科同题型、难度优先取同档再 ±1/±2、排除卷内已有，取使用次数最少者（冷门优先，推荐语义）
function paperReplaceQuestion(pid, uid, questionId) {
  const paper = db.prepare('SELECT * FROM papers WHERE id=?').get(Number(pid));
  if (!paper || paper.user_id !== Number(uid)) { const e = new Error('试卷不存在'); e.code = 40400; throw e; }
  const cur = db.prepare('SELECT pq.score, q.id, q.type, q.subject_id, q.difficulty FROM paper_questions pq JOIN questions q ON q.id=pq.question_id WHERE pq.paper_id=? AND pq.question_id=?')
    .get(Number(pid), Number(questionId));
  if (!cur) { const e = new Error('该题不在试卷中'); e.code = 40400; throw e; }
  const newQ = db.prepare(`
    SELECT id, type, difficulty, coefficient, kp_names, stem, options, answer, analysis
    FROM questions
    WHERE status=2 AND type=? AND subject_id=? AND id != ? AND id NOT IN (SELECT question_id FROM paper_questions WHERE paper_id=?)
      AND difficulty IN (?, ?, ?)
    ORDER BY ABS(difficulty - ?) ASC, use_count ASC, id ASC
    LIMIT 1`).get(cur.type, cur.subject_id, Number(questionId), Number(pid), cur.difficulty, cur.difficulty - 1, cur.difficulty + 1, cur.difficulty);
  if (!newQ) { const e = new Error('题库中暂无可替换的同题型题目'); e.code = 40400; throw e; }
  db.prepare('UPDATE paper_questions SET question_id=? WHERE paper_id=? AND question_id=?')
    .run(newQ.id, Number(pid), Number(questionId));
  return { removed: Number(questionId), added: serializeReplace(newQ, cur.score) };
}
function serializeReplace(q, score) {
  return { id: q.id, type: q.type, difficulty: q.difficulty, coefficient: q.coefficient, score,
    kp_names: q.kp_names, stem: q.stem, options: q.options ? JSON.parse(q.options) : null,
    answer: q.answer, analysis: q.analysis ? JSON.parse(q.analysis) : null };
}
// 知识点视角（教师端选题）：上架题的知识点聚合（Top 50，按题量降序）
function kpAggregation() {
  const rows = Array.from(db.prepare("SELECT kp_names FROM questions WHERE status=2 AND kp_names IS NOT NULL AND kp_names != ''").iterate());
  const counter = new Map();
  for (const r of rows) {
    for (const kp of String(r.kp_names).split(/[,，]/).map(s => s.trim()).filter(Boolean)) {
      counter.set(kp, (counter.get(kp) || 0) + 1);
    }
  }
  return [...counter.entries()].sort((a, b) => b[1] - a[1]).slice(0, 50)
    .map(([name, count]) => ({ name, count }));
}
function paperById(pid) {
  const paper = db.prepare('SELECT * FROM papers WHERE id = ?').get(Number(pid));
  if (!paper) return null;
  paper.questions = Array.from(db.prepare(
    `SELECT pq.sort, pq.score, q.id, q.type, q.difficulty, q.coefficient, q.stem, q.options, q.answer, q.analysis
     FROM paper_questions pq JOIN questions q ON q.id = pq.question_id
     WHERE pq.paper_id = ? ORDER BY pq.sort`).iterate(Number(pid)));
  return paper;
}

// ---------- 公开示范卷（教师端试卷选题，docs/25 TJ-10~14）----------
function publicPapers(category, level, year, pageNo, pageSize) {
  const conds = ['is_public=1'];
  const params = [];
  if (category) { conds.push('category=?'); params.push(String(category)); }
  if (level) { conds.push('level=?'); params.push(String(level)); }
  if (year) { conds.push('year=?'); params.push(Number(year)); }
  const where = 'WHERE ' + conds.join(' AND ');
  const total = db.prepare(`SELECT COUNT(*) AS c FROM papers ${where}`).get(...params).c;
  const rows = Array.from(db.prepare(`SELECT p.id, p.title, p.level, p.year, p.region, p.category, p.downloads, p.total_score,
      (SELECT COUNT(*) FROM paper_questions pq WHERE pq.paper_id=p.id) AS question_count
    FROM papers p ${where} ORDER BY p.id DESC LIMIT ? OFFSET ?`).iterate(...params, Number(pageSize), (Number(pageNo) - 1) * Number(pageSize)));
  return { total, rows };
}
// 整卷试读 30%：免费看题干（不含答案解析），至少 1 题（TJ-12）
function publicPaperPreview(pid) {
  const paper = db.prepare('SELECT * FROM papers WHERE id=? AND is_public=1').get(Number(pid));
  if (!paper) return null;
  const all = Array.from(db.prepare(`SELECT pq.sort, q.id, q.type, q.difficulty, q.kp_names, q.stem, q.options
    FROM paper_questions pq JOIN questions q ON q.id=pq.question_id WHERE pq.paper_id=? ORDER BY pq.sort`).iterate(Number(pid)));
  const readCount = Math.max(1, Math.ceil(all.length * 0.3));
  return {
    paper: { id: paper.id, title: paper.title, level: paper.level, year: paper.year, region: paper.region,
      category: paper.category, totalScore: paper.total_score, totalCount: all.length, readCount },
    questions: all.slice(0, readCount).map(q => ({ ...q, options: q.options ? JSON.parse(q.options) : null }))
  };
}
function isPublicPaper(pid) {
  return !!db.prepare('SELECT id FROM papers WHERE id=? AND is_public=1').get(Number(pid));
}
function paperIncDownloads(pid) {
  db.prepare('UPDATE papers SET downloads=downloads+1 WHERE id=?').run(Number(pid));
}

// ---------- 教师工具箱：组卷模板 / 收藏 / 精品专辑（docs/25 TJ-80/85/88）----------
function templateCreate(uid, b) {
  if (!Array.isArray(b.structure) || !b.structure.length) { const e = new Error('structure 不能为空'); e.code = 42200; throw e; }
  return db.prepare('INSERT INTO paper_template(user_id,name,subject_id,structure,difficulty_target) VALUES(?,?,?,?,?)')
    .run(Number(uid), String(b.name || '我的模板').slice(0, 64), b.subjectId ? Number(b.subjectId) : null,
         JSON.stringify(b.structure), Math.max(1, Math.min(5, Number(b.difficultyTarget) || 3))).lastInsertRowid;
}
function templatesByUser(uid) {
  return Array.from(db.prepare('SELECT id, name, subject_id, structure, difficulty_target, created_at FROM paper_template WHERE user_id=? ORDER BY id DESC LIMIT 30').iterate(Number(uid)))
    .map(t => ({ ...t, structure: JSON.parse(t.structure) }));
}
function templateDelete(uid, id) {
  const r = db.prepare('DELETE FROM paper_template WHERE id=? AND user_id=?').run(Number(id), Number(uid));
  if (!r.changes) { const e = new Error('模板不存在'); e.code = 40400; throw e; }
  return { id: Number(id) };
}
function favoriteToggle(uid, questionId) {
  const exists = db.prepare('SELECT id FROM favorites WHERE user_id=? AND question_id=?').get(Number(uid), Number(questionId));
  if (exists) {
    db.prepare('DELETE FROM favorites WHERE id=?').run(exists.id);
    return { questionId: Number(questionId), favorited: false };
  }
  const q = db.prepare('SELECT id FROM questions WHERE id=? AND status=2').get(Number(questionId));
  if (!q) { const e = new Error('题目不存在或未上架'); e.code = 40400; throw e; }
  db.prepare('INSERT OR IGNORE INTO favorites(user_id, question_id) VALUES(?,?)').run(Number(uid), Number(questionId));
  return { questionId: Number(questionId), favorited: true };
}
function favoritesByUser(uid, pageNo, pageSize) {
  const total = db.prepare('SELECT COUNT(*) AS c FROM favorites WHERE user_id=?').get(Number(uid)).c;
  const rows = Array.from(db.prepare(`SELECT f.question_id AS id, f.created_at, q.type, q.difficulty, q.coefficient, q.kp_names, q.stem, q.options, q.use_count
    FROM favorites f JOIN questions q ON q.id=f.question_id WHERE f.user_id=? ORDER BY f.id DESC LIMIT ? OFFSET ?`)
    .iterate(Number(uid), Number(pageSize), (Number(pageNo) - 1) * Number(pageSize)));
  return { total, rows: rows.map(q => ({ ...q, options: q.options ? JSON.parse(q.options) : null })) };
}
function albumsPublic() {
  return Array.from(db.prepare(`SELECT a.id, a.title, a.brand, a.description, a.cover_emoji,
      (SELECT COUNT(*) FROM album_paper ap WHERE ap.album_id=a.id) AS paper_count
    FROM album a ORDER BY a.id DESC`).iterate());
}
function albumDetail(id) {
  const a = db.prepare('SELECT * FROM album WHERE id=?').get(Number(id));
  if (!a) return null;
  const papers = Array.from(db.prepare(`SELECT p.id, p.title, p.level, p.year, p.region, p.category, p.downloads, p.total_score,
      (SELECT COUNT(*) FROM paper_questions pq WHERE pq.paper_id=p.id) AS question_count
    FROM album_paper ap JOIN papers p ON p.id=ap.paper_id WHERE ap.album_id=? ORDER BY ap.sort`).iterate(Number(id)));
  return { ...a, papers };
}

// ---------- 广告位 ----------
function activeAds(position) {
  return Array.from(db.prepare(
    `SELECT id, position, title, image_url, link_url, audience, sort FROM ads
     WHERE position = ? AND status = 1 ORDER BY sort`).iterate(normStr(position)));
}
function allAds() { return Array.from(db.prepare('SELECT * FROM ads ORDER BY position, sort').iterate()); }
function adById(id) { return db.prepare('SELECT * FROM ads WHERE id = ?').get(Number(id)); }
function adCreate(a) {
  return db.prepare('INSERT INTO ads(position,title,image_url,link_url,audience,sort,status) VALUES(?,?,?,?,?,?,?)')
    .run(normStr(a.position), normStr(a.title), normStr(a.image_url), normStr(a.link_url),
         normStr(a.audience) || 'ALL', Number(a.sort) || 0, a.status ? 1 : 0).lastInsertRowid;
}
function adUpdate(id, a) {
  return db.prepare('UPDATE ads SET position=?, title=?, image_url=?, link_url=?, audience=?, sort=?, status=? WHERE id=?')
    .run(normStr(a.position), normStr(a.title), normStr(a.image_url), normStr(a.link_url),
         normStr(a.audience) || 'ALL', Number(a.sort) || 0, a.status ? 1 : 0, Number(id)).changes;
}
function adDelete(id) { return db.prepare('DELETE FROM ads WHERE id = ?').run(Number(id)).changes; }

// ---------- 系统设置 ----------
function settingsAll() {
  const map = {};
  for (const row of db.prepare('SELECT key, value FROM settings').iterate()) map[row.key] = row.value;
  return map;
}
function settingsPut(map) {
  const up = db.prepare('INSERT INTO settings(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value');
  let n = 0;
  for (const k of Object.keys(map)) { if (normStr(k)) { up.run(String(k), String(map[k] === undefined ? '' : map[k])); n++; } }
  return n;
}

// ---------- 超管：题目 / 用户 / 公告 / 审计 / 看板 ----------
function adminQuestions(pageNo, pageSize) {
  const total = db.prepare('SELECT COUNT(*) AS c FROM questions').get().c;
  const rows = [];
  const stmt = db.prepare('SELECT id, type, difficulty, coefficient, scene, category, kp_names, source, status, use_count, aigc, created_at FROM questions ORDER BY id DESC LIMIT ? OFFSET ?');
  for (const row of stmt.iterate(Number(pageSize), Number((pageNo - 1) * pageSize))) rows.push(row);
  return { total, rows };
}
function adminQuestionUpdate(id, patch) {
  return db.prepare(`UPDATE questions SET scene=?, category=?, kp_names=?, difficulty=?, coefficient=?, status=? WHERE id=?`)
    .run(normStr(patch.scene), normStr(patch.category), normStr(patch.kp_names),
         Number(patch.difficulty) || 3, Number(patch.coefficient) || 0.65, Number(patch.status) || 0, Number(id)).changes;
}
function adminQuestionStatus(id, status) {
  return db.prepare('UPDATE questions SET status=? WHERE id=?').run(Number(status) || 0, Number(id)).changes;
}
function adminUsers() {
  return Array.from(db.prepare('SELECT id, mobile, nickname, role, status, certify, member_until, last_login_at, last_login_ip, created_at FROM users ORDER BY id').iterate());
}
function adminUserPatch(id, patch) {
  const u = db.prepare('SELECT * FROM users WHERE id = ?').get(Number(id));
  if (!u) return 0;
  const status = patch.status === undefined ? u.status : (Number(patch.status) ? 1 : 0);
  const certify = patch.certify === undefined ? u.certify : (Number(patch.certify) ? 1 : 0);
  const member = patch.member_until === undefined ? u.member_until : normStr(patch.member_until);
  return db.prepare('UPDATE users SET status=?, certify=?, member_until=? WHERE id=?')
    .run(status, certify, member, Number(id)).changes;
}
function noticeCreate(n) {
  return db.prepare('INSERT INTO notices(title,content,audience,status) VALUES(?,?,?,?)')
    .run(normStr(n.title) || '公告', normStr(n.content) || '', normStr(n.audience) || 'ALL', 1).lastInsertRowid;
}
function noticeList() { return Array.from(db.prepare('SELECT * FROM notices ORDER BY id DESC').iterate()); }
function noticeDelete(id) { return db.prepare('DELETE FROM notices WHERE id = ?').run(Number(id)).changes; }
function activeNotices() { return Array.from(db.prepare('SELECT id,title,content,created_at FROM notices WHERE status=1 ORDER BY id DESC LIMIT 5').iterate()); }
function auditList(limit) {
  return Array.from(db.prepare('SELECT * FROM audit_log ORDER BY id DESC LIMIT ?').iterate(Math.min(200, Number(limit) || 50)));
}
function dashboard() {
  return {
    users: db.prepare('SELECT COUNT(*) AS c FROM users').get().c,
    questions: db.prepare('SELECT COUNT(*) AS c FROM questions').get().c,
    questionsOn: db.prepare('SELECT COUNT(*) AS c FROM questions WHERE status=2').get().c,
    papers: db.prepare('SELECT COUNT(*) AS c FROM papers').get().c,
    ads: db.prepare('SELECT COUNT(*) AS c FROM ads WHERE status=1').get().c,
    audits: db.prepare('SELECT COUNT(*) AS c FROM audit_log').get().c
  };
}

// ---------- 用户与认证 ----------
function userByMobile(mobile) {
  return db.prepare('SELECT * FROM users WHERE mobile = ?').get(normStr(mobile));
}
function userById(id) {
  return db.prepare('SELECT * FROM users WHERE id = ?').get(Number(id));
}
function userCreate(mobile, passwordHash, nickname, role) {
  return db.prepare('INSERT INTO users(mobile,password_hash,nickname,role) VALUES(?,?,?,?)')
    .run(normStr(mobile), String(passwordHash), normStr(nickname), normStr(role)).lastInsertRowid;
}
function userStatus(id) {
  const u = db.prepare('SELECT status FROM users WHERE id = ?').get(Number(id));
  return u ? u.status : null;
}
// 登录审计（docs/20 §1/等保）：记录最近登录时间与 IP
function userTouchLogin(id, ip) {
  return db.prepare('UPDATE users SET last_login_at = datetime(\'now\',\'localtime\'), last_login_ip = ? WHERE id = ?')
    .run(String(ip || '-').slice(0, 64), Number(id)).changes;
}

module.exports = { qCount, qList, qById, qSimilar, stages, subjects, catalog, normStr, kpAggregation,
  userByMobile, userById, userCreate, userStatus, userTouchLogin,
  basketIds, basketAdd, basketRemove,
  savePaper, paperById, paperUpdate, paperReplaceQuestion,
  publicPapers, publicPaperPreview, isPublicPaper, paperIncDownloads,
  templateCreate, templatesByUser, templateDelete, favoriteToggle, favoritesByUser, albumsPublic, albumDetail,
  activeAds, allAds, adById, adCreate, adUpdate, adDelete,
  settingsAll, settingsPut,
  adminQuestions, adminQuestionUpdate, adminQuestionStatus, adminUsers, adminUserPatch,
  noticeCreate, noticeList, noticeDelete, activeNotices, auditList, dashboard };
