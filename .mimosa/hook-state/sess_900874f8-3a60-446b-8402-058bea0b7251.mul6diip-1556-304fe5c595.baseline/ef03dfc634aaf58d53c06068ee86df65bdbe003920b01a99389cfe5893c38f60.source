# -*- coding: utf-8 -*-
"""
题库采集核心：统一 schema / 去重 / 分片落盘 / 图片下载 / 统计。
所有数据源适配器都把题目归一化为 normalize_question() 的输出格式。
"""
import os, json, time, hashlib, re, ssl, threading
import urllib.request, urllib.error
from datetime import datetime

# ---------------- 全局配置 ----------------
ROOT = r"E:\code\business\zujuan-platform\题库"
# GitHub raw 直连在本机超时，走国内镜像
MIRRORS = [
    "https://gh-proxy.com/",
    "https://ghproxy.net/",
    "https://cdn.jsdelivr.net/gh/",   # 需不同 URL 形式，见 fetch_github_raw
    "https://raw.githubusercontent.com/",
]
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

_SSL_CTX = ssl.create_default_context()
_SSL_CTX.check_hostname = False
_SSL_CTX.verify_mode = ssl.CERT_NONE


# ---------------- HTTP ----------------
def http_get(url, timeout=25, retries=3, headers=None, binary=False):
    """带重试的 GET，返回 str 或 bytes"""
    hdr = {"User-Agent": UA, "Accept": "*/*", "Accept-Language": "zh-CN,zh;q=0.9"}
    if headers:
        hdr.update(headers)
    last = None
    for i in range(retries):
        try:
            req = urllib.request.Request(url, headers=hdr)
            raw = urllib.request.urlopen(req, timeout=timeout, context=_SSL_CTX).read()
            if binary:
                return raw
            for enc in ("utf-8", "gbk", "latin-1"):
                try:
                    return raw.decode(enc)
                except Exception:
                    continue
            return raw.decode("utf-8", "ignore")
        except Exception as e:
            last = e
            time.sleep(0.8 * (i + 1))
    raise last


def fetch_github_raw(repo, path, ref="main", timeout=30):
    """
    repo: 'owner/name'  path: 'Data/x.json'
    依次尝试镜像，返回 str
    """
    raw_url = "https://raw.githubusercontent.com/%s/%s/%s" % (repo, ref, path)
    attempts = [
        "https://gh-proxy.com/" + raw_url,
        "https://ghproxy.net/" + raw_url,
        "https://cdn.jsdelivr.net/gh/%s@%s/%s" % (repo, ref, path),
        raw_url,
    ]
    last = None
    for u in attempts:
        try:
            return http_get(u, timeout=timeout, retries=1)
        except Exception as e:
            last = e
    raise last


# ---------------- 工具 ----------------
def md5(s):
    return hashlib.md5(s.encode("utf-8", "ignore")).hexdigest()


def slugify(name):
    bad = '<>:"/\\|?*'
    s = "".join(("_" if c in bad else c) for c in str(name)).strip()
    s = re.sub(r"\.{2,}", "_", s)      # 防路径穿越：连续点替换
    return s.lstrip(".").rstrip(".") or "_"


def safe_join(base, *parts):
    """路径拼接 + 校验：分量不得含分隔符或 ..，结果必须落在 base 内。"""
    for p in parts:
        p = str(p)
        if (not p or re.search(r"[\\/]", p) or p == ".."
                or p.startswith(".") or ":" in p):
            raise ValueError("非法路径分量: %r" % p[:60])
    base_abs = os.path.abspath(base)
    full = os.path.abspath(os.path.join(base_abs, *[str(p) for p in parts]))
    if os.path.commonpath([full, base_abs]) != base_abs:
        raise ValueError("路径越界: %r" % full[:80])
    return full


