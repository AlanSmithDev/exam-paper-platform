# -*- coding: utf-8 -*-
"""
A 类开放数据集采集（HF 镜像，全部经 safe_http 白名单）：
  coig_exam  : BAAI/COIG exam_instructions.jsonl   6.3万 高考系真题（题+答案+解析）
  ceval      : ceval/ceval-exam 52 学科 parquet     1.3万 大学/职考
  cqia       : m-a-p/COIG-CQIA exam/logiqa/传统文化子集 数千
  ms_english : dry-melon 初中英语题库 grade7-9       ~1万
  tcm        : SylvanL 中医考试 4191
  agieval    : hails/agieval-gaokao-* 高考系列       ~1万

用法：
  python harvest_hf.py --source coig_exam
  python harvest_hf.py --source all
"""
import os, sys, json, re, time, io, csv, argparse, collections
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from safe_http import safe_get, safe_open
from tiku_core import BankWriter, normalize_question, ROOT
from harvest import split_options

HF = "https://hf-mirror.com"

# 每累计多少题 flush 一次分片，控制内存
FLUSH_EVERY = 20000

# 合法目录名成分（中文/字母/数字/下划线/括号/点连字符），杜绝 ../ 与分隔符
NAME_RE = re.compile(r"^[\u4e00-\u9fffA-Za-z0-9_（）()·\-.]{1,60}$")


def iter_jsonl(url):
    """流式逐行解析 JSONL（大文件不全量进内存）"""
    r = safe_open(url, timeout=60)
    for raw in r:
        line = raw.strip()
        if not line:
            continue
        try:
            yield json.loads(line)
        except Exception:
            continue
    r.close()


def s(v):
    """字段清洗：'[]'/None -> ''；list/dict 序列化为文本"""
    if v is None:
        return ""
    if isinstance(v, str):
        return "" if v.strip() in ("", "[]", "null") else v.strip()
    if isinstance(v, (list, dict)):
        return "" if not v else json.dumps(v, ensure_ascii=False)
    return str(v)


CJK_RE = re.compile(r"[\u4e00-\u9fff]")


def zh_ratio(txt):
    return len(CJK_RE.findall(txt or "")) / max(len(txt or ""), 1)


def add_q(writer, counters, **kw):
    q = normalize_question(**kw)
    if writer.add(q):
        counters["added"] += 1
        if (q["analysis"] or "").strip():
            counters["analysis"] += 1
        if zh_ratio(q["stem"]) > 0.2:
            counters["zh"] += 1
        else:
            counters["en"] += 1
        if counters["added"] % FLUSH_EVERY == 0:
            writer.flush()
    return q


# ============================================================
# A2 COIG exam（BAAI/COIG exam_instructions.jsonl）
# ============================================================
COIG_EXAM_URL = HF + "/datasets/BAAI/COIG/resolve/main/exam_instructions.jsonl"

# COIG exam subject 字段 -> (学段, 本项目学科)
COIG_EXAM_SUBJ = {
    "英语": ("高中", "英语"), "语文": ("高中", "语文"), "数学": ("高中", "数学"),
    "物理": ("高中", "物理"), "化学": ("高中", "化学"), "生物": ("高中", "生物"),
    "政治": ("高中", "政治"), "历史": ("高中", "历史"), "地理": ("高中", "地理"),
    "物理化学生物": ("高中", "理科综合"), "政治历史地理": ("高中", "文科综合"),
}


def split_options_inline(stem):
    """拆行内选项：'（　　） A．xx	B．xx C．xx D．xx' -> (题干, [选项])"""
    m = re.search(
        r"[\s　]+A[\.．、:：)]\s*(.+?)[\s　]+B[\.．、:：)]\s*(.+?)"
        r"[\s　]+C[\.．、:：)]\s*(.+?)[\s　]+D[\.．、:：)]\s*(.+?)\s*$",
        stem, re.S)
    if not m:
        return stem, []
    head = stem[:m.start()].rstrip()
    opts = [{"label": "ABCD"[i], "content": m.group(i + 1).strip()} for i in range(4)]
    if all(o["content"] for o in opts):
        return head, opts
    return stem, []


