# code 目录说明（工程启动指南）

本目录是代码工程的占位。启动开发时按以下顺序执行：

## 1. 从本地模板拉起骨架
1. 复制 `E:\code\tjxt_code\tianji` 的工程组织（tj-common / tj-api / tj-gateway 模式）为 `examforge-common / examforge-api / examforge-gateway`，升级 Spring Boot 2.7 → 3.2 / Java 17。
2. 从 `E:\code\resume\damai-main` 摘取四个框架包（redisson-framework、thread-pool-framework、id-generator-framework、spring-cloud-framework）按其封装思路适配到 examforge-common。
3. 运营后台直接基于 `E:\code\yudao-cloud` 的后台管理模块裁剪（去掉商城相关），保留 RBAC/操作日志/代码生成器。

## 2. 首批落地清单（对应 08 文档 I1 迭代）
- [ ] examforge-dependencies（BOM）
- [ ] nacos 配置规范（参考 tjxt 的 nacos-config 目录）
- [ ] 数据库 DDL（docs/04 全量建表脚本，落 `sql/` 目录）
- [ ] 统一响应体/异常/幂等注解/审计切面（examforge-common）
- [ ] question-svc 题目模型 + 管理端 CRUD（MyBatis-Plus）
- [ ] ES 索引 mapping（题目搜索）

## 3. POC 优先验证（M0 期间必须出结论）
1. Figure DSL → SVG 渲染器（TypeScript 同构）可行性
2. LaTeX → OMML → Word 可编辑公式链路（docx4j + MML2OMML.XSL）
3. ShardingSphere 16库×64表 分片路由 + 题目批量写入性能
4. KaTeX 服务端预渲染 + Redis 缓存命中率
5. Puppeteer 容器化渲染 PDF 的稳定性与内存水位
