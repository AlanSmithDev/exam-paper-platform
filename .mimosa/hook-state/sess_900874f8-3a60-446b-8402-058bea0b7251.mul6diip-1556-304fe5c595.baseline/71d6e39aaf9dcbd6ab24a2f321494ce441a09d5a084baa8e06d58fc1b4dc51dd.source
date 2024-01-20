# -*- coding: utf-8 -*-
"""
B 类：公开真题 PDF 采集管线（下载 → 文本层提取 → 切题 → 答案配对 → 入库）。

- 扫描件检测：每页字符数过低判定为扫描件 → 需 OCR（本机未装 PaddleOCR，直接跳过并记录）。
- 答案配对：按题号把「参考答案/评分参考」段落映射回题目；选择题答案字母必须在选项范围内。
- 质量分级：文本层 PDF 且配对成功 → B；配对失败不入库（宁缺毋假）。
- 仅允许 edu.cn / gov.cn 域（safe_http 白名单）。

用法：
  python harvest_pdf.py                     # 采集 PDF_SOURCES 中的清单
  python harvest_pdf.py --url <pdf直链> --stage 高中 --subject 数学 --year 2024 --region 新课标I
"""
import os, sys, re, json, time, argparse, collections
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import pymupdf   # PyMuPDF

from safe_http import safe_get
from tiku_core import BankWriter
from harvest import split_options
from harvest_hf import split_options_inline, s

# 已核实的官方 PDF 直链（edu.cn/gov.cn 域）。后续发现新源往这里加。
PDF_SOURCES = [
    # (url, 学段, 学科, 年份, 地区/卷别)
]


def fetch_pdf(url):
    raw = safe_get(url, binary=True, timeout=120)
    if not raw or not raw.startswith(b"%PDF"):
        raise ValueError("非 PDF 内容 (%s)" % url[:60])
    return raw


def pdf_text(raw):
    """提取文本；返回 (全文, 每页字符数列表)。扫描件每页字符数会极低。"""
    doc = pymupdf.open(stream=raw, filetype="pdf")
    pages, counts = [], []
    for pg in doc:
        t = pg.get_text("text") or ""
        pages.append(t)
        counts.append(len(t.strip()))
    doc.close()
    return "\n".join(pages), counts


QNUM_RE = re.compile(r"(?:^|\n)\s*(\d{1,3})\s*[．.、)]\s*")
ANS_SPLIT_RE = re.compile(r"(参考答案|答案[与及]解析|答案[：:]|评分参考|评分意见)")


def segment(text):
    """切题 + 答案配对。返回 [(题号, 题干, 答案文本)]"""
    m = ANS_SPLIT_RE.search(text)
    if not m:
        return []
    q_part, a_part = text[:m.start()], text[m.end():]

    qs = []          # [(题号, 起止)]
    for mm in QNUM_RE.finditer(q_part):
        qs.append((int(mm.group(1)), mm.start()))
    if not qs:
        return []

    blocks = {}
    for i, (num, start) in enumerate(qs):
        end = qs[i + 1][1] if i + 1 < len(qs) else len(q_part)
        stem = q_part[start:end]
        stem = re.sub(r"^\s*\d{1,3}\s*[．.、)]\s*", "", stem).strip()
        blocks.setdefault(num, stem)     # 重复题号保留首个

    answers = {}
    for mm in QNUM_RE.finditer(a_part):
        num = int(mm.group(1))
        start = mm.start()
        answers.setdefault(num, start)
    a_nums = sorted(answers.items(), key=lambda x: x[1])
    ans_text = {}
    for i, (num, start) in enumerate(a_nums):
        end = a_nums[i + 1][1] if i + 1 < len(a_nums) else len(a_part)
        ans_text[num] = a_part[start:end].strip()

    out = []
    for num, stem in blocks.items():
        a = ans_text.get(num, "")
        if a:
            out.append((num, stem, a))
    return out


LETTER_RE = re.compile(r"^[A-H]{1,4}$")


def clean_answer(ans):
    """从答案段落提取选择题字母（如 'D' / 'AC'）"""
    a = ans.strip()
    m = re.match(r"^\s*([A-H]{1,4})\b", a)
    if m:
        return m.group(1)
    m = re.match(r"^\s*[（(]?\s*([A-H]{1,4})\s*[)）]?\s*$", a[:12])
    if m:
        return m.group(1)
    return ""


def harvest_pdf_url(writer, log, url, stage, subject, year=None, region="",
                    source="公开真题PDF", min_qchars=8):
    raw = fetch_pdf(url)
    text, counts = pdf_text(raw)
    total_chars = sum(counts)
    scanned = total_chars < 150 * max(len(counts), 1)
    log("[pdf] %s 页数 %d 字符 %d %s"
        % (url.rsplit("/", 1)[-1][:40], len(counts), total_chars,
           "【疑似扫描件，跳过(需OCR)】" if scanned else ""))
    if scanned:
        return 0, "scanned"
    segs = segment(text)
    cnt = 0
    for num, stem, ans in segs:
        stem = split_options_inline(stem)[0] if False else stem   # 保留原始行
        stem2, opts = split_options(stem)
        if not opts:
            stem2, opts = split_options_inline(stem2)
        if len(stem2) < min_qchars:
            continue
        if opts:
            a = clean_answer(ans)
            if not a:
                continue    # 选择题配不出字母答案 → 丢弃
            valid = all(ch in {o["label"] for o in opts} for ch in a)
            if not valid:
                continue
        else:
            a = ans[:300]   # 主观题答案文本
            if len(a) < 2:
                continue
        q = {
            "stage": stage, "subject": subject, "stem": stem2, "options": opts,
            "answer": a, "analysis": "", "source": source, "source_url": url,
            "year": year, "region": region, "quality": "B",
        }
        from tiku_core import normalize_question
        qn = normalize_question(**q)
        if writer.add(qn):
            cnt += 1
    writer.flush()
    log("[pdf] 入库 %d 题（切题 %d）" % (cnt, len(segs)))
    return cnt, "ok"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", default="", help="单条 PDF 直链")
    ap.add_argument("--stage", default="高中")
    ap.add_argument("--subject", default="数学")
    ap.add_argument("--year", type=int, default=None)
    ap.add_argument("--region", default="")
    args = ap.parse_args()

    def log(msg):
        print(msg, flush=True)

    writer = BankWriter()
    log("去重基数: %d" % len(writer.seen))
    t0 = time.time()
    if args.url:
        harvest_pdf_url(writer, log, args.url, args.stage, args.subject,
                        args.year, args.region)
    else:
        stats = collections.Counter()
        for url, stage, subject, year, region in PDF_SOURCES:
            try:
                n, note = harvest_pdf_url(writer, log, url, stage, subject, year, region)
                stats[note] += 1
            except Exception as e:
                log("[pdf] FAIL %s -> %s" % (url[:60], str(e)[:80]))
                stats["fail"] += 1
        log("清单结果: %s" % dict(stats))
    writer.flush()
    log("耗时 %.0fs" % (time.time() - t0))


if __name__ == "__main__":
    main()
