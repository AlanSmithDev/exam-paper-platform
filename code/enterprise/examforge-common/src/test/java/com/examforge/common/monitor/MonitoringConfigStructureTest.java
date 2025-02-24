package com.examforge.common.monitor;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** G3 监控配置结构锁（docs/19 §5）：新增服务必须接入 Prometheus 抓取与告警规则（ShardingYamlStructureTest 同款惯例） */
class MonitoringConfigStructureTest {

    private final Path dockerDir = Path.of("..", "docker");

    /** 全部可运行服务都必须有对应抓取 job（新增服务遗漏时本测试失败，提醒同步 prometheus.yml） */
    @Test
    void 抓取配置覆盖全部服务() throws Exception {
        String yml = Files.readString(dockerDir.resolve("prometheus.yml"));
        List<String> services = List.of("gateway", "user", "question", "paper", "admin",
                "trade", "practice", "ai", "resource", "school");
        for (String s : services) {
            assertTrue(yml.contains("job_name: examforge-" + s), "prometheus.yml 缺少 examforge-" + s + " 抓取 job");
        }
        assertTrue(yml.contains("/actuator/prometheus"), "抓取必须走 actuator prometheus 端点");
        assertTrue(yml.contains("rules.yml"), "必须引用告警规则文件");
    }

    @Test
    void 告警规则_等级与关键阈值() throws Exception {
        String rules = Files.readString(dockerDir.resolve("prometheus-rules.yml"));
        assertTrue(rules.contains("severity: P0"), "必须有 P0 级告警（服务下线/支付成功率）");
        assertTrue(rules.contains("severity: P1"), "必须有 P1 级告警（5xx 错误率）");
        assertTrue(rules.contains("severity: P2"), "必须有 P2 级告警（P99/JVM/连接池）");
        assertTrue(rules.contains("ExamforgeServiceDown"), "服务下线告警缺失");
        assertTrue(rules.contains("PaymentSuccessRateLow"), "支付成功率告警缺失（docs/19 §5 P0 指标）");
        assertTrue(rules.contains("examforge_payment_callback_total"), "支付回调计数器未接入告警");
        assertTrue(rules.contains("0.999"), "支付成功率 99.9% 阈值缺失");
    }

    @Test
    void grafana自动装配齐备() {
        assertTrue(Files.exists(dockerDir.resolve("grafana/provisioning/datasources/prometheus.yml")), "Grafana 数据源 provisioning 缺失");
        assertTrue(Files.exists(dockerDir.resolve("grafana/provisioning/dashboards/provider.yml")), "Grafana 看板 provider 缺失");
        assertTrue(Files.exists(dockerDir.resolve("grafana/dashboards/examforge-overview.json")), "总览看板缺失");
    }
}
