package com.examforge.practice.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 扫描阅卷纯规则：扩展名/大小校验 + 状态机（e 卷通二阶段，docs/26 §7；可脱离 Spring 单测） */
public final class ScanRules {

    /** 允许的扫描件扩展名（小写） */
    public static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "pdf");

    /** 单文件上限 10MB（网关与本地 multipart 上限需 ≥ 此值） */
    public static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

    public static final String ST_UPLOADED = "UPLOADED";
    public static final String ST_RECOGNIZED = "RECOGNIZED";
    public static final String ST_IMPORTED = "IMPORTED";

    private ScanRules() { }

    /** 扩展名校验：取最后一个 '.' 之后并小写比较；无扩展名拒绝 */
    public static String validateExt(String fileName) {
        if (fileName == null || fileName.isBlank() || !fileName.contains(".")) {
            throw new IllegalArgumentException("文件缺少扩展名");
        }
        String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new IllegalArgumentException("仅支持 jpg/jpeg/png/pdf 扫描件");
        }
        return ext;
    }

    /** 大小校验：>0 且 ≤10MB */
    public static void validateSize(long sizeBytes) {
        if (sizeBytes <= 0 || sizeBytes > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("扫描件大小须在 1B~10MB 之间");
        }
    }

    /** 状态机：UPLOADED→RECOGNIZED→IMPORTED，单向不可跳级 */
    public static boolean canTransition(String from, String to) {
        if (ST_UPLOADED.equals(from)) return ST_RECOGNIZED.equals(to);
        if (ST_RECOGNIZED.equals(from)) return ST_IMPORTED.equals(to);
        return false;
    }

    public static void mustTransition(String from, String to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("非法扫描件状态跃迁: " + from + " -> " + to);
        }
    }

    /** 存储相对路径：scans/{assignmentId}/{studentId}/{ts}.{ext} */
    public static String storagePath(long assignmentId, long studentId, long ts, String ext) {
        return "scans/" + assignmentId + "/" + studentId + "/" + ts + "." + ext;
    }

    // ---------- 识别结果导入（docs/26 §7 e 卷通二阶段三期） ----------

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    /** 解析识别结果：{"answers":[{questionId,answer}...]} 或裸数组；缺 answer 项跳过；无有效项/结构非法抛 IllegalArgumentException */
    public static List<Map<String, Object>> parseAnswers(String ocrJson) {
        if (ocrJson == null || ocrJson.isBlank()) throw new IllegalArgumentException("识别结果为空");
        try {
            com.fasterxml.jackson.databind.JsonNode root = JSON.readTree(ocrJson);
            com.fasterxml.jackson.databind.JsonNode arr = root.isArray() ? root : root.path("answers");
            if (!arr.isArray()) throw new IllegalArgumentException("识别结果缺少 answers 数组");
            List<Map<String, Object>> out = new ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode n : arr) {
                if (!n.hasNonNull("questionId") || !n.has("answer")) continue;
                out.add(Map.of("questionId", n.get("questionId").asLong(), "answer", n.get("answer").asText()));
            }
            if (out.isEmpty()) throw new IllegalArgumentException("识别结果无有效作答项");
            return out;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("识别结果 JSON 非法");
        }
    }
}
