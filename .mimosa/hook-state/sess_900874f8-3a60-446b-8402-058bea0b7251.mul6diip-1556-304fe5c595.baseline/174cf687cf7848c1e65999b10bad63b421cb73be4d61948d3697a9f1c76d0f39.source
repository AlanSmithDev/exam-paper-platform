# -*- coding: utf-8 -*-
"""批量探测：哪些题库源真正能公开抓到题目内容"""
import urllib.request, urllib.error, re, ssl, time

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

H = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/json,*/*",
    "Accept-Language": "zh-CN,zh;q=0.9",
}

TARGETS = [
    # 名称, URL, 期望关键词
    ("jyeoo_list", "https://www.jyeoo.com/math/ques/search?f=1", ["解析", "考点"]),
    ("jyeoo_paper", "https://www.jyeoo.com/math/paper", ["试卷"]),
    ("21cnjy_zujuan", "https://zujuan.21cnjy.com/", ["试题", "组卷"]),
    ("gaokao_21cnjy", "https://www.21cnjy.com/", ["资料"]),
    ("tiku_koolearn", "https://tiku.koolearn.com/", ["题库"]),
    ("wenku_baidu", "https://wenku.baidu.com/", ["文档"]),
    ("xkw", "https://www.xkw.com/", ["学科网"]),
    ("github_search", "https://api.github.com/search/repositories?q=%E9%A2%98%E5%BA%93+dataset&per_page=5", ["items"]),
    ("github_raw", "https://raw.githubusercontent.com/datasets/registry/master/README.md", ["datasets"]),
    ("hf_api", "https://huggingface.co/api/datasets?search=math&limit=5", ["id"]),
    ("gitee_api", "https://gitee.com/api/v5/search/repositories?q=%E9%A2%98%E5%BA%93", ["full_name"]),
    ("ceval_gh", "https://raw.githubusercontent.com/hkust-nlp/ceval/main/README.md", ["C-Eval"]),
    ("math23k", "https://raw.githubusercontent.com/thsnotfound/Math23K/master/README.md", ["Math23K"]),
    ("gaokao_bench", "https://raw.githubusercontent.com/OpenLMLab/GAOKAO-Bench/main/README.md", ["GAOKAO"]),
    ("pypi", "https://pypi.org/simple/", ["requests"]),
]


def fetch(url, timeout=12):
    req = urllib.request.Request(url, headers=H)
    return urllib.request.urlopen(req, timeout=timeout, context=ctx).read()


def main():
    for name, url, kws in TARGETS:
        t0 = time.time()
        try:
            raw = fetch(url)
            try:
                txt = raw.decode("utf-8")
            except Exception:
                txt = raw.decode("gbk", "ignore")
            hits = [(k, txt.count(k)) for k in kws]
            print("%-16s OK  %6d bytes  %5.2fs  hits=%s" % (name, len(txt), time.time() - t0, hits))
        except Exception as e:
            print("%-16s ERR %s" % (name, str(e)[:70]))


if __name__ == "__main__":
    main()