def harvest_coig_exam(writer, log):
    counters = {"added": 0, "analysis": 0, "zh": 0, "en": 0}
    subj_cnt = collections.Counter()
    unmapped = collections.Counter()
    n = 0
    for o in iter_jsonl(COIG_EXAM_URL):
        n += 1
        subj = s(o.get("subject"))
        subj_cnt[subj] += 1
        if subj not in COIG_EXAM_SUBJ:
            unmapped[subj] += 1
            continue
        stage, subject = COIG_EXAM_SUBJ[subj]
        stem = s(o.get("textbox_question"))
        if len(stem) < 8:
            continue
        ctx = s(o.get("textbox_q_context"))
        instr = s(o.get("textbox_q_instruction"))
        full = "\n".join(x for x in (ctx or instr, stem) if x)
        stem2, opts = split_options_inline(full)
        if not opts:
            stem2, opts = split_options(stem2)
        add_q(writer, counters,
              stage=stage, subject=subject, stem=stem2, options=opts,
              answer=s(o.get("textbox_answer")),
              analysis=s(o.get("textbox_answer_analysis")),
              source="COIG-exam(BAAI高考汇编)", quality="B",
              source_url=COIG_EXAM_URL, region="COIG")
    writer.flush()
    log("[coig_exam] 行数 %d，入库 %d（解析 %d，中文 %d，英文 %d）"
        % (n, counters["added"], counters["analysis"], counters["zh"], counters["en"]))
    log("[coig_exam] subject 分布: %s" % subj_cnt.most_common())
    if unmapped:
        log("[coig_exam] 未映射 subject(已跳过): %s" % unmapped.most_common(10))
    return counters["added"]


# ============================================================
# A3 C-Eval（ceval/ceval-exam，52 学科 × dev/val/test parquet）
# ============================================================
CEVAL_URL = HF + "/datasets/ceval/ceval-exam/resolve/main/%s/%s-00000-of-00001.parquet"

