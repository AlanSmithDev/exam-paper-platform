# -*- coding: utf-8 -*-
"""
把 题库/ 下的 JSONL 导出为可直接入库的数据：
  1. SQLite 数据库  题库.db（本地可直接查询/对接后端）
  2. MySQL DDL      schema_mysql.sql（生产环境建表）
  3. CSV            分表 CSV（可选，便于批量导入）

表结构（与组卷网站常见设计对齐）：
  question           题目主表
  question_answer    答案与解析（与主表 1:1，便于按需加载）
  knowledge          知识点字典
  question_knowledge 题目-知识点关联
  question_image     题目配图
  paper              试卷（真题 / 模拟卷 / 练习册）
  paper_question     试卷-题目关联
"""
import os, json, sqlite3, csv, argparse, time, collections

ROOT = r"E:\code\business\zujuan-platform\题库"
DB_PATH = os.path.join(ROOT, "_meta", "题库.db")
MYSQL_DDL = os.path.join(ROOT, "_meta", "schema_mysql.sql")
DUPES_PATH = os.path.join(ROOT, "_meta", "simhash_dupes.json")

MYSQL = """-- 组卷网站题库 · MySQL 建表语句
CREATE TABLE IF NOT EXISTS question (
  qid          VARCHAR(32)  NOT NULL COMMENT '全局唯一ID（内容MD5）',
  stage        VARCHAR(16)  NOT NULL COMMENT '学段：小学/初中/高中/中职/大学/考研',
  subject      VARCHAR(64)  NOT NULL COMMENT '学科',
  grade        VARCHAR(32)  DEFAULT NULL COMMENT '年级',
  type         VARCHAR(32)  DEFAULT NULL COMMENT '题型',
  difficulty   TINYINT      DEFAULT 3 COMMENT '难度 1-5',
  stem         MEDIUMTEXT   NOT NULL COMMENT '题干（HTML/LaTeX）',
  options      JSON         DEFAULT NULL COMMENT '选项 [{"label":"A","content":"..."}]',
  answer       TEXT         DEFAULT NULL COMMENT '答案',
  analysis     MEDIUMTEXT   DEFAULT NULL COMMENT '详细解析',
  year         SMALLINT     DEFAULT NULL COMMENT '年份',
  region       VARCHAR(64)  DEFAULT NULL COMMENT '地区/卷别/适用版本',
  score        DECIMAL(5,2) DEFAULT NULL COMMENT '分值',
  quality      CHAR(1)      DEFAULT 'B' COMMENT '质量分级：A=真题权威 B=教辅习题 C=OCR/来源不明/无答案',
  source       VARCHAR(128) DEFAULT NULL COMMENT '来源',
  source_url   VARCHAR(255) DEFAULT NULL COMMENT '来源地址',
  created_at   DATETIME     DEFAULT NULL,
  PRIMARY KEY (qid),
  KEY idx_subject (stage, subject),
  KEY idx_type (type),
  KEY idx_year (year),
  KEY idx_diff (difficulty),
  FULLTEXT KEY ft_stem (stem)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目主表';

CREATE TABLE IF NOT EXISTS question_answer (
  qid       VARCHAR(32) NOT NULL,
  answer    TEXT,
  analysis  MEDIUMTEXT,
  knowledge JSON COMMENT '知识点标签',
  type      VARCHAR(32),
  source    VARCHAR(128),
  PRIMARY KEY (qid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='答案与解析';

CREATE TABLE IF NOT EXISTS knowledge (
  kid      INT AUTO_INCREMENT PRIMARY KEY,
  name     VARCHAR(128) NOT NULL,
  stage    VARCHAR(16),
  subject  VARCHAR(64),
  UNIQUE KEY uk_k (name, stage, subject)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识点字典';

CREATE TABLE IF NOT EXISTS question_knowledge (
  qid VARCHAR(32) NOT NULL,
  kid INT         NOT NULL,
  PRIMARY KEY (qid, kid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目-知识点关联';

CREATE TABLE IF NOT EXISTS question_image (
  id    INT AUTO_INCREMENT PRIMARY KEY,
  qid   VARCHAR(32) NOT NULL,
  path  VARCHAR(255) NOT NULL COMMENT '相对 题库/ 的路径',
  img_hash CHAR(64)  DEFAULT NULL COMMENT '图片内容 SHA-256（全局去重键）',
  width INT, height INT,
  mime  VARCHAR(32),
  alt   VARCHAR(255) COMMENT '图片描述（供检索与无障碍）',
  KEY idx_qid (qid), KEY idx_hash (img_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目配图';

CREATE TABLE IF NOT EXISTS paper (
  pid        VARCHAR(64) PRIMARY KEY COMMENT '试卷ID',
  stage      VARCHAR(16),
  subject    VARCHAR(64),
  title      VARCHAR(255),
  kind       VARCHAR(16) COMMENT '真题/模拟卷/练习册',
  year       SMALLINT,
  region     VARCHAR(64),
  total_score DECIMAL(6,2),
  source     VARCHAR(128)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试卷';

CREATE TABLE IF NOT EXISTS paper_question (
  pid  VARCHAR(64) NOT NULL,
  qid  VARCHAR(32) NOT NULL,
  seq  INT DEFAULT 0 COMMENT '题号顺序',
  PRIMARY KEY (pid, qid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试卷-题目关联';
"""


