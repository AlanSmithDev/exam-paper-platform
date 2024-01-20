# -*- coding: utf-8 -*-
"""
近似去重（PART 3 第 2 层）：SimHash 64bit + (学段,学科,题型) 分桶 + 4×16bit 分段倒排
+ 汉明距离 ≤3 判重 + 并查集聚类，保留信息量更全的一条。

报告 JSON 直接打到 stdout，由调用方重定向落盘（本脚本不做任何文件写入）：
  python dedup_sim.py > "..\\..\\题库\\_meta\\simhash_dupes.json"

export_db.py 与 report.py 构建时若发现该文件，会自动跳过清单中的重复 qid。
"""
import os, sys, json, re, time, collections

ROOT = r"E:\code\business\zujuan-platform\题库"

MASK64 = (1 << 64) - 1


def normalize_for_simhash(stem):
    """PART 3 规范化：去 HTML 标签、去 LaTeX 命令、只留汉字/数字/字母"""
    s = re.sub(r"<[^>]+>", "", stem or "")
    s = re.sub(r"\$[^$]*\$", " ", s)                 # 行内公式整体去掉（同一公式不同写法不再干扰）
    s = re.sub(r"\\[a-zA-Z]+", "", s)
    s = re.sub(r"[^\u4e00-\u9fff0-9a-zA-Z]", "", s)
    return s.lower()


def _h64(x):
    h = (x * 0x9E3779B97F4A7C15) & MASK64
    h ^= h >> 29
    h = (h * 0xBF58476D1CE4E5B9) & MASK64
    h ^= h >> 32
    return h


def simhash(text, n=3):
    """字符 n-gram 频次加权 SimHash"""
    if len(text) < n:
        return 0
    v = [0] * 64
    for i in range(len(text) - n + 1):
        g = text[i:i + n]
        hv = _h64(hash(g) & MASK64)
        for b in range(64):
            v[b] += 1 if (hv >> b) & 1 else -1
    h = 0
    for b in range(64):
        if v[b] > 0:
            h |= (1 << b)
    return h


def hamming(a, b):
    return bin(a ^ b).count("1")


# 来源权威度（越大越优先保留）
_SRC_RANK = [("真题", 50), ("GAOKAO", 50), ("考试院", 50), ("AGIEval", 40),
             ("C-Eval", 30), ("CMMLU", 25), ("COIG", 20), ("K12-KGraph", 15)]


def source_rank(source):
    s = source or ""
    for k, r in _SRC_RANK:
        if k.lower() in s.lower():
            return r
    return 10


def richness(q):
    """信息量评分：解析 > 选项 > 来源权威 > 题干更长"""
    return ((100 if (q.get("analysis") or "").strip() else 0)
            + (50 if q.get("options") else 0)
            + source_rank(q.get("source"))
            + min(len(q.get("stem") or ""), 2000) / 2000.0)


def iter_questions():
    for dirpath, _, files in os.walk(ROOT):
        if os.path.basename(dirpath) != "题目":
            continue
        for fn in sorted(files):
            if not fn.endswith(".jsonl"):
                continue
            fp = os.path.join(dirpath, fn)
            with open(fp, "r", encoding="utf-8") as f:
                for ln in f:
                    ln = ln.strip()
                    if not ln:
                        continue
                    try:
                        q = json.loads(ln)
                    except Exception:
                        continue
                    yield fp, q


def scan():
    """全库扫描，桶内近似去重。返回 (dup_map, total, clusters)"""
    buckets = collections.defaultdict(list)   # (stage,subject,type) -> [(qid, sim, rich)]
    total = 0
    t0 = time.time()
    for fp, q in iter_questions():
        total += 1
        stem = q.get("stem") or ""
        norm = normalize_for_simhash(stem)
        if len(norm) < 10:
            continue    # 过短不参与（避免误合并）
        sh = simhash(norm)
        key = (q.get("stage", "?"), q.get("subject", "?"), q.get("type", "?"))
        buckets[key].append((q.get("qid"), sh, richness(q)))
    print("扫描 %d 题（%.0fs），桶 %d 个" % (total, time.time() - t0, len(buckets)), file=sys.stderr)

    dup_map = {}       # dup_qid -> kept_qid
    clusters = 0
    for key, items in buckets.items():
        if len(items) < 2:
            continue
        # 4×16bit 分段倒排：汉明距离≤3 必然至少一段相同（鸽笼原理）
        inv = [collections.defaultdict(list) for _ in range(4)]
        for idx, (qid, sh, rich) in enumerate(items):
            for seg in range(4):
                inv[seg][(sh >> (seg * 16)) & 0xFFFF].append(idx)
        parent = list(range(len(items)))

        def find(x):
            while parent[x] != x:
                parent[x] = parent[parent[x]]
                x = parent[x]
            return x

        def union(a, b):
            ra, rb = find(a), find(b)
            if ra != rb:
                parent[max(ra, rb)] = min(ra, rb)

        seen_pair = set()
        for idx, (qid, sh, rich) in enumerate(items):
            cands = set()
            for seg in range(4):
                for j in inv[seg].get((sh >> (seg * 16)) & 0xFFFF, ()):
                    if j != idx:
                        cands.add(j)
            for j in cands:
                pj = (min(idx, j), max(idx, j))
                if pj in seen_pair:
                    continue
                seen_pair.add(pj)
                if hamming(sh, items[j][1]) <= 3:
                    union(idx, j)

        groups = collections.defaultdict(list)
        for idx in range(len(items)):
            groups[find(idx)].append(idx)
        for g in groups.values():
            if len(g) < 2:
                continue
            clusters += 1
            # 保留信息量最全的一条（同分保留先出现的）
            g_sorted = sorted(g, key=lambda i: (-items[i][2], i))
            kept = items[g_sorted[0]][0]
            for i in g_sorted[1:]:
                dq = items[i][0]
                if dq != kept:
                    dup_map[dq] = kept
    print("聚类 %d 组，需剔除 %d 题（%.0fs）" % (clusters, len(dup_map), time.time() - t0),
          file=sys.stderr)
    return dup_map, total, clusters


def main():
    dup_map, total, clusters = scan()
    report = {
        "说明": "近似重复(SimHash汉明距离≤3)剔除清单；export_db.py/report.py 已自动跳过本清单中的 qid",
        "生成时间": time.strftime("%Y-%m-%d %H:%M:%S"),
        "扫描总数": total,
        "重复聚类": clusters,
        "剔除数": len(dup_map),
        "剔除清单": sorted(dup_map),
        "映射样例": {k: v for k, v in list(dup_map.items())[:2000]},
    }
    json.dump(report, sys.stdout, ensure_ascii=False, indent=1)
    sys.stdout.write("\n")


if __name__ == "__main__":
    main()
