# 小程序服务端实现与验收

| 版本 | 日期 | 内容 |
|---|---|---|
| 1.22.0 | 2026-10-10 | 实现小程序业务契约的118个新增接口、服务分层、异步任务与管理审计 |

本文件描述源码实现；未执行生产数据库升级、镜像推送或部署。接口总入口为[API.md](API.md)，详细入出参为[MINIAPP-API.md](MINIAPP-API.md)。

## 分层与模块

代码位于`src/main/java/com/yingying/cuotiku/server/mini/`。Controller只处理认证上下文、路径参数、请求体和响应包装；Service负责权限、状态、校验、事务及业务编排；实体与Repository保留在既有目录。

| Controller | Service与协作对象 | 职责 |
|---|---|---|
| MiniAccountController | MiniAccountService、WechatIdentityClient、既有JwtService／CaptchaService | 能力、微信身份交换、绑定与登录、账号权益、帮助／关于 |
| MiniStudentController | MiniStudentService | 学生卡片、资料、当前学生与首页摘要 |
| MiniTaxonomyController | MiniTaxonomyService | 学生科目、主题、错误类型、排序与分类偏好 |
| MiniAssetController／MiniFileController | MiniAssetService、BookStorage | 上传会话、完整性检查、私有访问、本地二进制传输 |
| MiniCaptureController | MiniCaptureService | 拍摄批次、照片排序／删除、不可变图片版本、题框 |
| MiniProcessingController | MiniProcessingService、MiniOcrGateway、MiniAiAuditService | 异步矫正、增强、去手写、框题、组合处理、逐项重试与取消 |
| MiniBookController | MiniBookService、MiniEntryWriter、MiniSourceService | 部分成功批量保存、列表筛选、详情、修改、删除、练习记录 |
| MiniAgentController | MiniAgentService、MiniAgentStore、既有AgentRuntimeService | 讲解与举一反三、学生／题目版本缓存、调用审计 |
| MiniTemplateController | MiniTemplateService | 分类横向分页、模板示意图、发布版本 |
| MiniPaperController | MiniPaperService | 可用题量、随机组卷、草稿、固定来源版本与排序 |
| MiniPrintController | MiniPrintService、MiniPdfRenderer | 创建任务、不可变快照、PDF生成、重试、下载、用户确认 |
| MiniFeedbackController | MiniFeedbackService | 反馈提交、列表、内部备注隔离 |
| MiniAdminController | MiniAdminService、MiniTemplateService、MiniAuditService | 账号／权益、Agent配置、模板发布、云日志、任务诊断、资产清理、反馈与队列恢复 |

### 请求与权限

- `MiniBody`执行Jakarta对象校验并保留PATCH中“字段缺省”和“显式null”的区别。服务方法逐字段校验类型、枚举、长度、数值范围和关联归属。响应采用字段白名单Map，不直接序列化JPA实体。
- 业务令牌、微信短期身份令牌和资产传输票据用途分离。身份交换不会自动创建会员账号；绑定需要手机号、密码、一次性图形验证码。
- 每个私有关联同时核对账号和学生；当前学生只是偏好，不替代URL中明确的studentId。学生不跨账号共享。归档学生停止新增业务及排队中的云处理。
- `/api/admin/mini/v1/**`由Spring Security要求ADMIN；管理写请求在执行前生成审计行，在完成后补HTTP状态和可选reason。密码、令牌、验证码、云密钥与完整请求体不进入审计表。
- 旧接口保持入出参兼容，只访问student_id为空的历史业务记录。上线前需审计旧唯一索引、历史分类和归属；不会自动把历史数据分给孩子。

### 事务与一致性

- 状态切换使用短事务及悲观锁；错题和草稿使用版本号拒绝覆盖旧修改。排序先迁移到临时位置，再写最终顺序，避免唯一约束在交换位置时冲突。
- `MiniRequestReceipt`以账号、业务操作、请求键唯一，规范化JSON字段顺序后计算哈希。同键同输入返回原响应；不同输入409；24小时过期返回410。回执与业务写入同事务提交。
- 批量保存采用每项独立事务，响应明确CREATED／DUPLICATE／FAILED。练习计数、练习记录和回执同事务更新；并发重放只增加一次。
- 批次完成后不再追加拍摄内容。图片版本、打印项和打印事件保留历史快照。模板发布版本不可编辑，发布新内容产生新版本。
- 上传会话先写临时对象，校验大小、SHA-256、真实MIME和图片尺寸后写独立资产对象。之前的PUT URL不能覆盖确认资产。系统SVG严格解析并拒绝脚本、外部引用及DOCTYPE。
- 公共列表与私有列表采用范围约束、分页上限和带HMAC的游标；游标绑定账号、学生和过滤条件，不能跨筛选复用。

## 任务执行

`MiniOutboxService`在创建任务的同一事务写入Outbox。Worker每次认领一条，最多同时执行两条；认领使用数据库锁、10分钟租约及每分钟续租。重启可重新认领租约过期任务，执行采用至少一次语义；任务项尝试编号与发布前租约检查阻止旧Worker覆盖结果。