def strip_html(html):
    """极简 HTML → 文本（不依赖 bs4）"""
    t = re.sub(r"<script[\s\S]*?</script>", " ", html, flags=re.I)
    t = re.sub(r"<style[\s\S]*?</style>", " ", t, flags=re.I)
    t = re.sub(r"<br\s*/?>", "\n", t, flags=re.I)
    t = re.sub(r"</(p|div|li|tr|h[1-6])>", "\n", t, flags=re.I)
    t = re.sub(r"<[^>]+>", "", t)
    t = (t.replace("&nbsp;", " ").replace("&amp;", "&")
          .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", '"'))
    return re.sub(r"[ \t]+", " ", t).strip()


def guess_type(stem, options=None, explicit=None):
    if explicit:
        return explicit
    if options:
        return "单选题"
    s = stem or ""
    if re.search(r"填空|_____|\{\{", s):
        return "填空题"
    if re.search(r"判断|正确.*错误|对.*错", s):
        return "判断题"
    if re.search(r"证明", s):
        return "证明题"
    if re.search(r"计算|求解|求值", s):
        return "计算题"
    if re.search(r"简答|说明|分析|为什么", s):
        return "解答题"
    return "解答题"


# ---------------- 统一题目对象 ----------------
def normalize_question(stage, subject, stem, *, options=None, answer=None,
                       analysis="", qtype=None, year=None, grade="",
                       knowledge=None, source="", source_url="", region="",
                       score=None, difficulty=None, images=None, quality=None):
    stem = (stem or "").strip()
    options = options or []
    qid = md5("%s|%s|%s" % (stage, subject, re.sub(r"\s+", "", stem))[:4000])
    # 质量分级（PART 4）：A=真题权威 / B=教辅习题 / C=OCR或来源不明或无答案。
    # 显式传 quality 优先；否则自动判：无答案或无 source_url → C，真题源含关键字 → A，其余 B。
    if quality is None:
        src = (source or "") + " " + (source_url or "")
        if not (answer or "").strip() or not (source_url or "").strip():
            quality = "C"
        elif re.search(r"真题|考试院|GAOKAO|kaoyan|官方", src, re.I):
            quality = "A"
        else:
            quality = "B"
    q = {
        "qid": qid,
        "stage": stage,
        "subject": subject,
        "grade": grade,
        "type": guess_type(stem, options, qtype),
        "difficulty": difficulty if difficulty is not None else 3,
        "stem": stem,
        "options": options,
        "answer": answer if answer is not None else "",
        "analysis": (analysis or "").strip(),
        "knowledge": knowledge or [],
        "images": images or [],
        "source": source,
        "source_url": source_url,
        "year": year,
        "region": region,
        "score": score,
        "quality": quality,
        "created_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
    }
    return q


# ---------------- 落盘（分片 + 去重 + 计数） ----------------
class BankWriter:
    """按 学段/学科 分片写入 JSONL；答案同写一份；全局去重"""

    def __init__(self, root=ROOT, shard_size=5000):
        self.root = root
        self.shard_size = shard_size
        self.seen = set()          # 全局 qid 去重
        self.stats = {}            # (stage,subject) -> {题目, 答案, 图片}
        self._lock = threading.Lock()
        self._buffers = {}         # key -> list
        self._load_seen()

    def _load_seen(self):
        """启动时扫描已有 JSONL，恢复去重集合（断点续采）"""
        for dirpath, _, files in os.walk(self.root):
            if os.path.basename(dirpath) != "题目":
                continue
            for fn in files:
                if not fn.endswith(".jsonl"):
                    continue
                fp = os.path.join(dirpath, fn)
                try:
                    with open(fp, "r", encoding="utf-8") as f:
                        for line in f:
                            line = line.strip()
                            if not line:
                                continue
                            try:
                                self.seen.add(json.loads(line)["qid"])
                            except Exception:
                                pass
                except Exception:
                    pass

    def _key(self, stage, subject):
        return "%s/%s" % (stage, subject)

    def add(self, q):
        """加入一道题（去重 + 缓冲）"""
        qid = q.get("qid")
        if not qid or qid in self.seen:
            return False
        self.seen.add(qid)
        k = self._key(q["stage"], q["subject"])
        with self._lock:
            self._buffers.setdefault(k, []).append(q)
        return True

    def flush(self):
        """把缓冲写入磁盘分片"""
        total = 0
        for k, items in list(self._buffers.items()):
            if not items:
                continue
            stage, subject = k.split("/", 1)
            base = os.path.join(self.root, slugify(stage), slugify(subject))
            qdir = os.path.join(base, "题目")
            adir = os.path.join(base, "答案")
            os.makedirs(qdir, exist_ok=True)
            os.makedirs(adir, exist_ok=True)

            # 追加到当前未满的分片
            shard = self._pick_shard(qdir)
            qpath = os.path.join(qdir, shard)
            apath = os.path.join(adir, shard.replace(".jsonl", "_答案.jsonl"))
            with open(qpath, "a", encoding="utf-8") as fq, \
                    open(apath, "a", encoding="utf-8") as fa:
                for q in items:
                    fq.write(json.dumps(q, ensure_ascii=False) + "\n")
                    fa.write(json.dumps({
                        "qid": q["qid"],
                        "answer": q["answer"],
                        "analysis": q["analysis"],
                        "knowledge": q["knowledge"],
                        "type": q["type"],
                        "source": q["source"],
                    }, ensure_ascii=False) + "\n")
            st = self.stats.setdefault(k, {"题目": 0, "答案": 0, "图片": 0})
            st["题目"] += len(items)
            st["答案"] += len(items)
            total += len(items)
            self._buffers[k] = []
        return total

    def _pick_shard(self, qdir):
        """找到最后一个未满 5000 行的分片，否则新建"""
        best, best_n = None, -1
        if os.path.isdir(qdir):
            for fn in sorted(os.listdir(qdir)):
                if fn.endswith(".jsonl"):
                    try:
                        n = sum(1 for _ in open(os.path.join(qdir, fn), encoding="utf-8"))
                    except Exception:
                        n = 0
                    if n < self.shard_size and (best is None or n > best_n):
                        best, best_n = fn, n
        if best:
            return best
        idx = 1
        while os.path.exists(os.path.join(qdir, "part_%04d.jsonl" % idx)):
            idx += 1
        return "part_%04d.jsonl" % idx

    # ---------------- 图片 ----------------
    def save_image(self, stage, subject, url, name=None):
        """下载题目配图到 图片/ 目录，返回相对路径；失败返回 None"""
        try:
            img_dir = os.path.join(self.root, slugify(stage), slugify(subject), "图片")
            os.makedirs(img_dir, exist_ok=True)
            if not name:
                ext = os.path.splitext(url.split("?")[0])[1] or ".png"
                name = md5(url) + ext
            fp = os.path.join(img_dir, slugify(name))
            if os.path.exists(fp):
                return os.path.relpath(fp, self.root)
            data = http_get(url, timeout=25, retries=2, binary=True)
            if not data or len(data) < 100:
                return None
            with open(fp, "wb") as f:
                f.write(data)
            k = self._key(stage, subject)
            self.stats.setdefault(k, {"题目": 0, "答案": 0, "图片": 0})["图片"] += 1
            return os.path.relpath(fp, self.root).replace("\\", "/")
        except Exception:
            return None

    def report(self):
        tot = sum(v["题目"] for v in self.stats.values())
        img = sum(v["图片"] for v in self.stats.values())
        return self.stats, tot, img


# ---------------- 学科目录 ----------------
def load_catalog():
    p = os.path.join(ROOT, "_meta", "学科目录.json")
    with open(p, "r", encoding="utf-8") as f:
        return json.load(f)


def subject_exists(stage, subject):
    return os.path.isdir(os.path.join(ROOT, slugify(stage), slugify(subject)))
