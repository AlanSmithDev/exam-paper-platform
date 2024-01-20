# -*- coding: utf-8 -*-
"""
验证 HF 镜像数据源：cn-k12（约 28 万题，problem + solution）
流式下载并按块解析，统计条数、中英文比例、字段完整度。
"""
import urllib.request, ssl, json, re, time, os, sys

CTX = ssl.create_default_context()
CTX.check_hostname = False
CTX.verify_mode = ssl.CERT_NONE
H = {"User-Agent": "Mozilla/5.0"}

URL = "https://hf-mirror.com/datasets/aslawliet/cn-k12/resolve/main/train-00001-of-00001.json"
OUT = r"E:\code\business\zujuan-platform\code\crawler\_cn_k12_sample.json"
CHUNK = 4 * 1024 * 1024
MAX_BYTES = 24 * 1024 * 1024   # 只拉 60MB 做验证，避免过长


def cn_ratio(s):
    if not s:
        return 0.0
    cn = len(re.findall(r"[\u4e00-\u9fff]", s))
    return cn / max(len(s), 1)


def main():
    req = urllib.request.Request(URL, headers=H)
    f = urllib.request.urlopen(req, timeout=60, context=CTX)
    total = int(f.headers.get("Content-Length") or 0)
    print("总大小: %.1f MB" % (total / 1e6))

    buf = ""
    got = 0
    objs = []
    stats = {"total": 0, "with_solution": 0, "cn": 0, "en": 0, "latex": 0}
    t0 = time.time()
    dec = json.JSONDecoder()

    while got < MAX_BYTES:
        chunk = f.read(CHUNK)
        if not chunk:
            break
        got += len(chunk)
        buf += chunk.decode("utf-8", "ignore")

        # 逐个对象解析（buf 形如 pretty-print 的 [ {...}, {...}, ... ]）
        # 注意：pretty-print 下 "{ 与 "problem" 之间有换行，
        # 必须先找 "problem" 再往前回退到最近的 '{'，否则永远匹配不上
        while True:
            i = buf.find('"problem"')
            if i < 0:
                break
            start = buf.rfind("{", 0, i)
            if start < 0:
                buf = buf[i + 10:]
                continue
            try:
                obj, end = dec.raw_decode(buf, start)
            except Exception:
                # 对象被截断，等下一块数据；若已下载完则丢弃这段
                if not chunk:
                    buf = buf[i + 10:]
                    continue
                break
            buf = buf[end:]
            stats["total"] += 1
            sol = obj.get("solution") or ""
            prob = obj.get("problem") or ""
            if sol.strip():
                stats["with_solution"] += 1
            if "$" in prob or "$" in sol:
                stats["latex"] += 1
            if cn_ratio(prob) > 0.15:
                stats["cn"] += 1
            else:
                stats["en"] += 1
            if len(objs) < 3:
                objs.append(obj)
        if got % (20 * 1024 * 1024) < CHUNK:
            print("  已下载 %.0f MB，解析 %d 条，%.0fs" % (got / 1e6, stats["total"], time.time() - t0))

    f.close()
    print("\n===== 抽样统计（前 %.0f MB）=====" % (got / 1e6))
    print(json.dumps(stats, ensure_ascii=False, indent=2))
    if stats["total"]:
        print("含解答比例: %.1f%%" % (100.0 * stats["with_solution"] / stats["total"]))
        print("含 LaTeX 比例: %.1f%%" % (100.0 * stats["latex"] / stats["total"]))
        print("中文题比例: %.1f%%" % (100.0 * stats["cn"] / stats["total"]))
    print("外推全量约: %d 条" % int(stats["total"] * total / max(got, 1)))

    with open(OUT, "w", encoding="utf-8") as fp:
        json.dump(objs, fp, ensure_ascii=False, indent=2)
    print("样例已存:", OUT)
    for o in objs[:2]:
        print("\n--- problem ---")
        print((o.get("problem") or "")[:300])
        print("--- solution ---")
        print((o.get("solution") or "")[:300])


if __name__ == "__main__":
    main()
