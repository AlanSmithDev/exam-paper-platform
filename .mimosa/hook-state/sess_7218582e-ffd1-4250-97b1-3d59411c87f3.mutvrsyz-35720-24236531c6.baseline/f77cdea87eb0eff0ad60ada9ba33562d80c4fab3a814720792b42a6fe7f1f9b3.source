package com.examforge.paper.service;

import com.examforge.api.dto.QuestionSummaryDTO;
import com.examforge.api.feign.QuestionClient;
import com.examforge.paper.domain.PaperQuestion;
import com.examforge.paper.logic.ExportRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/** 导出文件生成：打印就绪 HTML（KaTeX 自渲染），PDF 走 Puppeteer 管线（docs/03/06，M2） */
@Service
@RequiredArgsConstructor
public class ExportService {

    private final QuestionClient questionClient;

    /** 试卷内容指纹：题目 id 集合的 SHA-256（30 天重复下载判定，docs/14 D-1） */
    public String paperHash(List<Long> questionIds) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(String.join(",", questionIds.stream().map(String::valueOf).sorted().toList())
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 生成 A4 单栏题后随卷的历史默认版面（存量调用行为不变） */
    public String renderHtml(String title, List<Item> items) {
        return renderHtml(title, items, ExportRules.normalize(null, null, null), ExportRules.AS_NONE);
    }

    /**
     * 生成打印就绪 HTML 试卷（docs/22 C6"排版即所得"）：
     * 版面由 ExportRules.Layout 决定（纸张/单双栏），答案三模式——INLINE 题后随卷、
     * SEPARATED 末尾独立答案页（另起一页，教师对折阅卷）、NONE 学生卷不含答案；
     * answerSpace（docs/25 TJ-111）：LINE 题后答题横线 / BLANK 作答空白框，供学生卷书写。
     */
    public String renderHtml(String title, List<Item> items, ExportRules.Layout layout, String answerSpace) {
        String as = ExportRules.normalizeAnswerSpace(answerSpace);
        StringBuilder body = new StringBuilder();
        StringBuilder answers = new StringBuilder();
        int no = 0;
        for (Item it : items) {
            no++;
            QuestionSummaryDTO q = questionClient.getById(it.questionId());
            body.append("<div class='q'><div class='no'>").append(no).append(".（").append(it.score())
                    .append("分）</div><div class='stem'>").append(esc(q.getStem())).append("</div>");
            if (q.getOptions() != null && !q.getOptions().isBlank()) {
                body.append("<div class='opts'>").append(esc(q.getOptions())).append("</div>");
            }
            if (!ExportRules.AS_NONE.equals(as)) {
                body.append("<div class='aspace'></div>");
            }
            if (layout.separated()) {
                answers.append("<div class='ans'><b>").append(no).append(".（").append(it.score())
                        .append("分）</b>").append(esc(q.getAnswer())).append("</div>");
            } else if (!layout.hidden()) {
                body.append("<div class='ans'><b>【答案】</b>").append(esc(q.getAnswer())).append("</div>");
            }
            body.append("</div>");
        }
        if (layout.separated()) {
            body.append("<section class='ans-page'><h2>参考答案与解析</h2>").append(answers).append("</section>");
        }
        String columnsCss = ExportRules.bodyCss(layout);
        String aspaceCss = ExportRules.answerSpaceCss(as);
        return """
                <!DOCTYPE html><html lang="zh-CN"><head><meta charset="UTF-8">
                <title>%s</title>
                <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.css">
                <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.js"></script>
                <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/contrib/auto-render.min.js"
                  onload="renderMathInElement(document.body,{delimiters:[{left:'\\\\(',right:'\\\\)',display:false}]});"></script>
                <style>
                  %s
                  body { font-family: "SimSun","Songti SC",serif; font-size: 12pt; color: #000; }
                  %s
                  %s
                  h1 { text-align: center; font-size: 16pt; }
                  .q { margin: 10px 0; page-break-inside: avoid; }
                  .no, .stem { display: inline; }
                  .ans { margin-top: 4px; color: #065f46; }
                  .ans-page { page-break-before: always; }
                  .meta { text-align: center; color: #333; }
                </style></head><body>
                <h1>%s</h1><p class="meta">考试时间：120 分钟　满分：%s 分　｜　智卷云 · 下载即所得</p>
                %s
                </body></html>
                """.formatted(esc(title), ExportRules.pageCss(layout), columnsCss, aspaceCss, esc(title),
                items.stream().mapToInt(Item::score).sum() + "", body);
    }

    /** 三参重载（不含作答区）：C6 存量契约保持 */
    public String renderHtml(String title, List<Item> items, ExportRules.Layout layout) {
        return renderHtml(title, items, layout, ExportRules.AS_NONE);
    }

    private String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public record Item(Long questionId, int score, String type, Integer difficulty) { }

    /** paper_question 行 → 导出条目 */
    public List<Item> toItems(List<PaperQuestion> rows) {
        return rows.stream().map(r -> new Item(r.getQuestionId(), r.getScore(), "", null)).toList();
    }

    /**
     * HTML 落地为可下载产物：配置 RENDER_URL（browserless/chromium）时输出 PDF，否则 HTML 兜底。
     * 试卷导出与答题卡共用（e 卷通二阶段，docs/26 §7）。
     * 返回相对下载路径（GET /api/v1/papers/export/download/{file}）。
     */
    public String writeArtifact(String fileName, String html) {
        try {
            java.nio.file.Path dir = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "examforge-exports");
            java.nio.file.Files.createDirectories(dir);
            String renderUrl = System.getenv("RENDER_URL");
            boolean pdf = renderUrl != null && !renderUrl.isBlank();
            java.nio.file.Path target = dir.resolve(pdf ? fileName.replaceAll("\\.html$", ".pdf") : fileName);
            if (pdf) {
                byte[] pdfBytes = java.net.http.HttpClient.newHttpClient().send(
                        java.net.http.HttpRequest.newBuilder(java.net.URI.create(renderUrl + "/pdf"))
                                .header("Content-Type", "application/json")
                                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(
                                        "{\"html\":" + com.fasterxml.jackson.databind.node.TextNode.valueOf(html).toString() + "}"))
                                .build(),
                        java.net.http.HttpResponse.BodyHandlers.ofByteArray()).body();
                java.nio.file.Files.write(target, pdfBytes);
            } else {
                java.nio.file.Files.writeString(target, html);
            }
            return "/api/v1/papers/export/download/" + target.getFileName();
        } catch (Exception e) {
            throw new IllegalStateException("导出文件生成失败: " + e.getMessage(), e);
        }
    }
}
