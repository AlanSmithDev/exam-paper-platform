# -*- coding: utf-8 -*-
"""
A 类第二批采集（大规模中文考试基准）：
  cmb      : FreedomIntelligence/CMB   CMB-Exam train+val  约27万 医学（医师/护理/药师/医技）
  cmexam   : fzkuji/CMExam             train+valid          约6万  执业医师（含完整解析）
  m3ke     : tjunlp-lab/M3KE (GitHub)  71 学科 dev+test     约7万  小学~考研
  xiezhi   : MikeGu721/XiezhiBenchmark 中文干净集            约3万  大学多学科
  fineval  : SUFE-AIFLM-Lab/FinEval    金融 val            约1.4千
  tal_math : math-eval/TAL-SCQ5K-CN    数学选择题 train+test 约5千（含解析）

全部经 safe_http 白名单抓取；日志走 stdout（调用方重定向），本文件不直接写文件。
用法：python harvest_hf2.py --source cmb
"""
import os, sys, json, re, time, io, csv, argparse, collections, zipfile
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from safe_http import safe_get, safe_open
from tiku_core import BankWriter, normalize_question
from harvest import split_options
from harvest_hf import iter_jsonl, s, zh_ratio, add_q, split_options_inline, extract_answer, NAME_RE

HF = "https://hf-mirror.com"
FLUSH_EVERY = 20000


def iter_json_array(url, chunk=1 << 20):
    """流式解析大 JSON 数组（边下边解，不全量进内存）"""
    dec = json.JSONDecoder()
    r = safe_open(url, timeout=120)
    buf = ""
    started = False
    while True:
        data = r.read(chunk)
        if not data:
            break
        buf += data.decode("utf-8", "ignore")
        if not started:
            i = buf.find("[")
            if i < 0:
                continue
            buf = buf[i + 1:]
            started = True
        while True:
            j = 0
            while j < len(buf) and buf[j] in " \t\r\n,":
                j += 1
            buf = buf[j:]
            if not buf or buf[0] == "]":
                break
            if buf[0] != "{":
                break    # 截断等更多数据
            try:
                o, end = dec.raw_decode(buf)
            except Exception:
                break
            yield o
            buf = buf[end:]
    r.close()


# ============================================================
# CMB（医学考试 27 万）
# ============================================================
CMB_URLS = [
    HF + "/datasets/FreedomIntelligence/CMB/resolve/main/CMB-Exam/CMB-train/CMB-train-merge.json",
    HF + "/datasets/FreedomIntelligence/CMB/resolve/main/CMB-Exam/CMB-val/CMB-val-merge.json",
]

# exam_subject 关键词 -> (学段, 学科)
CMB_SUBJ_RULES = [
    ("内科", ("大学", "内科学")), ("外科", ("大学", "外科学")),
    ("病理", ("大学", "病理学")), ("药理", ("大学", "药理学")),
    ("生理", ("大学", "生理学")), ("生化", ("大学", "生物化学")),
    ("生物化学", ("大学", "生物化学")), ("解剖", ("大学", "系统解剖学")),
    ("中医", ("考研", "中医综合")), ("中药", ("考研", "中医综合")),
    ("针灸", ("考研", "中医综合")), ("方剂", ("考研", "中医综合")),
    ("护理", ("中职", "护理学基础")),
    ("药学", ("大学", "药理学")), ("临床药学", ("大学", "药理学")),
]
CMB_FALLBACK = ("大学", "临床医学")


def cmb_map_subject(exam_subject):
    for k, v in CMB_SUBJ_RULES:
        if k in (exam_subject or ""):
            return v
    return CMB_FALLBACK