外部AI调用、Agent调用和PDF渲染在业务锁事务外完成，再进入短事务发布结果。OCR通过现有腾讯云SDK适配器执行，保留EXIF纠正、图像归一化、InternalError有限重试；每次实际调用都记录独立日志与traceId。逐项失败产生FAILED／PARTIAL_SUCCESS，不伪造成功图片。基础设施投递失败最多自动尝试3次；业务失败通过显式重试接口恢复。

打印渲染使用PDFBox生成真实PDF。支持DECLARATIVE、rendererVersion=1、毫米坐标、CONTAIN布局与图片题目；不执行模板任意代码，首期不支持showAnswer=true。份数已展开到PDF页中。READY仅表示PDF生成成功，用户确认后进入PRINT_CONFIRMED，服务端没有物理打印机出纸检测。

资产清理检查题目、版本、任务、日志等引用，仍被引用则拒绝。对象存储删除成功后才标记DELETED。上传临时对象的过期清理需配置COS生命周期策略；不会在数据库提交前删除有效业务资产。

## 运行配置

| 配置 | 默认 | 作用 |
|---|---|---|
| WECHAT_MINIAPP_APP_ID／WECHAT_MINIAPP_SECRET | 空 | 微信身份交换；凭据只在服务端配置 |
| MINIAPP_WORKER_ENABLED | true | 启动持久异步处理与打印消费者 |
| MINIAPP_POLL_DELAY_MS | 1000 | 队列轮询间隔（毫秒） |
| TENCENT_SECRET_ID／TENCENT_SECRET_KEY、COS_BUCKET／COS_REGION | 沿用现有配置 | 腾讯云OCR与COS；开发可使用本地对象存储 |
| DASHSCOPE_API_KEY／AGENT_DAILY_LIMIT | 沿用现有配置 | Agent模型与现有每日调用限额 |

客户端启动应读取capabilities。云服务未配置时不能完成真实识别；测试桩不代表云服务已联通。图形验证码仍使用现有进程缓存，多实例部署需要统一挑战存储或会话粘性。商业免费／会员配额、留存策略及历史数据归属仍需按业务需求确认。

## 验收标准与验证

| 标准 | 证据 |
|---|---|
| 契约118个新增路由可定位，不变更4个复用认证接口 | MiniApiIntegrationTest读取契约清单核对Spring路由 |
| 普通用户／管理员边界与微信身份用途正确 | HTTP鉴权、绑定、登录、单端会话、管理权限测试 |
| 同账号不同孩子、跨账号及历史接口隔离 | 学生范围与关联归属HTTP测试，旧接口拒绝学生错题 |
| 上传完整性和确认资产不可覆盖 | 本地二进制上传、校验、签名票据、重放测试 |
| 排序、版本、重复提交、部分保存行为正确 | MySQL唯一约束、草稿交换、并发练习与幂等测试 |
| 处理成功／失败／取消可区分，日志记录正确 | 真实服务编排＋腾讯SDK适配器桩；归档后不调用云服务 |
| 打印生成可打开的PDF并冻结来源／模板 | PDFBox加载页数、任务事件、下载与主动确认测试 |
| Worker发布与失败边界正确 | MiniTaskWorkerTest测试异步投递、租约失效及渲染失败 |
| 管理操作有审计，内部备注不向用户泄露 | HTTP审计、失败请求reason与反馈隔离测试 |
| 既有客户端回归无失败，构建可交付 | `mvn test`及`mvn -DskipTests package`；真实云冒烟无凭据自动跳过 |

不以本地测试代替真实微信／腾讯云／COS联调或生产升级验收。当前交付为代码、自动化测试、接口契约和运行文档。

验证记录（2026-10-10）：全量`mvn test`共122项，0失败、0错误，8项真实云测试因缺少凭据跳过。其中小程序HTTP集成测试25项，Worker测试2项；使用Testcontainers MySQL。124个契约JSON示例全部通过解析。

打包验证：`mvn -DskipTests package`成功，产物`target/yycuotiku-server.jar`，实现版本1.22.0。

真实云冒烟补充（2026-10-10）：从仓库外本机凭据文件注入环境变量。腾讯云3项真实测试通过，覆盖小图去手写、大图归一化后去手写、矫正增强及框题调用。COS真实上传／下载内容比对通过；独立COS凭据与严格删除另行验证。没有把密钥写入本文或仓库。微信端到端登录仍需小程序wx.login产生的新鲜code；DashScope未配置，尚未联调。

COS独立凭据复测：上传、下载比对与严格删除全部通过（1项真实测试，0失败）。

微信真实身份交换（2026-10-10）：使用小程序新鲜wx.login code调用官方jscode2session成功，返回openid和session_key；不保存或展示其值。此次验证确认AppID／AppSecret与code匹配，不代表会员绑定、业务JWT登录和客户端全链路已完成。临时code已消费。

小程序认证对接补充：me响应已对齐契约包装；8081本机联调使用独立shixing_local_auth数据库。客户端认证核心与页面已接入，其余Demo业务尚未对接。
