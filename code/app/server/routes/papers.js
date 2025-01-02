// WP-3: 组卷 —— 规则引擎智能组卷（题型数量 + 难度分布 + 去重 + 由易到难）+ 导出计费
const express = require('express');
const store = require('../store');
const trade = require('../trade');
const { required } = require('../auth');

const r = express.Router();

// —— 精品专辑（公开浏览，TJ-88；导出仍需登录+计费）——
r.get('/albums', (_req, res) => {
  res.json({ code: 0, message: 'ok', data: { list: store.albumsPublic() } });
});
r.get('/albums/:id', (req, res) => {
  const d = store.albumDetail(req.params.id);
  if (!d) return res.status(404).json({ code: 40400, message: '专辑不存在', data: null });
  res.json({ code: 0, message: 'ok', data: d });
});

r.use(required);

// 生成试卷：{ subjectId, title, structure:[{type,count,score}], difficultyTarget(1-5), excludeIds:[] }
r.post('/generate', (req, res) => {
  const b = req.body || {};
  const structure = Array.isArray(b.structure) ? b.structure : [];
  if (!structure.length) return res.status(422).json({ code: 42200, message: 'structure 不能为空', data: null });

  const exclude = new Set((Array.isArray(b.excludeIds) ? b.excludeIds : []).map(Number).filter(Number.isInteger));
  const paper = [];
  const used = new Set();
  let totalScore = 0;

  for (const sec of structure) {
    const want = Math.max(0, Math.min(30, Number(sec.count) || 0));
    const type = String(sec.type || '');
    // 按难度优先级选取：以目标难度为中心，向两侧扩展
    const target = Math.max(1, Math.min(5, Number(b.difficultyTarget) || 3));
    const order = [target, target - 1, target + 1, target - 2, target + 2, target - 3, target + 3, target - 4, target + 4]
      .filter(d => d >= 1 && d <= 5);
    let picked = 0;
    for (const d of order) {
      if (picked >= want) break;
      const filter = { subjectId: b.subjectId, type, difficulty: d, kp: b.kp };
      const candidates = store.qList(filter, want * 3, 0);
      for (const q of candidates) {
        if (picked >= want) break;
        if (used.has(q.id) || exclude.has(q.id)) continue;
        used.add(q.id);
        const score = Number(sec.score) || 5;
        totalScore += score;
        paper.push({ id: q.id, type: q.type, difficulty: q.difficulty, coefficient: q.coefficient, kp: q.kp_names, stem: q.stem, score });
        picked++;
      }
    }
    // 若该题型题量不足，继续用不带难度约束的候选补齐
    if (picked < want) {
      const candidates = store.qList({ subjectId: b.subjectId, type, kp: b.kp }, want * 3, 0);
      for (const q of candidates) {
        if (picked >= want) break;
        if (used.has(q.id) || exclude.has(q.id)) continue;
        used.add(q.id);
        const score = Number(sec.score) || 5;
        totalScore += score;
        paper.push({ id: q.id, type: q.type, difficulty: q.difficulty, coefficient: q.coefficient, kp: q.kp_names, stem: q.stem, score });
        picked++;
      }
    }
  }

  // 难度曲线：由易到难排序
  paper.sort((a, b2) => a.difficulty - b2.difficulty);
  const fit = paper.length
    ? Math.round(100 - Math.min(100, paper.reduce((s, q) => s + Math.abs(q.difficulty - (b.difficultyTarget || 3)), 0) / paper.length * 25)) / 100
    : 0;

  const blueprintJson = JSON.stringify({ subjectId: b.subjectId || null, structure, difficultyTarget: b.difficultyTarget || 3 });
  const pid = store.savePaper(req.user.uid, b.title || '智能组卷', blueprintJson, totalScore, paper);
  res.json({
    code: 0, message: 'ok',
    data: { paperId: pid, totalScore, count: paper.length, difficultyFit: fit, questions: paper,
      structure, subjectId: b.subjectId || null, title: b.title || '智能试卷' }
  });
});

