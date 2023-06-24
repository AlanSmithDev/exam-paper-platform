package com.examforge.paper.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.examforge.api.dto.QuestionSummaryDTO;
import com.examforge.api.feign.QuestionClient;
import com.examforge.common.web.GlobalExceptionHandler.BizException;
import com.examforge.common.web.Result;
import com.examforge.paper.domain.Paper;
import com.examforge.paper.domain.PaperQuestion;
import com.examforge.paper.logic.AnswerSheetRules;
import com.examforge.paper.mapper.PaperMapper;
import com.examforge.paper.mapper.PaperQuestionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 答题卡生成（e 卷通二阶段，docs/26 §7：试卷/作业一键生成，客观题涂卡网格 + 主观题作答区） */
@Service
@RequiredArgsConstructor
public class AnswerSheetService {

    private final PaperMapper paperMapper;
    private final PaperQuestionMapper paperQuestionMapper;
    private final QuestionClient questionClient;
    private final ExportService exportService;

    /** 试卷答题卡（教师本人卷） */
    public Map<String, Object> forPaper(Long uid, Long paperId) {
        Paper p = paperMapper.selectById(paperId);
        if (p == null || !p.getUserId().equals(uid)) throw new BizException(Result.NOT_FOUND, "试卷不存在");
        List<Long> qids = paperQuestionMapper.selectList(new LambdaQueryWrapper<PaperQuestion>()
                        .eq(PaperQuestion::getPaperId, paperId).orderByAsc(PaperQuestion::getSort))
                .stream().map(PaperQuestion::getQuestionId).toList();
        if (qids.isEmpty()) throw new BizException(Result.BAD_REQUEST, "试卷为空");
        return build(p.getTitle(), paperId, qids);
    }

    /** 通用生成（内部：作业经 Feign 调用；题目顺序 = 入参顺序） */
    public Map<String, Object> build(String title, Long refId, List<Long> questionIds) {
        // selectBatchIds 不保证顺序：按入参顺序重排，保证题号与卷面一致
        Map<Long, QuestionSummaryDTO> byId = new HashMap<>();
        for (QuestionSummaryDTO q : questionClient.listByIds(questionIds)) byId.put(q.getId(), q);
        List<QuestionSummaryDTO> ordered = questionIds.stream().map(byId::get).filter(Objects::nonNull).toList();
        if (ordered.isEmpty()) throw new BizException(Result.BAD_REQUEST, "题目列表为空或题目不存在");

        String html = renderHtml(title, ordered);
        String hash = exportService.paperHash(questionIds).substring(0, 8);
        String fileName = "sheet-" + refId + "-" + hash + ".html";
        String downloadUrl = exportService.writeArtifact(fileName, html);
        return Map.of("downloadUrl", downloadUrl,
                "format", downloadUrl.endsWith(".pdf") ? "PDF" : "HTML",
                "questionCount", ordered.size());
    }

    // ---------- HTML 渲染（A4 打印就绪：密封线 + 考号栏 + 三分区） ----------

