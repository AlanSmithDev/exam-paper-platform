package com.examforge.ai.service;

import com.examforge.ai.domain.AiLog;
import com.examforge.ai.logic.PhotoSearchRules;
import com.examforge.ai.mapper.AiLogMapper;
import com.examforge.ai.provider.AiProvider;
import com.examforge.common.web.Result;
import org.springframework.data.redis.core.StringRedisTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** AI 服务：提示词模板 + Provider 路由 + 治理日志（docs/15 AI-2~AI-5） */
@Slf4j
@Service
public class AiService {

    private final AiProvider provider;
    private final AiLogMapper aiLogMapper;
    private final AiProvider fallback;

    private final com.examforge.api.feign.TradeClient tradeClient;
    private final com.examforge.api.feign.QuestionClient questionClient;
    private final StringRedisTemplate redis;
    private final String quotaFree, quotaMember;

    public AiService(com.examforge.ai.provider.OpenAiCompatProvider real,
                     com.examforge.ai.provider.MockAiProvider mock,
                     AiLogMapper aiLogMapper,
                     com.examforge.api.feign.TradeClient tradeClient,
                     com.examforge.api.feign.QuestionClient questionClient,
                     StringRedisTemplate redis,
                     @Value("${examforge.ai.provider:MOCK}") String configured,
                     @Value("${examforge.ai.quota-free-per-day:20}") String quotaFree,
                     @Value("${examforge.ai.quota-member-per-day:200}") String quotaMember) {
        // 配置 MOCK 时直接用 MOCK；配置真实渠道时以真实 Provider 为主，失败降级 MOCK（AI-5）
        this.provider = "MOCK".equalsIgnoreCase(configured) ? mock : real;
        this.fallback = mock;
        this.aiLogMapper = aiLogMapper;
        this.tradeClient = tradeClient;
        this.questionClient = questionClient;
        this.redis = redis;
        this.quotaFree = quotaFree;
        this.quotaMember = quotaMember;
    }

