# -*- coding: utf-8 -*-
"""
题库采集主程序 —— 多数据源适配器

数据源：
  1. gaokao      : OpenLMLab/GAOKAO-Bench   高考真题（2010-2022，9 学科，含 LaTeX 与解析）
  2. kaoyan      : LIziak112/structured-kaoyan-english  考研英语一/二真题（1998-2025，含配图）
  3. k12         : haolpku/K12-KGraph   K12 数学（小学/初中人教版，含知识点图谱）

用法：
  python harvest.py --source gaokao
  python harvest.py --source kaoyan --images
  python harvest.py --source all --images
"""
import os, sys, json, re, time, argparse
from concurrent.futures import ThreadPoolExecutor, as_completed

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from tiku_core import (BankWriter, fetch_github_raw, http_get, normalize_question,
                       load_catalog, slugify, md5)

# ============================================================
# 1. GAOKAO-Bench
# ============================================================
GAOKAO_REPO = "OpenLMLab/GAOKAO-Bench"
GAOKAO_FILES = [
    # (路径, 学科, 题型)
    ("Data/Objective_Questions/2010-2013_English_MCQs.json", "英语", "单选题"),
    ("Data/Objective_Questions/2010-2022_Biology_MCQs.json", "生物", "单选题"),
    ("Data/Objective_Questions/2010-2022_Chemistry_MCQs.json", "化学", "单选题"),
    ("Data/Objective_Questions/2010-2022_Chinese_Lang_and_Usage_MCQs.json", "语文", "单选题"),
    ("Data/Objective_Questions/2010-2022_Chinese_Modern_Lit.json", "语文", "单选题"),
    ("Data/Objective_Questions/2010-2022_English_Fill_in_Blanks.json", "英语", "填空题"),
    ("Data/Objective_Questions/2010-2022_English_Reading_Comp.json", "英语", "阅读理解"),
    ("Data/Objective_Questions/2010-2022_Geography_MCQs.json", "地理", "单选题"),
    ("Data/Objective_Questions/2010-2022_History_MCQs.json", "历史", "单选题"),
    ("Data/Objective_Questions/2010-2022_Math_I_MCQs.json", "数学", "单选题"),
    ("Data/Objective_Questions/2010-2022_Math_II_MCQs.json", "数学", "单选题"),
    ("Data/Objective_Questions/2010-2022_Physics_MCQs.json", "物理", "单选题"),
    ("Data/Objective_Questions/2010-2022_Political_Science_MCQs.json", "政治", "单选题"),
    ("Data/Objective_Questions/2012-2022_English_Cloze_Test.json", "英语", "完形填空"),
    ("Data/Subjective_Questions/2010-2022_Biology_Open-ended_Questions.json", "生物", "解答题"),
    ("Data/Subjective_Questions/2010-2022_Chemistry_Open-ended_Questions.json", "化学", "解答题"),
    ("Data/Subjective_Questions/2010-2022_Chinese_Language_Ancient_Poetry_Reading.json", "语文", "古诗文阅读"),
    ("Data/Subjective_Questions/2010-2022_Chinese_Language_Classical_Chinese_Reading.json", "语文", "文言文阅读"),
    ("Data/Subjective_Questions/2010-2022_Chinese_Language_Famous_Passages_and_Sentences_Dictation.json", "语文", "名句默写"),
    ("Data/Subjective_Questions/2010-2022_Chinese_Language_Language_and_Writing_Skills_Open-ended_Questions.json", "语文", "语言文字运用"),
    ("Data/Subjective_Questions/2010-2022_Chinese_Language_Literary_Text_Reading.json", "语文", "文学类文本阅读"),
    ("Data/Subjective_Questions/2010-2022_Chinese_Language_Practical_Text_Reading.json", "语文", "实用类文本阅读"),
    ("Data/Subjective_Questions/2010-2022_Geography_Open-ended_Questions.json", "地理", "解答题"),
    ("Data/Subjective_Questions/2010-2022_History_Open-ended_Questions.json", "历史", "解答题"),
    ("Data/Subjective_Questions/2010-2022_Math_I_Fill-in-the-Blank.json", "数学", "填空题"),
    ("Data/Subjective_Questions/2010-2022_Math_I_Open-ended_Questions.json", "数学", "解答题"),
    ("Data/Subjective_Questions/2010-2022_Math_II_Fill-in-the-Blank.json", "数学", "填空题"),
    ("Data/Subjective_Questions/2010-2022_Math_II_Open-ended_Questions.json", "数学", "解答题"),
    ("Data/Subjective_Questions/2010-2022_Physics_Open-ended_Questions.json", "物理", "解答题"),
    ("Data/Subjective_Questions/2010-2022_Political_Science_Open-ended_Questions.json", "政治", "解答题"),
    ("Data/Subjective_Questions/2012-2022_English_Language_Error_Correction.json", "英语", "短文改错"),
    ("Data/Subjective_Questions/2014-2022_English_Language_Cloze_Passage.json", "英语", "完形填空"),
]

