// Word 导出（docs/25 TJ-100，C5 收官）：LaTeX → MathML → OMML 管线，公式在 Word 中可二次编辑。
// 参考移植：ref-exam/math_question_bank src/exporters/word_exporter.py（含上游库 bug 修复：
//   ① mathml2omml 输出已带 <m:oMath> 外壳，不可再包一层；② groupChrPr 误闭合；
//   ③ groupChr 缺 vertJc 导致向量/上划线基线偏移；④ 裸 & 非法 XML；⑤ aligned 等环境改写 array）。
// 设计原则：单条公式转换失败只退回该条 LaTeX 原文，绝不中断整份试卷导出。
const temml = require('temml');
const { mml2omml } = require('mathml2omml');
const {
  Document, Packer, Paragraph, TextRun, AlignmentType, Footer,
  ImportedXmlComponent, PageBreak, HeadingLevel
} = require('docx');

const MATH_NS = 'http://schemas.openxmlformats.org/officeDocument/2006/math';
const WORD_NS = 'http://schemas.openxmlformats.org/wordprocessingml/2006/main';
const MATH_FONT = 'Cambria Math';
const OMML_READY = true; // temml/mathml2omml 为直接依赖，缺失时整模块加载即失败

// ---------- LaTeX 预处理：aligned/align/split/gather → array（& 与 \\ 才能正确多行） ----------
const ALIGN_ENVS = ['aligned', 'alignedat', 'split', 'align', 'align*', 'gather', 'gather*', 'gathered', 'multline', 'multline*', 'flalign', 'flalign*', 'eqnarray', 'eqnarray*'];
const ALIGN_ENV_RE = new RegExp('\\\\begin\\{(' + ALIGN_ENVS.map(e => e.replace('*', '\\*')).join('|') + ')\\}([\\s\\S]*?)\\\\end\\{\\1\\}', 'g');

function alignEnvToArray(match, env, body) {
  const rows = body.split(/\\\\/).filter(r => r.trim());
  const ncol = Math.max(1, ...rows.map(r => (r.match(/&/g) || []).length + 1));
  const spec = 'r' + 'l'.repeat(ncol - 1);
  return '\\begin{array}{' + spec + '}' + body + '\\end{array}';
}
const normalizeLatex = (latex) => latex.replace(ALIGN_ENV_RE, alignEnvToArray);

