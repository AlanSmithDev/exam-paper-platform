# -*- coding: utf-8 -*-
"""Build the question-bank folder tree for zujuan-platform.

Structure:  题库/{学段}/{学科}/{题目,试卷,模拟卷,练习册,图形,答案}
Also emits a manifest.json + per-subject README stub + _meta schema.
"""
import json
import os

ROOT = r"E:\code\business\zujuan-platform\题库"

# (学段, [学科...])  —— 依据 docs/11-多学段扩展方案.md 的学段×学科矩阵
STAGES = [
    ("小学", ["数学", "语文", "英语"]),
    ("初中", ["数学", "语文", "英语", "物理", "化学", "生物", "道德与法治", "历史", "地理"]),
    ("高中", ["数学", "语文", "英语", "物理", "化学", "生物", "政治", "历史", "地理",
              "信息技术", "通用技术", "日语"]),
    ("大学", ["高等数学", "线性代数", "概率论与数理统计", "大学英语四级", "大学英语六级",
              "大学物理", "数据结构", "操作系统", "计算机网络", "数据库原理", "编译原理"]),
    ("考研", ["政治", "英语一", "英语二", "数学一", "数学二", "数学三",
              "396经济类联考", "408计算机", "西医综合", "法律硕士", "199管理类联考"]),
]

# 每个学科下的内容分类
Buckets = ["题目", "试卷", "模拟卷", "练习册", "图形", "答案"]

manifest = {"root": ROOT, "stages": []}
created = 0

for stage, subjects in STAGES:
    stage_entry = {"stage": stage, "subjects": []}
    for subj in subjects:
        base = os.path.join(ROOT, stage, subj)
        for b in Buckets:
            p = os.path.join(base, b)
            os.makedirs(p, exist_ok=True)
            created += 1
        stage_entry["subjects"].append({
            "subject": subj,
            "path": os.path.join(stage, subj).replace("\\", "/"),
            "buckets": Buckets,
        })
    manifest["stages"].append(stage_entry)

# ---- _meta: 题目统一 schema ----
meta_dir = os.path.join(ROOT, "_meta")
os.makedirs(meta_dir, exist_ok=True)

SCHEMA = {
    "$comment": "单题统一结构（JSONL 一行一题，UTF-8）。与 docs/04-数据库设计.md 的 question 表对齐。",
    "fields": {
        "qid": "string  全局唯一ID，格式 ZQ-{stage}-{subject}-{yyyy}-{8位序号}",
        "stage": "string  小学|初中|高中|大学|考研",
        "subject": "string  学科名",
        "grade": "string  年级（小学一年级…高三；大学/考研可为空或写'考研'）",
        "textbook_version": "string  教材版本：人教A/北师大/苏教/沪教/新课标…",
        "source_type": "enum  题目|试卷|模拟卷|练习册",
        "source_name": "string  来源名称，如《2025年新课标I卷数学》《五三·高中数学必修一》",
        "question_type": "string  单选题|多选题|填空题|解答题|计算题|证明题|应用题|作文|翻译|实验探究…",
        "difficulty": "number  0~1 难度系数（越大越难）",
        "difficulty_label": "enum  易|中|难",
        "knowledge_points": "array[string]  知识点路径数组，如 ['函数','三角函数','正弦定理']",
        "stem": "string  题干，公式用 $...$ / $$...$$ LaTeX 包裹",
        "options": "array[object]  选择题选项 [{label:'A', content:'…'}]，非选择题为空数组",
        "answer": "string  答案（选择题为 'A'，解答题为结论文本）",
        "analysis": "string  解析（五段式：考点/思路/详解/点评/易错）",
        "solution_steps": "array[string]  分步解答，逐步一行",
        "figures": "array[object]  图形 [{file:'图形/xxx.png', caption:'如图,AB⊥CD', dsl:'FigureDSL源码'}]",
        "score": "number  建议分值",
        "year": "int  年份（真题/模拟卷）",
        "region_code": "string  地区码或考研卷种（数一/数二/408…）",
        "school_source": "string  院校自命题来源（考研专业课）",
        "quality": "object  {status:'raw|reviewed|published', reviewer:'', ocr_confidence:0.0}",
        "ext": "object  扩展字段（学段特有标签）",
    },
}

with open(os.path.join(meta_dir, "题目schema.json"), "w", encoding="utf-8") as f:
    json.dump(SCHEMA, f, ensure_ascii=False, indent=2)

with open(os.path.join(meta_dir, "manifest.json"), "w", encoding="utf-8") as f:
    json.dump(manifest, f, ensure_ascii=False, indent=2)

# ---- 每个学科写一个占位说明 ----
SUBJ_DOC = """# {stage} · {subj} 题库目录

## 目录约定
| 子目录 | 放什么 |
|---|---|
| `题目/` | 单题（一道题一个 JSONL 分片，或 shards/question_0001.jsonl） |
| `试卷/` | 历年真题整卷（含原卷 PDF/图片 + 结构化 JSON） |
| `模拟卷/` | 模拟卷 / 联考卷 / 名校卷 |
| `练习册/` | 教辅练习册（五三、必刷题、教材课后习题…）分册拆分 |
| `图形/` | 题干/解析配图，白底清洗后的 PNG/SVG + Figure DSL 源码 |
| `答案/` | 与上面四类对应的答案与解析（按同名文件对齐） |

## 数据格式
统一 JSONL（一行一题），字段定义见 `_meta/题目schema.json`。
公式一律 LaTeX；图形统一白底 PNG + 可选 SVG/DSL 源码，便于导出 Word 时保真。

## 当前状态
- 题目数量：**0**（待灌入）
- 数据来源：待定（见根目录 README 的数据获取方案）
"""

for stage, subjects in STAGES:
    for subj in subjects:
        p = os.path.join(ROOT, stage, subj, "README.md")
        with open(p, "w", encoding="utf-8") as f:
            f.write(SUBJ_DOC.format(stage=stage, subj=subj))

# ---- 汇总日志（ASCII 安全） ----
lines = []
total_subj = 0
for stage, subjects in STAGES:
    total_subj += len(subjects)
    lines.append(f"{stage}: {len(subjects)} subjects -> " + ", ".join(subjects))
lines.append("")
lines.append(f"TOTAL stages   = {len(STAGES)}")
lines.append(f"TOTAL subjects = {total_subj}")
lines.append(f"TOTAL dirs     = {created} (+ _meta)")
with open(r"E:\code\business\zujuan-platform\_build_log.txt", "w", encoding="utf-8") as f:
    f.write("\n".join(lines))

print("OK")
