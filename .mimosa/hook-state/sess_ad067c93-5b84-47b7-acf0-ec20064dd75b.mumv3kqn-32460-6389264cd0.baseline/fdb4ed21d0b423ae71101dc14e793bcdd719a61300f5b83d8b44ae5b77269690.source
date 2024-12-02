package com.examforge.question.service;

import com.examforge.common.web.GlobalExceptionHandler.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Elasticsearch 检索（docs/03 §2 search-svc 精简版）：
 * - HttpClient 直连 ES REST API，零额外依赖；
 * - examforge.search.engine=db 时降级为数据库 LIKE（ES 故障也自动降级，保证可用性）；
 * - 索引 mapping 见 sql/es-question-mapping.json。
 */
@Slf4j
@Service
public class EsSearchService {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final String esUrl;
    private final boolean enabled;

    public EsSearchService(@Value("${examforge.search.engine:db}") String engine,
                           @Value("${examforge.search.es-url:http://localhost:9200}") String esUrl) {
        this.enabled = "es".equalsIgnoreCase(engine);
        this.esUrl = esUrl;
    }

    public boolean enabled() { return enabled; }

    /** 单题索引入库（审核上架时调用） */
    public void index(Long id, Long subjectId, String type, Integer difficulty,
                      String scene, String category, String kpNames, String stem, String source) {
        if (!enabled) return;
        String doc = """
                {"subjectId":%d,"type":"%s","difficulty":%d,"scene":"%s","category":"%s",
                 "kpNames":"%s","stem":"%s","source":"%s"}
                """.formatted(subjectId, esc(type), difficulty, esc(nvl(scene)), esc(nvl(category)),
                esc(nvl(kpNames)), esc(nvl(stem)), esc(nvl(source)));
        call("/examforge-questions/_doc/" + id, "PUT", doc);
    }

    /** multi_match 全文检索，返回命中的题目 id（按相关度排序）；ES 异常抛出让上层降级 */
    public List<Long> search(String keyword, Long subjectId, String type, Integer difficulty, int limit) {
        StringBuilder filter = new StringBuilder();
        if (subjectId != null) filter.append("{\"term\":{\"subjectId\":%d}},".formatted(subjectId));
        if (type != null) filter.append("{\"term\":{\"type.keyword\":\"%s\"}},".formatted(esc(type)));
        if (difficulty != null) filter.append("{\"term\":{\"difficulty\":%d}},".formatted(difficulty));
        String dsl = """
                {"size":%d,"query":{"bool":{
                   "must":[{"multi_match":{"query":"%s","fields":["stem^3","kpNames^2","source"]}}],
                   "filter":[%s]}},"_source":false}
                """.formatted(limit, esc(nvl(keyword)),
                filter.length() > 0 ? filter.substring(0, filter.length() - 1) : "");
        String resp = call("/examforge-questions/_search", "POST", dsl);
        return extractIds(resp);
    }

    private String call(String pathAndQuery, String method, String json) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(esUrl + pathAndQuery))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(3))
                    .method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) throw new IllegalStateException("ES " + resp.statusCode() + ": " + resp.body());
            return resp.body();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("ES 调用失败，降级数据库检索: {}", e.getMessage());
            throw new IllegalStateException(e);
        }
    }

    /** 从 ES 响应中提取 hits._id（零 JSON 库依赖的轻量解析） */
    private List<Long> extractIds(String resp) {
        List<Long> ids = new ArrayList<>();
        int i = 0;
        while ((i = resp.indexOf("\"_id\":\"", i)) >= 0) {
            int s = i + 7, e = resp.indexOf('"', s);
            try { ids.add(Long.parseLong(resp.substring(s, e))); } catch (NumberFormatException ignored) { }
            i = e;
        }
        return ids;
    }

    private String nvl(String s) { return s == null ? "" : s.replace("\"", "'").replace("\\", "").replace("\n", " "); }
    private String esc(String s) { return nvl(s); }
}