// ---------- OMML 修复 ----------
const BARE_AMP_RE = /&(?!(?:[A-Za-z][A-Za-z0-9]*|#\d+|#x[0-9A-Fa-f]+);)/g;
const GROUPCHR_PR_BUG_RE = /<m:groupChrPr>([\s\S]*?)<\/m:groupChr>/g; // 上游 0.0.2 误闭合
const GROUPCHR_PR_RE = /<m:groupChrPr>([\s\S]*?)<\/m:groupChrPr>/g;
const GROUPCHR_POS_RE = /<m:pos m:val="(top|bot)"\/>/;
const M_R_RE = /<m:r>(<m:rPr>[\s\S]*?<\/m:rPr>)?/g;

// 符号在上(pos=top)配 vertJc=bot，符号在下配 vertJc=top —— 修复向量基线偏移（[MS-OE376] 7.1.2.42）
function addGroupChrVertJc(omml) {
  return omml.replace(GROUPCHR_PR_RE, (m0, inner) => {
    if (inner.includes('<m:vertJc')) return m0;
    const pos = inner.match(GROUPCHR_POS_RE);
    if (!pos) return m0;
    const vj = pos[1] === 'top' ? 'bot' : 'top';
    return '<m:groupChrPr>' + inner.slice(0, pos.index + pos[0].length) + '<m:vertJc m:val="' + vj + '"/>' + inner.slice(pos.index + pos[0].length) + '</m:groupChrPr>';
  });
}
function mathRpr(fontPt) {
  const half = Math.round(fontPt * 2); // OOXML 字号单位半磅
  return '<w:rPr><w:rFonts w:ascii="' + MATH_FONT + '" w:hAnsi="' + MATH_FONT + '" w:cs="' + MATH_FONT + '"/><w:sz w:val="' + half + '"/><w:szCs w:val="' + half + '"/></w:rPr>';
}
const ommlCache = new Map();

/** LaTeX → '<m:oMath>…</m:m:oMath>' 之外的字符串（已修复上游坑、已补命名空间与字体 rPr）。 */
function latexToOmmlString(latex, fontPt) {
  const cacheKey = latex + '@' + fontPt;
  if (ommlCache.has(cacheKey)) return ommlCache.get(cacheKey);
  let mathml = temml.renderToString(normalizeLatex(latex), { displayMode: false, throwOnError: false, annotate: false, xml: true });
  mathml = mathml.replace(BARE_AMP_RE, '');
  let omml = mml2omml(mathml);
  omml = String(omml).replace(GROUPCHR_PR_BUG_RE, '<m:groupChrPr>$1</m:groupChrPr>');
  omml = addGroupChrVertJc(omml);
  const rpr = mathRpr(fontPt);
  omml = omml.replace(M_R_RE, (m0, rprExisting) => '<m:r>' + (rprExisting || '') + rpr);
  if (!omml.includes('xmlns:m=')) {
    omml = omml.replace('<m:oMath', '<m:oMath xmlns:m="' + MATH_NS + '" xmlns:w="' + WORD_NS + '"');
  }
  ommlCache.set(cacheKey, omml);
  if (ommlCache.size > 4096) ommlCache.clear();
  return omml;
}

// ---------- 混排解析：识别 \(..\)/\[..\]（题库存储风格）与 $..$/$$..$$ ----------
const MIX_RE = /(\$\$[\s\S]*?\$\$|\\\[([\s\S]*?)\\\]|\$[^$\n]+?\$|\\\(([\s\S]*?)\\\))/g;
function splitMixed(text) {
  const parts = [];
  let last = 0;
  for (const m of String(text).matchAll(MIX_RE)) {
    if (m.index > last) parts.push({ type: 'text', value: text.slice(last, m.index) });
    const raw = m[0];
    const display = raw.startsWith('$$') || raw.startsWith('\\[');
    const latex = (display ? raw.slice(2, -2) : raw.replace(/^(\\\(|\$)/, '').replace(/(\\\)|\$)$/, '')).trim();
    if (latex) parts.push({ type: 'math', latex, display });
    last = m.index + raw.length;
  }
  if (last < String(text).length) parts.push({ type: 'text', value: String(text).slice(last) });
  return parts;
}

function fontOf(pt) { return { font: '宋体', size: pt * 2, eastAsia: '宋体' }; }

/** 混排文本 → 段落数组：文本 run 与 <m:oMath> 交错；独立公式居中独段。返回追加的 oMath 数。
 *  leading：可选的首段前置 run（题号/选项字母），与第一段正文同段。 */
function emitMixed(paragraphs, text, { fontPt = 10.5, bold = false, leading } = {}) {
  let mathCount = 0;
  let runs = leading ? [...leading] : [];
  const flush = () => { if (runs.length) { paragraphs.push(new Paragraph({ children: runs })); runs = []; } };
  for (const part of splitMixed(text || '')) {
    if (part.type === 'text') {
      if (part.value) runs.push(new TextRun({ text: part.value, bold, ...fontOf(fontPt) }));
      continue;
    }
    try {
      const el = ImportedXmlComponent.fromXmlString(latexToOmmlString(part.latex, fontPt));
      if (part.display) {
        flush();
        paragraphs.push(new Paragraph({ alignment: AlignmentType.CENTER, children: [el] }));
      } else {
        runs.push(el);
      }
      mathCount++;
    } catch (_) {
      // 单条公式失败：退回 LaTeX 原文，不中断整卷
      runs.push(new TextRun({ text: '$' + part.latex + '$', bold, ...fontOf(fontPt) }));
    }
  }
  flush();
  return mathCount;
}

function labeledBlock(paragraphs, label, text, opts = {}) {
  const fontPt = opts.fontPt || 10.5;
  return emitMixed(paragraphs, text || '', { fontPt, leading: [new TextRun({ text: label, bold: true, ...fontOf(fontPt) })] });
}

/**
 * 生成整卷 docx（Buffer）。paper: store.paperById 结果；opts: {answerPos:'end|separate|none', fontPt, watermark}
 */
/** 构建单卷正文段落（供单卷/整辑复用）。返回 { children, mathCount }。 */
function buildPaperBody(paper, { answerPos = 'end', fontPt = 10.5, isFirst = true } = {}) {
  const body = [];
  body.push(new Paragraph({ alignment: AlignmentType.CENTER,
    children: [new TextRun({ text: paper.title, bold: true, font: '黑体', size: 32, eastAsia: '黑体', pageBreakBefore: !isFirst })] }));
  body.push(new Paragraph({ alignment: AlignmentType.CENTER,
    children: [new TextRun({ text: '姓名：__________　学校：__________　得分：__________', ...fontOf(fontPt) })] }));
  body.push(new Paragraph({ alignment: AlignmentType.CENTER,
    children: [new TextRun({ text: '满分 ' + (paper.total_score || 0) + ' 分　共 ' + paper.questions.length + ' 题', ...fontOf(fontPt) })] }));

  let mathCount = 0;
  for (let i = 0; i < paper.questions.length; i++) {
    const q = paper.questions[i];
    const numLabel = [new TextRun({ text: (i + 1) + '. ', bold: true, ...fontOf(fontPt) })];
    mathCount += emitMixed(body, q.stem, { fontPt, leading: numLabel });
    if (q.options) {
      try {
        for (const o of JSON.parse(q.options)) {
          const optLabel = [new TextRun({ text: o.l + '. ', bold: true, ...fontOf(fontPt) })];
          mathCount += emitMixed(body, o.v || '', { fontPt, leading: optLabel });
        }
      } catch (_) { /* 选项 JSON 损坏跳过，不中断 */ }
    }
    const answerText = q.answer || '';
    const analysisText = (() => { try { return q.analysis ? (JSON.parse(q.analysis).brief || '') : ''; } catch (_) { return ''; } })();
    if (answerPos === 'end') {
      if (answerText) mathCount += labeledBlock(body, '答案：', answerText, { fontPt });
      if (analysisText) mathCount += labeledBlock(body, '解析：', analysisText, { fontPt });
    }
  }

  if (answerPos === 'separate') {
    body.push(new Paragraph({ children: [new PageBreak()] }));
    body.push(new Paragraph({ alignment: AlignmentType.CENTER,
      children: [new TextRun({ text: '参考答案', bold: true, font: '黑体', size: 32, eastAsia: '黑体' })] }));
    paper.questions.forEach((q, i) => {
      mathCount += emitMixed(body, (i + 1) + '. ' + (q.answer || '—'), { fontPt, bold: false });
    });
  }
  return { children: body, mathCount };
}

/** 生成整卷 docx（Buffer）。 */
async function buildPaperDocx(paper, opts = {}) {
  const answerPos = opts.answerPos || 'end';
  const fontPt = opts.fontPt || 10.5;
  const watermark = opts.watermark || '智卷云';
  const { children, mathCount } = buildPaperBody(paper, { answerPos, fontPt, isFirst: true });

  const doc = new Document({
    creator: '智卷云',
    sections: [{
      properties: { page: { margin: { top: 1134, bottom: 1134, left: 1418, right: 1418 } } }, // 2cm/2.5cm（twips）
      footers: { default: new Footer({ children: [new Paragraph({ alignment: AlignmentType.CENTER,
        children: [new TextRun({ text: watermark, color: '94A3B8', size: 18 })] })] }) },
      children
    }]
  });
  const buffer = await Packer.toBuffer(doc);
  return { buffer, mathCount };
}

/** 整辑导出（TJ-112）：多卷合一 docx，卷间分页。 */
async function buildMultiPaperDocx(papers, opts = {}) {
  const fontPt = opts.fontPt || 10.5;
  const watermark = opts.watermark || '智卷云';
  const children = [];
  let mathCount = 0;
  papers.forEach((paper, idx) => {
    const part = buildPaperBody(paper, { answerPos: opts.answerPos || 'end', fontPt, isFirst: idx === 0 });
    children.push(...part.children);
    mathCount += part.mathCount;
  });
  const doc = new Document({
    creator: '智卷云',
    sections: [{
      properties: { page: { margin: { top: 1134, bottom: 1134, left: 1418, right: 1418 } } },
      footers: { default: new Footer({ children: [new Paragraph({ alignment: AlignmentType.CENTER,
        children: [new TextRun({ text: watermark, color: '94A3B8', size: 18 })] })] }) },
      children
    }]
  });
  const buffer = await Packer.toBuffer(doc);
  return { buffer, mathCount };
}

module.exports = { buildPaperDocx, buildMultiPaperDocx, latexToOmmlString, splitMixed };
