# 数据库模型与 JPA 映射实施说明

日期：2026-10-10。范围：实体与ORM。新增HTTP接口、Worker及会员绑定已完成，见SERVER-IMPLEMENTATION.md；未执行生产数据库升级。

模型依据：已确认的工作区 `shixing-miniapp/docs/DATABASE-MODEL.md`，本库同步保留在 `docs/DATABASE-MODEL.md`。实体在 `src/main/java/com/yingying/cuotiku/server/entity/`；Repository 在对应 `repository/` 目录。

## 已实现

- 共36个JPA实体：保留11个已有实体，新增25个实体（含请求回执与管理审计两个技术实体）。覆盖微信身份、账号档案、学生、错误类型、媒体资产、上传会话、采集、图片版本／题框、处理任务、模板分类／版本／代码、打印任务／快照／事件、组卷草稿、反馈、Outbox 和学生分类偏好。
- 每个新增实体有 Repository。已有学生业务 Repository 增加 `findByUserIdAndStudentId`，保持旧接口兼容。
- 既有 Java 属性／getter 不改名；数据库字段、索引使用显式 snake_case 映射，避免标量属性和关联属性产生重复逻辑列。
- 标量 ID 是写入入口；`*Ref` 为 `LAZY`、`insertable=false/updatable=false` 的只读 JPA 关联，不级联删除或持久化历史对象。新增表之间的关系使用物理外键；关联历史表的物理外键暂不强加，待历史审计后再收紧。
- JSON 使用 Hibernate `@JdbcTypeCode(SqlTypes.JSON)` 映射 MySQL JSON；已有 result_json TEXT 不转换。纸张尺寸使用 BigDecimal，时间使用 Instant；新增 UUID 与审计时间由生命周期方法生成。
- 图片版本、打印项快照、打印事件使用 Hibernate `@Immutable`，阻止常规 ORM 更新历史行。模板草稿／发布状态、JSON schema 和跨学生一致性由本次服务层校验；单凭 ORM 不能代替业务鉴权。
- BookEntry 与 PaperDraft 有 `@Version` 乐观锁；旧错题版本列有 `integer default 0` 兼容已有行。历史表新增学生／来源／供应商字段允许 null，旧写入不因缺少回填立即失败。
- 数据库约束覆盖学生年级学期、资产尺寸／大小、模板尺寸／题框数、打印题数／份数／页数及三选一来源。唯一键覆盖身份、幂等、分类、模板版本、打印项顺序和对象位置等。
- 腾讯云及 Agent 调用日志保留所有现有字段，补充 provider、Action、版本、模型、任务、输入输出资产、上游字符串错误码和尝试次数；不会修改现有腾讯云／COS 调用流程。

## 与逻辑模型的兼容映射

| 模型实体／字段 | 实际实现 | 原因 |
|---|---|---|
| user_taxonomy_pref 学生作用域 | 旧 UserTaxonomyPref 保持 user_id 主键；新增 StudentTaxonomyPref（user_id + student_id 唯一） | 不破坏旧 Repository 与账号分类偏好服务 |
| captcha_challenge | 保留 CaptchaService 缓存，不新增关系数据库表 | 图形验证码是短期、一次性挑战；Redis／多实例改造属于服务层后续工作 |
| user_subject 名称唯一 | MySQL 生成 scope_key；student_id 空时 user:{userId}，否则 student:{studentId} | 同时保护旧账号数据去重并允许不同孩子配置同名科目 |
| media_asset 对象位置唯一 | bucket/version 空值规范化为空串，生成 SHA-256 object_locator_hash 并唯一 | 避免 NULL 唯一键失效及 utf8mb4 长复合索引超过 MySQL 限制 |
| ai_agent_result 缓存唯一 | 生成 cache_key，涵盖账号、学生、Agent、subject_key、prompt_hash、input_content_hash | 兼容旧缓存维度，同时隔离学生与输入版本；新业务服务已提供完整归属字段 |
| 已有实体新增字段必填策略 | 历史兼容期 nullable，服务接入后回填再收紧 | 不能把旧行无 student_id 当作无效数据直接阻断原接口 |

生成列由 MySQL 计算，Java 仅只读访问。SHA-256 索引用于对象／缓存定位，不是鉴权凭据。

## 已有数据库升级注意事项

继续遵守后台 `AGENTS.md`：使用 `spring.jpa.hibernate.ddl-auto=update`，不提供 SQL 迁移脚本。本次只对测试容器建库，不连接生产数据库。

Hibernate update 能添加表／列／索引，但不会可靠删除旧唯一索引，也不负责业务数据回填。已有库在切换到学生业务前需检查：

1. `user_subject` 的新 `scope_key`、`uk_subject_scope_name`、`uk_subject_scope_system` 已创建；旧 `uk_user_norm_name`、`uk_user_system_key` 会阻止不同学生同名科目，须在确认新约束完整后按既有运维升级流程移除。
2. `ai_agent_result` 的新 `cache_key`、`uk_agent_result_cache` 已创建；旧 `uk_agent_result` 会阻止同题输入版本的新结果，应同样在新约束确认后移除。
3. 历史账号创建默认学生并回填分类／错题／练习／AI 的 student_id；关联归属检查完成前不启用小程序多人档案业务接口。旧 API 仍按账号查询，本次未改为学生过滤。
4. 历史偏好迁移到 student_taxonomy_pref；旧 user_taxonomy_pref 为旧接口保留。
5. 旧 COS ZIP object_key 保持可读；媒体独立资产迁移、真实 MIME 上传接口、清理补偿和任务 worker 尚未由本次 ORM 工作实现。

## 验证

`DatabaseModelIntegrationTest` 使用真实 MySQL 8／Testcontainers，启动时开启 Hibernate DDL 错误立即失败。验证所有实体／列可查询、采集版本和关联可回读、MySQL JSON 内容、打印快照、同名科目按学生隔离、历史错题可保存更新、旧偏好与学生偏好共存，以及重复资产／科目和非法年级被数据库拒绝。

常规命令：`mvn test` 与 `mvn -DskipTests package`。腾讯云和 COS 真实调用测试维持 opt-in，本次不调用付费云服务。

## 服务实现补充（1.22.0）

新增MiniRequestReceipt保存24小时幂等响应，MiniAdminAudit保存管理员写操作结果与可选原因。AssetUploadSession增加stagingObjectKey，隔离上传临时对象与确认资产；PaperDraftItem增加inputRevisionId，冻结照片来源版本；BookEntry旧文本分类字段长度扩展到64，兼容学生自定义分类。详细字段见DATABASE-MODEL.md。所有变更仍以JPA ddl-auto=update描述，未执行生产升级。
