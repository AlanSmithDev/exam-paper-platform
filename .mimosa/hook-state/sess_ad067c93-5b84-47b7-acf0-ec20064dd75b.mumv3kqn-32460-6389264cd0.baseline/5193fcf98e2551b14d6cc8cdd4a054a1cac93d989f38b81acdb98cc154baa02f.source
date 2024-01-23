package com.examforge.api.feign;

import com.examforge.api.dto.QuestionSummaryDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/** 题库服务 Feign 契约（paper 服务组卷时按题型+难度取候选题） */
@FeignClient(name = "examforge-question", path = "/internal/questions")
public interface QuestionClient {

    @GetMapping("/by-type")
    List<QuestionSummaryDTO> listByType(@RequestParam("subjectId") Long subjectId,
                                        @RequestParam("type") String type,
                                        @RequestParam("difficulty") Integer difficulty,
                                        @RequestParam("limit") Integer limit);

    @GetMapping("/{id}")
    QuestionSummaryDTO getById(@PathVariable("id") Long id);

    /** 按 ID 批量取题（paper 考查范围聚合/导出渲染，避免 N+1） */
    @PostMapping("/by-ids")
    List<QuestionSummaryDTO> listByIds(@RequestBody List<Long> ids);

    @GetMapping("/by-kp")
    List<QuestionSummaryDTO> listByKp(@RequestParam("subjectId") Long subjectId,
                                      @RequestParam("kp") String kp,
                                      @RequestParam(value = "type", required = false) String type,
                                      @RequestParam("limit") Integer limit);

    /** AI 变式题草稿入库（status=1 审核中），返回草稿题目 id */
    @PostMapping("/create-draft")
    Long createDraft(@RequestBody com.examforge.api.dto.QuestionSummaryDTO draft);

    /** 检索（AI 搜后端通道） */
    @GetMapping("/search")
    List<QuestionSummaryDTO> searchInternal(@RequestParam(value = "keyword", required = false) String keyword,
                                            @RequestParam(value = "subjectId", required = false) Long subjectId,
                                            @RequestParam(value = "type", required = false) String type,
                                            @RequestParam(value = "difficulty", required = false) Integer difficulty,
                                            @RequestParam(value = "limit", defaultValue = "10") Integer limit);
}
