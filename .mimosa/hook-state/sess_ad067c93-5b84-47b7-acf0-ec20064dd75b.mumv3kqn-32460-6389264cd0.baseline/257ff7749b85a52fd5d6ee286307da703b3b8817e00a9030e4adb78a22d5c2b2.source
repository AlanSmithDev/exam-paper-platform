package com.examforge.resource.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.examforge.api.feign.TradeClient;
import com.examforge.api.feign.UserStatsClient;
import com.examforge.common.web.GlobalExceptionHandler.BizException;
import com.examforge.common.web.Result;
import com.examforge.resource.domain.CopyrightAppeal;
import com.examforge.resource.domain.CreatorContract;
import com.examforge.resource.domain.CreatorEarning;
import com.examforge.resource.domain.CreatorSettlement;
import com.examforge.resource.domain.ResourceBasket;
import com.examforge.resource.domain.ResourceDownload;
import com.examforge.resource.domain.ResourceItem;
import com.examforge.resource.logic.ResourceRules;
import com.examforge.resource.mapper.CopyrightAppealMapper;
import com.examforge.resource.mapper.CreatorContractMapper;
import com.examforge.resource.mapper.CreatorEarningMapper;
import com.examforge.resource.mapper.CreatorSettlementMapper;
import com.examforge.resource.mapper.ResourceBasketMapper;
import com.examforge.resource.mapper.ResourceDownloadMapper;
import com.examforge.resource.mapper.ResourceItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 资源中心核心服务（docs/26 F-XKW-01/02/03/14 + T-26f P3 月度结算） */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceService {

    private final ResourceItemMapper itemMapper;
    private final ResourceBasketMapper basketMapper;
    private final ResourceDownloadMapper downloadMapper;
    private final CopyrightAppealMapper appealMapper;
    private final CreatorEarningMapper earningMapper;
    private final CreatorSettlementMapper settlementMapper;
    private final CreatorContractMapper contractMapper;
    private final TradeClient tradeClient;
    private final UserStatsClient userClient;
    private final JdbcTemplate jdbcTemplate;

    /** 创作者分成比例%（运营可配，docs/26 §6） */
    @Value("${examforge.resource.share-pct:50}")
    private int sharePct;

    // ---------- 浏览 ----------

    public Page<ResourceItem> page(Integer stage, String subject, String category, String level,
                                   String keyword, long page, long size) {
        LambdaQueryWrapper<ResourceItem> w = new LambdaQueryWrapper<ResourceItem>()
                .eq(ResourceItem::getStatus, 1)
                .eq(stage != null, ResourceItem::getStage, stage)
                .eq(subject != null && !subject.isBlank(), ResourceItem::getSubject, subject)
                .eq(category != null && !category.isBlank(), ResourceItem::getCategory, category)
                .eq(level != null && !level.isBlank(), ResourceItem::getLevel, level)
                .like(keyword != null && !keyword.isBlank(), ResourceItem::getTitle, keyword)
                .orderByDesc(ResourceItem::getId);
        return itemMapper.selectPage(new Page<>(page, Math.min(100, Math.max(1, size))), w);
    }

    /** 详情：浏览计数 + 预览信息（免费预览 33%，docs/26 F-XKW-02） */
    public Map<String, Object> detail(Long id) {
        ResourceItem r = itemMapper.selectById(id);
        if (r == null) throw new BizException(Result.NOT_FOUND, "资源不存在");
        itemMapper.incrBrowse(id);
        int totalPages = r.getPages() == null ? 0 : r.getPages();
        int freePages = ResourceRules.previewFreePages(totalPages,
                r.getPreviewFreePct() == null ? 33 : r.getPreviewFreePct());
        return Map.of("item", r,
                "preview", Map.of("totalPages", totalPages, "freePages", freePages,
                        "lockedPages", Math.max(0, totalPages - freePages)));
    }

    // ---------- 资源篮 ----------

    public Map<String, Object> addToBasket(Long uid, Long resourceId) {
        ResourceItem r = itemMapper.selectById(resourceId);
        if (r == null || r.getStatus() != 1) throw new BizException(Result.BAD_REQUEST, "资源不存在或未上架");
        if (basketMapper.insertIgnore(uid, resourceId) == 0) {
            throw new BizException(Result.BAD_REQUEST, "已在资源篮中");
        }
        return Map.of("ok", true);
    }

    public Map<String, Object> removeFromBasket(Long uid, Long resourceId) {
        basketMapper.delete(new LambdaQueryWrapper<ResourceBasket>()
                .eq(ResourceBasket::getUserId, uid).eq(ResourceBasket::getResourceId, resourceId));
        return Map.of("ok", true);
    }

    public List<ResourceItem> basket(Long uid) {
        List<Long> ids = basketMapper.selectList(new LambdaQueryWrapper<ResourceBasket>()
                        .eq(ResourceBasket::getUserId, uid).orderByDesc(ResourceBasket::getId))
                .stream().map(ResourceBasket::getResourceId).toList();
        return ids.isEmpty() ? List.of() : itemMapper.selectBatchIds(ids);
    }

    /**
     * 资源篮批量结算（docs/26 F-XKW-03 差距项：合并扣点）：逐件判价汇总 → 一次 deduct 扣取合计
     * （reason=RESOURCE_BASKET，ref=资源ID逗号串）→ 逐件落账/计数/分成（免费件也落 FREE 记录、
     * 已购与未上架跳过不重复计费）→ 成功后清空资源篮。不足额整单拒绝（部分成交会造成扣点歧义）。
     */
    @Transactional
    public Map<String, Object> checkoutBasket(Long uid) {
        List<ResourceItem> items = basket(uid);
        if (items.isEmpty()) throw new BizException(Result.BAD_REQUEST, "资源篮为空");
        int balance = entitlementBalance(uid);
        int total = 0, free = 0, ownedSkipped = 0, skipped = 0;
        List<Map<String, Object>> out = new ArrayList<>();
        List<Map.Entry<ResourceItem, Integer>> billables = new ArrayList<>();   // item → 本次计费点数（0=免费）
        for (ResourceItem r : items) {
            boolean onShelf = r.getStatus() != null && r.getStatus() == 1;
            boolean owned = downloadMapper.selectCount(new LambdaQueryWrapper<ResourceDownload>()
                    .eq(ResourceDownload::getUserId, uid)
                    .eq(ResourceDownload::getResourceId, r.getId())) > 0;
            ResourceRules.BasketMode m = ResourceRules.basketMode(r.getLevel(), owned, onShelf);
            int price = m == ResourceRules.BasketMode.POINTS ? (r.getPriceCents() == null ? 0 : r.getPriceCents()) : 0;
            switch (m) {
                case SKIP -> skipped++;
                case OWNED -> ownedSkipped++;
                case FREE -> { free++; billables.add(Map.entry(r, 0)); }
                case POINTS -> { total += price; billables.add(Map.entry(r, price)); }
            }
            out.add(Map.of("resourceId", r.getId(), "mode", m.name(), "priceCents", price));
        }
        if (total > balance) {
            throw new BizException(Result.TOO_MANY, "点数不足：本次需 " + total + " 点，余额 " + balance + " 点，请充值后结算");
        }
        if (total > 0) {
            String refs = billables.stream().filter(e -> e.getValue() > 0)
                    .map(e -> String.valueOf(e.getKey().getId())).collect(Collectors.joining(","));
            tradeClient.deduct(String.valueOf(uid),
                    Map.of("points", total, "reason", "RESOURCE_BASKET", "ref", refs));
        }
        int charged = 0;
        for (Map.Entry<ResourceItem, Integer> en : billables) {
            ResourceItem r = en.getKey();
            int price = en.getValue();
            ResourceDownload d = new ResourceDownload();
            d.setUserId(uid);
            d.setResourceId(r.getId());
            d.setMode(price > 0 ? "POINTS" : "FREE");
            d.setPriceCents(price);
            d.setCreatedAt(LocalDateTime.now());
            downloadMapper.insert(d);
            itemMapper.incrDownload(r.getId());
            settleCreatorShare(uid, r, price);
            charged += price;
        }
        basketMapper.delete(new LambdaQueryWrapper<ResourceBasket>().eq(ResourceBasket::getUserId, uid));
        log.info("资源篮批量结算: user={} 件={} 计费={} 免费={} 已购跳过={} 下架跳过={} 扣点={}",
                uid, items.size(), billables.size() - free, free, ownedSkipped, skipped, charged);
        return Map.of("totalCharged", charged, "freeCount", free, "ownedCount", ownedSkipped,
                "skippedCount", skipped, "items", out);
    }

    // ---------- 计费下载（docs/26 F-XKW-02：判价→扣点→落账→计数） ----------

    @Transactional
    public Map<String, Object> download(Long uid, Long resourceId) {
        ResourceItem r = itemMapper.selectById(resourceId);
        if (r == null || r.getStatus() != 1) throw new BizException(Result.NOT_FOUND, "资源不存在或未上架");
        boolean owned = downloadMapper.selectCount(new LambdaQueryWrapper<ResourceDownload>()
                .eq(ResourceDownload::getUserId, uid)
                .eq(ResourceDownload::getResourceId, resourceId)) > 0;
        int balance = entitlementBalance(uid);
        ResourceRules.Mode mode = ResourceRules.decide(r.getLevel(), owned, balance,
                r.getPriceCents() == null ? 0 : r.getPriceCents());
        if (mode == ResourceRules.Mode.INSUFFICIENT) {
            throw new BizException(Result.TOO_MANY, "点数不足，请充值后下载");
        }
        int charged = 0;
        if (mode == ResourceRules.Mode.POINTS) {
            charged = r.getPriceCents();
            tradeClient.deduct(String.valueOf(uid),
                    Map.of("points", charged, "reason", "RESOURCE", "ref", String.valueOf(resourceId)));
        }
        ResourceDownload d = new ResourceDownload();
        d.setUserId(uid);
        d.setResourceId(resourceId);
        d.setMode(mode == ResourceRules.Mode.POINTS ? "POINTS" : "FREE");
        d.setPriceCents(charged);
        d.setCreatedAt(LocalDateTime.now());
        downloadMapper.insert(d);
        itemMapper.incrDownload(resourceId);
        settleCreatorShare(uid, r, charged);
        log.info("资源下载: user={} resource={} mode={}", uid, resourceId, mode);
        return Map.of("mode", mode.name(), "charged", charged, "fileKey", r.getFileKey() == null ? "" : r.getFileKey());
    }

    /** 点数余额（trade 不可用时按 0 处理 → 走扣点失败兜底，不让资源站整体不可用） */
    private int entitlementBalance(Long uid) {
        try {
            Object v = tradeClient.entitlement(String.valueOf(uid)).get("pointBalance");
            return v instanceof Number n ? n.intValue() : 0;
        } catch (Exception e) {
            log.warn("权益查询失败，按 0 余额处理: {}", e.getMessage());
            return 0;
        }
    }

    /** 创作者分成（docs/26 §6）：POINTS 计费成功且非自下载 → 账本留痕 + 点数即时入账；比例取签约优先（快照入账本） */
    private void settleCreatorShare(Long downloader, ResourceItem r, int charged) {
        Long creator = r.getCreatorUserId();
        if (creator == null || creator.equals(downloader) || charged <= 0) return;   // 无归属/自下载/免费不分成
        int rate = shareRateFor(creator);
        int share = ResourceRules.shareCents(charged, rate);
        if (share <= 0) return;
        CreatorEarning e = new CreatorEarning();
        e.setCreatorUserId(creator);
        e.setResourceId(r.getId());
        e.setDownloaderId(downloader);
        e.setAmountCents(charged);
        e.setShareCents(share);
        e.setRatePct(rate);
        e.setCreditStatus(0);   // 先记待结算，入账成功置 1（失败由月度结算补发，docs/26 §6 P3）
        e.setCreatedAt(LocalDateTime.now());
        earningMapper.insert(e);
        try {
            tradeClient.credit(String.valueOf(creator),
                    Map.of("points", share, "reason", "CREATOR_SHARE", "ref", String.valueOf(r.getId())));
            earningMapper.update(null, new LambdaUpdateWrapper<CreatorEarning>()
                    .eq(CreatorEarning::getId, e.getId())
                    .set(CreatorEarning::getCreditStatus, 1));
        } catch (Exception ex) {
            // 入账失败不阻断下载：账本行保持待结算（credit_status=0），月度结算任务按自然月聚合补发
            log.warn("创作者分成入账失败（账本已留痕待补发）: creator={} share={} err={}", creator, share, ex.getMessage());
        }
    }

    /** 生效签约比例：有生效合同取签约比例，无合同/查询失败降级全局默认（docs/26 §6 签约比例优先） */
    private int shareRateFor(Long creator) {
        try {
            CreatorContract c = contractMapper.selectActive(creator, LocalDateTime.now());
            if (c != null) return ResourceRules.resolveSharePct(c.getRatePct(), sharePct);
        } catch (Exception e) {
            log.warn("签约比例查询失败（降级全局默认 {}%）: creator={} err={}", sharePct, creator, e.getMessage());
        }
        return ResourceRules.resolveSharePct(null, sharePct);
    }

    // ---------- 创作者（docs/26 §6：上传走既有 admin 上架审核流 + 收益查询） ----------

    /** 创作者上传：creator_user_id=当前用户，status=0 待审（复用 admin 上架审核） */
    public Map<String, Object> upload(Long uid, ResourceItem r) {
        if (r.getTitle() == null || r.getStage() == null || r.getCategory() == null) {
            throw new BizException(Result.BAD_REQUEST, "标题/学段/类别必填");
        }
        if (r.getLevel() == null) r.setLevel(ResourceRules.LEVEL_NORMAL);
        try {
            ResourceRules.validatePricing(r.getLevel(), r.getPriceCents());
        } catch (IllegalArgumentException e) {
            throw new BizException(Result.BAD_REQUEST, e.getMessage());
        }
        if (r.getPreviewFreePct() == null) r.setPreviewFreePct(33);
        if (r.getPriceCents() == null) r.setPriceCents(200);
        r.setId(null);
        r.setBrowseCount(null);   // 计数、时间与审核态由服务端决定，不信任请求体
        r.setDownloadCount(null);
        r.setCreatedAt(null);
        r.setUpdatedAt(null);
        r.setStatus(0);
        r.setCreatorUserId(uid);
        itemMapper.insert(r);
        log.info("创作者上传: creator={} resource={} title={}", uid, r.getId(), r.getTitle());
        return Map.of("id", r.getId(), "status", r.getStatus());
    }

    /** 创作者收益：分页流水 + 累计分成（合计按全部流水口径，不随分页截断） */
    public Map<String, Object> earnings(Long uid, long page, long size) {
        Page<CreatorEarning> p = earningMapper.selectPage(new Page<>(page, Math.min(100, Math.max(1, size))),
                new LambdaQueryWrapper<CreatorEarning>()
                        .eq(CreatorEarning::getCreatorUserId, uid)
                        .orderByDesc(CreatorEarning::getId));
        return Map.of("items", p.getRecords(), "total", p.getTotal(),
                "totalShareCents", earningMapper.sumShare(uid));
    }

    /** 月收入榜（docs/26 §6 月收入榜 TOP20 公开展示）：按自然月聚合分成，month 缺省当月；昵称经 user 服务补齐，失败降级 #ID */
    public List<Map<String, Object>> monthBoard(String month, Integer limit) {
        YearMonth ym = parseMonthOrBad(month, YearMonth.now());
        int top = Math.min(20, Math.max(1, limit == null ? 20 : limit));
        List<Map<String, Object>> rows = earningMapper.monthBoard(
                ym.atDay(1).atStartOfDay(), ym.plusMonths(1).atDay(1).atStartOfDay(), top);
        Map<Long, String> names = nicknames(rows.stream()
                .map(r -> ((Number) r.get("creatorUserId")).longValue()).toList());
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> r = rows.get(i);
            long cid = ((Number) r.get("creatorUserId")).longValue();
            String nick = names.get(cid);
            out.add(Map.of("rank", i + 1,
                    "creatorUserId", cid,
                    "nickname", nick == null || nick.isBlank() ? "创作者#" + cid : nick,
                    "shareCents", ((Number) r.get("shareCents")).intValue(),
                    "downloads", ((Number) r.get("cnt")).intValue()));
        }
        return out;
    }

    // ---------- 创作者月度结算/补发（T-26f P3，docs/26 §6：结算周期 + 失败补发） ----------

    /**
     * 月度结算：把窗口内即时入账失败（credit_status=0）的账本行按创作者聚合补发。
     * 顺序：先落结算单（status=0）并原子标记账本行（移出待结算池）→ 调 trade 入账 → 回填结算单状态；
     * 入账失败则回滚标记并删结算单，下轮重试。GET_LOCK 保证调度与手动触发并发安全（同 OrderTimeoutScheduler）。
     */
    public Map<String, Object> settleMonth(String month) {
        YearMonth ym = parseMonthOrBad(month, YearMonth.now().minusMonths(1));
        String monthStr = ym.toString();
        LocalDateTime start = ym.atDay(1).atStartOfDay();
        LocalDateTime end = ym.plusMonths(1).atDay(1).atStartOfDay();
        Boolean locked = jdbcTemplate.queryForObject("SELECT GET_LOCK('examforge:creator-settle', 0)", Boolean.class);
        if (!Boolean.TRUE.equals(locked)) return Map.of("month", monthStr, "skipped", "其他实例正在结算");
        try {
            List<Long> creators = settlementMapper.unsettledCreators(start, end);
            int settled = 0, failed = 0;
            long totalCents = 0;
            for (Long uid : creators) {
                long sum = settlementMapper.sumUnsettled(uid, start, end);
                if (sum <= 0) continue;   // 无待补发金额（理论不可达，防御）
                CreatorSettlement s = new CreatorSettlement();
                s.setCreatorUserId(uid);
                s.setMonth(monthStr);
                s.setShareCents((int) sum);
                s.setRowCount(settlementMapper.countUnsettled(uid, start, end));
                s.setStatus(0);
                s.setCreatedAt(LocalDateTime.now());
                settlementMapper.insert(s);
                markSettled(uid, start, end, s.getId());
                try {
                    tradeClient.credit(String.valueOf(uid),
                            Map.of("points", (int) sum, "reason", "CREATOR_SETTLE", "ref", "settle:" + monthStr));
                    s.setStatus(1);
                    s.setCreditedAt(LocalDateTime.now());
                    settlementMapper.updateById(s);
                    settled++;
                    totalCents += sum;
                    log.info("创作者分成补发: creator={} month={} cents={} rows={}", uid, monthStr, sum, s.getRowCount());
                } catch (Exception ex) {
                    jdbcTemplate.update("UPDATE creator_earning SET credit_status = 0, settlement_id = NULL WHERE settlement_id = ?", s.getId());
                    settlementMapper.deleteById(s.getId());
                    failed++;
                    log.warn("创作者分成补发失败（下轮重试）: creator={} month={} cents={} err={}", uid, monthStr, sum, ex.getMessage());
                }
            }
            return Map.of("month", monthStr, "creators", settled, "failed", failed, "totalShareCents", totalCents);
        } finally {
            jdbcTemplate.queryForObject("SELECT RELEASE_LOCK('examforge:creator-settle')", Boolean.class);
        }
    }

    /** 账本行原子移出待结算池并挂结算单（where credit_status=0 保证与并发结算互斥） */
    private void markSettled(Long uid, LocalDateTime start, LocalDateTime end, Long settlementId) {
        earningMapper.update(null, new LambdaUpdateWrapper<CreatorEarning>()
                .eq(CreatorEarning::getCreatorUserId, uid)
                .eq(CreatorEarning::getCreditStatus, 0)
                .ge(CreatorEarning::getCreatedAt, start)
                .lt(CreatorEarning::getCreatedAt, end)
                .set(CreatorEarning::getCreditStatus, 1)
                .set(CreatorEarning::getSettlementId, settlementId));
    }

    /** 创作者结算记录（分页） */
    public Page<CreatorSettlement> settlements(Long uid, long page, long size) {
        return settlementMapper.selectPage(new Page<>(page, Math.min(100, Math.max(1, size))),
                new LambdaQueryWrapper<CreatorSettlement>()
                        .eq(CreatorSettlement::getCreatorUserId, uid)
                        .orderByDesc(CreatorSettlement::getId));
    }

    // ---------- 创作者签约合同（T-26f 收尾，docs/26 §6：主体/比例/结算周期） ----------

    /** 签约（admin）：新建生效合同，比例覆盖全局默认 */
    public Map<String, Object> createContract(CreatorContract c) {
        if (c.getCreatorUserId() == null || c.getSubject() == null || c.getSubject().isBlank()) {
            throw new BizException(Result.BAD_REQUEST, "创作者与签约主体必填");
        }
        try {
            ResourceRules.validateContractRate(c.getRatePct());
        } catch (IllegalArgumentException e) {
            throw new BizException(Result.BAD_REQUEST, e.getMessage());
        }
        if (c.getSettleCycle() == null || c.getSettleCycle().isBlank()) c.setSettleCycle(ResourceRules.SETTLE_MONTHLY);
        c.setId(null);
        c.setStatus(ResourceRules.CONTRACT_ACTIVE);
        if (c.getStartAt() == null) c.setStartAt(LocalDateTime.now());
        c.setCreatedAt(LocalDateTime.now());
        contractMapper.insert(c);
        log.info("创作者签约: creator={} subject={} rate={}%", c.getCreatorUserId(), c.getSubject(), c.getRatePct());
        return Map.of("id", c.getId(), "status", c.getStatus());
    }

    /** 合同列表（admin，分页） */
    public Page<CreatorContract> contracts(long page, long size) {
        return contractMapper.selectPage(new Page<>(page, Math.min(100, Math.max(1, size))),
                new LambdaQueryWrapper<CreatorContract>().orderByDesc(CreatorContract::getId));
    }

    /** 解约（admin）：置 ENDED 并落 end_at（生效判定即失效） */
    public Map<String, Object> endContract(Long id) {
        CreatorContract c = contractMapper.selectById(id);
        if (c == null) throw new BizException(Result.NOT_FOUND, "合同不存在");
        c.setStatus(ResourceRules.CONTRACT_ENDED);
        c.setEndAt(LocalDateTime.now());
        contractMapper.updateById(c);
        log.info("创作者解约: creator={} contract={}", c.getCreatorUserId(), id);
        return Map.of("id", c.getId(), "status", c.getStatus());
    }

    /** 我的生效合同（创作者；无签约返回 null，前端展示默认比例） */
    public CreatorContract myContract(Long uid) {
        return contractMapper.selectActive(uid, LocalDateTime.now());
    }

    private YearMonth parseMonthOrBad(String month, YearMonth fallback) {
        try {
            return ResourceRules.parseMonth(month, fallback);
        } catch (IllegalArgumentException e) {
            throw new BizException(Result.BAD_REQUEST, e.getMessage());
        }
    }

    /** 批量昵称（user 服务不可用时降级为空映射，榜单仍可用） */
    private Map<Long, String> nicknames(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        try {
            String joined = ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
            Map<Long, String> out = new java.util.HashMap<>();
            for (Map<String, Object> u : userClient.batch(joined)) {
                out.put(((Number) u.get("id")).longValue(), String.valueOf(u.getOrDefault("nickname", "")));
            }
            return out;
        } catch (Exception e) {
            log.warn("榜单昵称批量查询失败（降级 #ID 展示）: {}", e.getMessage());
            return Map.of();
        }
    }

    public List<ResourceDownload> myDownloads(Long uid) {
        return downloadMapper.selectList(new LambdaQueryWrapper<ResourceDownload>()
                .eq(ResourceDownload::getUserId, uid).orderByDesc(ResourceDownload::getId));
    }

    // ---------- 版权异议/申诉（docs/26 F-XKW-14） ----------

    public Map<String, Object> submitAppeal(Long uid, String targetType, Long targetId,
                                            String appealType, String content, String contact) {
        validateTarget(targetType);
        if (appealType == null || appealType.isBlank()) appealType = "OTHER";
        if (content == null || content.length() < 5) {
            throw new BizException(Result.BAD_REQUEST, "请描述具体问题（至少 5 字）");
        }
        CopyrightAppeal a = new CopyrightAppeal();
        a.setUserId(uid);
        a.setTargetType(targetType);
        a.setTargetId(targetId == null ? 0 : targetId);
        a.setAppealType(appealType);
        a.setContent(content);
        a.setContact(contact);
        a.setStatus(ResourceRules.APPEAL_OPEN);
        a.setCreatedAt(LocalDateTime.now());
        appealMapper.insert(a);
        return Map.of("id", a.getId(), "status", a.getStatus());
    }

    public Map<String, Object> withdrawAppeal(Long uid, Long appealId) {
        CopyrightAppeal a = appealMapper.selectById(appealId);
        if (a == null || !a.getUserId().equals(uid)) throw new BizException(Result.NOT_FOUND, "申诉不存在");
        ResourceRules.mustTransition(a.getStatus(), ResourceRules.APPEAL_WITHDRAWN);
        a.setStatus(ResourceRules.APPEAL_WITHDRAWN);
        a.setResolvedAt(LocalDateTime.now());
        appealMapper.updateById(a);
        return Map.of("ok", true, "status", a.getStatus());
    }

    public List<CopyrightAppeal> myAppeals(Long uid) {
        return appealMapper.selectList(new LambdaQueryWrapper<CopyrightAppeal>()
                .eq(CopyrightAppeal::getUserId, uid).orderByDesc(CopyrightAppeal::getId));
    }

    @Transactional
    public Map<String, Object> handleAppeal(Long appealId, String handler, String action, String remark) {
        CopyrightAppeal a = appealMapper.selectById(appealId);
        if (a == null) throw new BizException(Result.NOT_FOUND, "申诉不存在");
        String to = switch (action == null ? "" : action) {
            case "RESOLVE" -> ResourceRules.APPEAL_RESOLVED;
            case "REJECT" -> ResourceRules.APPEAL_REJECTED;
            default -> throw new BizException(Result.BAD_REQUEST, "action 须为 RESOLVE/REJECT");
        };
        ResourceRules.mustTransition(a.getStatus(), to);   // 终态重复处理直接拒绝
        a.setStatus(to);
        a.setHandler(handler);
        a.setHandleRemark(remark);
        a.setResolvedAt(LocalDateTime.now());
        appealMapper.updateById(a);
        return Map.of("ok", true, "status", to);
    }

    /** 版权工单 SLA 看板（T-26c 收尾，docs/26 F-XKW-14）：slaHours 缺省 72 */
    public Map<String, Object> appealSla(Integer slaHours) {
        int hours = slaHours == null || slaHours < 1 ? 72 : slaHours;
        List<ResourceRules.SlaRow> rows = appealMapper.selectList(new LambdaQueryWrapper<CopyrightAppeal>()
                        .orderByAsc(CopyrightAppeal::getCreatedAt)).stream()
                .map(a -> new ResourceRules.SlaRow(a.getStatus(), a.getCreatedAt(), a.getResolvedAt()))
                .toList();
        return ResourceRules.appealSlaStats(rows, LocalDateTime.now(), hours);
    }

    private void validateTarget(String targetType) {
        if (!"RESOURCE".equals(targetType) && !"QUESTION".equals(targetType) && !"PAPER".equals(targetType)) {
            throw new BizException(Result.BAD_REQUEST, "targetType 须为 RESOURCE/QUESTION/PAPER");
        }
    }
}
