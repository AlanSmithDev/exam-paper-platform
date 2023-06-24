package com.examforge.user.logic;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;

/**
 * TOTP 双因子纯规则（RFC 6238 **SHA256 变体**，30s/6 位——强算法口径，Google Authenticator 现代版本兼容；
 * docs/20 §6 等保二级"双因子管理端登录"；可脱离 Spring 单测，含 RFC 官方 SHA256 测试向量）。
 */
public final class TotpRules {

    public static final int STEP_SECONDS = 30;
    public static final int DIGITS = 6;
    /** 时间窗容差：±1 步（时钟漂移兼容） */
    public static final int ALLOWED_DRIFT_STEPS = 1;
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private TotpRules() { }

    /** 生成密钥：20 字节随机 → Base32（26 字符） */
    public static String generateSecret() {
        byte[] buf = new byte[20];
        new SecureRandom().nextBytes(buf);
        return base32Encode(buf);
    }

    /** RFC 4648 Base32 编码（无 padding，大写） */
    public static String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                sb.append(BASE32_ALPHABET.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) sb.append(BASE32_ALPHABET.charAt((buffer << (5 - bits)) & 31));
        return sb.toString();
    }

    /** RFC 4648 Base32 解码（容忍小写/空白）；非法字符抛 IllegalArgumentException */
    public static byte[] base32Decode(String encoded) {
        String clean = encoded == null ? "" : encoded.trim().toUpperCase().replaceAll("[\\s=]", "");
        if (clean.isEmpty()) throw new IllegalArgumentException("空密钥");
        int outLen = clean.length() * 5 / 8;
        byte[] out = new byte[outLen];
        int buffer = 0, bits = 0, idx = 0;
        for (char c : clean.toCharArray()) {
            int v = BASE32_ALPHABET.indexOf(c);
            if (v < 0) throw new IllegalArgumentException("非法 Base32 字符: " + c);
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8 && idx < outLen) {
                out[idx++] = (byte) ((buffer >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return out;
    }

    /** 当前时间步对应动态码（HMAC-SHA1 / 截断 6 位） */
    public static String codeAt(String base32Secret, long epochSecond) {
        long step = epochSecond / STEP_SECONDS;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(base32Decode(base32Secret), "HmacSHA256"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24) | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8) | (hash[offset + 3] & 0xFF);
            return String.format("%0" + DIGITS + "d", binary % 1_000_000);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 校验动态码：±ALLOWED_DRIFT_STEPS 窗口内任一步匹配即通过（时钟漂移兼容）。
     * 重放边界：同一动态码仅在其 30s 步长窗口内有效，窗口滚动后自然失效（服务端防重放加固可扩展记录 last code，docs/20 注明）。
     */
    public static boolean verify(String base32Secret, String code, long epochSecond) {
        if (code == null || !code.trim().matches("\\d{" + DIGITS + "}")) return false;
        String trimmed = code.trim();
        long currentStep = epochSecond / STEP_SECONDS;
        for (int drift = -ALLOWED_DRIFT_STEPS; drift <= ALLOWED_DRIFT_STEPS; drift++) {
            String expected = codeAt(base32Secret, (currentStep + drift) * STEP_SECONDS);
            if (constantTimeEquals(expected, trimmed)) return true;
        }
        return false;
    }

    /** 恒时比较（防时序侧信道） */
    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) diff |= a.charAt(i) ^ b.charAt(i);
        return diff == 0;
    }

    /** otpauth:// 绑定 URI（Authenticator 手动录入/扫码口径） */
    public static String otpauthUri(String secret, String accountName, String issuer) {
        return "otpauth://totp/" + issuer + ":" + accountName
                + "?<REDACTED_SECRET>&issuer=" + issuer + "&algorithm=SHA256&digits=" + DIGITS
                + "&period=" + STEP_SECONDS;
    }
}
