# -*- coding: utf-8 -*-
"""
生成「小学 → 初中 → 高中 → 中职 → 大学 → 考研」全学段全学科题库目录骨架。

每个学科目录下固定 7 个子目录：
  题目/     JSONL 分片题目（每片 5000 题，支持千万级）
  试卷/     历年真题整卷（JSON）
  模拟卷/   模拟卷 / 押题卷
  练习册/   章节练习、课时练、专题训练
  答案/     与题目一一对应的详细解析（含分步解析、考点、易错点）
  图片/     图形/公式/图表截图（数学几何图、物理电路图、化学装置图…）
  _index/   学科级索引与采集状态
"""
import os, json, hashlib

ROOT = r"E:\code\business\zujuan-platform\题库"

# ---------------- 学科总表 ----------------
TREE = {
    "小学": [
        "语文", "数学", "英语", "道德与法治", "科学", "信息技术",
        "体育与健康", "音乐", "美术", "书法", "综合实践活动", "心理健康教育",
    ],
    "初中": [
        "语文", "数学", "英语", "物理", "化学", "生物",
        "道德与法治", "历史", "地理", "信息技术", "体育与健康", "音乐", "美术",
    ],
    "高中": [
        "语文", "数学", "英语", "物理", "化学", "生物",
        "政治", "历史", "地理", "信息技术", "通用技术",
        "体育与健康", "音乐", "美术", "日语", "俄语",
    ],
    "中职": [
        "语文", "数学", "英语", "计算机应用基础", "职业道德与法律",
        "电工电子技术", "机械基础", "会计基础", "旅游服务与管理", "护理学基础",
    ],
    "大学": [
        # 数学类
        "高等数学", "线性代数", "概率论与数理统计", "离散数学", "数学分析", "复变函数与积分变换",
        # 计算机类
        "数据结构", "操作系统", "计算机网络", "数据库原理", "计算机组成原理",
        "算法设计与分析", "软件工程", "编译原理", "人工智能", "机器学习", "C语言程序设计", "Java程序设计",
        # 外语类
        "大学英语四级", "大学英语六级", "大学日语", "学术英语读写",
        # 理科基础
        "大学物理", "大学化学", "工程数学",
        # 经管类
        "微观经济学", "宏观经济学", "管理学原理", "会计学", "财务管理",
        "市场营销", "统计学", "运筹学", "金融学", "国际贸易",
        # 法学类
        "法理学", "民法", "刑法", "宪法学", "经济法",
        # 医学类
        "系统解剖学", "生理学", "病理学", "药理学", "内科学", "外科学", "生物化学",
        # 工科类
        "电路原理", "模拟电子技术", "数字电子技术", "信号与系统", "自动控制原理",
        "材料力学", "理论力学", "机械设计基础", "土木工程材料", "工程制图",
        # 思政类
        "马克思主义基本原理", "中国近现代史纲要", "思想道德与法治", "毛泽东思想和中国特色社会主义理论体系概论",
    ],
    "考研": [
        # 公共课
        "政治", "英语一", "英语二", "数学一", "数学二", "数学三",
        # 统考专业课
        "408计算机学科专业基础", "199管理类联考综合能力", "396经济类联考综合能力",
        "311教育学专业基础", "312心理学专业基础", "西医综合", "中医综合",
        "法硕法学", "法硕非法学", "历史学基础", "数学分析与高等代数",
        # 热门自命题专业课
        "431金融学综合", "434国际商务专业基础", "433税务专业基础", "435保险专业基础",
        "436资产评估专业基础", "432应用统计", "357英语翻译基础", "448汉语写作与百科知识",
        "334新闻与传播专业综合能力", "440新闻与传播专业基础",
        "机械原理", "电路", "材料科学基础", "自动控制原理(自命题)", "有机化学", "普通物理",
    ],
}

SUBDIRS = ["题目", "试卷", "模拟卷", "练习册", "答案", "图片", "_index"]