def harvest_cmb(writer, log):
    counters = {"added": 0, "analysis": 0, "zh": 0, "en": 0}
    type_cnt = collections.Counter()
    subj_cnt = collections.Counter()
    for url in CMB_URLS:
        split = "train" if "train" in url else "val"
        n = 0
        for o in iter_json_array(url):
            n += 1
            stem = s(o.get("question"))
            ans = s(o.get("answer"))
            if len(stem) < 5 or not ans:
                continue
            opts_d = o.get("option") or {}
            opts = [{"label": k, "content": s(v)} for k, v in sorted(opts_d.items()) if s(v)]
            stage, subject = cmb_map_subject(s(o.get("exam_subject")))
            qt = s(o.get("question_type"))
            type_cnt[qt] += 1
            subj_cnt[s(o.get("exam_subject"))] += 1
            add_q(writer, counters,
                  stage=stage, subject=subject, stem=stem, options=opts,
                  answer=ans,
                  qtype="单选题" if "单项" in qt else ("多选题" if "多" in qt else None),
                  knowledge=[s(o.get("exam_type")), s(o.get("exam_class")),
                             s(o.get("exam_subject"))],
                  source="CMB(中文医学基准)", quality="B",
                  source_url="https://hf-mirror.com/datasets/FreedomIntelligence/CMB",
                  region=split)
            if counters["added"] % FLUSH_EVERY == 0:
                pass
        writer.flush()
        log("[cmb] %s 流行 %d 条" % (split, n))
    log("[cmb] 题型分布: %s" % type_cnt.most_common(8))
    log("[cmb] top exam_subject: %s" % subj_cnt.most_common(12))
    log("[cmb] 入库 %d（解析 0，中文 %d）" % (counters["added"], counters["zh"]))
    return counters["added"]


# ============================================================
# CMExam（执业医师，含完整解析）
# ============================================================
CM_URLS = [HF + "/datasets/fzkuji/CMExam/resolve/main/" + f
           for f in ("train.json", "valid.json", "test.json")]


def harvest_cmexam(writer, log):
    counters = {"added": 0, "analysis": 0, "zh": 0, "en": 0}
    for url in CM_URLS:
        name = url.rsplit("/", 1)[-1]
        n = cnt = 0
        try:
            for line in safe_open(url, timeout=120):
                line = line.strip()
                if not line:
                    continue
                n += 1
                try:
                    o = json.loads(line)
                except Exception:
                    continue
                stem = s(o.get("Question"))
                ans = s(o.get("Answer"))
                if len(stem) < 5 or not ans:
                    continue
                opts = [{"label": x.get("key"), "content": s(x.get("value"))}
                        for x in (o.get("Options") or []) if isinstance(x, dict) and s(x.get("value"))]
                add_q(writer, counters,
                      stage="大学", subject="临床医学", stem=stem, options=opts,
                      answer=ans, analysis=s(o.get("Explanation")),
                      qtype="单选题" if len(ans) == 1 else "多选题",
                      knowledge=["执业医师", "CMExam"],
                      source="CMExam(执业医师真题)", quality="B",
                      source_url=url, region=name.replace(".json", ""))
                cnt += 1
        except Exception as e:
            log("[cmexam] FAIL %s -> %s" % (name, str(e)[:60]))
        writer.flush()
        log("[cmexam] %-12s 行 %6d 入库 %6d" % (name, n, cnt))
    log("[cmexam] 入库 %d（解析 %d）" % (counters["added"], counters["analysis"]))
    return counters["added"]


# ============================================================
# M3KE（71 学科，小学~考研，GitHub zip 1.9MB）
# ============================================================
M3KE_ZIP = "https://gh-proxy.com/https://raw.githubusercontent.com/tjunlp-lab/M3KE/main/data/M3KE.zip"