def iter_jsonl(subdir):
    """遍历 题库/<学段>/<学科>/<subdir>/*.jsonl"""
    for dirpath, _, files in os.walk(ROOT):
        if os.path.basename(dirpath) != subdir:
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
                        yield fp, json.loads(line)
                    except Exception:
                        continue


_IMG_META_CACHE = {}


def img_meta(rel):
    """读取图片文件元数据：内容 SHA-256 / 宽高 / MIME（同路径只算一次）"""
    if rel in _IMG_META_CACHE:
        return _IMG_META_CACHE[rel]
    meta = {}
    fp = os.path.join(ROOT, rel.replace("/", os.sep))
    if os.path.isfile(fp):
        try:
            with open(fp, "rb") as f:
                data = f.read()
            import hashlib
            meta["img_hash"] = hashlib.sha256(data).hexdigest()
            head = data[:16].lstrip().lower()
            if head.startswith(b"<?xml") or head.startswith(b"<svg"):
                meta["mime"] = "image/svg+xml"
            else:
                try:
                    from PIL import Image
                    import io as _io
                    im = Image.open(_io.BytesIO(data))
                    meta["width"], meta["height"] = im.size
                    meta["mime"] = Image.MIME.get(im.format or "", "image/unknown")
                except Exception:
                    meta["mime"] = "image/unknown"
        except Exception:
            pass
    _IMG_META_CACHE[rel] = meta
    return meta


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--csv", action="store_true", help="额外导出 CSV")
    args = ap.parse_args()

    if os.path.exists(DB_PATH):
        os.remove(DB_PATH)
    con = sqlite3.connect(DB_PATH)
    cur = con.cursor()
    cur.executescript("""
    CREATE TABLE question (qid TEXT PRIMARY KEY, stage TEXT, subject TEXT, grade TEXT,
      type TEXT, difficulty INTEGER, stem TEXT, options TEXT, answer TEXT, analysis TEXT,
      year INTEGER, region TEXT, score REAL, quality TEXT, source TEXT, source_url TEXT, created_at TEXT);
    CREATE TABLE question_answer (qid TEXT PRIMARY KEY, answer TEXT, analysis TEXT,
      knowledge TEXT, type TEXT, source TEXT);
    CREATE TABLE knowledge (kid INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, stage TEXT, subject TEXT,
      UNIQUE(name, stage, subject));
    CREATE TABLE question_knowledge (qid TEXT, kid INTEGER, PRIMARY KEY(qid,kid));
    CREATE TABLE question_image (id INTEGER PRIMARY KEY AUTOINCREMENT, qid TEXT, path TEXT,
      img_hash TEXT, width INTEGER, height INTEGER, mime TEXT, alt TEXT);
    CREATE TABLE paper (pid TEXT PRIMARY KEY, stage TEXT, subject TEXT, title TEXT, kind TEXT,
      year INTEGER, region TEXT, total_score REAL, source TEXT);
    CREATE TABLE paper_question (pid TEXT, qid TEXT, seq INTEGER, PRIMARY KEY(pid,qid));
    CREATE INDEX idx_q_sub ON question(stage, subject);
    CREATE INDEX idx_q_type ON question(type);
    """)

    # 近似去重清单（dedup_sim.py 产出，可选）
    dup_qids = set()
    if os.path.exists(DUPES_PATH):
        try:
            with open(DUPES_PATH, encoding="utf-8") as f:
                dup_qids = set(json.load(f).get("剔除清单") or [])
            print("近似去重清单: 载入 %d 条待剔除" % len(dup_qids))
        except Exception as e:
            print("近似去重清单载入失败(忽略):", e)

    nq = 0
    kmap = {}
    for fp, q in iter_jsonl("题目"):
        if q.get("qid") in dup_qids:
            continue
        cur.execute("INSERT OR REPLACE INTO question VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", (
            q.get("qid"), q.get("stage"), q.get("subject"), q.get("grade"), q.get("type"),
            q.get("difficulty"), q.get("stem"),
            json.dumps(q.get("options") or [], ensure_ascii=False),
            q.get("answer") if isinstance(q.get("answer"), str) else json.dumps(q.get("answer"), ensure_ascii=False),
            q.get("analysis"), q.get("year"), q.get("region"), q.get("score"),
            q.get("quality") or "B",
            q.get("source"), q.get("source_url"), q.get("created_at")))
        cur.execute("INSERT OR REPLACE INTO question_answer VALUES (?,?,?,?,?,?)", (
            q.get("qid"),
            q.get("answer") if isinstance(q.get("answer"), str) else json.dumps(q.get("answer"), ensure_ascii=False),
            q.get("analysis"), json.dumps(q.get("knowledge") or [], ensure_ascii=False),
            q.get("type"), q.get("source")))
        for k in (q.get("knowledge") or []):
            if not k:
                continue
            key = (str(k), q.get("stage"), q.get("subject"))
            if key not in kmap:
                cur.execute("INSERT OR IGNORE INTO knowledge(name,stage,subject) VALUES (?,?,?)", key)
                cur.execute("SELECT kid FROM knowledge WHERE name=? AND stage=? AND subject=?", key)
                kmap[key] = cur.fetchone()[0]
            cur.execute("INSERT OR IGNORE INTO question_knowledge VALUES (?,?)", (q.get("qid"), kmap[key]))
        for img in (q.get("images") or []):
            meta = img_meta(img)
            cur.execute("INSERT INTO question_image(qid,path,img_hash,width,height,mime,alt) "
                        "VALUES (?,?,?,?,?,?,?)",
                        (q.get("qid"), img,
                         meta.get("img_hash"), meta.get("width"), meta.get("height"),
                         meta.get("mime"), meta.get("alt")))
        nq += 1

    # 答案目录（若有独立答案文件）
    na = 0
    for fp, a in iter_jsonl("答案"):
        qid = a.get("qid")
        if not qid:
            continue
        cur.execute("UPDATE question_answer SET answer=?, analysis=? WHERE qid=?",
                    (a.get("answer") if isinstance(a.get("answer"), str) else json.dumps(a.get("answer"), ensure_ascii=False),
                     a.get("analysis"), qid))
        na += 1

    # 试卷（真题/模拟卷/练习册目录里的 JSON，以及逐行的 papers.jsonl）
    np_ = 0
    for kind in ("试卷", "模拟卷", "练习册"):
        for dirpath, _, files in os.walk(ROOT):
            if os.path.basename(dirpath) != kind:
                continue
            for fn in files:
                fp = os.path.join(dirpath, fn)
                try:
                    if fn.endswith(".jsonl"):
                        papers = []
                        with open(fp, encoding="utf-8") as f:
                            for line in f:
                                line = line.strip()
                                if line:
                                    papers.append(json.loads(line))
                    elif fn.endswith(".json"):
                        with open(fp, encoding="utf-8") as f:
                            papers = [json.load(f)]
                    else:
                        continue
                except Exception:
                    continue
                for p in papers:
                    pid = p.get("pid") or (kind + "_" + fn)
                    cur.execute("INSERT OR REPLACE INTO paper VALUES (?,?,?,?,?,?,?,?,?)", (
                        pid, p.get("stage"), p.get("subject"), p.get("title"), kind,
                        p.get("year"), p.get("region"), p.get("total_score"), p.get("source")))
                    for i, qid in enumerate(p.get("questions") or []):
                        if qid in dup_qids:      # 整卷引用同样剔除近似重复
                            continue
                        cur.execute("INSERT OR IGNORE INTO paper_question VALUES (?,?,?)", (pid, qid, i))
                    np_ += 1

    con.commit()

    if args.csv:
        cdir = os.path.join(ROOT, "_meta", "csv")
        os.makedirs(cdir, exist_ok=True)
        for t in ("question", "question_answer", "knowledge", "question_knowledge", "question_image"):
            cur.execute("SELECT * FROM %s" % t)
            cols = [d[0] for d in cur.description]
            with open(os.path.join(cdir, t + ".csv"), "w", newline="", encoding="utf-8-sig") as f:
                w = csv.writer(f)
                w.writerow(cols)
                w.writerows(cur.fetchall())

    with open(MYSQL_DDL, "w", encoding="utf-8-sig") as f:
        f.write(MYSQL)

    # 汇总
    cur.execute("SELECT COUNT(*) FROM question"); a = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM question_answer"); b = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM knowledge"); c = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM question_image"); d = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM paper"); e = cur.fetchone()[0]
    cur.execute("SELECT quality, COUNT(*) FROM question GROUP BY quality"); qs = cur.fetchall()
    print("question           %d  (质量分级: %s)" % (a, ", ".join("%s=%s" % (k, v) for k, v in qs)))
    print("question_answer    %d" % b)
    print("knowledge          %d" % c)
    print("question_image     %d" % d)
    print("paper              %d" % e)
    print("SQLite:", DB_PATH)
    print("MySQL DDL:", MYSQL_DDL)
    con.close()


if __name__ == "__main__":
    main()
