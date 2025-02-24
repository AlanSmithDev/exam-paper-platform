package com.examforge.paper.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.examforge.api.dto.BillingDTO;
import com.examforge.api.feign.TradeClient;
import com.examforge.common.web.GlobalExceptionHandler.BizException;
import com.examforge.common.web.Result;
import com.examforge.paper.domain.Paper;
import com.examforge.paper.domain.PaperQuestion;
import com.examforge.paper.mapper.PaperMapper;
import com.examforge.paper.mapper.PaperQuestionMapper;
import com.examforge.paper.logic.ExportRules;
import com.examforge.paper.service.ExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 导出管线（docs/14 §6 计费闭环）：
 * 1) 判价 trade.billing（重复下载→会员→免费额度→点数）
 * 2) 点数不足 429 拒绝；3) 生成打印就绪试卷；4) consume 落计费记录；5) 返回下载地址
 */
@RestController
@RequestMapping("/api/v1/papers")
@RequiredArgsConstructor
public class ExportController {

    private final PaperMapper paperMapper;
    private final PaperQuestionMapper paperQuestionMapper;
    private final ExportService exportService;
    private final TradeClient tradeClient;

    @PostMapping("/{id}/export")
    public Result<Map<String, Object>> export(@RequestHeader("X-User-Id") String uid, @PathVariable Long id,
                                              @RequestBody(required = false) ExportReq req) {
        Paper paper = paperMapper.selectById(id);
        if (paper == null || !paper.getUserId().equals(Long.valueOf(uid))) {
            return Result.fail(Result.NOT_FOUND, "试卷不存在");
        }
        List<PaperQuestion> rows = paperQuestionMapper.selectList(new LambdaQueryWrapper<PaperQuestion>()
                .eq(PaperQuestion::getPaperId, id).orderByAsc(PaperQuestion::getSort));
        if (rows.isEmpty()) return Result.fail(Result.BAD_REQUEST, "试卷为空");

        String hash = exportService.paperHash(rows.stream().map(PaperQuestion::getQuestionId).toList());
        // 版面（C6）：不传参数即历史默认 A4 单栏题后随卷；作答区（TJ-111）默认不加；
        // 内容指纹不含版面/作答区（30 天重复下载口径不变，docs/14 D-1）
        ExportRules.Layout layout = ExportRules.normalize(
                req == null ? null : req.paper(), req == null ? null : req.columns(), req == null ? null : req.answerMode());
        String answerSpace = ExportRules.normalizeAnswerSpace(req == null ? null : req.answerSpace());

        // 1) 判价
        BillingDTO billing = tradeClient.billing(uid, rows.size(), hash);
        // 2) 点数不足拒绝（reason 由 PriceCalculator 生成）
        if ("POINTS".equals(billing.getMode()) && billing.getReason().contains("不足")) {
            return Result.fail(Result.TOO_MANY, billing.getReason());
        }

        // 3) 生成文件（RENDER_URL 时直出 PDF，否则 HTML 兜底）；文件名带版面/作答区标签防不同版面同名互覆
        String html = exportService.renderHtml(paper.getTitle(), exportService.toItems(rows), layout, answerSpace);
        String fileName = "paper-" + id + "-" + hash.substring(0, 8) + "-" + ExportRules.fileTag(layout, answerSpace) + ".html";
        String downloadUrl;
        try {
            downloadUrl = exportService.writeArtifact(fileName, html);
        } catch (IllegalStateException e) {
            throw new BizException(Result.SYSTEM, "导出文件生成失败");
        }

        // 4) 计费落账（FREE/MEMBER 记录、POINTS 扣点）
        Map<String, Object> charged = tradeClient.consume(uid,
                Map.of("questionCount", rows.size(), "paperHash", hash, "mode", billing.getMode()));

        // 5) 下载地址
        return Result.ok(Map.of("downloadUrl", downloadUrl,
                "mode", billing.getMode(), "reason", billing.getReason(), "charged", charged));
    }

    /** 导出版面参数（C6/TJ-111，全部可选）：纸张 A4/A3、栏数 1/2、答案模式 INLINE/SEPARATED/NONE、作答区 NONE/LINE/BLANK */
    public record ExportReq(String paper, Integer columns, String answerMode, String answerSpace) { }

    @GetMapping("/export/download/{file}")
    public org.springframework.http.ResponseEntity<byte[]> download(@PathVariable String file) {
        // paper-{paperId}-{hash8}[-{版面+作答区标签}]（试卷导出，标签 a4c1i / a3c2sln 等）与 sheet-{refId}-{hash8}（答题卡）
        if (!file.matches("(paper|sheet)-\\d+-[a-f0-9]{8}(?:-[a-z0-9]{2,8})?\\.(html|pdf)")) {
            return org.springframework.http.ResponseEntity.badRequest().build();
        }
        try {
            Path p = Path.of(System.getProperty("java.io.tmpdir"), "examforge-exports", file);
            byte[] body = Files.readAllBytes(p);
            String type = file.endsWith(".pdf") ? "application/pdf" : "text/html;charset=UTF-8";
            return org.springframework.http.ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + file + "\"")
                    .header("Content-Type", type)
                    .body(body);
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }
    }
}
