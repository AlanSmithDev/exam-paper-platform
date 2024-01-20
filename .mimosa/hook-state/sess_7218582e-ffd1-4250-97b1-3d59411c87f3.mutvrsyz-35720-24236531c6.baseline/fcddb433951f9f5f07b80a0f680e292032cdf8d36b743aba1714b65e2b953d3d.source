package com.examforge.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.examforge.common.web.GlobalExceptionHandler.BizException;
import com.examforge.common.web.Result;
import com.examforge.trade.domain.*;
import com.examforge.trade.logic.OrderRules;
import com.examforge.trade.logic.PriceCalculator;
import com.examforge.trade.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/** 交易域核心服务：会员/点数/优惠券/订单/计费（docs/14 全部规则编号见注释） */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeService {

    private final MemberPlanMapper planMapper;
    private final MemberMapper memberMapper;
    private final PointAccountMapper pointMapper;
    private final CouponTemplateMapper couponTemplateMapper;
    private final UserCouponMapper userCouponMapper;
    private final TradeOrderMapper orderMapper;
    private final DownloadRecordMapper downloadMapper;
    private final com.examforge.trade.mapper.CdkBatchMapper cdkBatchMapper;
    private final com.examforge.trade.mapper.CdkCodeMapper cdkCodeMapper;
    private final com.examforge.trade.mapper.TaskDefMapper taskDefMapper;
    private final com.examforge.trade.mapper.TaskRecordMapper taskRecordMapper;
    private final com.examforge.api.feign.SchoolClient schoolClient;

    // ---------- M-3 权益判定（全站统一入口） ----------
    public Map<String, Object> entitlement(Long uid) {
        Member m = memberMapper.selectById(uid);
        boolean memberActive = m != null && m.getExpireTime() != null && m.getExpireTime().isAfter(LocalDateTime.now());
        // 学校订阅维度（T-26h，docs/26 §7"教师全员享权益"）：有效订阅学校的教师视同会员；
        // school 服务不可用时降级为无学校权益（不影响个人会员路径），fail-open 不阻断权益判定
        boolean schoolActive = false;
        String schoolName = "";
        try {
            Map<String, Object> sch = schoolClient.membership(uid);
            schoolActive = Boolean.TRUE.equals(sch.get("active")) && "TEACHER".equals(sch.get("role"));
            if (schoolActive) schoolName = String.valueOf(sch.get("schoolName"));
        } catch (Exception e) {
            log.debug("学校权益查询不可用，按无学校权益处理: {}", e.getMessage());
        }
        boolean effectiveMember = memberActive || schoolActive;
        PointAccount acc = pointMapper.selectById(uid);
        int balance = acc == null ? 0 : acc.getBalance();
        long freeUsed = downloadMapper.selectCount(new LambdaQueryWrapper<DownloadRecord>()
                .eq(DownloadRecord::getUserId, uid)
                .eq(DownloadRecord::getCharged, "FREE")
                .apply("DATE(created_at) = CURDATE()"));
        int freeLeft = Math.max(0, 3 - (int) freeUsed);   // D-1：每日 3 次免费
        return Map.of("memberActive", effectiveMember,
                "plan", memberActive && m != null ? m.getPlanId() : null,
                "expireTime", memberActive && m != null ? m.getExpireTime() : null,
                "pointBalance", balance, "freeDownloadsLeft", freeLeft,
                "schoolActive", schoolActive, "schoolName", schoolName);
    }

    // ---------- O-2 幂等下单 ----------
    @Transactional
    public Map<String, Object> createOrder(Long uid, String skuType, String skuRef, int quantity,
                                           Long couponId, String idempotencyKey) {
        TradeOrder exist = orderMapper.selectOne(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getIdempotencyKey, idempotencyKey));
        if (exist != null) return Map.of("orderNo", exist.getOrderNo(), "duplicate", true);   // 幂等返回原单

        int amount = priceOf(skuType, skuRef) * Math.max(1, quantity);
        int discount = 0;
        UserCoupon coupon = null;
        if (couponId != null) {
            coupon = userCouponMapper.selectById(couponId);
            if (coupon == null || !coupon.getUserId().equals(uid) || coupon.getStatus() != 0)
                throw new BizException(Result.BAD_REQUEST, "优惠券不可用");
            CouponTemplate t = couponTemplateMapper.selectById(coupon.getTemplateId());
            discount = amount - OrderRules.applyCoupon(amount, t.getType(), t.getDiscountCents(),
                    t.getMinSpendCents(), t.getDiscountRate(), t.getRateCapCents());
        }

        TradeOrder o = new TradeOrder();
        o.setOrderNo("EF" + System.currentTimeMillis() + String.format("%04d", new java.security.SecureRandom().nextInt(10000)));
        o.setUserId(uid);
        o.setSkuType(skuType);
        o.setSkuRef(skuRef);
        o.setQuantity(Math.max(1, quantity));
        o.setAmountCents(amount);
        o.setDiscountCents(discount);
        o.setPayCents(amount - discount);
        o.setCouponId(couponId);
        o.setStatus(OrderRules.CREATED);
        o.setIdempotencyKey(idempotencyKey);
        try {
            orderMapper.insert(o);
        } catch (DuplicateKeyException e) {   // 并发幂等兜底
            TradeOrder dup = orderMapper.selectOne(new LambdaQueryWrapper<TradeOrder>()
                    .eq(TradeOrder::getIdempotencyKey, idempotencyKey));
            return Map.of("orderNo", dup.getOrderNo(), "duplicate", true);
        }
        return Map.of("orderNo", o.getOrderNo(), "duplicate", false,
                "amountCents", amount, "discountCents", discount, "payCents", o.getPayCents());
    }

    // ---------- O-4/O-5 支付回调（幂等）+ 履约同一事务 ----------
    @Transactional
    public Map<String, Object> payNotify(String orderNo) {
        TradeOrder o = orderMapper.selectOne(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getOrderNo, orderNo));
        if (o == null) throw new BizException(Result.NOT_FOUND, "订单不存在");
        if (OrderRules.PAID.equals(o.getStatus())) {
            return Map.of("orderNo", orderNo, "status", "PAID", "idempotent", true);   // 重复回调幂等
        }
        OrderRules.mustTransition(o.getStatus(), OrderRules.PAID);                       // 状态机校验
        int changes = orderMapper.update(null, new LambdaUpdateWrapper<TradeOrder>()
                .eq(TradeOrder::getId, o.getId()).eq(TradeOrder::getStatus, o.getStatus())
                .set(TradeOrder::getStatus, OrderRules.PAID).set(TradeOrder::getPaidAt, LocalDateTime.now()));
        if (changes == 0) return Map.of("orderNo", orderNo, "status", o.getStatus(), "idempotent", true);

        if (o.getCouponId() != null) useCoupon(o.getCouponId(), orderNo);                // C-4 核销
        fulfill(o);                                                                       // O-5 履约
        log.info("订单已支付并履约: {} sku={} ref={}", orderNo, o.getSkuType(), o.getSkuRef());
        return Map.of("orderNo", orderNo, "status", "PAID", "idempotent", false);
    }

    /** 履约：开会员（M-2 叠加续费）/ 点数充值（P-2 流水） */
    private void fulfill(TradeOrder o) {
        LocalDateTime now = LocalDateTime.now();
        if ("MEMBER".equals(o.getSkuType())) {
            MemberPlan plan = planMapper.selectById(o.getSkuRef());
            int days = plan == null ? 30 : plan.getDurationDays();
            Member m = memberMapper.selectById(o.getUserId());
            LocalDateTime base = (m != null && m.getExpireTime() != null && m.getExpireTime().isAfter(now))
                    ? m.getExpireTime() : now;
            if (m == null) {
                m = new Member();
                m.setUserId(o.getUserId());
                m.setPlanId(o.getSkuRef());
                m.setExpireTime(base.plusDays(days));
                memberMapper.insert(m);
            } else {
                m.setPlanId(o.getSkuRef());
                m.setExpireTime(base.plusDays(days));
                memberMapper.updateById(m);
            }
        } else if ("POINTS".equals(o.getSkuType())) {
            int points = o.getAmountCents();   // 点数充值 1 分=1 点
            ensurePointAccount(o.getUserId());
            pointMapper.add(o.getUserId(), points);
            addPointLog(o.getUserId(), points, "RECHARGE", o.getOrderNo());
        }
    }

    // ---------- C-3 领取（限量 + 每人限领 + 天天领券每日限领，docs/26 F-XKW-07） ----------
    @Transactional
    public Long claimCoupon(Long uid, Long templateId) {
        CouponTemplate t = couponTemplateMapper.selectById(templateId);
        if (t == null || t.getStatus() != 1) throw new BizException(Result.BAD_REQUEST, "券不存在或已下架");
        int granted = t.getGranted() == null ? 0 : t.getGranted();
        int changes = couponTemplateMapper.update(null, new LambdaUpdateWrapper<CouponTemplate>()
                .eq(CouponTemplate::getId, templateId)
                .lt(CouponTemplate::getGranted, t.getTotal())
                .setSql("granted = granted + 1"));
        if (changes == 0) throw new BizException(Result.BAD_REQUEST, "优惠券已领完");
        long mine = userCouponMapper.selectCount(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, uid).eq(UserCoupon::getTemplateId, templateId));
        if (mine >= (t.getPerLimit() == null ? 1 : t.getPerLimit())) {
            throw new BizException(Result.BAD_REQUEST, "已达每人限领次数");
        }
        if (t.getDailyPerLimit() != null && t.getDailyPerLimit() > 0) {
            long today = userCouponMapper.selectCount(new LambdaQueryWrapper<UserCoupon>()
                    .eq(UserCoupon::getUserId, uid).eq(UserCoupon::getTemplateId, templateId)
                    .apply("DATE(created_at) = CURDATE()"));
            if (today >= t.getDailyPerLimit()) throw new BizException(Result.BAD_REQUEST, "今日领取次数已用完，明天再来");
        }
        UserCoupon c = new UserCoupon();
        c.setTemplateId(templateId);
        c.setUserId(uid);
        c.setStatus(0);
        c.setExpireTime(t.getValidDays() == null ? null : LocalDateTime.now().plusDays(t.getValidDays()));
        userCouponMapper.insert(c);
        return c.getId();
    }

    // ---------- C-4 核销（条件更新防并发重用） ----------
    private void useCoupon(Long couponId, String orderNo) {
        int changes = userCouponMapper.update(null, new LambdaUpdateWrapper<UserCoupon>()
                .eq(UserCoupon::getId, couponId).eq(UserCoupon::getStatus, 0)
                .set(UserCoupon::getStatus, 1).set(UserCoupon::getUsedOrder, orderNo));
        if (changes == 0) throw new BizException(Result.BAD_REQUEST, "优惠券已被使用");
    }

    // ---------- P-2 点数 ----------
    public void ensurePointAccount(Long uid) {
        if (pointMapper.selectById(uid) == null) {
            PointAccount a = new PointAccount();
            a.setUserId(uid);
            a.setBalance(0);
            a.setVersion(0);
            try { pointMapper.insert(a); } catch (DuplicateKeyException ignored) { }
        }
    }

    public void addPointLog(Long uid, int change, String reason, String ref) {
        var l = new com.examforge.trade.domain.PointLog();
        l.setUserId(uid); l.setChangeVal(change); l.setReason(reason); l.setRef(ref);
        l.setCreatedAt(LocalDateTime.now());
        pointLogMapper.insert(l);
    }

    // ---------- K-3 签到积分（每日一次 +1 点，唯一索引防重复） ----------
    @Transactional
    public Map<String, Object> checkin(Long uid) {
        ensurePointAccount(uid);
        int inserted = signRecordMapper.insertIgnore(uid, java.time.LocalDate.now());
        if (inserted == 0) throw new BizException(Result.BAD_REQUEST, "今日已签到");
        pointMapper.add(uid, 1);
        addPointLog(uid, 1, "CHECKIN", "daily");
        return Map.of("ok", true, "rewardPoints", 1);
    }

    /** 纠错采纳等奖励发放（question 服务经 Feign 调用，docs/10） */
    @Transactional
    public Map<String, Object> reward(Long uid, int points, String reason, String ref) {
        ensurePointAccount(uid);
        pointMapper.add(uid, points);
        addPointLog(uid, points, "REWARD", reason + ":" + ref);
        return Map.of("ok", true, "points", points);
    }

    /** 点数扣减（资源下载等场景经 Feign 调用，docs/26 F-XKW-02）：条件更新防透支 */
    @Transactional
    public Map<String, Object> deduct(Long uid, int points, String reason, String ref) {
        if (points <= 0) throw new BizException(Result.BAD_REQUEST, "扣减点数非法");
        ensurePointAccount(uid);
        if (pointMapper.deduct(uid, points) == 0) throw new BizException(Result.TOO_MANY, "点数不足");
        addPointLog(uid, -points, reason, ref);
        return Map.of("ok", true, "deducted", points);
    }

    /** 点数入账（创作者分成等场景经 Feign 调用，docs/26 §6）：理由码与 reward/deduct 区分，便于对账 */
    @Transactional
    public Map<String, Object> credit(Long uid, int points, String reason, String ref) {
        if (points <= 0) throw new BizException(Result.BAD_REQUEST, "入账点数非法");
        ensurePointAccount(uid);
        pointMapper.add(uid, points);
        addPointLog(uid, points, reason, ref);
        return Map.of("ok", true, "credited", points);
    }

    // ================= 用户增长域：CDK 激活码 / 积分任务（docs/26 T-26d） =================

    /** 生成一批激活码（管理端，低频）：一码一 insert，单批 ≤5000 可接受 */
    @Transactional
    public Map<String, Object> generateCdkBatch(String rewardType, Integer days, Integer points, Long templateId,
                                                Integer total, Integer validDays, String operator) {
        com.examforge.trade.logic.GrowthRules.validateBatch(rewardType, days, points, templateId, total);
        java.time.LocalDateTime now = LocalDateTime.now();
        CdkBatch b = new CdkBatch();
        b.setBatchNo("CDK" + java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(now)
                + String.format("%04d", new java.security.SecureRandom().nextInt(10000)));
        b.setRewardType(rewardType);
        b.setRewardDays(days);
        b.setRewardPoints(points);
        b.setRewardTemplateId(templateId);
        b.setTotal(total);
        b.setRedeemed(0);
        b.setExpireTime(validDays != null && validDays > 0 ? now.plusDays(validDays) : null);
        b.setStatus(1);
        b.setOperator(operator == null ? "admin" : operator);
        b.setCreatedAt(now);
        cdkBatchMapper.insert(b);
        List<String> codes = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            String code = com.examforge.trade.logic.GrowthRules.generateCode();
            codes.add(code);
            CdkCode c = new CdkCode();
            c.setCode(code);
            c.setBatchId(b.getId());
            c.setStatus(0);
            c.setCreatedAt(now);
            cdkCodeMapper.insert(c);
        }
        log.info("CDK 批次生成: {} 类型={} 张数={}", b.getBatchNo(), rewardType, total);
        return Map.of("batchNo", b.getBatchNo(), "total", total, "codes", codes);
    }

    /** 兑换激活码：码占坑(0→1) → 批次校验(不通过抛异常整体回滚) → 批次计数 → 发放奖励 */
    @Transactional
    public Map<String, Object> redeemCdk(Long uid, String rawCode) {
        String code = com.examforge.trade.logic.GrowthRules.normalize(rawCode);
        if (!com.examforge.trade.logic.GrowthRules.validCode(code)) {
            throw new BizException(Result.BAD_REQUEST, "激活码格式不正确");
        }
        if (cdkCodeMapper.claim(code, uid) == 0) {
            throw new BizException(Result.BAD_REQUEST, "激活码无效或已被使用");
        }
        CdkCode c = cdkCodeMapper.selectOne(new LambdaQueryWrapper<CdkCode>().eq(CdkCode::getCode, code));
        CdkBatch b = cdkBatchMapper.selectById(c.getBatchId());
        if (!com.examforge.trade.logic.GrowthRules.redeemable(b.getStatus(), b.getExpireTime(), LocalDateTime.now())) {
            throw new BizException(Result.BAD_REQUEST, "激活码批次已停用或已过期");   // 事务回滚，码不消耗
        }
        if (cdkBatchMapper.incrRedeemed(b.getId()) == 0) {
            throw new BizException(Result.BAD_REQUEST, "该批次已全部兑完");           // 事务回滚，码不消耗
        }
        Object reward = switch (b.getRewardType()) {
            case "MEMBER_DAYS" -> {
                grantMemberDays(uid, b.getRewardDays());
                yield Map.of("type", "MEMBER_DAYS", "days", b.getRewardDays());
            }
            case "POINTS" -> {
                ensurePointAccount(uid);
                pointMapper.add(uid, b.getRewardPoints());
                addPointLog(uid, b.getRewardPoints(), "CDK", b.getBatchNo());
                yield Map.of("type", "POINTS", "points", b.getRewardPoints());
            }
            case "COUPON" -> {
                CouponTemplate t = couponTemplateMapper.selectById(b.getRewardTemplateId());
                if (t == null || t.getStatus() != 1) throw new BizException(Result.BAD_REQUEST, "激活码对应券模板已下架");
                UserCoupon uc = new UserCoupon();
                uc.setTemplateId(t.getId());
                uc.setUserId(uid);
                uc.setStatus(0);
                uc.setExpireTime(t.getValidDays() == null ? null : LocalDateTime.now().plusDays(t.getValidDays()));
                userCouponMapper.insert(uc);   // CDK 通道独立发放，不占 perLimit 配额
                yield Map.of("type", "COUPON", "couponId", uc.getId(), "template", t.getName());
            }
            default -> throw new BizException(Result.BAD_REQUEST, "奖励类型非法");
        };
        log.info("CDK 兑换成功: user={} batch={} reward={}", uid, b.getBatchNo(), reward);
        return Map.of("ok", true, "reward", reward);
    }

    /** 会员天数叠加发放（与开会员同规则：未过期在 expire_time 上叠加） */
    private void grantMemberDays(Long uid, int days) {
        LocalDateTime now = LocalDateTime.now();
        Member m = memberMapper.selectById(uid);
        LocalDateTime base = (m != null && m.getExpireTime() != null && m.getExpireTime().isAfter(now))
                ? m.getExpireTime() : now;
        if (m == null) {
            m = new Member();
            m.setUserId(uid);
            m.setExpireTime(base.plusDays(days));
            memberMapper.insert(m);
        } else {
            m.setExpireTime(base.plusDays(days));
            memberMapper.updateById(m);
        }
    }

    // ---------- 积分任务中心（docs/26 F-XKW-07）：完成即发奖，唯一索引幂等 ----------

    /** 任务列表（前台）：含今日/终身完成状态 */
    public List<Map<String, Object>> listTasks(Long uid) {
        List<TaskDef> defs = taskDefMapper.selectList(new LambdaQueryWrapper<TaskDef>()
                .eq(TaskDef::getStatus, 1).orderByAsc(TaskDef::getId));
        Set<String> done = taskRecordMapper.selectList(new LambdaQueryWrapper<TaskRecord>()
                        .eq(TaskRecord::getUserId, uid)).stream()
                .map(r -> r.getTaskKey() + "@" + r.getPeriod())
                .collect(java.util.stream.Collectors.toSet());
        java.time.LocalDate today = java.time.LocalDate.now();
        return defs.stream().map(d -> {
            String period = com.examforge.trade.logic.GrowthRules.periodOf(d.getDaily() != null && d.getDaily() == 1, today);
            boolean finished = done.contains(d.getTaskKey() + "@" + period);
            return Map.<String, Object>of(
                    "taskKey", d.getTaskKey(), "name", d.getName(),
                    "rewardPoints", d.getRewardPoints(),
                    "daily", d.getDaily() != null && d.getDaily() == 1,
                    "done", finished);
        }).toList();
    }

    /** 完成任务并发放点数；重复完成返回 duplicate 而非报错（自动任务上报友好） */
    @Transactional
    public Map<String, Object> completeTask(Long uid, String taskKey) {
        TaskDef def = taskDefMapper.selectOne(new LambdaQueryWrapper<TaskDef>()
                .eq(TaskDef::getTaskKey, taskKey));
        if (def == null || def.getStatus() == null || def.getStatus() != 1) {
            throw new BizException(Result.NOT_FOUND, "任务不存在或已下线");
        }
        String period = com.examforge.trade.logic.GrowthRules.periodOf(def.getDaily() != null && def.getDaily() == 1,
                java.time.LocalDate.now());
        ensurePointAccount(uid);
        if (taskRecordMapper.insertIgnore(uid, taskKey, period) == 0) {
            return Map.of("ok", false, "duplicate", true, "rewardPoints", 0);
        }
        int pts = def.getRewardPoints() == null ? 0 : def.getRewardPoints();
        if (pts > 0) {
            pointMapper.add(uid, pts);
            addPointLog(uid, pts, "TASK", taskKey);
        }
        return Map.of("ok", true, "duplicate", false, "rewardPoints", pts);
    }

    private final com.examforge.trade.mapper.SignRecordMapper signRecordMapper;

    // ---------- D-1/D-2 下载计费 ----------
    public Map<String, Object> billing(Long uid, int questionCount, String paperHash) {
        var ent = entitlement(uid);
        boolean repeat = !downloadMapper.selectList(new LambdaQueryWrapper<DownloadRecord>()
                .eq(DownloadRecord::getUserId, uid).eq(DownloadRecord::getPaperHash, paperHash)
                .apply("created_at > DATE_SUB(NOW(), INTERVAL 30 DAY)")).isEmpty();
        PriceCalculator.Decision d = PriceCalculator.decide(repeat,
                Boolean.TRUE.equals(ent.get("memberActive")),
                (Integer) ent.get("freeDownloadsLeft"), (Integer) ent.get("pointBalance"), questionCount);
        return Map.of("mode", d.mode(), "needPoints", d.needPoints(), "reason", d.reason());
    }

    @Transactional
    public Map<String, Object> consumeDownload(Long uid, int questionCount, String paperHash, String mode) {
        if ("POINTS".equals(mode)) {
            ensurePointAccount(uid);
            int price = PriceCalculator.paperPriceCents(questionCount);
            if (pointMapper.deduct(uid, price) == 0) throw new BizException(Result.TOO_MANY, "点数不足");
            addPointLog(uid, -price, "DOWNLOAD", paperHash);
        }
        DownloadRecord r = new DownloadRecord();
        r.setUserId(uid);
        r.setPaperHash(paperHash);
        r.setQuestionCount(questionCount);
        r.setCharged(mode);
        r.setCreatedAt(LocalDateTime.now());
        downloadMapper.insert(r);
        return Map.of("charged", mode);
    }

    private int priceOf(String skuType, String skuRef) {
        if ("MEMBER".equals(skuType)) {
            MemberPlan p = planMapper.selectById(skuRef);
            if (p == null || p.getStatus() != 1) throw new BizException(Result.BAD_REQUEST, "会员档位不存在");
            return p.getPriceCents();
        }
        if ("POINTS".equals(skuType)) {
            try { return Integer.parseInt(skuRef); } catch (Exception e) { throw new BizException(Result.BAD_REQUEST, "点数包非法"); }
        }
        throw new BizException(Result.BAD_REQUEST, "SKU 类型非法");
    }

    private final com.examforge.trade.mapper.PointLogMapper pointLogMapper;
}