CEVAL_SUBJ = {
    "accountant": ("大学", "会计学", "注册会计师"),
    "advanced_mathematics": ("大学", "高等数学", "高等数学"),
    "art_studies": ("大学", "艺术学", "艺术学"),
    "basic_medicine": ("大学", "基础医学", "基础医学"),
    "business_administration": ("大学", "管理学原理", "工商管理"),
    "chinese_language_and_literature": ("大学", "中国语言文学", "中国语言文学"),
    "civil_engineer": ("大学", "土木工程", "注册结构工程师"),
    "clinical_medicine": ("大学", "临床医学", "临床医学"),
    "college_chemistry": ("大学", "大学化学", "大学化学"),
    "college_economics": ("大学", "经济学", "大学经济学"),
    "college_physics": ("大学", "大学物理", "大学物理"),
    "college_programming": ("大学", "C语言程序设计", "大学编程"),
    "computer_architecture": ("大学", "计算机组成原理", "计算机组成"),
    "computer_network": ("大学", "计算机网络", "计算机网络"),
    "discrete_mathematics": ("大学", "离散数学", "离散数学"),
    "electrical_engineer": ("大学", "电路原理", "注册电气工程师"),
    "environmental_impact_assessment_engineer": ("大学", "环境科学", "环评工程师"),
    "fire_engineer": ("大学", "安全工程", "注册消防工程师"),
    "graduate_politics": ("考研", "政治", "研究生政治"),
    "ideological_and_moral_cultivation": ("大学", "思想道德与法治", "思想道德修养与法律基础"),
    "law": ("大学", "法学综合", "法学"),
    "legal_professional": ("大学", "法学综合", "法律职业资格"),
    "logic": ("大学", "离散数学", "逻辑学"),
    "mao_introduction": ("大学", "毛泽东思想和中国特色社会主义理论体系概论", "毛泽东思想概论"),
    "marxism": ("大学", "马克思主义基本原理", "马克思主义基本原理"),
    "metrology_engineer": ("大学", "大学物理", "注册计量师"),
    "physician": ("大学", "临床医学", "医师资格"),
    "plant_protection": ("大学", "植物保护", "植物保护"),
    "probability_and_statistic": ("大学", "概率论与数理统计", "概率统计"),
    "probability_and_statistics": ("大学", "概率论与数理统计", "概率统计"),
    "professional_tour_guide": ("大学", "旅游管理", "导游资格"),
    "sports_science": ("大学", "体育学", "体育学"),
    "tax_accountant": ("大学", "会计学", "税务师"),
    "teacher_qualification": ("大学", "教育学", "教师资格"),
    "urban_and_rural_planner": ("大学", "土木工程", "注册城乡规划师"),
    "veterinary_medicine": ("大学", "兽医学", "兽医学"),
    # 初高中与补充学科（第二遍补映射）
    "high_school_biology": ("高中", "生物", "高中生物"),
    "high_school_chemistry": ("高中", "化学", "高中化学"),
    "high_school_chinese": ("高中", "语文", "高中语文"),
    "high_school_geography": ("高中", "地理", "高中地理"),
    "high_school_history": ("高中", "历史", "高中历史"),
    "high_school_mathematics": ("高中", "数学", "高中数学"),
    "high_school_physics": ("高中", "物理", "高中物理"),
    "high_school_politics": ("高中", "政治", "高中政治"),
    "middle_school_biology": ("初中", "生物", "初中生物"),
    "middle_school_chemistry": ("初中", "化学", "初中化学"),
    "middle_school_geography": ("初中", "地理", "初中地理"),
    "middle_school_history": ("初中", "历史", "初中历史"),
    "middle_school_mathematics": ("初中", "数学", "初中数学"),
    "middle_school_physics": ("初中", "物理", "初中物理"),
    "middle_school_politics": ("初中", "道德与法治", "初中道德与法治"),
    "civil_servant": ("大学", "管理学原理", "公务员考试"),
    "education_science": ("大学", "教育学", "教育科学"),
    "mao_zedong_thought": ("大学", "毛泽东思想和中国特色社会主义理论体系概论", "毛泽东思想概论"),
    "modern_chinese_history": ("大学", "中国近现代史纲要", "中国近现代史"),
    "operating_system": ("大学", "操作系统", "操作系统"),
}

# C-Eval 新增学科需要补充进目录（见 update_catalog()）
CEVAL_NEW_SUBJECTS = ["艺术学", "基础医学", "中国语言文学", "土木工程", "临床医学",
                      "经济学", "环境科学", "安全工程", "法学综合", "教育学",
                      "植物保护", "旅游管理", "体育学", "兽医学"]


def ceval_subjects(log):
    d = json.loads(safe_get(HF + "/api/datasets/ceval/ceval-exam/tree/main?recursive=false", timeout=30))
    subs = sorted(t["path"] for t in d if t.get("type") == "directory")
    log("[ceval] 仓库学科目录 %d 个: %s" % (len(subs), ",".join(subs)))
    return subs


def harvest_ceval(writer, log):
    counters = {"added": 0, "analysis": 0, "zh": 0, "en": 0}
    import pandas as pd
    subs = ceval_subjects(log)
    skip_unmapped = collections.Counter()
    for sub in subs:
        if sub not in CEVAL_SUBJ:
            skip_unmapped[sub] += 1
            continue
        stage, subject, tag = CEVAL_SUBJ[sub]
        for split in ("dev", "val", "test"):
            url = CEVAL_URL % (sub, split)
            try:
                raw = safe_get(url, binary=True, timeout=60)
                df = pd.read_parquet(io.BytesIO(raw))
            except Exception as e:
                log("[ceval] FAIL %s/%s -> %s" % (sub, split, str(e)[:60]))
                continue
            cnt = 0
            for _, row in df.iterrows():
                stem = str(row.get("question") or "").strip()
                ans = str(row.get("answer") or "").strip()
                if len(stem) < 5 or not ans:
                    continue
                opts = [{"label": k, "content": str(row.get(k) or "").strip()}
                        for k in ("A", "B", "C", "D") if str(row.get(k) or "").strip()]
                expl = str(row.get("explanation") or "").strip()
                add_q(writer, counters,
                      stage=stage, subject=subject, stem=stem, options=opts,
                      answer=ans, analysis=expl, qtype="单选题",
                      knowledge=[tag, "C-Eval"], source="C-Eval(%s)" % tag, quality="B",
                      source_url="https://hf-mirror.com/datasets/ceval/ceval-exam",
                      region=split)
                cnt += 1
            writer.flush()
            log("[ceval] %-42s %-4s %5d 题" % (sub, split, cnt))
    writer.flush()
    if skip_unmapped:
        log("[ceval] 未映射学科(已跳过): %s" % sorted(skip_unmapped))
    log("[ceval] 入库 %d（解析 %d，中文 %d）" % (counters["added"], counters["analysis"], counters["zh"]))
    return counters["added"]