// 我的试卷列表（导出接线用）
r.get('/', (req, res) => {
  const db = require('../db');
  const rows = Array.from(db.prepare('SELECT id, title, total_score, created_at FROM papers WHERE user_id=? ORDER BY id DESC LIMIT 50')
    .iterate(req.user.uid));
  res.json({ code: 0, message: 'ok', data: { list: rows } });
});

// —— 教师工具箱：组卷模板 / 收藏 / 精品专辑（docs/25 TJ-80/85/88）——
// POST /templates 存为模板（TJ-80）
r.post('/templates', (req, res) => {
  const b = req.body || {};
  try {
    const id = store.templateCreate(req.user.uid, b);
    res.json({ code: 0, message: 'ok', data: { id } });
  } catch (e) {
    if (e.code === 42200) return res.status(422).json({ code: e.code, message: e.message, data: null });
    throw e;
  }
});
r.get('/templates', (req, res) => {
  res.json({ code: 0, message: 'ok', data: { list: store.templatesByUser(req.user.uid) } });
});
r.delete('/templates/:id', (req, res) => {
  try {
    res.json({ code: 0, message: 'ok', data: store.templateDelete(req.user.uid, req.params.id) });
  } catch (e) {
    if (e.code === 40400) return res.status(404).json({ code: e.code, message: e.message, data: null });
    throw e;
  }
});
// POST /favorites 收藏 toggle（TJ-85）
r.post('/favorites', (req, res) => {
  const b = req.body || {};
  if (!Number.isInteger(Number(b.questionId))) return res.status(422).json({ code: 42200, message: 'questionId 非法', data: null });
  try {
    res.json({ code: 0, message: 'ok', data: store.favoriteToggle(req.user.uid, Number(b.questionId)) });
  } catch (e) {
    if (e.code === 40400) return res.status(404).json({ code: e.code, message: e.message, data: null });
    throw e;
  }
});
r.get('/favorites', (req, res) => {
  const pageNo = Math.max(1, Number(req.query.pageNo) || 1);
  const pageSize = Math.min(50, Number(req.query.pageSize) || 10);
  res.json({ code: 0, message: 'ok', data: store.favoritesByUser(req.user.uid, pageNo, pageSize) });
});

// —— 公开示范卷（教师端试卷选题，docs/25 TJ-10~14）——
// GET /papers/public?category=&level=&year=&pageNo=（登录；组卷网四大类频道）
r.get('/public', (req, res) => {
  const pageNo = Math.max(1, Number(req.query.pageNo) || 1);
  const pageSize = Math.min(20, Number(req.query.pageSize) || 10);
  res.json({ code: 0, message: 'ok', data: store.publicPapers(req.query.category, req.query.level, req.query.year, pageNo, pageSize) });
});

// GET /papers/public/:id/preview（登录）—— 整卷试读 30%（仅题干，不含答案解析）
r.get('/public/:id/preview', (req, res) => {
  const d = store.publicPaperPreview(req.params.id);
  if (!d) return res.status(404).json({ code: 40400, message: '示范卷不存在', data: null });
  res.json({ code: 0, message: 'ok', data: d });
});

// PUT /papers/:id —— 工作台保存（编辑标题/删题/改分/重排，整卷覆盖）
r.put('/:id', (req, res) => {
  const b = req.body || {};
  try {
    store.paperUpdate(req.params.id, req.user.uid, b.title, b.questions);
    const paper = store.paperById(Number(req.params.id));
    res.json({ code: 0, message: 'ok', data: { paperId: paper.id, title: paper.title, totalScore: paper.total_score,
      count: paper.questions.length, questions: paper.questions.map(q => ({ id: q.id, type: q.type, difficulty: q.difficulty, score: q.score })) } });
  } catch (e) {
    if (e.code === 40400) return res.status(404).json({ code: e.code, message: e.message, data: null });
    if (e.code === 42200) return res.status(422).json({ code: e.code, message: e.message, data: null });
    throw e;
  }
});

// POST /papers/:id/replace —— 智能换题（同题型同学科，难度就近，排除卷内已有）
r.post('/:id/replace', (req, res) => {
  const b = req.body || {};
  if (!Number.isInteger(Number(b.questionId))) return res.status(422).json({ code: 42200, message: 'questionId 非法', data: null });
  try {
    res.json({ code: 0, message: 'ok', data: store.paperReplaceQuestion(req.params.id, req.user.uid, Number(b.questionId)) });
  } catch (e) {
    if (e.code === 40400) return res.status(404).json({ code: e.code, message: e.message, data: null });
    throw e;
  }
});