    private String renderHtml(String title, List<QuestionSummaryDTO> questions) {
        record Obj(int no, List<String> letters) { }
        record Blank(int no) { }
        record Subjective(int no, int heightMm) { }
        List<Obj> objective = new ArrayList<>();
        List<Blank> fills = new ArrayList<>();
        List<Subjective> subjective = new ArrayList<>();

        int no = 0;
        for (QuestionSummaryDTO q : questions) {
            no++;
            String type = q.getType() == null ? "" : q.getType();
            if (AnswerSheetRules.objectiveType(type)) {
                int optionCount = countOptions(q.getOptions());
                objective.add(new Obj(no, "判断题".equals(type)
                        ? AnswerSheetRules.judgeLetters()
                        : AnswerSheetRules.objectiveLetters(optionCount)));
            } else if (AnswerSheetRules.fillType(type)) {
                fills.add(new Blank(no));
            } else {
                subjective.add(new Subjective(no, AnswerSheetRules.answerAreaHeightMm(null)));
            }
        }

        StringBuilder obj = new StringBuilder();
        if (!objective.isEmpty()) {
            obj.append("<h2>一、客观题（请用 2B 铅笔填涂）</h2><div class='grid'>");
            for (List<Obj> row : AnswerSheetRules.paginate(objective, AnswerSheetRules.OBJECTIVE_PER_ROW)) {
                obj.append("<div class='row'>");
                for (Obj o : row) {
                    obj.append("<div class='cell'><span class='qn'>").append(o.no()).append("</span>");
                    for (String letter : o.letters()) obj.append("<span class='bub'>").append(letter).append("</span>");
                    obj.append("</div>");
                }
                obj.append("</div>");
            }
            obj.append("</div><p class='legend'>判断题：A=对，B=错</p>");
        }

        StringBuilder fill = new StringBuilder();
        if (!fills.isEmpty()) {
            fill.append("<h2>二、填空题（请在题号后的横线上作答）</h2>");
            for (Blank b : fills) {
                fill.append("<div class='blank'><span class='qn'>").append(b.no()).append("</span> ____________________________</div>");
            }
        }

        StringBuilder subj = new StringBuilder();
        if (!subjective.isEmpty()) {
            // 标号 = 一 + 前面已出现的分区数（客观/填空各计 1）
            int priorSections = (objective.isEmpty() ? 0 : 1) + (fill.isEmpty() ? 0 : 1);
            char label = (char) ('一' + priorSections);
            subj.append("<h2>").append(label).append("、解答题（请在框内作答，不得超过边框）</h2>");
            for (Subjective s : subjective) {
                subj.append("<div class='qarea' style='height:").append(s.heightMm()).append("mm'>")
                        .append("<span class='qn'>").append(s.no()).append("</span></div>");
            }
        }

        StringBuilder idBoxes = new StringBuilder();
        for (int i = 0; i < AnswerSheetRules.ID_BOX_COUNT; i++) idBoxes.append("<span class='idbox'></span>");

        return """
                <!DOCTYPE html><html lang="zh-CN"><head><meta charset="UTF-8"><title>%s · 答题卡</title>
                <style>
                  @page { size: A4; margin: 12mm 12mm 12mm 20mm; }
                  body { font-family: "SimSun","Songti SC",serif; font-size: 11pt; color: #000; position: relative; }
                  body::before { content: "········ 密封线内不得作答 ········"; position: absolute; left: -6mm;
                    writing-mode: vertical-rl; top: 60mm; font-size: 8pt; color: #666; letter-spacing: 3px; }
                  h1 { text-align: center; font-size: 15pt; margin: 0 0 4mm; }
                  h2 { font-size: 11pt; border-bottom: 1px solid #000; padding-bottom: 1mm; margin: 6mm 0 3mm; }
                  .idline { text-align: center; margin: 3mm 0; }
                  .idline span { display: inline-block; margin: 0 6mm; }
                  .idbox { display: inline-block; width: 6mm; height: 7mm; border: 1px solid #000; margin: 0 0.5mm; }
                  .grid .row { display: flex; justify-content: space-between; border-bottom: 1px dotted #999;
                    padding: 2mm 0; page-break-inside: avoid; }
                  .cell { display: flex; gap: 2mm; align-items: center; }
                  .qn { font-weight: bold; min-width: 6mm; }
                  .bub { display: inline-block; width: 5mm; height: 5mm; line-height: 5mm; text-align: center;
                    border: 1px solid #000; border-radius: 50%; font-size: 8pt; }
                  .legend { font-size: 8pt; color: #333; }
                  .blank { margin: 3mm 0; }
                  .qarea { border: 1px solid #000; margin: 3mm 0 5mm; position: relative; page-break-inside: avoid; }
                  .qarea .qn { position: absolute; top: 1mm; left: 2mm; }
                </style></head><body>
                <h1>%s</h1>
                <p class="idline">姓名 ____________　班级 ____________　考号 <span class='ids'>%s</span></p>
                %s%s%s
                </body></html>
                """.formatted(esc(title), esc(title), idBoxes, obj, fill, subj);
    }

    /** 选项数解析：options JSON 形如 [{"label":"A",...},...]，解析失败按 0（规则内兜底 A-D） */
    private int countOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) return 0;
        try {
            return com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                    .readTree(optionsJson).size();
        } catch (Exception e) {
            return 0;
        }
    }

    private String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