# 文件名主体 -> (学段, 学科, 知识点标签)
M3KE_MAP = {
    "Advanced Mathematics": ("大学", "高等数学", "高等数学"),
    "Ancient Chinese Language": ("大学", "中国语言文学", "古代汉语"),
    "Animal Physiology": ("大学", "生理学", "动物生理学"),
    "Anthropotomy": ("大学", "系统解剖学", "人体解剖学"),
    "Basic Principle of Marxism": ("大学", "马克思主义基本原理", "马克思主义基本原理"),
    "Biochemistry and Pathology": ("大学", "生物化学", "生物化学与病理"),
    "Biochemistry": ("大学", "生物化学", "生物化学"),
    "Chinese Civil Service Examination": ("大学", "管理学原理", "公务员考试"),
    "Chinese Constitutional Law": ("大学", "宪法学", "宪法学"),
    "Chinese Medicine": ("考研", "中医综合", "中医"),
    "Civil Law": ("大学", "民法", "民法"),
    "Computer Fundamentals": ("中职", "计算机应用基础", "计算机基础"),
    "Computer Networks": ("大学", "计算机网络", "计算机网络"),
    "Computer Programming Language": ("大学", "C语言程序设计", "程序设计"),
    "Criminal Jurisprudence": ("大学", "刑法", "刑法学"),
    "Current Affairs and Politics": ("考研", "政治", "时事政治"),
    "Dance": ("大学", "艺术学", "舞蹈"),
    "Data Structures": ("大学", "数据结构", "数据结构"),
    "Developmental and Educational Psychology": ("考研", "312心理学专业基础", "发展与教育心理学"),
    "Economics": ("大学", "微观经济学", "经济学"),
    "Educational Research Methods": ("大学", "教育学", "教育研究方法"),
    "Experimental Psychology": ("考研", "312心理学专业基础", "实验心理学"),
    "Film": ("大学", "艺术学", "影视"),
    "Fine Arts": ("大学", "艺术学", "美术"),
    "History Foundation": ("大学", "历史学", "历史学基础"),
    "History of Chinese Education": ("大学", "教育学", "中国教育史"),
    "History of Foreign Education": ("大学", "教育学", "外国教育史"),
    "History of the Chinese Legal System": ("大学", "法理学", "中国法制史"),
    "Humanistic Medicine": ("大学", "临床医学", "医学人文"),
    "Immunology": ("大学", "基础医学", "免疫学"),
    "Internal Medicine": ("大学", "内科学", "内科学"),
    "Introduction to Mao Tsetung Thoughts": ("大学", "毛泽东思想和中国特色社会主义理论体系概论", "毛泽东思想概论"),
    "Introduction to Psychology": ("考研", "312心理学专业基础", "普通心理学"),
    "Jurisprudence": ("大学", "法理学", "法理学"),
    "Linear Algebra": ("大学", "线性代数", "线性代数"),
    "Management": ("大学", "管理学原理", "管理学"),
    "Modern History": ("大学", "中国近现代史纲要", "中国近现代史"),
    "Modern World History": ("大学", "历史学", "世界近现代史"),
    "Moral Cultivation": ("大学", "思想道德与法治", "思想道德修养"),
    "Music": ("大学", "艺术学", "音乐"),
    "Novels": ("大学", "中国语言文学", "小说"),
    "Operating Systems": ("大学", "操作系统", "操作系统"),
    "Outline of Chinese Modern History": ("大学", "中国近现代史纲要", "中国近现代史纲要"),
    "Pharmacology": ("大学", "药理学", "药理学"),
    "Physiology": ("大学", "生理学", "生理学"),
    "Principles of Computer Composition": ("大学", "计算机组成原理", "计算机组成原理"),
    "Principles of Pedagogy": ("大学", "教育学", "教育学原理"),
    "Probability Theory": ("大学", "概率论与数理统计", "概率论"),
    "Psychology of Teaching": ("大学", "教育学", "教学心理学"),
    "Religion": ("大学", "哲学", "宗教学"),
    "Sociology": ("大学", "管理学原理", "社会学"),
    "Stomatology": ("大学", "临床医学", "口腔医学"),
    "Surgical Sciences": ("大学", "外科学", "外科学"),
}
M3KE_SCHOOL = {
    "High school": "高中", "Junior high school": "初中", "Primary school": "小学",
}
M3KE_SCHOOL_SUBJ = {
    "Biology": "生物", "Chemistry": "化学", "Chinese": "语文", "Geography": "地理",
    "History": "历史", "Math": "数学", "Physics": "物理", "Politics": "政治",
}
# 中小学新学科目录（需登记进目录 JSON）
M3KE_NEW_SUBJECTS = ["历史学", "哲学"]


def m3ke_map(stem_name):
    """'Math-Natural Sciences-High school' -> (学段, 学科, 标签)"""
    parts = stem_name.split("-")
    level = parts[-1].strip()
    subj = parts[0].strip()
    if level in M3KE_SCHOOL:
        base = M3KE_SCHOOL_SUBJ.get(subj)
        if base:
            return (M3KE_SCHOOL[level], base, "M3KE")
        return None
    return M3KE_MAP.get(stem_name)


