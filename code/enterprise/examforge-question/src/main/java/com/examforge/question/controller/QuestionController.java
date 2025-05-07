package com.examforge.question.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.examforge.common.web.Result;
import com.examforge.question.domain.Question;
import com.examforge.question.service.QuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/questions")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService service;

    /** 学段/学科/章节树/题型/场景/分类枚举 */
    @GetMapping("/meta")
    public Result<Map<String, Object>> meta() {
        return Result.ok(Map.of(
                "types", List.of("单选题", "多选题", "填空题", "解答题", "判断题"),
                "scenes", List.of("预习", "课堂", "作业", "单元测试", "阶段检测", "高考"),
                "categories", List.of("典型题", "压轴题", "同步题", "易错题", "常考题", "好题", "新定义")
        ));
    }

    @GetMapping
    public Result<Map<String, Object>> list(@RequestParam(required = false) Long subjectId,
                                            @RequestParam(required = false) String scene,
                                            @RequestParam(required = false) String type,
                                            @RequestParam(required = false) Integer difficulty,
                                            @RequestParam(required = false) String category,
                                            @RequestParam(required = false) String kp,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String literacy,
                                            @RequestParam(defaultValue = "1") long pageNo,
                                            @RequestParam(defaultValue = "10") long pageSize) {
        Page<Question> p = service.search(subjectId, scene, type, difficulty, category, kp, keyword, literacy, pageNo, pageSize);
        return Result.ok(Map.of("total", p.getTotal(), "pageNo", pageNo, "pageSize", pageSize, "list", p.getRecords()));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        Question q = service.detail(id);
        if (q == null) return Result.fail(Result.NOT_FOUND, "题目不存在");
        return Result.ok(Map.of(
                "question", q,
                "similar", service.similar(id, q.getKpNames(), q.getType())
        ));
    }

    /** 服务间内部接口（网关不暴露，仅内网/Feign） */
    @GetMapping("/internal/by-type")
    public Result<List<Question>> internalByType(@RequestParam Long subjectId, @RequestParam String type,
                                                 @RequestParam(required = false) Integer difficulty,
                                                 @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(service.listByType(subjectId, type, difficulty, limit));
    }
}
