# -*- coding: utf-8 -*-
"""探测 A 类 HF 数据源的真实结构（文件清单/大小/字段样例），只读不写。
全部经 safe_http 白名单抓取。"""
import json, sys, time

sys.path.insert(0, ".")
from safe_http import safe_get, safe_open, assert_safe_url


def api_tree(repo, repo_type="datasets"):
    """列 HF 仓库文件树（走 hf-mirror API）"""
    url = "https://hf-mirror.com/api/%s/%s/tree/main?recursive=true&expand=false" % (repo_type, repo)
    try:
        d = json.loads(safe_get(url, timeout=30))
        return [(t["path"], t.get("size", 0)) for t in d if t.get("type") == "file"]
    except Exception as e:
        return [("ERR:" + str(e)[:60], 0)]


def sample_lines(url, n=2):
    """取 JSONL 前几行看字段"""
    try:
        r = safe_open(url, timeout=30)
        out = []
        for i, line in enumerate(r):
            if i >= n:
                break
            o = json.loads(line)
            out.append({k: str(v)[:70] for k, v in o.items()})
        r.close()
        return out
    except Exception as e:
        return ["ERR:" + str(e)[:70]]


if __name__ == "__main__":
    t0 = time.time()
    print("== C-Eval repo tree(前20) ==")
    for p, s in api_tree("ceval/ceval-exam")[:20]:
        print("  %10s %s" % (s, p))

    print("== COIG-CQIA full.jsonl ==")
    try:
        u = "https://hf-mirror.com/datasets/m-a-p/COIG-CQIA/resolve/main/COIG-CQIA-full.jsonl"
        r = safe_open(u, timeout=30)
        print("  status", r.status, "len", r.headers.get("Content-Length"))
        line = r.readline()
        print("  keys:", list(json.loads(line).keys()))
        r.close()
    except Exception as e:
        print("  ERR", str(e)[:80])

    print("== agieval gaokao-biology parquet ==")
    try:
        u = "https://hf-mirror.com/datasets/hails/agieval-gaokao-biology/resolve/main/data/train-00000-of-00001.parquet"
        r = safe_open(u, timeout=30)
        print("  status", r.status, "len", r.headers.get("Content-Length"))
        r.close()
    except Exception as e:
        print("  ERR", str(e)[:80])

    print("== TCM exam tree(前15) ==")
    for p, s in api_tree("SylvanL/Traditional-Chinese-Medicine-Exam")[:15]:
        print("  %10s %s" % (s, p))

    print("== A5 middle-school-english tree ==")
    for p, s in api_tree("dry-melon/Chinese-middle-school-English-exam-questions"):
        print("  %10s %s" % (s, p))

    print("耗时 %.1fs" % (time.time() - t0))
