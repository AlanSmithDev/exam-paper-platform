# -*- coding: utf-8 -*-
"""
第二批数据源适配器：
  1. gaokao_full : rainewhk/gaokao   465 个 jsonl，按 学科/年份/卷别/题型 组织的高考真题
  2. cmmlu       : haonan-li/cmmlu   67 个学科 CSV（覆盖大学、考研、职业资格考试）

用法：
  python harvest_more.py --source cmmlu
  python harvest_more.py --source gaokao_full
  python harvest_more.py --source all
"""
import os, sys, json, re, time, csv, io, argparse, urllib.parse
from concurrent.futures import ThreadPoolExecutor, as_completed

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from tiku_core import (BankWriter, fetch_github_raw, http_get, normalize_question,
                       slugify, ROOT)
from harvest import split_options

# ============================================================
# 1. rainewhk/gaokao
# ============================================================
GK_REPO = "rainewhk/gaokao"


def list_repo_files(repo, ref="main"):
    """列仓库所有文件；默认分支可能是 main / master / 其他，逐个回退"""
    import ssl, urllib.request
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    last = None
    for r in [ref, "master", "main"]:
        try:
            u = "https://api.github.com/repos/%s/git/trees/%s?recursive=1" % (repo, r)
            d = json.load(urllib.request.urlopen(
                urllib.request.Request(u, headers={"User-Agent": "curl/8"}),
                timeout=25, context=ctx))
            return [t["path"] for t in d.get("tree", []) if t["type"] == "blob"], r
        except Exception as e:
            last = e
    raise last


def harvest_gaokao_full(writer, log, max_workers=10):
    try:
        paths, ref = list_repo_files(GK_REPO)
    except Exception as e:
        log("[gaokao_full] 列目录失败: %s" % str(e)[:70])
        return 0
    log("[gaokao_full] 分支=%s，文件 %d 个" % (ref, len(paths)))

    def one(path):
        # 中文路径需 URL 编码（保留 /）
        try:
            txt = fetch_github_raw(GK_REPO, urllib.parse.quote(path, safe="/"), ref=ref)
        except Exception as e:
            return path, 0, "FAIL %s" % str(e)[:50]
        cnt = 0
        for line in txt.splitlines():
            line = line.strip()
            if not line:
                continue
            try:
                o = json.loads(line)
            except Exception:
                continue
            if not isinstance(o, dict):
                continue
            stem = o.get("question", "")
            if not isinstance(stem, str) or not stem.strip():
                continue
            stem2, opts = split_options(stem)
            ans = o.get("answer", "")
            if isinstance(ans, str) and ans.strip().startswith("["):
                try:
                    ans = "".join(json.loads(ans))
                except Exception:
                    pass
            subject = o.get("subject") or "未知"
            q = normalize_question(
                stage="高中", subject=subject, stem=stem2, options=opts, answer=ans,
                analysis=o.get("analysis", "") or "",
                qtype=o.get("question_type", ""),
                year=int(o["year"]) if str(o.get("year", "")).isdigit() else None,
                grade="高三",
                source="高考真题(%s %s)" % (o.get("paper_type") or "", o.get("exam_type") or ""),
                source_url="https://github.com/%s" % GK_REPO,
                region=o.get("province") or o.get("paper_type") or "",
                score=o.get("score"),
            )
            if writer.add(q):
                cnt += 1
        return path, cnt, ""

    total = 0
    done = 0
    with ThreadPoolExecutor(max_workers=max_workers) as ex:
        futs = {ex.submit(one, p): p for p in paths}
        for f in as_completed(futs):
            path, cnt, note = f.result()
            total += cnt
            done += 1
            if done % 60 == 0 or note:
                log("[gaokao_full] %d/%d 累计 %d 题  %s" % (done, len(paths), total, path))
    log("[gaokao_full] 完成 %d 文件，新增 %d 题" % (len(paths), total))
    return total