OPT_RE = re.compile(r"^\s*([A-D])[\.、．\)．]\s*(.+)$")


def split_options(stem):
    """从题干里拆出 A. B. C. D. 选项"""
    lines = stem.split("\n")
    opts, kept = [], []
    for ln in lines:
        m = OPT_RE.match(ln.strip())
        if m and len(opts) < 8:
            opts.append({"label": m.group(1), "content": m.group(2).strip()})
        else:
            kept.append(ln)
    if len(opts) >= 2:
        return "\n".join(kept).strip(), opts
    return stem, []


def harvest_gaokao(writer, log):
    n = 0
    for path, subject, qtype in GAOKAO_FILES:
        try:
            txt = fetch_github_raw(GAOKAO_REPO, path)
            data = json.loads(txt)
            examples = data.get("example", []) if isinstance(data, dict) else data
            cnt = 0
            for ex in examples:
                stem = ex.get("question", "")
                if not stem.strip():
                    continue
                stem2, opts = split_options(stem)
                ans = ex.get("answer", "")
                if isinstance(ans, list):
                    ans = "".join(ans) if all(len(x) == 1 for x in ans) else "；".join(ans)
                q = normalize_question(
                    stage="高中", subject=subject,
                    stem=stem2, options=opts, answer=ans,
                    analysis=ex.get("analysis", ""),
                    qtype=qtype,
                    year=int(ex["year"]) if str(ex.get("year", "")).isdigit() else None,
                    grade="高三",
                    knowledge=[data.get("keywords", "")] if isinstance(data, dict) else [],
                    source="GAOKAO-Bench(高考真题)",
                    source_url="https://github.com/%s" % GAOKAO_REPO,
                    region=ex.get("category", ""),
                    score=ex.get("score"),
                )
                if writer.add(q):
                    cnt += 1
            n += cnt
            log("[gaokao] %-46s %4d 题" % (os.path.basename(path)[:46], cnt))
        except Exception as e:
            log("[gaokao] FAIL %s -> %s" % (path, str(e)[:70]))
    return n


# ============================================================
# 2. 考研英语真题（1998-2025，英语一/二）
# ============================================================
KY_REPO = "LIziak112/structured-kaoyan-english"
KY_YEARS = (
    [("%d" % y, None) for y in range(1998, 2010)] +
    [("%d-%d" % (y, k), k) for y in range(2010, 2026) for k in (1, 2)]
)


def walk_questions(node, out, ctx):
    """递归遍历任意 JSON，抽取含 question/answer 的对象"""
    if isinstance(node, dict):
        keys = {k.lower(): k for k in node.keys()}
        qk = keys.get("question") or keys.get("stem") or keys.get("题干")
        if qk and isinstance(node[qk], str) and node[qk].strip():
            ans = node.get("answer") or node.get("answers") or node.get("正确答案") or ""
            opts = node.get("options") or node.get("choices") or []
            if isinstance(opts, dict):
                opts = [{"label": k, "content": v} for k, v in opts.items()]
            elif isinstance(opts, list) and opts and isinstance(opts[0], str):
                opts = [{"label": chr(65 + i), "content": v} for i, v in enumerate(opts)]
            out.append({
                "stem": node[qk],
                "answer": ans,
                "options": opts,
                "analysis": node.get("analysis") or node.get("explanation") or node.get("解析") or "",
                "ctx": ctx,
            })
        for k, v in node.items():
            walk_questions(v, out, ctx)
    elif isinstance(node, list):
        for v in node:
            walk_questions(v, out, ctx)