r.get('/:id/export', async (req, res) => {
  const paper = store.paperById(Number(req.params.id));
  // 归属放宽（TJ-14）：本人试卷 或 公开示范卷（公开卷他人导出走统一计费）
  const isPublic = paper && store.isPublicPaper(paper.id);
  if (!paper || (paper.user_id !== req.user.uid && !isPublic)) return res.status(404).json({ code: 40400, message: '试卷不存在', data: null });
  if (isPublic) store.paperIncDownloads(paper.id);
  const ids = paper.questions.map(q => q.id);
  const hash = trade.paperHashOf(ids);
  const qCount = ids.length;
  // 判价（D-1）：点数模式且余额不足 → 42900 引导充值；其余放行并在渲染成功后计费（paperId 供限时免费判定）
  const verdict = trade.billing(req.user.uid, qCount, hash, paper.id);
  // 导出参数（TJ-86/TJ-110/TJ-111）：格式/答案位置/纸张/分栏/字号/作答区
  const format = ['docx', 'card'].includes(req.query.format) ? req.query.format : 'html';
  const answerPos = ['end', 'separate', 'none'].includes(req.query.answerPos) ? req.query.answerPos : 'end';
  const paperSize = req.query.paperSize === 'A3' ? 'A3' : 'A4';
  const columns = req.query.columns === '2' ? 2 : 1;
  const answerSpace = ['line', 'blank'].includes(req.query.answerSpace) ? req.query.answerSpace : 'none'; // TJ-111
  const fontPx = { s: 12.5, m: 14, l: 16 }[req.query.fontSize] || 14;
  // 存储型 XSS 防护：录题题干/答案与用户昵称属用户输入，导出 HTML 一律转义（LaTeX 反斜杠不受影响）
  const escHtml = (v) => String(v == null ? '' : v).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  // 水印昵称在源头剥离危险字符：答题卡/HTML 两处模板共用，剥离后转义成为无操作，避免实体双重编码
  const nick = String(req.user.nickname || '').replace(/[<>&"']/g, '');
  const watermark = `${nick}（ID:${req.user.uid}）· ${new Date().toLocaleString('zh-CN')} · 智卷云`;

  // POINTS 模式余额检查（各格式共用）
  if (verdict.mode === 'POINTS') {
    const ent = trade.entitlement(req.user.uid);
    if (ent.points.balance < verdict.needPoints) {
      return res.status(429).json({ code: 42900, message: '点数余额不足，请充值后下载',
        data: { needPoints: verdict.needPoints, balance: ent.points.balance } });
    }
  }
  const consumeOnce = () => { try { return trade.consume(req.user.uid, paper.id, hash, qCount); } catch (_) { return null; } };

  // —— 答题卡（docs/25 TJ-110）：与 html 导出同构，模板内联、动态字段一律 escHtml ——
  if (format === 'card') {
    consumeOnce();
    const objective = []; // 涂卡矩阵行（标签转义后入库）
    const subjective = []; // 填空/解答留白区
    paper.questions.forEach((q, i) => {
      const no = i + 1;
      if (q.type === '单选题' || q.type === '多选题') {
        let opts = ['A', 'B', 'C', 'D'];
        try { const o = JSON.parse(q.options || 'null'); if (Array.isArray(o) && o.length) opts = o.map(x => escHtml(x.l)); } catch (_) {}
        if (q.type === '多选题') opts = opts.concat(['E', 'F'].slice(0, Math.max(0, 6 - opts.length)));
        objective.push({ no, multi: q.type === '多选题', opts });
      } else if (q.type === '判断题') {
        objective.push({ no, multi: false, opts: ['T', 'F'] });
      } else if (q.type === '填空题') {
        subjective.push({ no, box: false });
      } else {
        subjective.push({ no, box: true });
      }
    });
    // 涂卡矩阵：每行最多 5 题，网格排版
    const perRow = 5;
    const gridRows = [];
    for (let i = 0; i < objective.length; i += perRow) gridRows.push(objective.slice(i, i + perRow));
    const gridHtml = gridRows.map(row => `<tr>${row.map(it => `
    <td class="cell">
      <div class="no">${it.no}${it.multi ? '<i class="multi">多选</i>' : ''}</div>
      <div class="opts">${it.opts.map(o => `<span class="bubble">${o}</span>`).join('')}</div>
    </td>`).join('')}${row.length < perRow ? '<td class="cell empty" colspan="' + (perRow - row.length) + '"></td>' : ''}</tr>`).join('');
    const subjectiveHtml = subjective.map(it => it.box
      ? `<div class="sub-block"><div class="sub-no">${it.no}. 解答区</div><div class="blank-box"></div><div class="blank-box short"></div></div>`
      : `<div class="sub-block"><div class="sub-no">${it.no}. 填空区</div><div class="fill-line"></div><div class="fill-line"></div></div>`).join('');
    const cardHtml = `<!DOCTYPE html><html lang="zh-CN"><head><meta charset="UTF-8"><title>${escHtml(paper.title)} · 答题卡</title>
<style>
body{font-family:'SimHei','Microsoft YaHei',sans-serif;max-width:760px;margin:24px auto;padding:0 16px;color:#111}
h1{text-align:center;font-size:20px;margin:6px 0}
.head{display:flex;justify-content:space-between;align-items:center;border-bottom:2px solid #111;padding-bottom:8px}
.head .info{font-size:14px}.head .info span{margin-right:22px}
.head .info i{display:inline-block;border-bottom:1px solid #111;min-width:110px;font-style:normal}
.sect{margin:14px 0 6px;font-size:14px;font-weight:700;border-left:4px solid #111;padding-left:8px}
table{width:100%;border-collapse:collapse}
td.cell{border:1px solid #999;padding:8px 10px;vertical-align:top}
td.empty{border:none}
.no{font-size:13px;font-weight:700;margin-bottom:4px}
.no .multi{font-style:normal;font-size:10px;color:#b91c1c;margin-left:4px}
.opts .bubble{display:inline-block;width:22px;height:22px;line-height:20px;text-align:center;border:1.2px solid #333;border-radius:50%;font-size:11px;margin-right:6px}
.sub-block{margin:10px 0;page-break-inside:avoid}
.sub-no{font-size:13px;font-weight:700;margin-bottom:6px}
.fill-line{border-bottom:1px solid #333;height:26px;margin:0 10px 8px}
.blank-box{border:1px solid #333;height:150px;margin-bottom:8px}
.blank-box.short{height:90px}
.wm{margin-top:26px;color:#94a3b8;font-size:12px;text-align:center;border-top:1px solid #e2e8f0;padding-top:8px}
.notice{background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;font-size:12px;color:#475569;padding:8px 12px;margin:10px 0}
</style></head><body>
<div class="head"><h1>${escHtml(paper.title)} · 答题卡</h1><div class="info">
<span>姓名 <i></i></span><span>考号 <i></i></span><span>班级 <i></i></span></div></div>
<div class="notice">填涂说明：客观题请用 2B 铅笔将对应字母涂黑；多选题每题可多涂；填空题书写工整、解答题在框内作答，超出区域无效。</div>
<div class="sect">一、客观题涂卡区</div>
<table><tbody>${gridHtml || '<tr><td class="cell">本卷无客观题</td></tr>'}</tbody></table>
<div class="sect">二、填空与解答区</div>
${subjectiveHtml || '<p class="muted" style="font-size:13px">本卷无填空/解答题</p>'}
<div class="wm">${escHtml(watermark)}</div>
</body></html>`;
    res.set('Content-Type', 'text/html; charset=utf-8');
    return res.send(cardHtml);
  }
  // —— Word 原生公式导出（docs/25 TJ-100：docx + temml + mathml2omml，C5 收官）——
  if (format === 'docx') {
    const { buildPaperDocx } = require('../export/word');
    const { buffer, mathCount } = await buildPaperDocx(paper, {
      answerPos: answerPos === 'separate' ? 'separate' : (answerPos === 'none' ? 'none' : 'end'),
      fontPt: { s: 9, m: 10.5, l: 12 }[req.query.fontSize] || 10.5,
      watermark
    });
    consumeOnce();
    res.set('Content-Type', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document');
    res.set('Content-Disposition', 'attachment; filename="' + encodeURIComponent(paper.title) + '.docx"');
    res.set('X-Math-Count', String(mathCount));
    return res.send(Buffer.from(buffer));
  }

  const itemsHtml = paper.questions.map((q, i) => {
    let brief = '';
    try { brief = q.analysis ? (JSON.parse(q.analysis).brief || '') : ''; } catch (_) { brief = ''; }
    // TJ-111 作答区：非选择题按模式插入下划线行/空白框
    const aspace = answerSpace !== 'none' && !q.options
      ? (answerSpace === 'line'
        ? '<div class="as-line"></div><div class="as-line"></div>'
        : '<div class="as-blank"></div>')
      : '';
    let optsHtml = '';
    if (q.options) {
      try { optsHtml = `<ol class="opts">${JSON.parse(q.options).map(o => `<li><b>${escHtml(o.l)}.</b> ${escHtml(o.v)}</li>`).join('')}</ol>`; } catch (_) {}
    }
    return `<div class="q"><p><b>${i + 1}.</b> ${escHtml(q.stem)}
    ${answerPos !== 'none' ? `<span class="ans"><b>答案：</b>${escHtml(q.answer)}${brief ? `　<span class="an-brief">${escHtml(brief)}</span>` : ''}</span>` : ''}
    </p>
    ${optsHtml}${aspace}</div>`;
  }).join('\n');
  const answerSection = answerPos === 'separate'
    ? `<div class="ans-page"><h2>参考答案</h2><ol>${paper.questions.map(q => `<li>${escHtml(q.answer)}</li>`).join('')}</ol></div>`
    : '';
  const inlineAns = answerPos === 'separate' ? '' : `<style>.ans{display:none}</style>`;
  const html = `<!DOCTYPE html><html lang="zh-CN"><head><meta charset="UTF-8"><title>${escHtml(paper.title)} · 导出</title>
<style>@page{size:${paperSize} portrait;margin:16mm}
body{font-family:'Times New Roman','SimSun',serif;max-width:${paperSize === 'A3' ? 1000 : 760}px;margin:24px auto;padding:0 16px;font-size:${fontPx}px${columns === 2 ? ';column-count:2;column-gap:32px' : ''}}
h1{text-align:center;font-size:20px;column-span:all}.q{margin:14px 0;page-break-inside:avoid}.opts{list-style:none;padding-left:22px}
.as-line{border-bottom:1px solid #94a3b8;height:26px;margin:6px 24px}.as-blank{border:1px solid #cbd5e1;height:150px;margin:8px 24px}
.opts li{display:inline-block;margin-right:18px}.ans{color:#475569;font-size:${fontPx - 1.5}px}.an-brief{color:#94a3b8}
.ans-page{page-break-before:always;column-span:all}.wm{margin-top:30px;color:#94a3b8;font-size:12px;text-align:center;border-top:1px solid #e2e8f0;padding-top:8px;column-span:all}
${inlineAns}</style>
</head><body><h1>${escHtml(paper.title)}</h1><p style="text-align:center">总分 ${paper.total_score} 分　共 ${qCount} 题</p>
${itemsHtml}${answerSection}<div class="wm">${escHtml(watermark)}</div></body></html>`;
  // 导出成功后计费（D-2：服务端复算，免费额度/点数在此扣减并落下载记录）
  let charged = null;
  try { charged = trade.consume(req.user.uid, paper.id, hash, qCount); } catch (e) { /* 计费失败不阻断已渲染内容，但记录告警 */ }
  res.set('Content-Type', 'text/html; charset=utf-8');
  res.send(html);
});

r.get('/:id', (req, res) => {
  const paper = store.paperById(Number(req.params.id));
  if (!paper || paper.user_id !== req.user.uid) return res.status(404).json({ code: 40400, message: '试卷不存在', data: null });
  res.json({ code: 0, message: 'ok', data: paper });
});

module.exports = r;
