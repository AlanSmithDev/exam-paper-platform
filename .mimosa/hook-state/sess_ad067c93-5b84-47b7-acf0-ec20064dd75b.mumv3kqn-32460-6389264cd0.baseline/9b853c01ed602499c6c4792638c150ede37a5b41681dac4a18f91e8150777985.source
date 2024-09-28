package com.examforge.trade.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.examforge.common.web.GlobalExceptionHandler.BizException;
import com.examforge.common.web.Result;
import com.examforge.trade.domain.CdkBatch;
import com.examforge.trade.domain.TaskDef;
import com.examforge.trade.mapper.CdkBatchMapper;
import com.examforge.trade.mapper.TaskDefMapper;
import com.examforge.trade.service.TradeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 用户增长域管理端：CDK 批次生成/查询、任务定义配置（SUPER_ADMIN/OP，docs/26 T-26d） */
@RestController
@RequestMapping("/api/v1/trade/admin")
@RequiredArgsConstructor
public class AdminGrowthController {

    private final TradeService tradeService;
    private final CdkBatchMapper cdkBatchMapper;
    private final TaskDefMapper taskDefMapper;

    // ---------- CDK 激活码批次 ----------

    @PostMapping("/cdk/batches")
    public Result<Map<String, Object>> generate(@RequestHeader(value = "X-User-Role", required = false) String role,
                                                @RequestBody Map<String, Object> body) {
        requireRole(role);
        return Result.ok(tradeService.generateCdkBatch(
                str(body.get("rewardType")),
                intOrNull(body.get("rewardDays")),
                intOrNull(body.get("rewardPoints")),
                body.get("rewardTemplateId") == null ? null : Long.valueOf(String.valueOf(body.get("rewardTemplateId"))),
                intOrNull(body.getOrDefault("total", 100)),
                intOrNull(body.get("validDays")),
                role));
    }

    @GetMapping("/cdk/batches")
    public Result<List<CdkBatch>> batches(@RequestHeader(value = "X-User-Role", required = false) String role) {
        requireRole(role);
        return Result.ok(cdkBatchMapper.selectList(
                new LambdaQueryWrapper<CdkBatch>().orderByDesc(CdkBatch::getId)));
    }

    @PutMapping("/cdk/batches/{id}/status")
    public Result<Map<String, Object>> batchStatus(@RequestHeader(value = "X-User-Role", required = false) String role,
                                                   @PathVariable Long id, @RequestBody Map<String, Object> body) {
        requireRole(role);
        CdkBatch b = new CdkBatch();
        b.setId(id);
        b.setStatus("1".equals(String.valueOf(body.get("status"))) ? 1 : 0);
        cdkBatchMapper.updateById(b);
        return Result.ok(Map.of("ok", true));
    }

    // ---------- 积分任务定义 ----------

    @GetMapping("/tasks")
    public Result<List<TaskDef>> tasks(@RequestHeader(value = "X-User-Role", required = false) String role) {
        requireRole(role);
        return Result.ok(taskDefMapper.selectList(new LambdaQueryWrapper<TaskDef>().orderByAsc(TaskDef::getId)));
    }

    @PostMapping("/tasks")
    public Result<Map<String, Object>> createTask(@RequestHeader(value = "X-User-Role", required = false) String role,
                                                  @RequestBody TaskDef d) {
        requireRole(role);
        if (d.getTaskKey() == null || d.getTaskKey().isBlank() || d.getName() == null) {
            return Result.fail(Result.BAD_REQUEST, "taskKey 与名称必填");
        }
        if (d.getRewardPoints() == null || d.getRewardPoints() <= 0) d.setRewardPoints(1);
        if (d.getDaily() == null) d.setDaily(0);
        if (d.getStatus() == null) d.setStatus(1);
        taskDefMapper.insert(d);
        return Result.ok(Map.of("id", d.getId()));
    }

    @PutMapping("/tasks/{id}/status")
    public Result<Map<String, Object>> taskStatus(@RequestHeader(value = "X-User-Role", required = false) String role,
                                                  @PathVariable Long id, @RequestBody Map<String, Object> body) {
        requireRole(role);
        taskDefMapper.update(null, new LambdaUpdateWrapper<TaskDef>()
                .eq(TaskDef::getId, id)
                .set(TaskDef::getStatus, "1".equals(String.valueOf(body.get("status"))) ? 1 : 0));
        return Result.ok(Map.of("ok", true));
    }

    private void requireRole(String role) {
        if (!"SUPER_ADMIN".equals(role) && !"OP".equals(role)) {
            throw new BizException(Result.FORBIDDEN, "无权限");
        }
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private Integer intOrNull(Object o) {
        return o == null ? null : Integer.valueOf(String.valueOf(o));
    }
}