def harvest_kaoyan(writer, log, with_images=True, max_workers=6):
    jobs = []
    for tag, _ in KY_YEARS:
        jobs.append(tag)

    def one(tag):
        url = "data/%s/%s.json" % (tag, tag)
        try:
            data = json.loads(fetch_github_raw(KY_REPO, url))
        except Exception as e:
            return tag, 0, "FAIL %s" % str(e)[:60]
        exam = data.get("exam", "英语一")
        subject = "英语一" if "一" in str(exam) else "英语二"
        year = data.get("year")
        out = []
        walk_questions(data.get("sections", data), out, tag)

        # 先收集本卷所有配图并下载，拿回相对路径（必须在建题前完成，否则关联不上）
        img_paths = []
        if with_images:
            for m in re.finditer(r'"(?:image|img|figure|asset)":\s*"([^"]+\.(?:jpg|png|jpeg|gif))"',
                                 json.dumps(data, ensure_ascii=False)):
                rel = m.group(1)
                if rel.startswith("http"):
                    u = rel
                else:
                    u = "https://raw.githubusercontent.com/%s/main/data/%s/%s" % (KY_REPO, tag, rel.lstrip("./"))
                p = writer.save_image("考研", subject, u)
                if p:
                    img_paths.append(p)

        cnt = 0
        for it in out:
            stem = it["stem"]
            if len(stem.strip()) < 5:
                continue
            q = normalize_question(
                stage="考研", subject=subject, stem=stem,
                options=it["options"], answer=it["answer"],
                analysis=it["analysis"], qtype="阅读理解",
                year=year if isinstance(year, int) else None,
                source="考研英语真题(结构化)",
                source_url="https://github.com/%s" % KY_REPO,
                region=tag,
                images=list(img_paths),   # 整卷配图挂到该卷题目上
            )
            if writer.add(q):
                cnt += 1
        return tag, cnt, ("img=%d" % len(img_paths))

    total = 0
    with ThreadPoolExecutor(max_workers=max_workers) as ex:
        futs = {ex.submit(one, t): t for t in jobs}
        for f in as_completed(futs):
            tag, cnt, note = f.result()
            total += cnt
            log("[kaoyan] %-8s %4d 题  %s" % (tag, cnt, note))
    return total


# ============================================================
# 3. K12 数学（小学/初中人教版）
# ============================================================
K12_REPO = "haolpku/K12-KGraph"
K12_FILES = [
    ("demo/benchmark/task1_subtask1.jsonl", "小学", "数学"),
    ("demo/benchmark/task1_subtask2.jsonl", "小学", "数学"),
    ("demo/benchmark/task2_subtask1.jsonl", "小学", "数学"),
    ("demo/benchmark/task2_subtask2.jsonl", "小学", "数学"),
    ("demo/benchmark/task3.jsonl", "初中", "数学"),
    ("demo/benchmark/task4_subtask1.jsonl", "初中", "数学"),
    ("demo/benchmark/task4_subtask2.jsonl", "初中", "数学"),
    ("demo/benchmark/task5_subtask1.jsonl", "初中", "数学"),
    ("demo/benchmark/task5_subtask2.jsonl", "初中", "数学"),
    ("demo/sft_qa/math_primary_rjb_node.sample.jsonl", "小学", "数学"),
    ("demo/sft_qa/math_primary_rjb_edge.sample.jsonl", "小学", "数学"),
]


