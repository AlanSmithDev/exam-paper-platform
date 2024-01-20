# -*- coding: utf-8 -*-
"""
安全 HTTP 抓取：仅允许 https + 域名白名单，逐跳重定向校验，
阻断解析到私网/环回/链路本地/保留地址的目标（防 SSRF 与 DNS rebinding）。
所有新增数据源抓取代码必须经 safe_get / safe_open 使用。
"""
import ssl, socket, ipaddress, urllib.parse, urllib.request, threading

# 允许的域名后缀（精确匹配或以其为父域）
ALLOWED_HOST_SUFFIXES = (
    "hf-mirror.com",       # HuggingFace 国内镜像
    "hf.co",               # HF 的 CAS/CDN 跳转目标（*.xethub.hf.co 等）
    "huggingface.co",
    "github.com", "githubusercontent.com",   # GitHub 及 raw
    "gh-proxy.com", "ghproxy.net",           # GitHub raw 国内镜像
    "jsdelivr.net",
    "edu.cn", "gov.cn",                      # B 类：教育/政府公开真题
    # C 类公开题库站探测（probe_web.py；未通过实测的站点不会接入采集）
    "exam8.com", "51test.net", "ruiwen.com", "5ykj.com", "zww.cn",
    "liuxue86.com", "diyifanwen.com", "eol.cn",
)
LOCK = threading.Lock()
_DNS_CACHE = {}
_SSL_CTX = ssl.create_default_context()
_SSL_CTX.check_hostname = False
_SSL_CTX.verify_mode = ssl.CERT_NONE
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")


class SafeURL(Exception):
    pass


def _check_host(host):
    if not host:
        raise SafeURL("空主机名")
    host = host.lower().rstrip(".")
    ok = any(host == s or host.endswith("." + s) for s in ALLOWED_HOST_SUFFIXES)
    if not ok:
        raise SafeURL("域名不在白名单: %s" % host)
    with LOCK:
        if host in _DNS_CACHE:
            ips = _DNS_CACHE[host]
        else:
            ips = sorted({ai[4][0] for ai in socket.getaddrinfo(host, 443, proto=socket.IPPROTO_TCP)})
            _DNS_CACHE[host] = ips
    for ip in ips:
        addr = ipaddress.ip_address(ip)
        if (addr.is_private or addr.is_loopback or addr.is_link_local
                or addr.is_reserved or addr.is_multicast or addr.is_unspecified):
            raise SafeURL("域名解析到受限地址 %s: %s" % (host, ip))
    return host


def assert_safe_url(url):
    """校验 URL：https 协议 + 白名单域名 + 解析 IP 非受限。通过则原样返回。"""
    p = urllib.parse.urlparse(url)
    if p.scheme != "https":
        raise SafeURL("仅允许 https: %s" % url[:80])
    _check_host(p.hostname)
    return url


class _SafeRedirectHandler(urllib.request.HTTPRedirectHandler):
    """重定向的每一跳都重新过白名单与私网校验"""

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        assert_safe_url(newurl)
        return super().redirect_request(req, fp, code, msg, headers, newurl)


_OPENER = urllib.request.build_opener(
    _SafeRedirectHandler(),
    urllib.request.HTTPSHandler(context=_SSL_CTX))


def safe_get(url, timeout=30, retries=3, headers=None, binary=False):
    """带重试的安全 GET。返回 bytes（binary=True）或解码文本。"""
    assert_safe_url(url)
    hdr = {"User-Agent": UA, "Accept": "*/*"}
    if headers:
        hdr.update(headers)
    last = None
    for i in range(retries):
        try:
            req = urllib.request.Request(url, headers=hdr)
            r = _OPENER.open(req, timeout=timeout)
            raw = r.read()
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
            import time
            time.sleep(0.8 * (i + 1))
    raise last


def safe_open(url, timeout=60, headers=None):
    """流式打开（逐行读取大文件用），返回响应对象。"""
    assert_safe_url(url)
    hdr = {"User-Agent": UA, "Accept": "*/*"}
    if headers:
        hdr.update(headers)
    req = urllib.request.Request(url, headers=hdr)
    return _OPENER.open(req, timeout=timeout)
