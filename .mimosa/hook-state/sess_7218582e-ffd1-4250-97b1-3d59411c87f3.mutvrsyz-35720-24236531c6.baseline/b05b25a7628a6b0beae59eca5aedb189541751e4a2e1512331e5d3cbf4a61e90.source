package com.examforge.ai.logic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 拍照搜题纯规则（docs/22 C7，docs/23 §3A 拍照搜题；可脱离 Spring 单测）。
 * 链路：拍照 → AI 视觉提取题干 → extractKeywords 取检索词 → 题库检索 → matchScore/rerank 匹配度重排。
 */
public final class PhotoSearchRules {

    /** 检索关键词个数上限（LIKE 通道词多无益，取前 N 个区分词） */
    public static final int MAX_KEYWORDS = 6;
    /** 关键词长度下限：单字词区分度差（求/数/图…） */
    public static final int MIN_KEYWORD_LEN = 2;
    /** 关键词长度上限：超长连续片段截断（无分词器的轻量口径，LIKE 仍可命中） */
    public static final int MAX_KEYWORD_LEN = 12;
    /** 图片字节上限（对齐 practice 扫描上传 10MB 口径，取 8MB） */
    public static final long MAX_IMAGE_BYTES = 8L * 1024 * 1024;
    /** 允许的图片类型（拍照场景：jpg/png/webp；扫描件的 pdf 不适用） */
    private static final Set<String> ALLOWED_MIME = Set.of("jpg", "jpeg", "png", "webp");

    /** 题干高频非区分词（虚词/套话），切词时当分隔符剔除。不收"则/且"类会误伤实义词（规则/并且）的高风险字 */
    private static final List<String> STOP_WORDS = List.of(
            "如图", "已知", "求证", "证明", "下列", "选项", "其中", "分别", "所有", "依次", "成立",
            "正确", "错误", "结论", "命题", "条件", "问题", "计算", "说明", "回答", "那么", "如果", "以及",
            "的", "了", "是", "在", "中", "有", "和", "与", "及", "或", "个", "为", "求", "若", "使", "时", "所");

    private PhotoSearchRules() { }

    /** 题干文本 → 检索关键词：停止词当分隔符切分 → 仅保留纯中文连续段 → 长度夹取 → 去重保序 → 截断上限 */
    public static List<String> extractKeywords(String stemText) {
        if (stemText == null || stemText.isBlank()) return List.of();
        String t = stemText;
        for (String w : STOP_WORDS) t = t.replace(w, " ");
        List<String> out = new ArrayList<>();
        StringBuilder run = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (isCjk(c)) {
                run.append(c);
            } else {
                addRun(out, run);
            }
        }
        addRun(out, run);
        return out.stream().limit(MAX_KEYWORDS).toList();
    }

    /** 候选题干匹配度：命中关键词数 / 关键词总数（0~1）；关键词为空记 0（无检索词不做匹配承诺） */
    public static double matchScore(String candidateStem, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) return 0.0;
        String stem = candidateStem == null ? "" : candidateStem;
        long hits = keywords.stream().filter(stem::contains).count();
        return (double) hits / keywords.size();
    }

    /** 候选卡片按匹配度降序重排（并列保持原序，List.sort 稳定）；卡片需含 stem 字段，结果就地补 matchScore */
    public static List<Map<String, Object>> rerank(List<Map<String, Object>> candidates, List<String> keywords) {
        List<Map<String, Object>> cards = new ArrayList<>(candidates == null ? List.of() : candidates);
        for (Map<String, Object> c : cards) {
            c.put("matchScore", matchScore(String.valueOf(c.getOrDefault("stem", "")), keywords));
        }
        cards.sort((a, b) -> Double.compare((Double) b.get("matchScore"), (Double) a.get("matchScore")));
        return cards;
    }

    /**
     * 图片校验：返回 null 表示通过，否则为可直接提示用户的错误文案。
     * base64 允许带 data URL 前缀（前端 FileReader.readAsDataURL 原样上传），大小按解码后字节近似计。
     */
    public static String validateImage(String imageBase64, String mime) {
        if (imageBase64 == null || imageBase64.isBlank()) return "imageBase64 必填";
        if (imageBase64.length() > MAX_IMAGE_BYTES / 3 * 4) return "图片超过 8MB 上限，请压缩后重试";
        String m = resolveMime(mime, imageBase64);
        if (m.isEmpty() || !ALLOWED_MIME.contains(m.substring("image/".length()))) {
            return "仅支持 jpg/png/webp 图片";
        }
        return null;
    }

    /** 剥离 data URL 前缀（data:image/png;base64,xxx → xxx）；纯 base64 原样返回 */
    public static String stripDataUrl(String imageBase64) {
        if (imageBase64 == null) return "";
        String lower = imageBase64.toLowerCase();
        int i = lower.indexOf(";base64,");
        return i >= 0 ? imageBase64.substring(i + ";base64,".length()) : imageBase64;
    }

    /** mime 归一化：客户端显式 mime 优先，其次从 data URL 头解析，均无则空串（image/ 前缀统一小写） */
    public static String resolveMime(String clientMime, String imageBase64) {
        String c = clientMime == null ? "" : clientMime.trim().toLowerCase();
        if (c.startsWith("image/")) return c;
        if (imageBase64 != null) {
            String lower = imageBase64.toLowerCase();
            if (lower.startsWith("data:image/")) {
                int i = lower.indexOf(';');
                if (i > "data:".length()) return lower.substring("data:".length(), i);
            }
        }
        return "";
    }

    private static void addRun(List<String> out, StringBuilder run) {
        if (run.length() < MIN_KEYWORD_LEN) {
            run.setLength(0);
            return;
        }
        String kw = run.length() > MAX_KEYWORD_LEN ? run.substring(0, MAX_KEYWORD_LEN) : run.toString();
        if (!out.contains(kw)) out.add(kw);
        run.setLength(0);
    }

    private static boolean isCjk(char c) {
        return c >= 0x4E00 && c <= 0x9FFF;
    }
}