def harvest_k12(writer, log):
    n = 0
    for path, stage, subject in K12_FILES:
        try:
            txt = fetch_github_raw(K12_REPO, path)
            cnt = 0
            for line in txt.splitlines():
                line = line.strip()
                if not line:
                    continue
                try:
                    o = json.loads(line)
                except Exception:
                    continue
                # 常见字段：question / q / prompt / input； answer / a / output / response
                stem = ""
                for k in ("question", "q", "prompt", "input", "problem", "query"):
                    if isinstance(o.get(k), str) and len(o[k]) > 5:
                        stem = o[k]
                        break
                if not stem:
                    continue
                ans = ""
                for k in ("answer", "a", "output", "response", "solution", "label"):
                    if o.get(k):
                        ans = o[k] if isinstance(o[k], str) else json.dumps(o[k], ensure_ascii=False)
                        break
                stem2, opts = split_options(stem)
                q = normalize_question(
                    stage=stage, subject=subject, stem=stem2, options=opts, answer=ans,
                    analysis=o.get("analysis") or o.get("explanation") or "",
                    knowledge=[o.get("knowledge", o.get("point", ""))] if o.get("knowledge") or o.get("point") else [],
                    source="K12-KGraph(人教版)",
                    source_url="https://github.com/%s" % K12_REPO,
                    region="人教版",
                )
                if writer.add(q):
                    cnt += 1
            n += cnt
            log("[k12] %-46s %4d 题" % (os.path.basename(path)[:46], cnt))
        except Exception as e:
            log("[k12] FAIL %s -> %s" % (path, str(e)[:70]))
    return n


# ============================================================
# 主流程
# ============================================================
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--source", default="all", choices=["all", "gaokao", "kaoyan", "k12"])
    ap.add_argument("--images", action="store_true", help="是否下载配图")
    ap.add_argument("--workers", type=int, default=6)
    args = ap.parse_args()

    logf = open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "harvest.log"),
                "a", encoding="utf-8")

    def log(msg):
        print(msg, flush=True)
        logf.write(msg + "\n")
        logf.flush()

    log("\n===== 采集开始 %s | source=%s =====" % (
        time.strftime("%Y-%m-%d %H:%M:%S"), args.source))

    writer = BankWriter()
    log("已有题目去重基数: %d" % len(writer.seen))

    t0 = time.time()
    total = 0
    if args.source in ("all", "gaokao"):
        total += harvest_gaokao(writer, log)
    if args.source in ("all", "kaoyan"):
        total += harvest_kaoyan(writer, log, with_images=args.images, max_workers=args.workers)
    if args.source in ("all", "k12"):
        total += harvest_k12(writer, log)

    written = writer.flush()
    stats, tot, img = writer.report()
    log("---- 本次新增(去重后) %d 题，落盘 %d 题，图片 %d，耗时 %.1fs ----"
        % (total, written, img, time.time() - t0))

    for k in sorted(stats):
        log("  %-28s 题目 %6d  答案 %6d  图片 %4d"
            % (k, stats[k]["题目"], stats[k]["答案"], stats[k]["图片"]))

    # 全局统计
    allcnt = 0
    for dirpath, _, files in os.walk(ROOT_LOCAL):
        if os.path.basename(dirpath) == "题目":
            for fn in files:
                if fn.endswith(".jsonl"):
                    try:
                        allcnt += sum(1 for _ in open(os.path.join(dirpath, fn), encoding="utf-8"))
                    except Exception:
                        pass
    log("题库累计总题量: %d" % allcnt)

    with open(os.path.join(ROOT_LOCAL, "_meta", "采集统计.json"), "w", encoding="utf-8-sig") as f:
        json.dump({"更新时间": time.strftime("%Y-%m-%d %H:%M:%S"),
                   "本次新增": total, "累计总量": allcnt,
                   "分学科": {k: v for k, v in stats.items()}}, f, ensure_ascii=False, indent=2)
    log("===== 采集结束 =====\n")


ROOT_LOCAL = r"E:\code\business\zujuan-platform\题库"

if __name__ == "__main__":
    main()
