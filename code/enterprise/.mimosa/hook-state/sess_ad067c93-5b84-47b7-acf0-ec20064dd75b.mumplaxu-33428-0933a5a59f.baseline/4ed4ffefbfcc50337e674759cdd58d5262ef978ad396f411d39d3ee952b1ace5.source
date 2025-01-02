package com.examforge.resource.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.examforge.api.feign.TradeClient;
import com.examforge.api.feign.UserStatsClient;
import com.examforge.common.web.GlobalExceptionHandler.BizException;
import com.examforge.common.web.Result;
import com.examforge.resource.domain.CopyrightAppeal;
import com.examforge.resource.domain.CreatorEarning;
import com.examforge.resource.domain.ResourceBasket;
import com.examforge.resource.domain.ResourceDownload;
import com.examforge.resource.domain.ResourceItem;
import com.examforge.resource.logic.ResourceRules;
import com.examforge.resource.mapper.CopyrightAppealMapper;
import com.examforge.resource.mapper.CreatorEarningMapper;
import com.examforge.resource.mapper.ResourceBasketMapper;
import com.examforge.resource.mapper.ResourceDownloadMapper;
import com.examforge.resource.mapper.ResourceItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 资源中心核心服务（docs/26 F-XKW-01/02/03/14） */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceService {

    private final ResourceItemMapper itemMapper;
    private final ResourceBasketMapper basketMapper;
    private final ResourceDownloadMapper downloadMapper;
    private final CopyrightAppealMapper appealMapper;
    private final CreatorEarningMapper earningMapper;
    private final TradeClient tradeClient;
    private final UserStatsClient userClient;

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

    /** 创作者分成（docs/26 §6）：POINTS 计费成功且非自下载 → 账本留痕 + 点数即时入账 */
    private void settleCreatorShare(Long downloader, ResourceItem r, int charged) {
        Long creator = r.getCreatorUserId();
        if (creator == null || creator.equals(downloader) || charged <= 0) return;   // 无归属/自下载/免费不分成
        int share = ResourceRules.shareCents(charged, sharePct);
        if (share <= 0) return;
        CreatorEarning e = new CreatorEarning();
        e.setCreatorUserId(creator);
        e.setResourceId(r.getId());
        e.setDownloaderId(downloader);
        e.setAmountCents(charged);
        e.setShareCents(share);
        e.setRatePct(sharePct);
        e.setCreatedAt(LocalDateTime.now());
        earningMapper.insert(e);
        try {
            tradeClient.credit(String.valueOf(creator),
                    Map.of("points", share, "reason", "CREATOR_SHARE", "ref", String.valueOf(r.getId())));
        } catch (Exception ex) {
            // 入账失败不阻断下载：账本已留痕，月度结算（P3）以账本为准补发
            log.warn("创作者分成入账失败（账本已留痕待补发）: creator={} share={} err={}", creator, share, ex.getMessage());
        }
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
        YearMonth ym;
        try {
            ym = (month == null || month.isBlank()) ? YearMonth.now() : YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new BizException(Result.BAD_REQUEST, "month 须为 yyyy-MM 格式");
        }
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

    private void validateTarget(String targetType) {
        if (!"RESOURCE".equals(targetType) && !"QUESTION".equals(targetType) && !"PAPER".equals(targetType)) {
            throw new BizException(Result.BAD_REQUEST, "targetType 须为 RESOURCE/QUESTION/PAPER");
        }
    }
}