def harvest_m3ke(writer, log):
    counters = {"added": 0, "analysis": 0, "zh": 0, "en": 0}
    raw = safe_get(M3KE_ZIP, binary=True, timeout=120)
    z = zipfile.ZipFile(io.BytesIO(raw))
    unmapped = collections.Counter()
    for name in sorted(z.namelist()):
        if not name.endswith(".jsonl"):
            continue
        stem_name = os.path.basename(name)[:-6]
        mapped = m3ke_map(stem_name)
        if not mapped:
            unmapped[stem_name] += 1
            continue
        stage, subject, tag = mapped
        cnt = 0
        with z.open(name) as f:
            for raw_line in f:
                line = raw_line.decode("utf-8", "ignore").strip()
                if not line:
                    continue
                try:
                    o = json.loads(line)
                except Exception:
                    continue
                q = s(o.get("question"))
                ans = s(o.get("answer"))
                if len(q) < 4 or not ans:
                    continue
                opts = [{"label": k, "content": s(o.get(k))} for k in "ABCD" if s(o.get(k))]
                add_q(writer, counters,
                      stage=stage, subject=subject, stem=q, options=opts,
                      answer=ans, qtype="单选题",
                      knowledge=[tag, "M3KE"], source="M3KE(%s)" % tag, quality="B",
                      source_url="https://github.com/tjunlp-lab/M3KE",
                      region=stem_name)
                cnt += 1
        log("[m3ke] %-55s %5d" % (stem_name[:55], cnt))
    if unmapped:
        log("[m3ke] 未映射(跳过): %s" % dict(unmapped))
    writer.flush()
    log("[m3ke] 入库 %d（中文 %d）" % (counters["added"], counters["zh"]))
    return counters["added"]


# ============================================================
# Xiezhi（大学多学科，中文干净集）
# ============================================================
XIEZHI_FILES = [
    "https://gh-proxy.com/https://raw.githubusercontent.com/MikeGu721/XiezhiBenchmark/main/Tasks/Knowledge/Benchmarks/test/xiezhi_spec_chn/xiezhi.v1.1.json",
    "https://gh-proxy.com/https://raw.githubusercontent.com/MikeGu721/XiezhiBenchmark/main/Tasks/Knowledge/Benchmarks/test/xiezhi_inter_chn/xiezhi.v1.1.json",
    "https://gh-proxy.com/https://raw.githubusercontent.com/MikeGu721/XiezhiBenchmark/main/Tasks/Knowledge/Benchmarks/train/xiezhi_train_chn/xiezhi.v1.1.json",
]

# 学科门类标签 -> (学段, 学科)
XIEZHI_LABEL = {
    "历史学": ("大学", "历史学"), "文学": ("大学", "中国语言文学"),
    "艺术学": ("大学", "艺术学"), "经济学": ("大学", "经济学"),
    "法学": ("大学", "法学综合"), "教育学": ("大学", "教育学"),
    "管理学": ("大学", "管理学原理"), "医学": ("大学", "临床医学"),
    "哲学": ("大学", "哲学"), "农学": ("大学", "农学"),
    "理学": ("大学", "综合理科"), "工学": ("大学", "综合工科"),
    "军事学": ("大学", "综合工科"),
}
XIEZHI_NEW_SUBJECTS = ["综合理科", "综合工科", "农学"]