# ============================================================
# 2. CMMLU（67 学科）
# ============================================================
CMMLU_REPO = "haonan-li/cmmlu"

# CMMLU 学科 -> (学段, 学科) 映射到本项目目录
CMMLU_MAP = {
    # 小学
    "elementary_mathematics": ("小学", "数学"),
    "elementary_chinese": ("小学", "语文"),
    "elementary_commonsense": ("小学", "科学"),
    "elementary_information_and_technology": ("小学", "信息技术"),
    # 初中
    "middle_school_mathematics": ("初中", "数学"),
    "middle_school_biology": ("初中", "生物"),
    "middle_school_chemistry": ("初中", "化学"),
    "middle_school_physics": ("初中", "物理"),
    "middle_school_politics": ("初中", "道德与法治"),
    "middle_school_geography": ("初中", "地理"),
    "middle_school_history": ("初中", "历史"),
    "middle_school_chinese": ("初中", "语文"),
    "middle_school_english": ("初中", "英语"),
    # 高中
    "high_school_mathematics": ("高中", "数学"),
    "high_school_biology": ("高中", "生物"),
    "high_school_chemistry": ("高中", "化学"),
    "high_school_physics": ("高中", "物理"),
    "high_school_politics": ("高中", "政治"),
    "high_school_geography": ("高中", "地理"),
    "high_school_history": ("高中", "历史"),
    "high_school_chinese": ("高中", "语文"),
    "high_school_english": ("高中", "英语"),
    # 大学
    "college_mathematics": ("大学", "高等数学"),
    "college_engineering_hydrology": ("大学", "土木工程材料"),
    "college_actuarial_science": ("大学", "概率论与数理统计"),
    "college_medical_statistics": ("大学", "统计学"),
    "college_medicine": ("大学", "内科学"),
    "college_law": ("大学", "法理学"),
    "college_education": ("大学", "管理学原理"),
    "computer_science": ("大学", "数据结构"),
    "computer_security": ("大学", "计算机网络"),
    "machine_learning": ("大学", "机器学习"),
    "conceptual_physics": ("大学", "大学物理"),
    "astronomy": ("大学", "大学物理"),
    "college_medical_statistics2": ("大学", "统计学"),
    "electrical_engineering": ("大学", "电路原理"),
    "economics": ("大学", "微观经济学"),
    "management": ("大学", "管理学原理"),
    "marketing": ("大学", "市场营销"),
    "professional_accounting": ("大学", "会计学"),
    "anatomy": ("大学", "系统解剖学"),
    "clinical_knowledge": ("大学", "内科学"),
    "genetics": ("大学", "生物化学"),
    "virology": ("大学", "生物化学"),
    "nutrition": ("大学", "生物化学"),
    "food_science": ("大学", "生物化学"),
    "agronomy": ("大学", "生物化学"),
    "human_sexuality": ("大学", "生理学"),
    "professional_medicine": ("大学", "内科学"),
    "traditional_chinese_medicine": ("大学", "生物化学"),
    "international_law": ("大学", "经济法"),
    "jurisprudence": ("大学", "法理学"),
    "professional_law": ("大学", "民法"),
    "philosophy": ("大学", "马克思主义基本原理"),
    "logical": ("大学", "离散数学"),
    "journalism": ("大学", "大学英语四级"),
    "sports_science": ("大学", "体育与健康") if False else ("大学", "大学英语四级"),
    "chinese_teacher_qualification": ("大学", "管理学原理"),
    "chinese_civil_service_exam": ("大学", "管理学原理"),
    "chinese_driving_rule": ("大学", "大学英语四级"),
    "chinese_food_culture": ("大学", "大学英语四级"),
    "chinese_foreign_policy": ("大学", "马克思主义基本原理"),
    "chinese_history": ("大学", "中国近现代史纲要"),
    "chinese_literature": ("大学", "大学英语四级"),
    "ancient_chinese": ("大学", "大学英语四级"),
    "modern_chinese": ("大学", "大学英语四级"),
    "world_history": ("大学", "中国近现代史纲要"),
    "world_religions": ("大学", "马克思主义基本原理"),
    "sociology": ("大学", "管理学原理"),
    "ethnology": ("大学", "马克思主义基本原理"),
    "education": ("大学", "管理学原理"),
    "public_relations": ("大学", "市场营销"),
    "security_study": ("大学", "计算机网络"),
    "global_facts": ("大学", "大学英语四级"),
    "arts": ("大学", "大学英语四级"),
    "construction_project_management": ("大学", "土木工程材料"),
    "legal_and_moral_basis": ("大学", "思想道德与法治"),
    "marxist_theory": ("考研", "政治"),
    "professional_psychology": ("考研", "312心理学专业基础"),
    "college_education2": ("考研", "311教育学专业基础"),
    "trigonometry": ("高中", "数学"),
}