# ============================================================
# A4 COIG-CQIA 考试相关子集
# ============================================================
CQIA_URL = HF + "/datasets/m-a-p/COIG-CQIA/resolve/main/"

# 答案提取：'故本题选择：B' / '故选A' / '答案是C' / '答案为D' / '正确答案为 D'…
# 用 (?![A-Za-z0-9]) 而不是 \b（中文属于 \w，\b 在字母与汉字之间不成立）
ANS_RE = re.compile(
    r"(?:故(?:本题)?(?:应)?(?:答案)?选(?:择)?|答案(?:是|为)|正确答案(?:是|为|：|:)|应选|本题选)"
    r"\s*[:：]?\s*([A-D])(?![A-Za-z0-9])")


def extract_answer(text):
    m = ANS_RE.search(text or "")
    return m.group(1) if m else ""


def harvest_cqia(writer, log):
    counters = {"added": 0, "analysis": 0, "zh": 0, "en": 0}
    noans = collections.Counter()

    # ---- 1) 中学考试（语文为主）----
    for o in iter_jsonl(CQIA_URL + "exam/coig_exam_sampled_clean_v3.jsonl"):
        stem = s(o.get("instruction"))
        inp = s(o.get("input"))
        out = s(o.get("output"))
        if len(stem) < 10 or len(out) < 2:
            continue
        domain = "/".join(o.get("domain") or [])
        subject = "语文"
        for k, v in (("数学", "数学"), ("英语", "英语"), ("物理", "物理"), ("化学", "化学"),
                     ("生物", "生物"), ("历史", "历史"), ("地理", "地理"), ("政治", "政治")):
            if k in domain:
                subject = v
                break
        full = stem + ("\n" + inp if inp else "")
        stem2, opts = split_options(full)
        if not opts:
            stem2, opts = split_options_inline(full)
        ans = ""
        if opts:
            ans = extract_answer(out) or extract_answer(stem2)
            if not ans:
                noans["coig_exam"] += 1
                continue
        add_q(writer, counters,
              stage="高中", subject=subject, stem=stem2, options=opts,
              answer=ans if opts else out[:500],
              analysis=out, source="COIG-CQIA(中学考试)", quality="B",
              source_url=CQIA_URL + "exam/coig_exam_sampled_clean_v3.jsonl",
              region="COIG-CQIA")

    # ---- 2) 考研题（kaoyan.jsonl）----
    for o in iter_jsonl(CQIA_URL + "exam/kaoyan.jsonl"):
        stem = s(o.get("instruction"))
        out = s(o.get("output"))
        if len(stem) < 10 or len(out) < 2:
            continue
        domain = "/".join(o.get("domain") or [])
        subject = "政治"
        for k, v in (("数学", "数学一"), ("政治", "政治"), ("英语", "英语一"),
                     ("化学", "有机化学"), ("物理", "普通物理"), ("生物", "生物化学")):
            if k in domain:
                subject = v
                break
        stem2, opts = split_options(stem)
        if not opts:
            stem2, opts = split_options_inline(stem)
        ans = ""
        if opts:
            ans = extract_answer(out) or extract_answer(stem2)
            if not ans:
                noans["kaoyan"] += 1
                continue
        meta = s(o.get("metadata"))
        m = re.search(r"year:(\d{4})", meta)
        add_q(writer, counters,
              stage="考研", subject=subject, stem=stem2, options=opts,
              answer=ans if opts else out[:500], analysis=out,
              year=int(m.group(1)) if m else None,
              source="COIG-CQIA(考研)", quality="B",
              source_url=CQIA_URL + "exam/kaoyan.jsonl", region="COIG-CQIA")

    # ---- 3) 法律考研（法硕）----
    for o in iter_jsonl(CQIA_URL + "exam/law_gee_exam_clean_v2.jsonl"):
        stem = s(o.get("instruction"))
        out = s(o.get("output"))
        if len(stem) < 10 or len(out) < 2:
            continue
        stem2, opts = split_options(stem)
        if not opts:
            stem2, opts = split_options_inline(stem)
        if not opts:
            continue
        ans = extract_answer(out) or extract_answer(stem2)
        if not ans:
            noans["law"] += 1
            continue
        meta = s(o.get("metadata"))
        m = re.search(r"year:(\d{4})", meta)
        typ = "法硕非法学" if "非法学" in meta else "法硕法学"
        add_q(writer, counters,
              stage="考研", subject=typ, stem=stem2, options=opts,
              answer=ans, analysis=out, qtype="单选题",
              year=int(m.group(1)) if m else None,
              source="COIG-CQIA(法律考研)", quality="B",
              source_url=CQIA_URL + "exam/law_gee_exam_clean_v2.jsonl",
              region="COIG-CQIA")

    # ---- 4) 逻辑推理（LogiQA，管理类联考逻辑题源）----
    for o in iter_jsonl(CQIA_URL + "logi_qa/logi-qa.jsonl"):
        stem = s(o.get("instruction"))
        out = s(o.get("output"))
        if len(stem) < 15 or len(out) < 2:
            continue
        stem2, opts = split_options(stem)
        if not opts:
            stem2, opts = split_options_inline(stem)
        ans = ""
        if opts:
            ans = extract_answer(out) or extract_answer(stem2)
            if not ans:
                noans["logiqa"] += 1
                continue
        add_q(writer, counters,
              stage="考研", subject="199管理类联考综合能力", stem=stem2, options=opts,
              answer=ans if opts else out[:500], analysis=out,
              knowledge=["逻辑推理"], source="COIG-CQIA(LogiQA)", quality="B",
              source_url=CQIA_URL + "logi_qa/logi-qa.jsonl", region="COIG-CQIA")

    # ---- 5) 传统文化选择题 ----
    for f in ("trad-multi-choice-100.jsonl", "trad-multi-choice-100-2.jsonl",
              "trad-multi-choice-40.jsonl"):
        for o in iter_jsonl(CQIA_URL + "chinese_traditional/" + f):
            stem = s(o.get("instruction"))
            out = s(o.get("output"))
            if len(stem) < 10 or len(out) < 2:
                continue
            stem2, opts = split_options(stem)
            if not opts:
                stem2, opts = split_options_inline(stem)
            if not opts:
                continue
            ans = extract_answer(out)
            if not ans:
                noans[f] += 1
                continue
            add_q(writer, counters,
                  stage="高中", subject="语文", stem=stem2, options=opts,
                  answer=ans, analysis=out, qtype="单选题",
                  knowledge=["中国传统文化"], source="COIG-CQIA(传统文化)", quality="B",
                  source_url=CQIA_URL + "chinese_traditional/" + f, region="COIG-CQIA")

    writer.flush()
    log("[cqia] 入库 %d（解析 %d，中文 %d），无显式答案跳过: %s"
        % (counters["added"], counters["analysis"], counters["zh"], dict(noans)))
    return counters["added"]


