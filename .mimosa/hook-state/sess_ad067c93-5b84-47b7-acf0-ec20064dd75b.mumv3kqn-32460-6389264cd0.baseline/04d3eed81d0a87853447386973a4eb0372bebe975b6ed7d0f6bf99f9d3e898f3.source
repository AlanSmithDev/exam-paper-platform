// 练习/错题域服务（docs/15 §1 PR-1~PR-6）：生成练习、客观题自动判分、错题本、再练卷、学情聚合。
// 安全说明：全部 SQL 静态语句 + 参数绑定；判分纯函数化，解答题记 PENDING 不自动判。
const db = require('./db');
const trade = require('./trade');

const nowStr = () => {
  const d = new Date();
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
};

// ---------- 判分（PR-2，纯函数）----------
const normBlank = (s) => String(s || '').trim().replace(/\s+/g, '').replace(/，/g, ',').toLowerCase();
function normalizeMulti(s) {
  return [...new Set(String(s || '').split(/[,，、\s]+/).filter(Boolean))].sort().join(',');
}
function judge(q, ans) {
  const correct = String(q.answer || '').trim();
  const given = String(ans === undefined || ans === null ? '' : ans).trim();
  if (q.type === '单选题' || q.type === '判断题') return given === correct ? 1 : 0;
  if (q.type === '多选题') return given && normalizeMulti(given) === normalizeMulti(correct) ? 1 : 0;
  if (q.type === '填空题') return normBlank(given) === normBlank(correct) ? 1 : 0;
  return null; // 解答题：人工批改，暂记 PENDING
}

// ---------- 生成练习（PR-1 / PR-5 再练）----------
function createPractice(uid, b) {
  const count = Math.max(1, Math.min(20, Number(b.count) || 10));
  const mode = b.mode === 'REPEAT' ? 'REPEAT' : 'KP';
  let candidates = [];
  if (mode === 'REPEAT') {
    // 再练卷：取未解决错题的知识点，回题库按知识点抽新题（排除原错题）
    const wrongs = Array.from(db.prepare('SELECT question_id FROM wrong_question WHERE user_id=? AND resolved=0').iterate(Number(uid)));
    if (!wrongs.length) { const e = new Error('错题本为空，无需再练'); e.code = 42200; throw e; }
    const wrongIds = new Set(wrongs.map(w => w.question_id));
    const kps = [...new Set(wrongs.map(w => (db.prepare('SELECT kp_names FROM questions WHERE id=?').get(w.question_id) || {}).kp_names).filter(Boolean))];
    const seen = new Set();
    for (const kp of kps) {
      for (const q of Array.from(db.prepare("SELECT id FROM questions WHERE status=2 AND kp_names=? LIMIT ?").iterate(kp, count * 2))) {
        if (candidates.length >= count) break;
        if (wrongIds.has(q.id) || seen.has(q.id)) continue;
        seen.add(q.id); candidates.push(q.id);
      }
      if (candidates.length >= count) break;
    }
    if (!candidates.length) { const e = new Error('错题知识点暂无可再练的题目'); e.code = 42200; throw e; }
  } else {
    const filter = { subjectId: b.subjectId, kp: b.kp, type: b.type };
    candidates = storeQIds(filter, count);
    if (!candidates.length) { const e = new Error('该条件下无可用题目'); e.code = 42200; throw e; }
  }
  const info = db.prepare('INSERT INTO practice(user_id, subject_id, mode, kp, question_ids, total, created_at) VALUES(?,?,?,?,?,?,?)')
    .run(Number(uid), b.subjectId ? Number(b.subjectId) : null, mode, b.kp || null,
         JSON.stringify(candidates), candidates.length, nowStr());
  return { practiceId: info.lastInsertRowid, mode, total: candidates.length, questions: practiceQuestions(candidates) };
}
// 复用 store 的筛选查询（保持题库 SQL 单一归属）
function storeQIds(filter, count) {
  const store = require('./store');
  return store.qList(filter, count, 0).map(q => q.id);
}
function practiceQuestions(ids) {
  return ids.map(id => {
    const q = db.prepare('SELECT id, type, difficulty, coefficient, kp_names, stem, options FROM questions WHERE id=? AND status=2').get(id);
    return q ? { ...q, options: q.options ? JSON.parse(q.options) : null } : null;
  }).filter(Boolean);
}