def harvest_xiezhi(writer, log):
    counters = {"added": 0, "analysis": 0, "zh": 0, "en": 0}
    label_cnt = collections.Counter()
    unmapped = collections.Counter()
    for url in XIEZHI_FILES:
        n = cnt = 0
        for line in safe_open(url, timeout=120):
            line = line.strip()
            if not line:
                continue
            n += 1
            try:
                o = json.loads(line)
            except Exception:
                continue
            q = s(o.get("question"))
            ans = s(o.get("answer"))
            opts_raw = s(o.get("options"))
            if len(q) < 5 or not ans or not opts_raw:
                continue
            labels = o.get("labels") or []
            lab = labels[0] if labels else ""
            label_cnt[lab] += 1
            if lab not in XIEZHI_LABEL:
                unmapped[lab] += 1
                continue
            stage, subject = XIEZHI_LABEL[lab]
            opts = [{"label": chr(65 + i), "content": x.strip()}
                    for i, x in enumerate(opts_raw.split("\n")) if x.strip()]
            # 答案是全文 -> 转字母（与选项匹配）
            letter = ""
            for od in opts:
                if od["content"] == ans:
                    letter = od["label"]
                    break
            add_q(writer, counters,
                  stage=stage, subject=subject, stem=q, options=opts,
                  answer=letter or ans, qtype="单选题",
                  knowledge=labels + ["Xiezhi"], source="Xiezhi(獬豸 Benchmark)",
                  quality="B",
                  source_url=url, region="、".join(labels))
            cnt += 1
        writer.flush()
        log("[xiezhi] %s 行 %d 入库 %d" % (url.rsplit("/", 2)[-2], n, cnt))
    log("[xiezhi] 标签分布: %s" % label_cnt.most_common())
    if unmapped:
        log("[xiezhi] 未映射(跳过): %s" % unmapped.most_common())
    log("[xiezhi] 入库 %d" % counters["added"])
    return counters["added"]


# ============================================================
# FinEval（金融）
# ============================================================
FINEVAL_ZIP = HF + "/datasets/SUFE-AIFLM-Lab/FinEval/resolve/main/FinEval.zip"
FINEVAL_SUBJ = {
    "accountant": ("大学", "会计学", "会计"), "certified_public_accountant": ("大学", "会计学", "注册会计师"),
    "finance": ("大学", "金融学", "金融"), "economics": ("大学", "微观经济学", "经济学"),
    "taxation": ("大学", "会计学", "税务"), "bank_curities": ("大学", "金融学", "银行证券"),
    "insurance": ("大学", "金融学", "保险"), "audit": ("大学", "会计学", "审计"),
    "financial_management": ("大学", "财务管理", "财务管理"),
    "economic_law": ("大学", "经济法", "经济法"),
    "commercial_bank": ("大学", "金融学", "商业银行"),
    "international_trade": ("大学", "国际贸易", "国际贸易"),
    "cost_accounting": ("大学", "会计学", "成本会计"),
    "corporate_strategy_and_risk_management": ("大学", "管理学原理", "公司战略与风险管理"),
    "china_actuary": ("大学", "概率论与数理统计", "精算"),
    "central_banking": ("大学", "金融学", "中央银行学"),
    "banking_practitioner_qualification_certificate": ("大学", "金融学", "银行从业资格"),
    "advanced_financial_accounting": ("大学", "会计学", "高级财务会计"),
}


def harvest_fineval(writer, log):
    counters = {"added": 0, "analysis": 0, "zh": 0, "en": 0}
    raw = safe_get(FINEVAL_ZIP, binary=True, timeout=120)
    z = zipfile.ZipFile(io.BytesIO(raw))
    names = [n for n in z.namelist() if n.endswith((".csv", ".jsonl", ".parquet"))]
    log("[fineval] zip 内文件 %d 个: %s" % (len(names), names[:8]))
    for name in names:
        stem = os.path.basename(name).rsplit(".", 1)[0].rsplit("-", 1)[0]
        key = stem.lower()
        mapped = None
        for k, v in FINEVAL_SUBJ.items():
            if k in key:
                mapped = v
                break
        if not mapped:
            log("[fineval] 未映射: %s" % name)
            continue
        stage, subject, tag = mapped
        cnt = 0
        data = z.read(name).decode("utf-8", "ignore")
        if name.endswith(".csv"):
            rd = csv.DictReader(io.StringIO(data))
            rows = list(rd)
        else:
            rows = [json.loads(x) for x in data.splitlines() if x.strip()]
        for o in rows:
            q = s(o.get("question") or o.get("Question"))
            ans = s(o.get("answer") or o.get("Answer"))
            expl = s(o.get("explanation") or o.get("Explanation"))
            if len(q) < 5 or not ans:
                continue
            opts = [{"label": k, "content": s(o.get(k))}
                    for k in ("A", "B", "C", "D") if s(o.get(k))]
            add_q(writer, counters,
                  stage=stage, subject=subject, stem=q, options=opts,
                  answer=ans, analysis=expl, qtype="单选题",
                  knowledge=[tag, "FinEval"], source="FinEval(金融考试)", quality="B",
                  source_url=FINEVAL_ZIP, region=name)
            cnt += 1
        log("[fineval] %-46s %5d" % (name[:46], cnt))
    writer.flush()
    log("[fineval] 入库 %d" % counters["added"])
    return counters["added"]