    /** AI-4 配额治理：免费 20 次/日、会员 200 次/日。Redis INCR 计数优先（原子+日过期），不可用降级 DB 计数 */
    private void checkQuota(Long userId) {
        int limit;
        try {
            Map<String, Object> ent = tradeClient.entitlement(String.valueOf(userId));
            boolean member = Boolean.TRUE.equals(ent.get("memberActive"));
            limit = Integer.parseInt(member ? quotaMember : quotaFree);
        } catch (Exception e) {
            log.warn("权益判定不可用，按免费配额执行: {}", e.getMessage());
            limit = Integer.parseInt(quotaFree);
        }
        // Redis 原子计数（首选）
        try {
            String key = "ai:quota:" + userId + ":" + java.time.LocalDate.now();
            Long used = redis.opsForValue().increment(key);
            redis.expire(key, java.time.Duration.ofHours(24));
            if (used != null && used > limit) {
                throw new com.examforge.common.web.GlobalExceptionHandler.BizException(
                        Result.TOO_MANY, "今日 AI 配额已用完（" + limit + " 次/日），升级会员可获得更高配额");
            }
            return;
        } catch (com.examforge.common.web.GlobalExceptionHandler.BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Redis 配额计数不可用，降级 DB 计数: {}", e.getMessage());
        }
        // DB 计数兜底
        long used = aiLogMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiLog>()
                .eq(AiLog::getUserId, userId)
                .apply("DATE(created_at) = CURDATE()"));
        if (used >= limit) {
            throw new com.examforge.common.web.GlobalExceptionHandler.BizException(
                    Result.TOO_MANY, "今日 AI 配额已用完（" + limit + " 次/日）");
        }
    }

    /** AI-2 变式出题：生成同考点同题型草稿（aigc=1，进审核工作流） */
    public Map<String, Object> generateVariant(Long userId, String stem, String meta) {
        checkQuota(userId);
        String system = "你是一名高中数学命题专家。基于给定题目生成一道同考点、同题型、同难度的变式题（改变情境与数据），" +
                "严格输出 JSON：{stem, answer, analysis:{brief, solve, comment}, aigc:true}，不要输出 JSON 以外内容。";
        String user = "原题：" + nvl(stem) + "\n题目元信息：" + nvl(meta) + "\n请生成一道变式题。";
        Map<String, Object> out = run(userId, "VARIANT", system, user);
        // 成功且未降级时，草稿自动进入题库审核队列（AI-2 × 审核工作流闭环）
        if (Boolean.FALSE.equals(out.get("degraded"))) {
            try {
                var om = new com.fasterxml.jackson.databind.ObjectMapper();
                var root = om.readTree(String.valueOf(out.get("raw")));
                var dto = new com.examforge.api.dto.QuestionSummaryDTO();
                dto.setStem(root.path("stem").asText(null));
                dto.setAnswer(root.path("answer").asText(null));
                dto.setAnalysis(root.path("analysis").toString());
                dto.setAuthor("AI:" + provider.name());
                if (dto.getStem() != null && dto.getAnswer() != null) {
                    Long draftId = questionClient.createDraft(dto);
                    out = new java.util.HashMap<>(out);
                    out.put("draftQuestionId", draftId);
                    out.put("auditStatus", "审核中（通过后上架并保留 AI 标识）");
                }
            } catch (Exception e) {
                log.warn("变式草稿入库失败（原始返回仍可用）: {}", e.getMessage());
            }
        }
        return out;
    }

    /** AI-3 分步讲题 */
    public Map<String, Object> explain(Long userId, String stem, String answer) {
        checkQuota(userId);
        String system = "你是一名耐心的数学老师。按步骤讲解给定题目（苏格拉底式提问引导），严格输出 JSON：" +
                "{steps:[{step,text,latex}], aigc:true}，不要输出 JSON 以外内容。";
        String user = "题目：" + nvl(stem) + "\n参考答案：" + nvl(answer);
        return run(userId, "EXPLAIN", system, user);
    }

    /**
     * TJ-42 AI 教学设计草稿（docs/26 §8"AI生成教学设计"，docs/22 §2.17）：
     * 输入学段/学科/知识点（可附例题题干）→ 结构化教案草稿（教学目标/重难点/教学过程/板书设计）。
     * RAG 挂课标库为 P2（当前提示词内置课标要点口径）；aigc 草稿仅供教师参考，不直接进任何发布流。
     */
    public Map<String, Object> teachingDesign(Long userId, String gradeLevel, String subject, String kp, String stem) {
        checkQuota(userId);
        String system = "你是一名资深教研员。按课标口径生成一份教学设计草稿，严格输出 JSON：" +
                "{gradeLevel, subject, kp, objectives:[3条], keyPoints:[], difficulties:[], " +
                "process:[{step,title,detail} 四环节：课前预习/课堂例题/随堂检测/课后作业分层], " +
                "board:[板书行], aigc:true}，不要输出 JSON 以外内容。";
        String user = "学段：" + nvl(gradeLevel) + "\n学科：" + nvl(subject) + "\n知识点：" + nvl(kp)
                + (stem == null || stem.isBlank() ? "" : "\n例题题干：" + stem)
                + "\n请生成教学设计。";
        Map<String, Object> out = new java.util.HashMap<>(run(userId, "TEACHING_DESIGN", system, user));
        try {
            var root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(String.valueOf(out.get("raw")));
            out.put("gradeLevel", root.path("gradeLevel").asText(nvl(gradeLevel)));
            out.put("subject", root.path("subject").asText(nvl(subject)));
            out.put("kp", root.path("kp").asText(nvl(kp)));
            out.put("objectives", om().readTree(root.path("objectives").toString()));
            out.put("keyPoints", om().readTree(root.path("keyPoints").toString()));
            out.put("difficulties", om().readTree(root.path("difficulties").toString()));
            out.put("process", om().readTree(root.path("process").toString()));
            out.put("board", om().readTree(root.path("board").toString()));
            out.put("aigc", true);
        } catch (Exception e) {
            log.warn("教学设计解析失败（保留 raw）: {}", e.getMessage());
        }
        return out;
    }

    private com.fasterxml.jackson.databind.ObjectMapper om() {
        return new com.fasterxml.jackson.databind.ObjectMapper();
    }

    /** S-1/S-2 AI 搜：自然语言 → 结构化筛选 → 题库检索（docs/16 §3） */
    public Map<String, Object> aiSearch(Long userId, String query) {
        Map<String, Object> parsed;
        try {
            parsed = "MOCK".equals(provider.name()) || provider.name().startsWith("MOCK")
                    ? ruleParse(query)
                    : llmParse(query);
        } catch (Exception e) {
            parsed = ruleParse(query);   // S-3 解析失败降级规则解析
        }
        String keyword = String.valueOf(parsed.getOrDefault("keyword", ""));
        Long subjectId = parsed.get("subjectId") == null ? null : Long.valueOf(String.valueOf(parsed.get("subjectId")));
        String type = parsed.get("type") == null || "null".equals(parsed.get("type")) ? null : String.valueOf(parsed.get("type"));
        Integer difficulty = parsed.get("difficulty") == null ? null : Integer.valueOf(String.valueOf(parsed.get("difficulty")));
        List<Map<String, Object>> items = toCards(questionClient.searchInternal(keyword, subjectId, type, difficulty, 10));
        Map<String, Object> out = new java.util.HashMap<>();
        out.put("parsed", parsed);
        out.put("count", items.size());
        out.put("list", items);
        return out;
    }

    /** 检索结果 → 前端题目卡片（aiSearch 与 photoSearch 共用，docs/16 S-2 字段契约） */
    private List<Map<String, Object>> toCards(List<com.examforge.api.dto.QuestionSummaryDTO> qs) {
        List<Map<String, Object>> items = new java.util.ArrayList<>();
        for (var q : qs) {
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("id", q.getId());
            m.put("type", q.getType() == null ? "" : q.getType());
            m.put("difficulty", q.getDifficulty() == null ? "" : q.getDifficulty());
            m.put("coefficient", q.getCoefficient() == null ? "" : q.getCoefficient());
            m.put("kpNames", q.getKpNames() == null ? "" : q.getKpNames());
            m.put("stem", q.getStem() == null ? "" : q.getStem());
            items.add(m);
        }
        return items;
    }

    /**
     * C7 拍照搜题（docs/23 §3A）：拍照 → AI 视觉提取题干 → 关键词检索 → 匹配度重排。
     * 与文本 AI 搜不同：每次调用含一次视觉模型调用，计 AI 配额并落 ai_log（scene=PHOTO_SEARCH）。
     * 降级链（docs/23 §3A S-3）：Provider 无视觉/识别失败 → 有 hint 走文本规则解析检索；无 hint 返回引导文案。
     */
    public Map<String, Object> photoSearch(Long userId, String imageBase64, String mime, String hint) {
        checkQuota(userId);
        String invalid = PhotoSearchRules.validateImage(imageBase64, mime);
        if (invalid != null) {
            throw new com.examforge.common.web.GlobalExceptionHandler.BizException(Result.BAD_REQUEST, invalid);
        }
        String b64 = PhotoSearchRules.stripDataUrl(imageBase64);
        String normMime = PhotoSearchRules.resolveMime(mime, imageBase64);

        // 视觉提取题干（提示词含"题干"标记，MOCK Provider 据此返回确定性伪题干保证链路可离线演示）
        String system = "你是题目图片识别器。从照片中提取题目题干文本（保留 \\\\( \\\\)/\\\\[ \\\\] LaTeX 定界符），" +
                "严格输出 JSON：{\"recognized\":true,\"stem\":\"<题干文本>\"}；" +
                "无法辨认时输出 {\"recognized\":false,\"reason\":\"原因\"}，不要输出 JSON 以外内容。";
        long start = System.currentTimeMillis();
        boolean degraded = false;
        String visionJson = null;
        String visionFail = "";
        try {
            visionJson = provider.chatVision(system, "请识别图片中的题目题干。", b64, normMime);
        } catch (UnsupportedOperationException e) {
            visionFail = "当前 AI Provider 不支持视觉识别";
        } catch (Exception e) {
            log.warn("AI 视觉主通道失败，降级 MOCK: {}", e.getMessage());
            try {
                visionJson = fallback.chatVision(system, "请识别图片中的题目题干。", b64, normMime);
                degraded = true;
            } catch (Exception e2) {
                visionFail = "AI 视觉通道不可用：" + e2.getMessage();
            }
        }
        long cost = System.currentTimeMillis() - start;

        boolean recognized = false;
        String stem = null;
        String visionReason = visionFail;
        if (visionJson != null) {
            try {
                var root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(visionJson);
                recognized = root.path("recognized").asBoolean(false);
                stem = root.path("stem").asText(null);
                if (!recognized) visionReason = root.path("reason").asText("未能识别图片中的题目");
            } catch (Exception e) {
                visionReason = "AI 返回解析失败";
            }
        }

        Map<String, Object> out = new java.util.HashMap<>();
        out.put("provider", provider.name());
        out.put("degraded", degraded);
        out.put("costMs", cost);
        List<String> keywords = new java.util.ArrayList<>();
        List<Map<String, Object>> list = new java.util.ArrayList<>();
        if (recognized && stem != null && !stem.isBlank()) {
            // 主链路：题干 → 关键词 → 逐词检索（最多前 3 词，首个非空即止）→ 匹配度重排
            keywords = PhotoSearchRules.extractKeywords(stem);
            for (String kw : keywords.subList(0, Math.min(3, keywords.size()))) {
                list = toCards(questionClient.searchInternal(kw, null, null, null, 10));
                if (!list.isEmpty()) break;
            }
            list = PhotoSearchRules.rerank(list, keywords);
            out.put("recognized", true);
            out.put("stem", stem);
        } else if (nvl(hint).isBlank()) {
            // 降级①：视觉不可用且无文字补充 → 引导文案（流程不断）
            out.put("recognized", false);
            out.put("reason", visionReason == null || visionReason.isBlank() ? "未能识别图片中的题目" : visionReason);
        } else {
            // 降级②：有文字补充 → 文本规则解析检索（docs/23 S-3 兜底口径）
            Map<String, Object> parsed = ruleParse(hint);
            String kw = String.valueOf(parsed.getOrDefault("keyword", ""));
            if (!kw.isBlank()) keywords.add(kw);
            list = toCards(questionClient.searchInternal(kw, null, null, null, 10));
            list = PhotoSearchRules.rerank(list, keywords);
            out.put("recognized", false);
            out.put("reason", (visionReason == null || visionReason.isBlank() ? "未能识别图片中的题目" : visionReason)
                    + "，已按文字补充检索");
            out.put("hintUsed", true);
        }
        out.put("keywords", keywords);
        out.put("count", list.size());
        out.put("list", list);

        // 治理日志（scene=PHOTO_SEARCH；promptChars 记 base64 体积便于视觉调用量核算）
        try {
            AiLog l = new AiLog();
            l.setUserId(userId); l.setScene("PHOTO_SEARCH"); l.setProvider(provider.name());
            l.setModel(provider.name()); l.setPromptChars(imageBase64.length());
            l.setRespChars(visionJson == null ? 0 : visionJson.length());
            l.setCostMs((int) cost); l.setDegraded(degraded ? 1 : 0); l.setCreatedAt(LocalDateTime.now());
            aiLogMapper.insert(l);
        } catch (Exception ignored) { }
        return out;
    }

    /** 规则解析（MOCK 通道）：难度词/题型词/学段学科词抽取，其余作为关键词 */
    Map<String, Object> ruleParse(String query) {
        Map<String, Object> r = new java.util.HashMap<>();
        String q = nvl(query);
        for (String w : new String[]{"容易", "适中", "较难", "困难", "难"}) {
            if (q.contains(w)) {
                r.put("difficulty", w.equals("容易") ? 1 : w.equals("适中") ? 3 : w.equals("困难") || w.equals("难") ? 5 : 4);
                q = q.replace(w, "");
                break;
            }
        }
        for (String t : new String[]{"单选题", "多选题", "填空题", "解答题", "判断题"}) {
            if (q.contains(t) || (q.contains("选择") && t.equals("单选题")) || (q.contains("填空") && t.equals("填空题"))
                    || (q.contains("解答") && t.equals("解答题"))) {
                r.put("type", t);
                q = q.replace(t, "");
                break;
            }
        }
        if (q.contains("立体几何") || q.contains("空间向量")) r.put("keyword", "立体几何");
        else if (q.contains("导数")) r.put("keyword", "导数");
        else if (q.contains("圆锥曲线") || q.contains("双曲线")) r.put("keyword", "圆锥曲线");
        else r.put("keyword", q.trim());
        return r;
    }

    /** LLM 意图解析（真实通道）：输出 JSON {keyword,type,difficulty,subjectId}，失败抛出走降级 */
    Map<String, Object> llmParse(String query) throws com.fasterxml.jackson.core.JsonProcessingException {
        String raw = provider.chat("你是搜索意图解析器。从用户输入抽取搜索条件，严格输出 JSON：" +
                "{keyword, type(单选题|多选题|填空题|解答题|判断题|null), difficulty(1-5|null), subjectId(null 即可)}", query);
        var root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(raw);
        Map<String, Object> r = new java.util.HashMap<>();
        if (root.hasNonNull("keyword")) r.put("keyword", root.get("keyword").asText());
        if (root.hasNonNull("type") && !root.get("type").isNull()) r.put("type", root.get("type").asText());
        if (root.hasNonNull("difficulty") && !root.get("difficulty").isNull()) r.put("difficulty", root.get("difficulty").asInt());
        if (root.hasNonNull("subjectId") && !root.get("subjectId").isNull()) r.put("subjectId", root.get("subjectId").asLong());
        if (!r.containsKey("keyword")) r.put("keyword", query);
        return r;
    }

    /** AI-3 SSE 流式讲题：解析步骤 JSON 后逐条输出（真实 Provider 的 token 级流式为 M2） */
    public java.util.List<Map<String, Object>> explainSteps(String stem, String answer) {
        String raw = explain(null, stem, answer).get("raw").toString();
        java.util.List<Map<String, Object>> steps = new java.util.ArrayList<>();
        try {
            var root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(raw);
            if (root.has("steps") && root.get("steps").isArray()) {
                for (var s : root.get("steps")) {
                    steps.add(Map.of("step", s.path("step").asInt(), "text", s.path("text").asText(),
                            "latex", s.path("latex").asText("")));
                }
            }
        } catch (Exception ignored) { }
        if (steps.isEmpty()) steps.add(Map.of("step", 1, "text", raw, "latex", ""));
        return steps;
    }

    /** 扫描件视觉 OCR（e 卷通二阶段 P3 钩子，docs/26 §7）：教师侧低频操作，不占学生 AI 配额 */
    public Map<String, Object> ocrScan(Long teacherId, String imageBase64, String mime, List<Long> questionIds) {
        String system = "你是答题卡扫描件识别器。图片中题目按给定顺序排列，提取每题的学生手写作答，" +
                "严格输出 JSON：{\"recognized\":true,\"answers\":[{\"questionId\":<题目ID>,\"answer\":\"<作答>\"}]}；" +
                "无法辨认时输出 {\"recognized\":false,\"reason\":\"原因\"}，不要输出 JSON 以外内容。";
        String user = "题目顺序（questionId）：" + questionIds + "。请按此顺序输出每题的学生作答。";
        long start = System.currentTimeMillis();
        boolean degraded = false;
        String resp;
        try {
            resp = provider.chatVision(system, user, imageBase64, mime);
        } catch (UnsupportedOperationException e) {
            // MOCK 等无视觉 Provider：显式未识别，前端降级人工转录（流程不断）
            return Map.of("recognized", false, "degraded", true,
                    "reason", "当前 AI Provider 不支持视觉识别，请人工转录", "answers", List.of(), "raw", "", "costMs", 0L);
        } catch (Exception e) {
            log.warn("AI 视觉主通道失败，降级 MOCK: {}", e.getMessage());
            try {
                resp = fallback.chatVision(system, user, imageBase64, mime);
                degraded = true;
            } catch (Exception e2) {
                return Map.of("recognized", false, "degraded", true,
                        "reason", "AI 视觉通道不可用：" + e2.getMessage(), "answers", List.of(), "raw", "", "costMs", 0L);
            }
        }
        long cost = System.currentTimeMillis() - start;
        try {
            AiLog l = new AiLog();
            l.setUserId(teacherId); l.setScene("OCR_SCAN"); l.setProvider(provider.name());
            l.setModel(provider.name()); l.setPromptChars(imageBase64.length()); l.setRespChars(resp.length());
            l.setCostMs((int) cost); l.setDegraded(degraded ? 1 : 0); l.setCreatedAt(LocalDateTime.now());
            aiLogMapper.insert(l);
        } catch (Exception ignored) { }
        Map<String, Object> out = new HashMap<>();
        out.put("raw", resp); out.put("degraded", degraded); out.put("provider", provider.name()); out.put("costMs", cost);
        try {
            var root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(resp);
            out.put("recognized", root.path("recognized").asBoolean(false));
            out.put("reason", root.path("reason").asText(""));
            List<Map<String, Object>> answers = new java.util.ArrayList<>();
            if (root.has("answers") && root.get("answers").isArray()) {
                for (var a : root.get("answers")) {
                    answers.add(Map.of("questionId", a.path("questionId").asLong(),
                            "answer", a.path("answer").asText()));
                }
            }
            out.put("answers", answers);
        } catch (Exception e) {
            out.put("recognized", false); out.put("reason", "AI 返回解析失败");
            out.put("answers", List.of());
        }
        return out;
    }

    private Map<String, Object> run(Long userId, String scene, String system, String user) {
        long start = System.currentTimeMillis();
        boolean degraded = false;
        String resp;
        try {
            resp = provider.chat(system, user);
        } catch (Exception e) {
            log.warn("AI 主通道失败，降级 MOCK: {}", e.getMessage());
            resp = fallback.chat(system, user);
            degraded = true;
        }
        long cost = System.currentTimeMillis() - start;
        try {
            AiLog l = new AiLog();
            l.setUserId(userId); l.setScene(scene); l.setProvider(provider.name());
            l.setModel(provider.name()); l.setPromptChars(user.length()); l.setRespChars(resp.length());
            l.setCostMs((int) cost); l.setDegraded(degraded ? 1 : 0); l.setCreatedAt(LocalDateTime.now());
            aiLogMapper.insert(l);
        } catch (Exception ignored) { }
        return Map.of("raw", resp, "degraded", degraded, "provider", provider.name(), "costMs", cost);
    }

    private String nvl(String s) { return s == null ? "" : s; }
}
