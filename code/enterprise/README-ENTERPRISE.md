# 智卷云 · 企业级工程（enterprise）

> 微服务 + 双前端 + 数据层 + 编排 + CI 的完整可部署工程，对应《03-系统架构设计》。
> 端到端覆盖：客户端（Vue3 教师端 / Vue3 超管端）→ 服务端（5 个 Spring Boot 3 微服务）→ 数据库（MySQL 8 + Redis 7 + Elasticsearch 8）。

## 1. 工程结构

```
enterprise/
├── pom.xml                     # Maven 父工程（Java 17 / Spring Boot 3.2 / Spring Cloud 2023）
├── examforge-common/             # 统一响应体/全局异常/JWT 工具/分页对象（所有服务复用）
├── examforge-api/                # 服务间 Feign 契约（DTO + OpenFeign 接口）
├── examforge-gateway/            # API 网关：路由聚合、JWT 全局校验、限流
├── examforge-user/               # 用户服务：注册/登录/RBAC/教师认证
├── examforge-question/           # 题库服务：学段学科/章节/知识点/题目 CRUD/五段式解析/搜索
├── examforge-paper/              # 组卷服务：试题篮/智能组卷引擎/试卷管理
├── examforge-admin/              # 管理服务：广告位/公告/系统设置/操作审计/数据看板
├── sql/                        # MySQL 全量建库脚本（01~04）+ ES 索引 mapping
├── docker/                     # docker-compose（MySQL/Redis/ES/网关/5 服务）+ 各服务 Dockerfile
├── frontend/                   # 教师端 Vue3 + Vite + Element Plus + Pinia + KaTeX
├── frontend-admin/             # 超管端 Vue3（广告/题目/用户/公告/设置/审计）
├── ci/                         # GitHub Actions 流水线 + Makefile
└── README-ENTERPRISE.md        # 本文件
```

## 2. 一键启动（本地全栈）

```bash
# ① 数据层 + 全部服务（首次自动执行 sql/ 初始化）
cd docker && docker compose up -d
# 网关:        http://localhost:8080
# 前台(教师端): http://localhost:5173  （开发模式见 frontend/README）
# 超管端:      http://localhost:5174

# ② 本地编译（JDK17 + Maven 3.8+）
mvn -pl examforge-common,examforge-api install -am -DskipTests
mvn -pl examforge-gateway spring-boot:run     # 或逐服务打包镜像

# ③ 前端
cd frontend && npm i && npm run dev         # http://localhost:5173
cd frontend-admin && npm i && npm run dev   # http://localhost:5174
```

## 3. 服务清单与端口

| 服务 | 端口 | 职责 | 数据库 |
|---|---|---|---|
| examforge-gateway | 8080 | 统一入口 /api/**，JWT 校验，屏蔽 /internal/**，限流 | — |
| examforge-user | 8101 | 注册/登录/角色/教师认证 | examforge_user |
| examforge-question | 8102 | 题库全域（学段/学科/章节/知识点/题目/五段式解析/ES搜索/录题投稿/审核工作流/纠错工单） | examforge_question |
| examforge-paper | 8103 | 试题篮/智能组卷引擎/试卷/导出（计费闭环+browserless PDF 渲染管线） | examforge_paper |
| examforge-admin | 8104 | 广告位/公告/系统设置/操作审计 | examforge_admin |
| examforge-trade | 8105 | 会员/点数/优惠券/订单支付（沙箱+生产验签位）/下载计费/签到积分/纠错奖励 | examforge_trade |
| examforge-practice | 8106 | 练习/自动判分/错题本/学情报告 | examforge_practice |
| examforge-ai | 8107 | AI 变式出题（自动进审核队列）/SSE 分步讲题/配额治理（对接会员权益）/调用审计 | examforge_ai |
| examforge-resource | 8108 | 资源中心：课件/教案/学案库（类别×等级筛选）/33% 预览/计费下载（Feign 扣点）/资源篮/版权异议申诉工单（docs/26 F-XKW-01/02/03/14） | examforge_resource |

中间件：MySQL 3306、Redis 6379（缓存/会话）、Elasticsearch 9200（题目搜索，可选降级 DB LIKE）、browserless 3100（HTML→PDF 渲染）。

需求规格文档：docs/12（权限）、13（多Agent任务书）、14（商业化）、15（练习与AI域）。

## 4. 关键设计（与 docs/03/04/12 对应）
- **RBAC + 数据权限**：SUPER_ADMIN / OP / EDITOR / TEACHER / STUDENT；网关校验 JWT，服务内注解校验角色。
- **题目全结构化**：`question.stem/analysis` 存 JSON Block（text/latex/figure），渲染层 KaTeX + 白底图片规范（docs/06）。
- **组卷引擎**：蓝图（题型数量+难度分布+去重+知识点覆盖）→ 规则求解 → 难度曲线排序（`PaperGenerateService`）。
- **广告位体系**：7 类 position 枚举，素材为空时前端 `AdSlot.vue` 渲染占位框（后台配置后自动替换）。
- **操作审计**：管理端全部写操作落 `audit_log`（谁/何时/IP/前后值）。
- **SSRF 验收条件**：管理端素材 URL 校验仅允许 http/https 且拒绝内网/环回/保留地址（`UrlSafetyChecker`）。
- **SQL 全参数化**：MyBatis-Plus 全链路参数绑定，无字符串拼接 SQL。
- **可观测性（G3，docs/19 §5）**：全服务经 examforge-common 传递 actuator+micrometer，`/actuator/prometheus` 内网暴露（不经网关路由）；`docker/prometheus.yml` 抓取全部服务，`prometheus-rules.yml` 按 P0/P1/P2 告警（服务下线 / 5xx 错误率 / 支付成功率<99.9% / 网关 P99 / JVM 堆 / 连接池），Grafana 总览看板自动装配（compose 起 `prometheus:9090` + `grafana:3001`，账号 admin / `GRAFANA_PASSWORD`）；支付回调业务计数器 `examforge_payment_callback_total` 在 trade。**新增服务必须同步 prometheus.yml 抓取 job**（`MonitoringConfigStructureTest` 结构锁强制）。

## 5. 测试
- `mvn test`：各服务单测（组卷引擎难度分布/去重断言、题目筛选、JWT 工具）。
- 前端：`npm run build` 构建校验；接口冒烟：`docker/exec-smoke.sh`。

## 6. 与 MVP（code/app）的关系
code/app 是可演示的单体原型；本目录是企业级正式工程骨架+核心实现，二者数据库模型一致，数据可从 SQLite 导出导入 MySQL（`sql/05_migrate_from_mvp.sql`）。
