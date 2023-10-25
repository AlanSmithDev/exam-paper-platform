package com.examforge.admin.security;

import com.examforge.common.web.GlobalExceptionHandler.BizException;

import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.util.List;

/**
 * SSRF 防护（docs/12 安全验收条件）：
 * 仅允许 http/https；host 为 IP 时直接校验私网/环回/保留段；为域名时解析后逐一校验。
 */
public class UrlSafetyChecker {

    private static final List<String> ALLOWED = List.of("http", "https");

    public static void check(String raw) {
        if (raw == null || raw.isBlank()) return; // 空素材合法（前台渲染占位框）
        URI uri;
        try { uri = new URL(raw.trim()).toURI(); } catch (Exception e) {
            throw new BizException(42200, "URL 格式非法");
        }
        if (!ALLOWED.contains(uri.getScheme())) {
            throw new BizException(42200, "仅允许 http/https 协议");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) throw new BizException(42200, "URL 缺少主机");
        if (isPrivate(host)) throw new BizException(42200, "禁止访问内网/环回/保留地址");
        try {
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                if (isPrivate(addr.getHostAddress())) {
                    throw new BizException(42200, "域名解析到内网地址，已拒绝");
                }
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(42200, "域名无法解析");
        }
    }

    private static boolean isPrivate(String ip) {
        return ip.equals("127.0.0.1") || ip.equals("::1") || ip.equals("0.0.0.0")
                || ip.startsWith("10.") || ip.startsWith("192.168.") || ip.startsWith("169.254.")
                || ip.matches("^172\\.(1[6-9]|2\\d|3[01])\\..*");
    }

    /** 占位：避免未使用告警 */
    @SuppressWarnings("unused")
    private static final List<String> UNUSED = ALLOWED;
}
