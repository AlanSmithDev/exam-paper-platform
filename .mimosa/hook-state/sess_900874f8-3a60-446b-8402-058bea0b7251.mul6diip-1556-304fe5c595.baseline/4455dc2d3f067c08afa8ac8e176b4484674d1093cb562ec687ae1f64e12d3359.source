# -*- coding: utf-8 -*-
"""
C 类候选站探测（只读，不采集入库）。
判定标准（任务包 PART 0）：返回的原始 HTML 里能否直接搜到题干/正文关键字
（防 JS 骨架页），以及 robots.txt 是否禁止抓取相关路径。

输出：每站一行实测结论，写进任务包 PART 3 数据源清单。
"""
import sys, re, os, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from safe_http import safe_get, safe_open

CJK = re.compile(r"[\u4e00-\u9fff]")

# (名称, 域名, 一个代表性「真题列表/详情」路径)
CANDIDATES = [
    ("考试吧", "www.exam8.com", "/"),
    ("无忧考网", "www.51test.net", "/"),
    ("瑞文网", "www.ruiwen.com", "/"),
    ("莲山课件", "www.5ykj.com", "/"),
    ("中学语文网", "shijuan.zww.cn", "/"),
    ("出国留学网", "www.liuxue86.com", "/"),
    ("第一范文网", "www.diyifanwen.com", "/"),
    ("中国教育在线", "gaokao.eol.cn", "/"),
]


def robots_allows(domain, path="/"):
    try:
        txt = safe_get("https://%s/robots.txt" % domain, timeout=15, retries=1)
    except Exception as e:
        return "robots不可达(%s)" % str(e)[:20]
    for block in re.split(r"(?i)user-agent:", txt):
        if re.match(r"\s*\*", block) is None:
            continue
        for line in block.splitlines():
            line = line.strip()
            m = re.match(r"(?i)disallow:\s*(\S*)", line)
            if m and m.group(1) and not m.group(1).startswith("#"):
                d = m.group(1)
                if d == "/" :
                    return "robots禁止全站"
    return "robots未见全站禁止"


def probe(domain, path):
    url = "https://%s%s" % (domain, path)
    t0 = time.time()
    try:
        r = safe_open(url, timeout=20)
        raw = r.read(300000)
        r.close()
        html = raw.decode("utf-8", "ignore")
    except Exception as e:
        return "FAIL %s" % str(e)[:50], 0
    cjk = len(CJK.findall(html))
    has_link = "试题" in html or "试卷" in html
    ms = time.time() - t0
    note = "HTML %dKB, 汉字 %d, 含试题字样=%s" % (len(html) // 1024, cjk, has_link)
    if cjk < 500:
        note += " → JS骨架页(不可用)"
    elif not has_link:
        note += " → 首页无试题栏目(需进一步定位)"
    else:
        note += " → 服务端渲染，有题目文本潜力，需逐页实测"
    return note, ms


def main():
    for name, domain, path in CANDIDATES:
        rb = robots_allows(domain, path)
        note, ms = probe(domain, path)
        print("%-8s %-22s %s | %s (%.1fs)" % (name, domain, rb, note, ms), flush=True)


if __name__ == "__main__":
    main()