// ---------- 提交作答（PR-3/PR-4）----------
const submitTx = db.transaction((uid, practiceId, answers) => {
  const practice = db.prepare('SELECT * FROM practice WHERE id=? AND user_id=?').get(Number(practiceId), Number(uid));
  if (!practice) { const e = new Error('练习不存在'); e.code = 40400; throw e; }
  const ids = JSON.parse(practice.question_ids);
  const byId = new Map((answers || []).map(a => [Number(a.questionId), a]));
  const results = [];
  let objective = 0, correctCount = 0;
  for (const qid of ids) {
    const q = db.prepare('SELECT id, type, answer, analysis, kp_names FROM questions WHERE id=?').get(qid);
    if (!q) continue;
    const given = byId.get(qid);
    const verdict = judge(q, given ? given.answer : '');
    const correct = verdict === null ? null : (verdict === 1 ? 1 : 0);
    db.prepare('INSERT INTO practice_answer(practice_id, user_id, question_id, answer, correct, duration_ms, created_at) VALUES(?,?,?,?,?,?,?)')
      .run(Number(practiceId), Number(uid), qid, given ? String(given.answer) : '', correct,
           given ? Math.max(0, Math.min(3600000, Number(given.durationMs) || 0)) : 0, nowStr());
    if (correct !== null) {
      objective++;
      if (correct === 1) correctCount++;
    }
    if (correct === 0) {
      // 答错自动入错题本（PR-4）：wrong_count 累加、重新置为未解决
      db.prepare(`INSERT INTO wrong_question(user_id, question_id, wrong_count, resolved, last_wrong_at) VALUES(?,?,1,0,?)
        ON CONFLICT(user_id, question_id) DO UPDATE SET wrong_count=wrong_count+1, resolved=0, last_wrong_at=excluded.last_wrong_at`)
        .run(Number(uid), qid, nowStr());
    } else if (correct === 1) {
      // 再练答对 → 错题解决（PR-4）
      db.prepare('UPDATE wrong_question SET resolved=1 WHERE user_id=? AND question_id=?').run(Number(uid), qid);
    }
    results.push({ questionId: qid, correct, yourAnswer: given ? given.answer : '',
      answer: q.answer, analysis: q.analysis ? JSON.parse(q.analysis) : null, kpNames: q.kp_names });
  }
  return { practiceId: Number(practiceId), total: ids.length, objective,
    correctRate: objective ? Math.round(correctCount / objective * 100) / 100 : null,
    wrongIds: results.filter(r => r.correct === 0).map(r => r.questionId), results };
});
function submit(uid, practiceId, answers) { return submitTx(uid, practiceId, answers); }

function practiceById(uid, id) {
  const p = db.prepare('SELECT * FROM practice WHERE id=? AND user_id=?').get(Number(id), Number(uid));
  if (!p) return null;
  return { practiceId: p.id, mode: p.mode, kp: p.kp, total: p.total, createdAt: p.created_at, questions: practiceQuestions(JSON.parse(p.question_ids)) };
}

// ---------- 错题本（PR-4）与学情（PR-6）----------
function wrongQuestions(uid, subjectId) {
  const sql = `SELECT w.question_id AS id, w.wrong_count, w.resolved, w.last_wrong_at, q.type, q.difficulty, q.kp_names, q.stem
    FROM wrong_question w JOIN questions q ON q.id = w.question_id
    WHERE w.user_id=? AND w.resolved=0 ${subjectId ? 'AND q.subject_id=?' : ''} ORDER BY w.last_wrong_at DESC LIMIT 100`;
  const rows = subjectId
    ? Array.from(db.prepare(sql).iterate(Number(uid), Number(subjectId)))
    : Array.from(db.prepare(sql).iterate(Number(uid)));
  return rows;
}
function resolveWrong(uid, questionId) {
  const r = db.prepare('UPDATE wrong_question SET resolved=1 WHERE user_id=? AND question_id=?').run(Number(uid), Number(questionId));
  if (!r.changes) { const e = new Error('错题不存在或已解决'); e.code = 40400; throw e; }
  return { questionId: Number(questionId), resolved: 1 };
}
function kpReport(uid) {
  return Array.from(db.prepare(`
    SELECT q.kp_names AS kp, COUNT(*) AS answered,
           SUM(CASE WHEN pa.correct=1 THEN 1 ELSE 0 END) AS correct,
           SUM(CASE WHEN pa.correct=0 THEN 1 ELSE 0 END) AS wrong
    FROM practice_answer pa JOIN questions q ON q.id = pa.question_id
    WHERE pa.user_id=? AND pa.correct IS NOT NULL AND q.kp_names IS NOT NULL
    GROUP BY q.kp_names ORDER BY wrong DESC`).iterate(Number(uid)))
    .map(r => ({ ...r, correctRate: r.answered ? Math.round(r.correct / r.answered * 100) / 100 : null }));
}

module.exports = { createPractice, practiceById, submit, judge, wrongQuestions, resolveWrong, kpReport };
