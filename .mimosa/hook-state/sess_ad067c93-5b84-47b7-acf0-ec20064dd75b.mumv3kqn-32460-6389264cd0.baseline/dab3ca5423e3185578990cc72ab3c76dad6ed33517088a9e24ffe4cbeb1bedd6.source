package com.examforge.ai.service;

import com.examforge.ai.domain.AiLog;
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
        List<Map<String, Object>> items = new java.util.ArrayList<>();
        for (var q : questionClient.searchInternal(keyword, subjectId, type, difficulty, 10)) {
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("id", q.getId());
            m.put("type", q.getType() == null ? "" : q.getType());
            m.put("difficulty", q.getDifficulty() == null ? "" : q.getDifficulty());
            m.put("coefficient", q.getCoefficient() == null ? "" : q.getCoefficient());
            m.put("kpNames", q.getKpNames() == null ? "" : q.getKpNames());
            m.put("stem", q.getStem() == null ? "" : q.getStem());
            items.add(m);
        }
        Map<String, Object> out = new java.util.HashMap<>();
        out.put("parsed", parsed);
        out.put("count", items.size());
        out.put("list", items);
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