# ============================================================
# A5 初中英语题库（dry-melon）
# ============================================================
MS_URL = HF + "/datasets/dry-melon/Chinese-middle-school-English-exam-questions/resolve/main/"
GRADE = {"grade7": "初一", "grade8": "初二", "grade9": "初三"}
MS_FILES = [
    ("Multiple-Choice-Question.jsonl", "单选题"),
    ("Reading-Comprehension-With-Multiple-Choices.jsonl", "阅读理解"),
    ("Reading-Comprehension-With-True-or-False.jsonl", "判断题"),
    ("Cloze-With-Multiple-Choices.jsonl", "完形填空"),
    ("Cloze-With-Free-Responses.jsonl", "填空题"),
]


def harvest_ms_english(writer, log):
    counters = {"added": 0, "analysis": 0, "zh": 0, "en": 0}
    for grade, gname in GRADE.items():
        for fn, qtype in MS_FILES:
            url = MS_URL + "dataset/%s/%s" % (grade, fn)
            cnt = 0
            try:
                for o in iter_jsonl(url):
                    ctx = s(o.get("context"))
                    qs = o.get("questions") or []
                    if not isinstance(qs, list):
                        continue
                    for qi, qd in enumerate(qs, 1):
                        if not isinstance(qd, dict):
                            continue
                        text = s(qd.get("text"))
                        # 完形填空的 text 可能为空（空格挂在 context 的编号括号里）
                        if ctx and text:
                            stem = ctx + "\n" + text
                        elif ctx:
                            stem = "%s\n（第%d空）" % (ctx, qi)
                        else:
                            stem = text
                        if len(stem.strip()) < 3:
                            continue
                        choices = qd.get("choices") or {}
                        if isinstance(choices, dict):
                            opts = [{"label": k, "content": s(v)} for k, v in sorted(choices.items())]
                        elif isinstance(choices, list):
                            opts = [{"label": chr(65 + i), "content": s(v)} for i, v in enumerate(choices)]
                        else:
                            opts = []
                        ans = s(qd.get("answer"))
                        if not ans and isinstance(qd.get("answers"), list):
                            ans = "；".join(s(x) for x in qd["answers"] if s(x))
                        if not ans:
                            continue
                        if qtype == "判断题" and ans.upper() in ("T", "F", "TRUE", "FALSE"):
                            ans = "正确" if ans.upper().startswith("T") else "错误"
                        add_q(writer, counters,
                              stage="初中", subject="英语", stem=stem, options=opts,
                              answer=ans,
                              analysis=s(qd.get("analysis")) or s(qd.get("explanation")),
                              qtype=qtype, grade=gname,
                              source="初中英语题库(dry-melon)", quality="B",
                              source_url=url, region="%s-%s#%d" % (gname, qtype, qi))
                        cnt += 1
            except Exception as e:
                log("[ms_english] FAIL %s/%s -> %s" % (grade, fn, str(e)[:60]))
            writer.flush()
            log("[ms_english] %s %-52s %5d 题" % (grade, fn, cnt))
    writer.flush()
    log("[ms_english] 入库 %d（中文 %d，英文 %d）" % (counters["added"], counters["zh"], counters["en"]))
    return counters["added"]


