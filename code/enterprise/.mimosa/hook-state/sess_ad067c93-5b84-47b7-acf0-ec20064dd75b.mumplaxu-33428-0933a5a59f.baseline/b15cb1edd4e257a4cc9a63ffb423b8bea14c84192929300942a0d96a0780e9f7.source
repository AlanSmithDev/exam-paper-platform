package com.examforge.question.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.examforge.question.domain.Question;
import com.examforge.question.mapper.QuestionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/** 题库查询/筛选（全部走 MyBatis-Plus 条件构造器，参数绑定无拼接）；详情带 Redis 缓存 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionService {

    private final QuestionMapper mapper;
    private final EsSearchService esSearchService;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${examforge.cache.question-detail-ttl-seconds:600}")
    private int detailTtlSeconds;

    private String detailKey(Long id) { return "q:detail:" + id; }

    public Page<Question> search(Long subjectId, String scene, String type, Integer difficulty,
                                 String category, String kp, String keyword, long pageNo, long pageSize) {
        // ES 检索通道（docs/03）：有关键词且 ES 启用时优先走 ES，故障自动降级 LIKE
        if (keyword != null && !keyword.isBlank() && esSearchService.enabled()) {
            try {
                List<Long> ids = esSearchService.search(keyword, subjectId, type, difficulty, (int) Math.min(pageSize * pageNo, 200));
                if (!ids.isEmpty()) {
                    LambdaQueryWrapper<Question> w = new LambdaQueryWrapper<Question>()
                            .eq(Question::getStatus, 2).in(Question::getId, ids)
                            .eq(scene != null && !scene.isBlank(), Question::getScene, scene)
                            .eq(category != null && !category.isBlank(), Question::getCategory, category)
                            .like(kp != null && !kp.isBlank(), Question::getKpNames, kp);
                    List<Question> rows = mapper.selectList(w);
                    rows.sort(java.util.Comparator.comparingInt(q -> ids.indexOf(q.getId())));   // 按相关度排序
                    Page<Question> page = new Page<>(pageNo, pageSize, rows.size());
                    page.setRecords(rows.stream().skip((pageNo - 1) * pageSize).limit(pageSize).toList());
                    return page;
                }
                return new Page<>(pageNo, pageSize);
            } catch (Exception e) {
                // 降级到 LIKE
            }
        }
        return searchByDb(subjectId, scene, type, difficulty, category, kp, keyword, pageNo, pageSize);
    }

    public Page<Question> searchByDb(Long subjectId, String scene, String type, Integer difficulty,
                                     String category, String kp, String keyword, long pageNo, long pageSize) {
        LambdaQueryWrapper<Question> w = new LambdaQueryWrapper<Question>()
                .eq(Question::getStatus, 2)
                .eq(subjectId != null, Question::getSubjectId, subjectId)
                .eq(scene != null && !scene.isBlank(), Question::getScene, scene)
                .eq(type != null && !type.isBlank(), Question::getType, type)
                .eq(difficulty != null, Question::getDifficulty, difficulty)
                .eq(category != null && !category.isBlank(), Question::getCategory, category)
                .like(kp != null && !kp.isBlank(), Question::getKpNames, kp)
                .and(keyword != null && !keyword.isBlank(),
                        x -> x.like(Question::getStem, keyword).or().like(Question::getAnswer, keyword))
                .orderByDesc(Question::getUseCount)
                .orderByAsc(Question::getId);
        return mapper.selectPage(new Page<>(pageNo, Math.min(pageSize, 50)), w);
    }

    public Question detail(Long id) {
        // Redis 缓存（docs/03 §4.1）：命中直接返回；未命中查库回填；Redis 不可用 fail-open 直查库
        try {
            String cached = redis.opsForValue().get(detailKey(id));
            if (cached != null) return objectMapper.readValue(cached, Question.class);
        } catch (Exception e) {
            log.debug("缓存读取失败（fail-open）: {}", e.getMessage());
        }
        Question q = mapper.selectOne(new LambdaQueryWrapper<Question>()
                .eq(Question::getId, id).eq(Question::getStatus, 2));
        if (q != null) {
            try {
                redis.opsForValue().set(detailKey(id), objectMapper.writeValueAsString(q),
                        Duration.ofSeconds(detailTtlSeconds));
            } catch (Exception e) {
                log.debug("缓存写入失败（fail-open）: {}", e.getMessage());
            }
        }
        return q;
    }

    /** 审核状态变更/纠错生效时剔除缓存 */
    public void evictDetail(Long id) {
        try { redis.delete(detailKey(id)); } catch (Exception ignored) { }
    }

    /** 保存（含 AI 草稿入库） */
    public void save(Question q) {
        mapper.insert(q);
    }

    public long countAll() { return mapper.selectCount(null); }

    public long countOnShelf() {
        return mapper.selectCount(new LambdaQueryWrapper<Question>().eq(Question::getStatus, 2));
    }

    public List<Question> similar(Long id, String kpNames, String type) {
        return mapper.selectList(new LambdaQueryWrapper<Question>()
                .eq(Question::getStatus, 2).ne(Question::getId, id)
                .and(x -> x.eq(Question::getKpNames, kpNames).or().eq(Question::getType, type))
                .last("LIMIT 3"));
    }

    /** 服务间内部接口：按题型+难度取候选题（Feign 供 paper 服务组卷） */
    public List<Question> listByType(Long subjectId, String type, Integer difficulty, int limit) {
        return mapper.selectList(new LambdaQueryWrapper<Question>()
                .eq(Question::getStatus, 2)
                .eq(subjectId != null, Question::getSubjectId, subjectId)
                .eq(Question::getType, type)
                .eq(difficulty != null, Question::getDifficulty, difficulty)
                .orderByDesc(Question::getUseCount)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 200)));
    }

    /** 服务间内部接口：按 ID 批量取题（paper 整卷插题的考查范围聚合/导出渲染，避免 N+1） */
    public List<Question> listByIds(List<Long> ids) {
        return ids == null || ids.isEmpty() ? List.of() : mapper.selectBatchIds(ids);
    }
}
