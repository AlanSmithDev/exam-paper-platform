package com.examforge.user.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** TOTP 双因子纯规则单测：RFC 6238 附录 B 官方测试向量（SHA256 口径）+ Base32 编解码 */
class TotpRulesTest {

    /** RFC 6238 附录 B SHA256 标准种子（ASCII "12345678901234567890123456789012"，32 字节） */
    private static final String RFC_SEED = TotpRules.base32Encode(
            "12345678901234567890123456789012".getBytes(java.nio.charset.StandardCharsets.US_ASCII));

    @Test
    void RFC6238官方测试向量_六位码() {
        assertEquals("46119246".substring(2), TotpRules.codeAt(RFC_SEED, 59L), "T=59 → 46119246 截取后 6 位");
        assertEquals("68084774".substring(2), TotpRules.codeAt(RFC_SEED, 1111111109L), "T=1111111109 → 68084774");
        assertEquals("91819424".substring(2), TotpRules.codeAt(RFC_SEED, 1234567890L), "T=1234567890 → 91819424");
        assertEquals("90698825".substring(2), TotpRules.codeAt(RFC_SEED, 2000000000L), "T=2000000000 → 90698825");
    }

    @Test
    void 校验_当前步与漂移容差() {
        long now = 1111111109L;
        String code = TotpRules.codeAt(RFC_SEED, now);
        assertTrue(TotpRules.verify(RFC_SEED, code, now), "当前步动态码通过");
        assertTrue(TotpRules.verify(RFC_SEED, TotpRules.codeAt(RFC_SEED, now + 30), now), "±1 步漂移容差通过");
        assertFalse(TotpRules.verify(RFC_SEED, TotpRules.codeAt(RFC_SEED, now + 90), now), "±2 步外拒绝");
        assertFalse(TotpRules.verify(RFC_SEED, "000000", now), "错误码拒绝");
        assertFalse(TotpRules.verify(RFC_SEED, "12ab", now), "非 6 位拒绝");
        assertFalse(TotpRules.verify(RFC_SEED, null, now));
    }

    @Test
    void Base32编解码_往返与容错() {
        for (byte[] raw : new byte[][]{new byte[20], new byte[]{1, 2, 3}, "any-secret-bytes!".getBytes()}) {
            assertArrayEquals(raw, TotpRules.base32Decode(TotpRules.base32Encode(raw)), "编解码往返一致");
        }
        assertEquals(TotpRules.base32Encode(new byte[]{100, 50}),
                TotpRules.base32Encode(new byte[]{100, 50}).toUpperCase(), "输出为大写");
        assertThrows(IllegalArgumentException.class, () -> TotpRules.base32Decode("abc1!"), "1/! 非法 Base32 字符");
        assertThrows(IllegalArgumentException.class, () -> TotpRules.base32Decode(""));
    }

    @Test
    void 密钥生成_长度与唯一性() {
        String s1 = TotpRules.generateSecret();
        assertEquals(32, s1.length(), "20 字节 → 32 字符 Base32");
        assertEquals(20, TotpRules.base32Decode(s1).length, "解码还原原始 20 字节（32×5/8）");
        assertNotEquals(s1, TotpRules.generateSecret(), "随机生成不重复");
    }

    @Test
    void 绑定URI_标准参数() {
        String uri = TotpRules.otpauthUri("ABC234DEF", "1001", "智卷云后台");
        assertTrue(uri.startsWith("otpauth://totp/智卷云后台:1001?"));
        assertTrue(uri.contains("secret=ABC234DEF"));
        assertTrue(uri.contains("digits=6") && uri.contains("period=30") && uri.contains("algorithm=SHA256"));
    }
}