# ============================================================
# A6 中医考试（SylvanL）
# ============================================================
TCM_URL = HF + "/datasets/SylvanL/Traditional-Chinese-Medicine-Exam/resolve/main/tcm_exam_1.csv"


def harvest_tcm(writer, log):
    counters = {"added": 0, "analysis": 0, "zh": 0, "en": 0}
    txt = safe_get(TCM_URL, timeout=60)
    rd = csv.DictReader(io.StringIO(txt))
    for row in rd:
        scope = s(row.get("范围"))
        raw = s(row.get("题目"))
        ans = s(row.get("答案"))
        if len(raw) < 8 or not ans:
            continue
        # 题目字段格式：'题干描述：...\n选项A：...\n选项B：...'
        stem_parts, opts = [], []
        for ln in raw.replace("\r", "").split("\n"):
            m = re.match(r"^选项\s*([A-G])\s*[:：]\s*(.+)$", ln.strip())
            if m:
                opts.append({"label": m.group(1), "content": m.group(2).strip()})
            else:
                mm = re.match(r"^题干描述\s*[:：]\s*(.*)$", ln.strip())
                stem_parts.append(mm.group(1) if mm else ln)
        stem = "\n".join(x for x in stem_parts if x.strip()).strip()
        if len(stem) < 8:
            continue
        add_q(writer, counters,
              stage="考研", subject="中医综合", stem=stem, options=opts,
              answer=ans, analysis="",
              qtype="单选题" if opts else "解答题",
              knowledge=[scope, "中医"], source="中医考试题库(SylvanL)", quality="B",
              source_url=TCM_URL, region=scope)
    writer.flush()
    log("[tcm] 入库 %d（中文 %d）" % (counters["added"], counters["zh"]))
    return counters["added"]