# 英文 -> 中文学科名（写进 knowledge 便于检索）
CMMLU_ZH = {
    "agronomy": "农学", "anatomy": "解剖学", "ancient_chinese": "古汉语", "arts": "艺术学",
    "astronomy": "天文学", "business_ethics": "商业伦理", "chinese_civil_service_exam": "公务员考试",
    "chinese_driving_rule": "中国驾驶规则", "chinese_food_culture": "中国饮食文化",
    "chinese_foreign_policy": "中国外交政策", "chinese_history": "中国历史",
    "chinese_literature": "中国文学", "chinese_teacher_qualification": "教师资格",
    "clinical_knowledge": "临床知识", "college_actuarial_science": "精算学",
    "college_education": "教育学", "college_engineering_hydrology": "工程水文学",
    "college_law": "法学", "college_mathematics": "大学数学", "college_medical_statistics": "医学统计",
    "college_medicine": "医学院", "computer_science": "计算机科学", "computer_security": "计算机安全",
    "conceptual_physics": "概念物理", "construction_project_management": "工程管理",
    "economics": "经济学", "education": "教育学", "electrical_engineering": "电气工程",
    "elementary_chinese": "小学语文", "elementary_commonsense": "小学常识",
    "elementary_information_and_technology": "小学信息技术", "elementary_mathematics": "小学数学",
    "ethnology": "民族学", "food_science": "食品科学", "genetics": "遗传学", "global_facts": "通识常识",
    "high_school_biology": "高中生物", "high_school_chemistry": "高中化学",
    "high_school_geography": "高中地理", "high_school_mathematics": "高中数学",
    "high_school_physics": "高中物理", "high_school_politics": "高中政治",
    "human_sexuality": "人类性学", "international_law": "国际法", "journalism": "新闻学",
    "jurisprudence": "法理学", "legal_and_moral_basis": "法律与道德基础", "logical": "逻辑学",
    "machine_learning": "机器学习", "management": "管理学", "marketing": "市场营销",
    "marxist_theory": "马克思主义理论", "modern_chinese": "现代汉语", "nutrition": "营养学",
    "philosophy": "哲学", "professional_accounting": "专业会计", "professional_law": "专业法律",
    "professional_medicine": "专业医学", "professional_psychology": "专业心理学",
    "public_relations": "公共关系", "security_study": "安全研究", "sociology": "社会学",
    "sports_science": "体育学", "traditional_chinese_medicine": "中医中药",
    "trigonometry": "三角函数", "virology": "病毒学", "world_history": "世界历史",
    "world_religions": "世界宗教",
}


