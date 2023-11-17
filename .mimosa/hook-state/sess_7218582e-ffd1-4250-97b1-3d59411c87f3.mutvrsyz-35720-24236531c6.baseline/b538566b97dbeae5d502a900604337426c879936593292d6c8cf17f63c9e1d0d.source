package com.examforge.question.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * G2 分表配置结构锁（docs/27 §9.2）：
 * 锁定 sharding.yaml 的 8 分表节点、INLINE 路由表达式与 $${VAR::default} 占位符语法——
 * 防止占位符被误改成与分片语法冲突的单美元形式（单美元 ${0..7} 是 ShardingSphere 行内范围语法）。
 */
class ShardingYamlStructureTest {

    @SuppressWarnings("unchecked")
    private Map<String, Object> load() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("sharding.yaml")) {
            assertNotNull(in, "sharding.yaml 必须位于 examforge-question 类路径");
            // SnakeYAML 2.2 默认拒绝 !SHARDING 自定义标签：本地标签（!开头）按 LinkedHashMap 展开，结构锁只关心键值
            org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml(
                    new org.yaml.snakeyaml.constructor.Constructor(Object.class, new org.yaml.snakeyaml.LoaderOptions()) {
                        @Override
                        protected Class<?> getClassForNode(org.yaml.snakeyaml.nodes.Node node) {
                            if (node.getTag().getValue().startsWith("!")) return java.util.LinkedHashMap.class;
                            return super.getClassForNode(node);
                        }
                    });
            return (Map<String, Object>) yaml.load(in);
        } catch (Exception e) {
            throw new IllegalStateException("sharding.yaml 解析失败", e);
        }
    }

    @Test
    void 分片规则_8个分表节点与INLINE路由() {
        Map<String, Object> yaml = load();
        List<Map<String, Object>> rules = (List<Map<String, Object>>) yaml.get("rules");
        assertEquals(1, rules.size());
        Map<String, Object> sharding = rules.get(0);
        Map<String, Object> tables = (Map<String, Object>) sharding.get("tables");
        Map<String, Object> question = (Map<String, Object>) tables.get("question");

        assertEquals("ds0.question_${0..7}", question.get("actualDataNodes"),
                "分表节点必须是 8 个（先分表后分库，docs/27 定稿）");
        Map<String, Object> strategy = (Map<String, Object>) question.get("tableStrategy");
        Map<String, Object> standard = (Map<String, Object>) strategy.get("standard");
        assertEquals("id", standard.get("shardingColumn"));
        assertEquals("question-inline", standard.get("shardingAlgorithmName"));

        Map<String, Object> algorithms = (Map<String, Object>) sharding.get("shardingAlgorithms");
        Map<String, Object> inline = (Map<String, Object>) algorithms.get("question-inline");
        assertEquals("INLINE", inline.get("type"));
        Map<String, Object> props = (Map<String, Object>) inline.get("props");
        assertEquals("question_${id % 8}", props.get("algorithm-expression"));
    }

    @Test
    void 数据源占位符必须是双美元ShardingSphere原生语法() {
        Map<String, Object> yaml = load();
        Map<String, Object> ds0 = (Map<String, Object>) ((Map<String, Object>) yaml.get("dataSources")).get("ds0");
        String jdbcUrl = String.valueOf(ds0.get("jdbcUrl"));
        assertTrue(jdbcUrl.contains("$${MYSQL_HOST::localhost}"),
                "占位符必须用 $${VAR::default}（5.5.0 URLArgumentLine 实证语法），单美元 ${} 会与分片语法冲突");
        assertEquals("examforge_question", yaml.get("databaseName"));
    }
}
