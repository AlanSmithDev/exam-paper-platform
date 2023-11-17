package com.examforge.resource.job;

import com.examforge.resource.service.ResourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 创作者分成月度结算（T-26f P3，docs/26 §6）：每月 1 日 03:00 结算上一自然月（含即时入账失败补发） */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreatorSettlementScheduler {

    private final ResourceService resourceService;

    @Scheduled(cron = "0 0 3 1 * ?")
    public void settlePreviousMonth() {
        resourceService.settleMonth(null);   // 缺省=上一自然月
    }
}
