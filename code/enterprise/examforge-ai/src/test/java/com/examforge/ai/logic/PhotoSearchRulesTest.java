package com.examforge.ai.logic;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 拍照搜题纯规则单测（docs/22 C7，docs/23 §3A 验收） */
class PhotoSearchRulesTest {

    @Test
    void 关键词提取_停止词与标点剔除() {
        List<String> kws = PhotoSearchRules.extractKeywords(
                "已知函数 f(x)=x²-2ax+1 在区间 (0,+∞) 上单调递增，求实数 a 的取值范围。");
        // 停止词（已知/在/求/的）当分隔符；公式等非中文段丢弃；中文连续段保留
        assertEquals(List.of("函数", "区间", "上单调递增", "实数", "取值范围"), kws);
    }

    @Test
    void 关键词提取_空值去重与截断() {
        assertTrue(PhotoSearchRules.extractKeywords(null).isEmpty());
        assertTrue(PhotoSearchRules.extractKeywords("").isEmpty());
        assertTrue(PhotoSearchRules.extractKeywords("f(x)=2x+1 求解").isEmpty(), "纯公式/单字无区分词应全被剔除");
        assertEquals(List.of("函数", "导数"), PhotoSearchRules.extractKeywords("函数？？导数…函数"));
        // 超过上限 6 个时截断
        List<String> many = PhotoSearchRules.extractKeywords("单调性 极值 零点 抛物线 椭圆 双曲线 数列");
        assertEquals(PhotoSearchRules.MAX_KEYWORDS, many.size());
        assertEquals(List.of("单调性", "极值", "零点", "抛物线", "椭圆", "双曲线"), many);
    }

    @Test
    void 关键词提取_长度夹取() {
        // 15 字无任何停止词的连续中文片段 → 保留为一段并截断到 12 字
        String longRun = "椭圆离心率取值范围讨论题设解析";
        List<String> kws = PhotoSearchRules.extractKeywords(longRun);
        assertEquals(1, kws.size());
        assertEquals(PhotoSearchRules.MAX_KEYWORD_LEN, kws.get(0).length());
    }

    @Test
    void 匹配度_命中比例与空关键词() {
        String stem = "已知函数 f(x) 在区间上单调递增";
        assertEquals(2.0 / 3, PhotoSearchRules.matchScore(stem, List.of("函数", "单调递增", "椭圆")), 1e-9);
        assertEquals(0.0, PhotoSearchRules.matchScore(stem, List.of("椭圆", "双曲线")));
        assertEquals(0.0, PhotoSearchRules.matchScore(stem, List.of()));
        assertEquals(0.0, PhotoSearchRules.matchScore(null, List.of("函数")));
    }

    @Test
    void 候选重排_匹配度降序_并列稳定() {
        List<Map<String, Object>> cards = new java.util.ArrayList<>();
        cards.add(card(1, "二次函数图象平移"));
        cards.add(card(2, "函数单调性与导数"));
        cards.add(card(3, "导数应用之函数零点"));
        List<String> kws = List.of("函数", "导数");
        List<Map<String, Object>> out = PhotoSearchRules.rerank(cards, kws);
        // #2/#3 各命中 2 词并列，稳定性保持原序；#1 命中 1 词垫底
        assertEquals(List.of(2L, 3L, 1L), out.stream().map(c -> c.get("id")).toList());
        assertEquals(1.0, (Double) out.get(0).get("matchScore"));
        assertEquals(0.5, (Double) out.get(2).get("matchScore"));
        assertEquals(List.of(), PhotoSearchRules.rerank(null, kws));
        // 空关键词：全部 0 分，保持原序
        assertEquals(List.of(1L, 2L, 3L),
                PhotoSearchRules.rerank(new java.util.ArrayList<>(cards), List.of())
                        .stream().map(c -> c.get("id")).toList());
    }

    @Test
    void 图片校验_mime白名单与大小上限() {
        assertTrue(PhotoSearchRules.validateImage("aGVsbG8=", "image/png") == null);
        // data URL 自带 mime，客户端未传也能过
        assertTrue(PhotoSearchRules.validateImage("data:image/jpeg;base64,aGVsbG8=", null) == null);
        assertTrue(PhotoSearchRules.validateImage("aGVsbG8=", "image/gif").contains("仅支持"));
        assertTrue(PhotoSearchRules.validateImage("aGVsbG8=", null).contains("仅支持"), "无法判定类型应拒绝");
        assertTrue(PhotoSearchRules.validateImage(null, "image/png").contains("必填"));
        assertTrue(PhotoSearchRules.validateImage("  ", "image/png").contains("必填"));
        assertTrue(PhotoSearchRules.validateImage("A".repeat(12_000_000), "image/png").contains("8MB"));
    }

    @Test
    void 数据链接剥离与mime解析() {
        assertEquals("AAAA", PhotoSearchRules.stripDataUrl("data:image/png;base64,AAAA"));
        assertEquals("BBBB", PhotoSearchRules.stripDataUrl("BBBB"));
        assertEquals("", PhotoSearchRules.stripDataUrl(null));
        assertEquals("image/png", PhotoSearchRules.resolveMime(null, "data:image/png;base64,AAAA"));
        assertEquals("image/webp", PhotoSearchRules.resolveMime("IMAGE/WEBP", "AAAA"));
        assertEquals("", PhotoSearchRules.resolveMime(null, "AAAA"));
        assertEquals("image/jpeg", PhotoSearchRules.resolveMime("image/jpeg", "data:image/png;base64,AAAA"), "客户端显式 mime 优先");
    }

    private Map<String, Object> card(long id, String stem) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("stem", stem);
        return m;
    }
}