def harvest_cmmlu(writer, log, max_workers=12):
    try:
        allf, ref = list_repo_files(CMMLU_REPO)
    except Exception as e:
        log("[cmmlu] 列目录失败: %s" % str(e)[:70])
        return 0
    allf = [p for p in allf if p.endswith(".csv")]
    # 只取 test 集（题量最大），dev 作为补充
    testf = sorted([p for p in allf if p.startswith("data/test/") and p.endswith(".csv")])
    devf = sorted([p for p in allf if p.startswith("data/dev/") and p.endswith(".csv")])
    files = testf + devf
    log("[cmmlu] 待处理文件 %d 个（test %d + dev %d）" % (len(files), len(testf), len(devf)))

    def one(path):
        name = os.path.basename(path).replace(".csv", "")
        try:
            txt = fetch_github_raw(CMMLU_REPO, path, ref=ref)
        except Exception as e:
            return name, 0, "FAIL %s" % str(e)[:50]
        stage_s, subj = CMMLU_MAP.get(name, ("大学", "大学英语四级"))
        zh = CMMLU_ZH.get(name, name)
        cnt = 0
        try:
            reader = csv.DictReader(io.StringIO(txt))
            for row in reader:
                stem = (row.get("Question") or "").strip()
                if len(stem) < 5:
                    continue
                opts = [{"label": k, "content": (row.get(k) or "").strip()}
                        for k in ("A", "B", "C", "D") if row.get(k)]
                ans = (row.get("Answer") or "").strip()
                q = normalize_question(
                    stage=stage_s, subject=subj, stem=stem, options=opts, answer=ans,
                    analysis="", qtype="单选题",
                    knowledge=[zh, "CMMLU"],
                    source="CMMLU(%s)" % zh,
                    source_url="https://github.com/%s" % CMMLU_REPO,
                )
                if writer.add(q):
                    cnt += 1
        except Exception as e:
            return name, cnt, "PARSE %s" % str(e)[:50]
        return name, cnt, ""

    total = 0
    with ThreadPoolExecutor(max_workers=max_workers) as ex:
        futs = {ex.submit(one, p): p for p in files}
        for f in as_completed(futs):
            name, cnt, note = f.result()
            total += cnt
            if note or cnt > 0:
                log("[cmmlu] %-38s %5d 题 %s" % (name[:38], cnt, note))
    log("[cmmlu] 完成，新增 %d 题" % total)
    return total


# ============================================================
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--source", default="all", choices=["all", "gaokao_full", "cmmlu"])
    ap.add_argument("--workers", type=int, default=12)
    args = ap.parse_args()

    logf = open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "harvest.log"),
                "a", encoding="utf-8")

    def log(msg):
        print(msg, flush=True)
        logf.write(msg + "\n")
        logf.flush()

    log("\n===== 第二批采集 %s | source=%s =====" % (time.strftime("%H:%M:%S"), args.source))
    writer = BankWriter()
    log("去重基数: %d" % len(writer.seen))

    t0 = time.time()
    total = 0
    if args.source in ("all", "gaokao_full"):
        total += harvest_gaokao_full(writer, log, max_workers=args.workers)
    if args.source in ("all", "cmmlu"):
        total += harvest_cmmlu(writer, log, max_workers=args.workers)

    written = writer.flush()
    stats, tot, img = writer.report()
    log("---- 新增(去重后) %d，落盘 %d，耗时 %.1fs ----" % (total, written, time.time() - t0))
    for k in sorted(stats):
        log("  %-30s 题目 %6d" % (k, stats[k]["题目"]))

    allcnt = 0
    for dirpath, _, files in os.walk(ROOT):
        if os.path.basename(dirpath) == "题目":
            for fn in files:
                if fn.endswith(".jsonl"):
                    try:
                        allcnt += sum(1 for _ in open(os.path.join(dirpath, fn), encoding="utf-8"))
                    except Exception:
                        pass
    log("题库累计总题量: %d" % allcnt)
    with open(os.path.join(ROOT, "_meta", "采集统计.json"), "w", encoding="utf-8-sig") as f:
        json.dump({"更新时间": time.strftime("%Y-%m-%d %H:%M:%S"),
                   "本次新增": total, "累计总量": allcnt,
                   "分学科": stats}, f, ensure_ascii=False, indent=2)
    log("===== 结束 =====\n")


if __name__ == "__main__":
    main()