# 题型字典（写入学科 README，供采集与组卷使用）
Q_TYPES = ["单选题", "多选题", "填空题", "判断题", "计算题", "解答题", "证明题",
           "实验探究题", "作图题", "阅读理解", "完形填空", "语法填空", "短文改错",
           "书面表达", "材料分析题", "论述题", "综合题", "选做题", "编程题", "简答题"]


def slugify(name):
    """Windows 合法目录名清洗"""
    bad = '<>:"/\\|?*'
    return "".join(("_" if c in bad else c) for c in name).strip().rstrip(".")


def count_all():
    return sum(len(v) for v in TREE.values())


def main():
    created_dirs = 0
    catalog = {"学段": []}

    for stage, subjects in TREE.items():
        stage_dir = os.path.join(ROOT, slugify(stage))
        entry = {"学段": stage, "路径": stage_dir, "学科": []}
        for subj in subjects:
            subj_name = slugify(subj)
            subj_dir = os.path.join(stage_dir, subj_name)
            for sd in SUBDIRS:
                d = os.path.join(subj_dir, sd)
                os.makedirs(d, exist_ok=True)
                created_dirs += 1
            readme = os.path.join(subj_dir, "README.md")
            if not os.path.exists(readme):
                # 注意：% 与 + 同优先级左结合，必须先单独格式化标题，否则会落到最后一段表格上
                head = "# %s · %s\n\n" % (stage, subj)
                body = (
                    "## 目录说明\n\n"
                    "| 目录 | 内容 | 文件格式 |\n|---|---|---|\n"
                    "| `题目/` | 题目主数据，按片存储 | JSONL，每片 5000 题 |\n"
                    "| `试卷/` | 历年真题整卷 | JSON |\n"
                    "| `模拟卷/` | 模拟卷 / 押题卷 | JSON |\n"
                    "| `练习册/` | 章节练 / 专题练 / 课时练 | JSONL |\n"
                    "| `答案/` | 详细解析（分步、考点、易错点） | JSONL，与题目 qid 对应 |\n"
                    "| `图片/` | 图形 / 公式 / 图表截图 | PNG / JPG / SVG |\n"
                    "| `_index/` | 学科索引、知识点树、采集状态 | JSON |\n\n"
                    "## 题型覆盖\n\n" + "、".join(Q_TYPES) + "\n\n"
                    "## 题目字段规范\n\n"
                    "| 字段 | 类型 | 说明 |\n|---|---|---|\n"
                    "| `qid` | string | 全局唯一 ID（内容 MD5，去重用） |\n"
                    "| `stage` | string | 学段 |\n"
                    "| `subject` | string | 学科 |\n"
                    "| `grade` | string | 年级 |\n"
                    "| `type` | string | 题型 |\n"
                    "| `difficulty` | int | 1-5 难度 |\n"
                    "| `stem` | string | 题干（含 HTML/LaTeX） |\n"
                    "| `options` | list | 选项 A-D |\n"
                    "| `answer` | string/list | 答案 |\n"
                    "| `analysis` | string | 详细解析 |\n"
                    "| `knowledge` | list | 知识点标签 |\n"
                    "| `images` | list | 配图相对路径 |\n"
                    "| `source` | string | 来源（真题/模拟/练习册/开源数据集） |\n"
                    "| `year` | int | 年份 |\n"
                    "| `region` | string | 地区 / 适用版本 |\n"
                )
                with open(readme, "w", encoding="utf-8-sig") as f:
                    f.write(head + body)
            entry["学科"].append({"学科": subj, "路径": subj_dir})
        catalog["学段"].append(entry)

    os.makedirs(os.path.join(ROOT, "_meta"), exist_ok=True)
    cat_path = os.path.join(ROOT, "_meta", "学科目录.json")
    with open(cat_path, "w", encoding="utf-8-sig") as f:
        json.dump(catalog, f, ensure_ascii=False, indent=2)

    print("学段数:", len(TREE))
    print("学科总数:", count_all())
    print("创建/确认目录数:", created_dirs)
    print("目录清单:", cat_path)


if __name__ == "__main__":
    main()