# ============================================================
# A7 AGIEval 高考系列（hails/agieval-gaokao-*）
# ============================================================
AGI_SUBJ = {
    "biology": "生物", "chemistry": "化学", "chinese": "语文", "english": "英语",
    "geography": "地理", "history": "历史", "mathqa": "数学", "physics": "物理",
}


def harvest_agieval(writer, log):
    counters = {"added": 0, "analysis": 0, "zh": 0, "en": 0}
    import pandas as pd
    for sub, subject in AGI_SUBJ.items():
        url = "%s/datasets/hails/agieval-gaokao-%s/resolve/main/data/test-00000-of-00001.parquet" % (HF, sub)
        try:
            raw = safe_get(url, binary=True, timeout=60)
            df = pd.read_parquet(io.BytesIO(raw))
        except Exception as e:
            log("[agieval] FAIL gaokao-%s -> %s" % (sub, str(e)[:60]))
            continue
        cnt = 0
        for _, row in df.iterrows():
            query = str(row.get("query") or "").strip()
            gold = row.get("gold")
            choices = row.get("choices")
            if not query:
                continue
            # query 形如 '问题：... 选项：(A)... (B)...'；去掉包装前缀
            stem = re.sub(r"^\s*问题\s*[:：]\s*", "", query).strip()
            stem = re.sub(r"\s*选项\s*[:：]\s*$", "", stem).strip()
            opts = []
            if choices is not None and len(choices) > 0:
                for i, c in enumerate(choices):
                    c = str(c).strip()
                    c = re.sub(r"^\(([A-J])\)\s*", "", c)   # 去掉 (A) 前缀
                    if c:
                        opts.append({"label": chr(65 + i), "content": c})
            ans = ""
            if gold is not None and len(gold) > 0:
                try:
                    idxs = [int(g) for g in gold]
                    if len(idxs) == 1 and opts:
                        ans = chr(65 + idxs[0])
                    else:
                        ans = "；".join(chr(65 + i) for i in idxs if i < 26)
                except Exception:
                    ans = ""
            if not ans and not opts:
                continue    # 既无选项也无答案，丢弃
            add_q(writer, counters,
                  stage="高中", subject=subject, stem=stem, options=opts,
                  answer=ans, analysis="",
                  qtype="单选题" if opts else "解答题",
                  source="AGIEval(高考真题)", quality="A",
                  source_url="https://hf-mirror.com/datasets/hails/agieval-gaokao-%s" % sub,
                  region="AGIEval")
            cnt += 1
        writer.flush()
        log("[agieval] gaokao-%-10s %5d 题" % (sub, cnt))
    writer.flush()
    log("[agieval] 入库 %d（中文 %d，英文 %d）" % (counters["added"], counters["zh"], counters["en"]))
    return counters["added"]


SOURCES = ["coig_exam", "ceval", "cqia", "ms_english", "tcm", "agieval"]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--source", default="all", choices=["all"] + SOURCES)
    args = ap.parse_args()

    def log(msg):
        print(msg, flush=True)

    log("===== A类HF数据源采集 | source=%s =====" % args.source)
    writer = BankWriter()
    log("去重基数: %d" % len(writer.seen))

    t0 = time.time()
    runs = SOURCES if args.source == "all" else [args.source]
    totals = {}
    for src in runs:
        totals[src] = globals()["harvest_" + src](writer, log)
    writer.flush()
    log("---- 各源新增: %s ----" % totals)
    log("耗时 %.0fs" % (time.time() - t0))


if __name__ == "__main__":
    main()