# ============================================================
# TAL-SCQ5K-CN（学而思数学选择题，含解析）
# ============================================================
TAL_URLS = [HF + "/datasets/math-eval/TAL-SCQ5K/resolve/main/TAL-SCQ5K-CN/" + f
            for f in ("train.jsonl", "test.jsonl")]


def _tal_stage(o):
    """从竞赛来源/知识路线判断学段"""
    blob = (s(o.get("competition_source_list")) + s(o.get("knowledge_point_routes")))
    if "小学" in blob:
        return "小学"
    if "高中" in blob:
        return "高中"
    return "初中"


def harvest_tal_math(writer, log):
    counters = {"added": 0, "analysis": 0, "zh": 0, "en": 0}
    for url in TAL_URLS:
        n = cnt = 0
        try:
            for line in safe_open(url, timeout=60):
                line = line.strip()
                if not line:
                    continue
                n += 1
                try:
                    o = json.loads(line)
                except Exception:
                    continue
                q = s(o.get("problem"))
                ans = s(o.get("answer_value")) or s(o.get("answer_choice"))
                if len(q) < 5 or not ans:
                    continue
                # answer_option_list / answer_analysis 是 JSON 字符串
                opts = []
                try:
                    aol = json.loads(o.get("answer_option_list") or "[]")
                    for grp in aol:
                        if isinstance(grp, list):
                            for x in grp:
                                if isinstance(x, dict) and s(x.get("content")):
                                    opts.append({"label": s(x.get("aoVal")) or "?",
                                                 "content": s(x.get("content"))})
                except Exception:
                    pass
                analysis = ""
                try:
                    al = json.loads(o.get("answer_analysis") or "[]")
                    analysis = "\n".join(s(x) for x in al if s(x))
                except Exception:
                    analysis = s(o.get("answer_analysis"))
                if not opts:
                    q2, opts = split_options_inline(q)
                    if not opts:
                        q2, opts = split_options(q)
                    q = q2
                stage = _tal_stage(o)
                add_q(writer, counters,
                      stage=stage, subject="数学", stem=q, options=opts,
                      answer=ans, analysis=analysis,
                      qtype="单选题",
                      knowledge=["数学竞赛", "TAL-SCQ5K"],
                      source="TAL-SCQ5K(学而思竞赛数学)", quality="B",
                      source_url=url, region=url.rsplit("/", 1)[-1].replace(".jsonl", ""))
                cnt += 1
        except Exception as e:
            log("[tal] FAIL %s -> %s" % (url[-30:], str(e)[:60]))
        writer.flush()
        log("[tal] %s 行 %d 入库 %d" % (url.rsplit("/", 1)[-1], n, cnt))
    log("[tal] 入库 %d（解析 %d）" % (counters["added"], counters["analysis"]))
    return counters["added"]


SOURCES = ["cmb", "cmexam", "m3ke", "xiezhi", "fineval", "tal_math"]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--source", default="all", choices=["all"] + SOURCES)
    args = ap.parse_args()

    def log(msg):
        print(msg, flush=True)

    log("===== A类第二批采集 %s | source=%s =====" % (time.strftime("%Y-%m-%d %H:%M:%S"), args.source))
    writer = BankWriter()
    log("去重基数: %d" % len(writer.seen))

    t0 = time.time()
    runs = SOURCES if args.source == "all" else [args.source]
    totals = {}
    for src in runs:
        try:
            totals[src] = globals()["harvest_" + src](writer, log)
        except Exception as e:
            import traceback
            log("[%s] 异常: %s" % (src, str(e)[:100]))
            log(traceback.format_exc()[-500:])
    writer.flush()
    log("---- 各源新增: %s ----" % totals)
    log("耗时 %.0fs" % (time.time() - t0))


if __name__ == "__main__":
    main()
