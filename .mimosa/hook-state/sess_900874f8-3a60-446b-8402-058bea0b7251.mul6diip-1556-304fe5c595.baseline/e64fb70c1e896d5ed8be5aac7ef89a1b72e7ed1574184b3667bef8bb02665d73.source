# -*- coding: utf-8 -*-
"""
题库统计报告：按学段 / 学科 / 题型 / 年份 汇总，输出 Markdown + JSON。
"""
import os, json, collections, time, sys, argparse

ROOT = r"E:\code\business\zujuan-platform\题库"
OUT_MD = os.path.join(ROOT, "_meta", "题库统计报告.md")
OUT_JSON = os.path.join(ROOT, "_meta", "题库统计.json")
DUPES_PATH = os.path.join(ROOT, "_meta", "simhash_dupes.json")


def iter_questions(root=ROOT):
    """遍历所有 题目/*.jsonl"""
    for dirpath, _, files in os.walk(root):
        if os.path.basename(dirpath) != "题目":
            continue
        for fn in sorted(files):
            if not fn.endswith(".jsonl"):
                continue
            fp = os.path.join(dirpath, fn)
            with open(fp, "r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if not line:
                        continue
                    try:
                        yield json.loads(line)
                    except Exception:
                        continue


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--top", type=int, default=30)
    args = ap.parse_args()

    by_stage = collections.Counter()
    by_subject = collections.Counter()      # 学段/学科
    by_type = collections.Counter()
    by_year = collections.Counter()
    by_source = collections.Counter()
    empty_subject = collections.Counter()   # 有目录但 0 题的学科
    has_analysis = 0
    has_options = 0
    has_images = 0
    total = 0
    by_quality = collections.Counter()
    valid_count = 0          # 有答案 + 有溯源 + 非 C 级
    raw_total = 0            # 含近似重复的原始行数
    t0 = time.time()

    # 近似去重清单（dedup_sim.py 产出，可选）
    dup_qids = set()
    if os.path.exists(DUPES_PATH):
        try:
            with open(DUPES_PATH, encoding="utf-8") as f:
                dup_qids = set(json.load(f).get("剔除清单") or [])
        except Exception:
            dup_qids = set()

    for q in iter_questions():
        raw_total += 1
        if q.get("qid") in dup_qids:
            continue
        total += 1
        by_stage[q.get("stage", "?")] += 1
        by_subject["%s/%s" % (q.get("stage", "?"), q.get("subject", "?"))] += 1
        by_type[q.get("type", "?")] += 1
        if q.get("year"):
            by_year[str(q["year"])] += 1
        by_source[q.get("source", "?")] += 1
        if (q.get("analysis") or "").strip():
            has_analysis += 1
        if q.get("options"):
            has_options += 1
        if q.get("images"):
            has_images += 1
        by_quality[q.get("quality") or "B"] += 1
        if (q.get("answer") or "").strip() and (q.get("source_url") or "").strip() \
                and (q.get("quality") or "B") != "C":
            valid_count += 1

    # 学科目录完整性
    cat_path = os.path.join(ROOT, "_meta", "学科目录.json")
    all_subjects = []
    if os.path.exists(cat_path):
        with open(cat_path, encoding="utf-8-sig") as f:   # 目录文件带 BOM
            cat = json.load(f)
        for st in cat["学段"]:
            for s in st["学科"]:
                all_subjects.append("%s/%s" % (st["学段"], s["学科"]))
    for s in all_subjects:
        if by_subject.get(s, 0) == 0:
            empty_subject[s] += 1

    lines = []
    lines.append("# 题库统计报告\n")
    lines.append("生成时间：%s\n" % time.strftime("%Y-%m-%d %H:%M:%S"))
    lines.append("## 总览\n")
    lines.append("| 指标 | 数值 |")
    lines.append("|---|---|")
    lines.append("| 原始题目行数 | %d |" % raw_total)
    lines.append("| 近似重复剔除 | %d |" % len(dup_qids))
    lines.append("| 去重后题目总量 | **%d** |" % total)
    lines.append("| 有效题量（有答案+有溯源+非C级） | **%d** |" % valid_count)
    lines.append("| 质量分级 | A=%d，B=%d，C=%d |" % (
        by_quality.get("A", 0), by_quality.get("B", 0), by_quality.get("C", 0)))
    lines.append("| 学科目录数 | %d |" % len(all_subjects))
    lines.append("| 已有题目的学科数 | %d |" % len([k for k, v in by_subject.items() if v > 0]))
    lines.append("| 尚为空的学科数 | %d |" % len(empty_subject))
    lines.append("| 含解析的题目 | %d（%.1f%%） |" % (has_analysis, 100.0 * has_analysis / max(total, 1)))
    lines.append("| 含选项的题目 | %d（%.1f%%） |" % (has_options, 100.0 * has_options / max(total, 1)))
    lines.append("| 含配图的题目 | %d |" % has_images)
    lines.append("| 统计耗时 | %.1fs |\n" % (time.time() - t0))

    lines.append("## 按学段\n")
    lines.append("| 学段 | 题量 | 占比 |")
    lines.append("|---|---|---|")
    for k, v in by_stage.most_common():
        lines.append("| %s | %d | %.1f%% |" % (k, v, 100.0 * v / max(total, 1)))
    lines.append("")

    lines.append("## 按学科（Top %d）\n" % args.top)
    lines.append("| 学段/学科 | 题量 |")
    lines.append("|---|---|")
    for k, v in by_subject.most_common(args.top):
        lines.append("| %s | %d |" % (k, v))
    lines.append("")

    lines.append("## 按题型\n")
    lines.append("| 题型 | 题量 |")
    lines.append("|---|---|")
    for k, v in by_type.most_common():
        lines.append("| %s | %d |" % (k, v))
    lines.append("")

    lines.append("## 按来源\n")
    lines.append("| 来源 | 题量 |")
    lines.append("|---|---|")
    for k, v in by_source.most_common():
        lines.append("| %s | %d |" % (k, v))
    lines.append("")

    if by_year:
        lines.append("## 按年份\n")
        lines.append("| 年份 | 题量 |")
        lines.append("|---|---|")
        for k, v in sorted(by_year.items()):
            lines.append("| %s | %d |" % (k, v))
        lines.append("")

    if empty_subject:
        lines.append("## 尚无题目的学科（%d 个）\n" % len(empty_subject))
        lines.append("<details><summary>展开全部</summary>\n")
        for k in sorted(empty_subject):
            lines.append("- %s" % k)
        lines.append("\n</details>\n")

    md = "\n".join(lines)
    with open(OUT_MD, "w", encoding="utf-8-sig") as f:
        f.write(md)
    with open(OUT_JSON, "w", encoding="utf-8-sig") as f:
        json.dump({"总量": total, "学段": dict(by_stage), "学科": dict(by_subject),
                   "题型": dict(by_type), "年份": dict(by_year), "来源": dict(by_source),
                   "空学科": list(empty_subject)}, f, ensure_ascii=False, indent=2)

    print("题目总量:", total, "| 有效题量:", valid_count)
    print("质量分级: A=%d B=%d C=%d | 近似剔除: %d" % (
        by_quality.get("A", 0), by_quality.get("B", 0), by_quality.get("C", 0), len(dup_qids)))
    print("学科目录:", len(all_subjects), "| 空学科:", len(empty_subject))
    print("含解析:", has_analysis, "| 含选项:", has_options)
    print("报告:", OUT_MD)


if __name__ == "__main__":
    main()
