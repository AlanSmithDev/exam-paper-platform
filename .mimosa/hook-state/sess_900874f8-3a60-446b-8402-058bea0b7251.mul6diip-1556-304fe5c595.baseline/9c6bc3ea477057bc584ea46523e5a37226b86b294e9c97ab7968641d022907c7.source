# -*- coding: utf-8 -*-
"""探测菁优网搜索页：是否直接内含题目/答案/解析"""
import urllib.request, re, os

H = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Referer": "https://www.jyeoo.com/",
    "Accept": "text/html,application/xhtml+xml,*/*",
    "Accept-Language": "zh-CN,zh;q=0.9",
}

URL = "https://www.jyeoo.com/math/ques/search?f=1&q=&page=1"
OUT = "E:/code/business/zujuan-platform/code/crawler/_probe_jyeoo.html"


def main():
    req = urllib.request.Request(URL, headers=H)
    html = urllib.request.urlopen(req, timeout=20).read().decode("utf-8", "ignore")
    with open(OUT, "w", encoding="utf-8") as f:
        f.write(html)
    print("len", len(html))

    # 关键 class / 特征
    for kw in ["ques", "pt1", "answer", "analysis", "解析", "答案", "detail", "jyeoo.net"]:
        print(kw, "->", html.count(kw))

    # 找题目链接
    links = set(re.findall(r'/math/ques/detail/[0-9a-zA-Z]+', html))
    print("detail links:", len(links), list(links)[:5])

    # 找分页总数
    m = re.findall(r'共\s*<[^>]*>?\s*([\d,]+)\s*', html)
    print("total hints:", m[:5])
    m2 = re.findall(r'(\d{4,9})\s*道', html)
    print("daoshu:", m2[:5])

    # 打印一段疑似题目区
    i = html.find('list-box')
    print("---- list-box snippet ----")
    print(re.sub(r"\s+", " ", html[i:i + 2500]))


if __name__ == "__main__":
    main()
