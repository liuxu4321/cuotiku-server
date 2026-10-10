# 拾星错题本：小程序业务接口契约

## 版本记录

| 契约版本 | 日期 | 内容 | 实现状态 |
|---|---|---|---|
| 1.0.1 | 2026-10-10 | 118个新增接口、异步Worker、PDF及管理审计完成；明确运行边界 | 服务端1.22.0源码实现，未部署 |
| 1.0.0 | 2026-10-10 | 首次完整接口清单、输入输出与样例、客户端业务编排、管理接口及兼容规则 | 设计基线；除明确“已有”项外均待实现 |

契约版本与服务端包版本分别管理。当前后台包版本1.22.0，新增118个业务接口已在源码实现，另复用4个认证接口。配置与验收见SERVER-IMPLEMENTATION.md；未部署生产。用户故事见 `USER-STORIES.md`，实体见 `DATABASE-MODEL.md`，现有后端已实现接口见权威 `API.md`。

新增小程序接口统一使用 `/api/mini/v1`；新增管理接口使用 `/api/admin/mini/v1`，避免改变桌面端现有 `/api`、`/api/v2` 契约。“已实现”指源码接口，云服务可用性仍取决于配置与capabilities。已有兼容接口的精确行为以Controller、DTO及 `API.md` 为准。

## 1. 全部目标接口清单

下表为本契约全部接口，每项详情的编号固定。后续实现和客户端任务都应引用该编号；并非按数据库每张表自动生成CRUD，身份、密钥、后台日志不能让客户端任意写。

| 编号 | 方法与路径 | 用途 | 状态／授权 |
|---|---|---|---|
| [AUTH-01](#auth-01) | `GET /api/auth/captcha` | 获取图形验证码 | 已有／公开 |
| [AUTH-02](#auth-02) | `GET /api/mini/v1/capabilities` | 协商小程序接口能力 | 已实现／公开 |
| [AUTH-03](#auth-03) | `POST /api/mini/v1/auth/wechat` | 交换微信身份会话 | 已实现／公开 |
| [AUTH-04](#auth-04) | `POST /api/mini/v1/auth/member-bind` | 微信身份绑定会员账号并登录 | 已实现／身份令牌＋验证码／账号凭据 |
| [AUTH-05](#auth-05) | `POST /api/mini/v1/auth/login` | 登录已绑定微信账号 | 已实现／已绑定身份令牌 |
| [AUTH-06](#auth-06) | `POST /api/auth/refresh` | 刷新账号令牌 | 已有／刷新令牌 |
| [AUTH-07](#auth-07) | `GET /api/mini/v1/me` | 读取账号、权益及最近学生 | 已实现／账号 Bearer |
| [AUTH-08](#auth-08) | `POST /api/auth/logout` | 退出账号会话 | 已有／账号 Bearer |
| [AUTH-09](#auth-09) | `POST /api/auth/password` | 修改账号密码 | 已有／账号 Bearer |
| [PUBLIC-01](#public-01) | `GET /api/mini/v1/content/{key}` | 读取使用帮助或关于内容 | 已实现／公开 |
| [STU-01](#stu-01) | `GET /api/mini/v1/students` | 学生卡片列表 | 已实现／账号 Bearer |
| [STU-02](#stu-02) | `POST /api/mini/v1/students` | 增加学生 | 已实现／账号 Bearer |
| [STU-03](#stu-03) | `GET /api/mini/v1/students/{studentId}` | 查看学生资料 | 已实现／账号 Bearer |
| [STU-04](#stu-04) | `PATCH /api/mini/v1/students/{studentId}` | 编辑学生资料或归档 | 已实现／账号 Bearer |
| [STU-05](#stu-05) | `PUT /api/mini/v1/me/current-student` | 记录最近使用学生 | 已实现／账号 Bearer |
| [STU-06](#stu-06) | `GET /api/mini/v1/students/{studentId}/summary` | 读取首页学生错题摘要 | 已实现／账号 Bearer |
| [SUB-01](#sub-01) | `GET /api/mini/v1/students/{studentId}/subjects` | 查询科目列表 | 已实现／账号 Bearer |
| [SUB-02](#sub-02) | `POST /api/mini/v1/students/{studentId}/subjects` | 增加科目 | 已实现／账号 Bearer |
| [SUB-03](#sub-03) | `PATCH /api/mini/v1/students/{studentId}/subjects/{id}` | 更新／停用科目 | 已实现／账号 Bearer |
| [SUB-04](#sub-04) | `PUT /api/mini/v1/students/{studentId}/subjects/order` | 调整科目顺序 | 已实现／账号 Bearer |
| [SUB-05](#sub-05) | `DELETE /api/mini/v1/students/{studentId}/subjects/{id}` | 删除无引用科目 | 已实现／账号 Bearer |
| [ERR-01](#err-01) | `GET /api/mini/v1/students/{studentId}/error-types` | 查询错误类型列表 | 已实现／账号 Bearer |
| [ERR-02](#err-02) | `POST /api/mini/v1/students/{studentId}/error-types` | 增加错误类型 | 已实现／账号 Bearer |
| [ERR-03](#err-03) | `PATCH /api/mini/v1/students/{studentId}/error-types/{id}` | 更新／停用错误类型 | 已实现／账号 Bearer |
| [ERR-04](#err-04) | `PUT /api/mini/v1/students/{studentId}/error-types/order` | 调整错误类型顺序 | 已实现／账号 Bearer |
| [ERR-05](#err-05) | `DELETE /api/mini/v1/students/{studentId}/error-types/{id}` | 删除无引用错误类型 | 已实现／账号 Bearer |
| [TOP-01](#top-01) | `GET /api/mini/v1/students/{studentId}/subjects/{subjectId}/topics` | 读取科目主题 | 已实现／账号 Bearer |
| [TOP-02](#top-02) | `POST /api/mini/v1/students/{studentId}/subjects/{subjectId}/topics` | 增加科目主题 | 已实现／账号 Bearer |
| [TOP-03](#top-03) | `PATCH /api/mini/v1/students/{studentId}/topics/{id}` | 编辑／停用主题 | 已实现／账号 Bearer |
| [TOP-04](#top-04) | `PUT /api/mini/v1/students/{studentId}/subjects/{subjectId}/topics/order` | 调整主题顺序 | 已实现／账号 Bearer |
| [TOP-05](#top-05) | `DELETE /api/mini/v1/students/{studentId}/topics/{id}` | 删除无引用主题 | 已实现／账号 Bearer |
| [PREF-01](#pref-01) | `GET /api/mini/v1/students/{studentId}/preferences` | 查询学生分类与模板偏好 | 已实现／账号 Bearer |
| [PREF-02](#pref-02) | `PUT /api/mini/v1/students/{studentId}/preferences` | 保存学生偏好 | 已实现／账号 Bearer |
| [AST-01](#ast-01) | `POST /api/mini/v1/students/{studentId}/uploads` | 创建COS上传会话 | 已实现／账号 Bearer |
| [AST-02](#ast-02) | `POST /api/mini/v1/students/{studentId}/uploads/{uploadId}/complete` | 确认上传并校验对象 | 已实现／账号 Bearer |
| [AST-03](#ast-03) | `GET /api/mini/v1/students/{studentId}/assets/{assetId}` | 读取资产元数据 | 已实现／账号 Bearer |
| [AST-04](#ast-04) | `POST /api/mini/v1/students/{studentId}/assets/{assetId}/access` | 申请私有图片或PDF访问地址 | 已实现／账号 Bearer |
| [CAP-01](#cap-01) | `POST /api/mini/v1/students/{studentId}/capture-batches` | 建立单张／多页采集批次 | 已实现／账号 Bearer |
| [CAP-02](#cap-02) | `GET /api/mini/v1/students/{studentId}/capture-batches` | 最近拍摄批次列表 | 已实现／账号 Bearer |
| [CAP-03](#cap-03) | `GET /api/mini/v1/students/{studentId}/capture-batches/{batchId}` | 读取照片处理工作区 | 已实现／账号 Bearer |
| [CAP-04](#cap-04) | `POST /api/mini/v1/students/{studentId}/capture-batches/{batchId}/photos` | 把上传成功照片加入批次 | 已实现／账号 Bearer |
| [CAP-05](#cap-05) | `POST /api/mini/v1/students/{studentId}/capture-batches/{batchId}/finish` | 结束拍摄进入处理 | 已实现／账号 Bearer |
| [CAP-06](#cap-06) | `PUT /api/mini/v1/students/{studentId}/capture-batches/{batchId}/photos/order` | 调整照片显示顺序 | 已实现／账号 Bearer |
| [CAP-07](#cap-07) | `DELETE /api/mini/v1/students/{studentId}/photos/{photoId}` | 删除工作区照片 | 已实现／账号 Bearer |
| [CAP-08](#cap-08) | `DELETE /api/mini/v1/students/{studentId}/capture-batches/{batchId}` | 放弃或移除采集工作区 | 已实现／账号 Bearer |
| [IMG-01](#img-01) | `GET /api/mini/v1/students/{studentId}/photos/{photoId}/revisions` | 读取原图与处理版本 | 已实现／账号 Bearer |
| [IMG-02](#img-02) | `PUT /api/mini/v1/students/{studentId}/photos/{photoId}/current-revision` | 选择有效处理版本 | 已实现／账号 Bearer |
| [JOB-01](#job-01) | `POST /api/mini/v1/students/{studentId}/processing-jobs` | 提交单张／整批智能处理 | 已实现／账号 Bearer |
| [JOB-02](#job-02) | `GET /api/mini/v1/students/{studentId}/processing-jobs/{jobId}` | 轮询处理状态 | 已实现／账号 Bearer |
| [JOB-03](#job-03) | `POST /api/mini/v1/students/{studentId}/processing-jobs/{jobId}/retry` | 重试失败的处理项 | 已实现／账号 Bearer |
| [JOB-04](#job-04) | `POST /api/mini/v1/students/{studentId}/processing-jobs/{jobId}/cancel` | 请求取消处理任务 | 已实现／账号 Bearer |
| [REG-01](#reg-01) | `GET /api/mini/v1/students/{studentId}/photos/{photoId}/regions` | 读取指定图片版本题框 | 已实现／账号 Bearer |
| [REG-02](#reg-02) | `PUT /api/mini/v1/students/{studentId}/photos/{photoId}/regions` | 保存手动题框与修改 | 已实现／账号 Bearer |
| [ENT-01](#ent-01) | `POST /api/mini/v1/students/{studentId}/entries/batch` | 分类批量保存题框或照片 | 已实现／账号 Bearer |
| [ENT-02](#ent-02) | `GET /api/mini/v1/students/{studentId}/entries` | 筛选错题卡片列表 | 已实现／账号 Bearer |
| [ENT-03](#ent-03) | `GET /api/mini/v1/students/{studentId}/entries/{entryId}` | 统一错题详情 | 已实现／账号 Bearer |
| [ENT-04](#ent-04) | `PATCH /api/mini/v1/students/{studentId}/entries/{entryId}` | 修改错题分类或内容 | 已实现／账号 Bearer |
| [ENT-05](#ent-05) | `DELETE /api/mini/v1/students/{studentId}/entries/{entryId}` | 软删除错题 | 已实现／账号 Bearer |
| [PRA-01](#pra-01) | `POST /api/mini/v1/students/{studentId}/entries/{entryId}/practices` | 提交做题结果 | 已实现／账号 Bearer |
| [PRA-02](#pra-02) | `GET /api/mini/v1/students/{studentId}/entries/{entryId}/practices` | 查询单题练习历史 | 已实现／账号 Bearer |
| [PRA-03](#pra-03) | `GET /api/mini/v1/students/{studentId}/practices` | 查询学生跨题练习历史 | 已实现／账号 Bearer |
| [AGT-01](#agt-01) | `POST /api/mini/v1/students/{studentId}/entries/{entryId}/explanation` | 请求讲解 | 已实现／账号 Bearer |
| [AGT-02](#agt-02) | `POST /api/mini/v1/students/{studentId}/entries/{entryId}/analogies` | 请求举一反三 | 已实现／账号 Bearer |
| [RND-01](#rnd-01) | `POST /api/mini/v1/students/{studentId}/random-papers/availability` | 查询各错误类型可抽题数 | 已实现／账号 Bearer |
| [RND-02](#rnd-02) | `POST /api/mini/v1/students/{studentId}/random-papers` | 随机组卷并返回有序来源 | 已实现／账号 Bearer |
| [DRF-01](#drf-01) | `POST /api/mini/v1/students/{studentId}/paper-drafts` | 建立可恢复打印草稿（可选） | 已实现／账号 Bearer |
| [DRF-02](#drf-02) | `GET /api/mini/v1/students/{studentId}/paper-drafts/{draftId}` | 恢复有序选题草稿 | 已实现／账号 Bearer |
| [DRF-03](#drf-03) | `PATCH /api/mini/v1/students/{studentId}/paper-drafts/{draftId}` | 选择草稿预选模板 | 已实现／账号 Bearer |
| [DRF-04](#drf-04) | `PUT /api/mini/v1/students/{studentId}/paper-drafts/{draftId}/items/order` | 调整草稿题序 | 已实现／账号 Bearer |
| [DRF-05](#drf-05) | `DELETE /api/mini/v1/students/{studentId}/paper-drafts/{draftId}/items/{itemId}` | 从草稿删除打印项 | 已实现／账号 Bearer |
| [DRF-06](#drf-06) | `DELETE /api/mini/v1/students/{studentId}/paper-drafts/{draftId}` | 放弃草稿 | 已实现／账号 Bearer |
| [TPL-01](#tpl-01) | `GET /api/mini/v1/template-categories` | 分类加载模板示意图 | 已实现／账号 Bearer |
| [TPL-02](#tpl-02) | `GET /api/mini/v1/template-categories/{categoryId}/templates` | 横向加载同分类更多模板 | 已实现／账号 Bearer |
| [TPL-03](#tpl-03) | `GET /api/mini/v1/templates/{templateId}/versions/{versionId}` | 读取指定发布模板版本 | 已实现／账号 Bearer |
| [TPL-04](#tpl-04) | `GET /api/mini/v1/templates/{templateId}/preview` | 获取模板SVG示意图 | 已实现／账号 Bearer |
| [PRT-01](#prt-01) | `POST /api/mini/v1/students/{studentId}/print-tasks` | 选模板后创建正式打印任务 | 已实现／账号 Bearer |
| [PRT-02](#prt-02) | `GET /api/mini/v1/students/{studentId}/print-tasks` | 打印菜单任务列表 | 已实现／账号 Bearer |
| [PRT-03](#prt-03) | `GET /api/mini/v1/students/{studentId}/print-tasks/{taskId}` | 轮询任务或读取快照详情 | 已实现／账号 Bearer |
| [PRT-04](#prt-04) | `POST /api/mini/v1/students/{studentId}/print-tasks/{taskId}/retry` | 原快照重试生成PDF | 已实现／账号 Bearer |
| [PRT-05](#prt-05) | `POST /api/mini/v1/students/{studentId}/print-tasks/{taskId}/cancel` | 取消可取消的打印任务 | 已实现／账号 Bearer |
| [PRT-06](#prt-06) | `POST /api/mini/v1/students/{studentId}/print-tasks/{taskId}/download` | 获得生成PDF下载地址 | 已实现／账号 Bearer |
| [PRT-07](#prt-07) | `POST /api/mini/v1/students/{studentId}/print-tasks/{taskId}/confirm` | 用户明确确认纸张已打印 | 已实现／账号 Bearer |
| [PRT-08](#prt-08) | `DELETE /api/mini/v1/students/{studentId}/print-tasks/{taskId}` | 从用户列表隐藏历史任务 | 已实现／账号 Bearer |
| [PRT-09](#prt-09) | `GET /api/mini/v1/students/{studentId}/print-tasks/{taskId}/events` | 查看本人打印任务状态历史 | 已实现／账号 Bearer |
| [FDB-01](#fdb-01) | `POST /api/mini/v1/feedbacks` | 提交意见反馈 | 已实现／账号 Bearer |
| [FDB-02](#fdb-02) | `GET /api/mini/v1/feedbacks` | 查询本人反馈及处理状态 | 已实现／账号 Bearer |
| [ADM-01](#adm-01) | `GET /api/admin/mini/v1/accounts` | 管理查询普通／会员账号 | 已实现／ADMIN Bearer |
| [ADM-02](#adm-02) | `GET /api/admin/mini/v1/accounts/{userId}` | 管理查看账号详情 | 已实现／ADMIN Bearer |
| [ADM-03](#adm-03) | `POST /api/admin/mini/v1/accounts` | 管理创建账号 | 已实现／ADMIN Bearer |
| [ADM-04](#adm-04) | `PATCH /api/admin/mini/v1/accounts/{userId}` | 更新资料、权益和AI授权 | 已实现／ADMIN Bearer |
| [ADM-05](#adm-05) | `PUT /api/admin/mini/v1/accounts/{userId}/password` | 管理重置密码并撤销会话 | 已实现／ADMIN Bearer |
| [ADM-06](#adm-06) | `DELETE /api/admin/mini/v1/accounts/{userId}/membership` | 管理撤销会员权益 | 已实现／ADMIN Bearer |
| [ADM-07](#adm-07) | `GET /api/admin/mini/v1/accounts/{userId}/identities` | 只读微信绑定排查 | 已实现／ADMIN Bearer |
| [ADM-08](#adm-08) | `GET /api/admin/mini/v1/accounts/{userId}/students` | 只读账号学生诊断 | 已实现／ADMIN Bearer |
| [ADM-09](#adm-09) | `GET /api/admin/mini/v1/integrity-checks` | 查询归属与迁移诊断 | 已实现／ADMIN Bearer |
| [ADM-10](#adm-10) | `GET /api/admin/mini/v1/template-categories` | 管理模板分类列表 | 已实现／ADMIN Bearer |
| [ADM-11](#adm-11) | `POST /api/admin/mini/v1/template-categories` | 管理创建模板分类 | 已实现／ADMIN Bearer |
| [ADM-12](#adm-12) | `PATCH /api/admin/mini/v1/template-categories/{categoryId}` | 管理修改分类显示／启停 | 已实现／ADMIN Bearer |
| [ADM-13](#adm-13) | `GET /api/admin/mini/v1/templates` | 管理模板列表 | 已实现／ADMIN Bearer |
| [ADM-14](#adm-14) | `POST /api/admin/mini/v1/templates` | 管理创建模板身份 | 已实现／ADMIN Bearer |
| [ADM-15](#adm-15) | `PATCH /api/admin/mini/v1/templates/{templateId}` | 修改模板摘要／停用 | 已实现／ADMIN Bearer |
| [ADM-16](#adm-16) | `POST /api/admin/mini/v1/templates/{templateId}/versions` | 新建模板草稿版本 | 已实现／ADMIN Bearer |
| [ADM-17](#adm-17) | `GET /api/admin/mini/v1/templates/{templateId}/versions` | 管理读取版本及代码 | 已实现／ADMIN Bearer |
| [ADM-18](#adm-18) | `PUT /api/admin/mini/v1/templates/{templateId}/versions/{versionId}` | 修改未发布完整版本 | 已实现／ADMIN Bearer |
| [ADM-19](#adm-19) | `POST /api/admin/mini/v1/assets` | 上传受控系统SVG预览资产 | 已实现／ADMIN Bearer |
| [ADM-20](#adm-20) | `POST /api/admin/mini/v1/templates/{templateId}/versions/{versionId}/publish` | 校验并发布不可变模板版本 | 已实现／ADMIN Bearer |
| [ADM-21](#adm-21) | `GET /api/admin/mini/v1/agents` | 查询模型和提示词配置 | 已实现／ADMIN Bearer |
| [ADM-22](#adm-22) | `PUT /api/admin/mini/v1/agents/{agentKey}` | 维护模型提示词并使缓存失效 | 已实现／ADMIN Bearer |
| [ADM-23](#adm-23) | `GET /api/admin/mini/v1/ai-calls` | 逐次调用日志查询 | 已实现／ADMIN Bearer |
| [ADM-24](#adm-24) | `GET /api/admin/mini/v1/ai-statistics` | AI流量及成功失败统计 | 已实现／ADMIN Bearer |
| [ADM-25](#adm-25) | `GET /api/admin/mini/v1/processing-jobs` | 后台查询处理任务 | 已实现／ADMIN Bearer |
| [ADM-26](#adm-26) | `GET /api/admin/mini/v1/processing-jobs/{taskId}` | 后台查看任务固定输入／快照 | 已实现／ADMIN Bearer |
| [ADM-27](#adm-27) | `POST /api/admin/mini/v1/processing-jobs/{taskId}/retry` | 后台有原因重试失败任务 | 已实现／ADMIN Bearer |
| [ADM-28](#adm-28) | `GET /api/admin/mini/v1/print-tasks` | 后台查询打印任务 | 已实现／ADMIN Bearer |
| [ADM-29](#adm-29) | `GET /api/admin/mini/v1/print-tasks/{taskId}` | 后台查看任务固定输入／快照 | 已实现／ADMIN Bearer |
| [ADM-30](#adm-30) | `POST /api/admin/mini/v1/print-tasks/{taskId}/retry` | 后台有原因重试失败任务 | 已实现／ADMIN Bearer |
| [ADM-31](#adm-31) | `GET /api/admin/mini/v1/assets` | 管理查询资产与清理候选 | 已实现／ADMIN Bearer |
| [ADM-32](#adm-32) | `POST /api/admin/mini/v1/assets/{assetId}/cleanup` | 请求无引用资产清理 | 已实现／ADMIN Bearer |
| [ADM-33](#adm-33) | `GET /api/admin/mini/v1/feedbacks` | 管理查询反馈 | 已实现／ADMIN Bearer |
| [ADM-34](#adm-34) | `PATCH /api/admin/mini/v1/feedbacks/{feedbackId}` | 处理反馈并保存内部备注 | 已实现／ADMIN Bearer |
| [ADM-35](#adm-35) | `GET /api/admin/mini/v1/outbox-events` | 查询队列投递和失败重试 | 已实现／ADMIN Bearer |
| [ADM-36](#adm-36) | `POST /api/admin/mini/v1/outbox-events/{eventId}/retry` | 恢复任务消息投递 | 已实现／ADMIN Bearer |

## 2. 通用对接约定

### 2.1 地址、认证与响应

Base URL 示例 `https://api.example.test`，本机示例 `http://127.0.0.1:8080`。除COS上传／文件下载外使用JSON UTF-8。账号接口请求头 `Authorization: Bearer <token>`；ADMIN接口还校验后端role；身份令牌只出现在明确要求的登录／绑定请求体，不可当账号Token。客户端不提交可信userId，服务端从登录态取得。

成功响应：`{"code":0,"message":"ok","data":...}`。本契约所有响应样例均为完整包装。POST异步任务使用HTTP202但仍code=0；普通成功200。无数据时data为{}，已有接口可省略data。JSON字段统一camelCase，数据库snake_case由后端转换；接口不直接序列化JPA实体／LAZY关联。

目标所有ID（包括原Long科目／主题／账号ID）均为字符串。旧接口中Long数值ID和旧时间格式不变。时间使用UTC ISO8601带Z，日期用yyyy-MM-dd，纸张毫米使用十进制字符串，sizeBytes为JS安全整数范围内字节数。例中的UUID简写／域名／签名／Token均为占位，不可复制充当真实凭据。

### 2.2 学生作用域、分页和错误

所有学生业务路径显式studentId。服务端逐级验证账号、学生、分类、照片、区域、版本及资产一致；归属不符返回404，不能用GET最新学生偏好代替请求作用域。只有模板／静态内容／反馈是非学生作用域。

列表标准响应data=`{items,nextCursor,hasMore}`；首请求不传cursor，后续原样传nextCursor及相同筛选条件。limit默认20、1～100（具体以能力接口为准）。nextCursor为服务端不透明值，绑定筛选和作用域，不能客户端拼接时间。items空不代表接口失败。时间筛选转换上海日期为UTC半开区间。分类排序asc sortOrder、id；错题／任务列表createdAt DESC,id ASC；其余资源按固定服务端排序编码游标。

| HTTP／code | 意义 | 客户端处理 |
|---|---|---|
| 400／400 | 参数、图形验证码、数量或范围不合法 | 展示message，绑定失败重新取验证码 |
| 401／401 | 账号Token过期／无效 | 非刷新接口可单次刷新后重放；刷新失败清会话 |
| 401／4011 | 当前账号被新登录替换 | 清访问／刷新Token及账号草稿，不再刷新 |
| 403／403 | 角色／AI授权不足、账号停用 | 展示原因，不不断重试；不能按会员标签绕过 |
| 404／404 | 不存在／不属于当前作用域 | 刷新列表或返回；不泄露他人存在性 |
| 409／409 | 请求幂等键不同内容、任务状态或源版本冲突 | 保留用户输入，按data中的当前状态修正 |
| 409／4091 | 版本／修订号冲突 | data.current为最新资源，提示刷新，不强行覆盖 |
| 409／4092 | 分类／资产有引用不能删除 | 改停用或保留，绝不强制删历史 |
| 409／4093 | 同作用域规范化名称重复 | 提示改名 |
| 410／410 | 目标草稿或抽题幂等缓存过期 | 丢弃失效草稿，明确重新选题／组卷 |
| 429／429 | 额度／频率超限 | 按Retry-After／data.retryAfterMs退避 |
| 500／500 | 内部错误 | 提示重试，幂等写操作保留原请求键 |
| 502／502、503／503、504／504 | 上游失败、供应商未配置、超时 | 展示原因；超时不承诺未计费，先查任务／缓存 |

目标错误样例：
```json
{"code":4091,"message":"错题已更新，请刷新后重试","data":{"current":{"id":"entry-uuid","version":2},"traceId":"trace-id"}}
```
当前已有接口的特定code／data结构仍以API.md为准，不能把新data.current包装套给旧接口。

### 2.3 幂等、可重试性和空值

创建批次／上传／保存／处理／练习／打印携带各自clientRequestId／requestId+clientId／requestKey。同用户同请求键同输入复用原结果，不同学生或输入返回409。发生网络超时重用原键，不生成新键盲目再建任务。幂等支持时长由服务端公布，至少覆盖客户端重试窗口；不因删除记录就重用旧业务键。

PATCH缺失字段保留原值；null只有明确允许清除的字段可用；required字段不可null；空字符串仅备注／答案或明确清除语义允许。PUT为文中所述完整范围替换。列表读取、状态轮询可重试；AI forceRefresh、账号登录等可能有额外消耗／撤会话，不自动无限重放。

模板页本地草稿是首期允许方案；features.paperDraft=false时不调用DRF接口，PRT-01直接提交有序sources。服务器草稿启用后仍固定studentId，并使用version防并发覆盖。一个打印项ENTRY／REGION／PHOTO三选一，PHOTO必须指定inputRevisionId；不能仅传临时文件路径、客户端对象键或带期限URL。

### 2.4 处理与打印状态机

处理：QUEUED → RUNNING → SUCCEEDED／PARTIAL_SUCCESS／FAILED／CANCELLED；每图item无PARTIAL_SUCCESS。输出资产AVAILABLE后才能发布版本；纯SPLIT无需成图。取消与完成有竞态，终态以服务器为准。

打印：QUEUED → RENDERING → READY／FAILED／CANCELLED；READY → PRINT_CONFIRMED。失败重试重新排队并记事件但不改快照。READY仅PDF生成，下载／分享／原生打印弹窗不等于纸张输出。取消已ready不适用，隐藏历史走DELETE。客户端尊重pollAfterMs，使用退避、页面可见性和终态停止，不用固定高速轮询。

## 3. 返回对象字段定义

接口详情引用以下DTO；响应样例展示代表性完整结构。null字段依说明允许，嵌套对象详情及状态必须按契约解析，客户端允许未来新增字段但不能把缺少必需字段当成功。

### Student

学生卡片；年级1～12，学期1／2。

| 字段 | 类型 | 含义 |
|---|---|---|
| id | string | 业务主键，字符串传输 |
| nickname | string | 学生昵称 |
| avatarAssetId | string／null | 头像资产ID，可空 |
| grade | integer | 年级1～12 |
| term | integer | 学期1上／2下 |
| status | string | 实体或任务状态，值域见对应DTO／状态机 |
| sortOrder | integer | 展示顺序，非负整数 |
| createdAt | string | 创建时间UTC ISO8601 |
| updatedAt | string | 更新时间UTC ISO8601 |

### Account

已绑定账号；到期不改变账号归属，memberName需后端补齐字段。

| 字段 | 类型 | 含义 |
|---|---|---|
| id | string | 业务主键，字符串传输 |
| memberName | string | 服务端会员显示名，独立于学生昵称 |
| memberNo | string | 会员号，不作为鉴权凭据 |
| memberExpireAt | string | 会员到期日DATE，可空 |
| memberActive | boolean | 会员权益是否有效，不等同AI授权 |
| role | string | USER／ADMIN |
| aiEnabled | boolean | 独立AI开关 |
| lastStudentId | string | 最近使用学生偏好，不代替请求studentId |

### Capabilities

能力按当前配置协商；以下示例为Worker开启但云凭据未配置的开发环境。limits是当前服务端技术上限，不是商业套餐或腾讯云平台限制。

| 字段 | 类型 | 含义 |
|---|---|---|
| contractVersion | string | 本小程序API契约版本 |
| serverVersion | string | 实际后台包版本，与契约版本独立 |
| miniappApiSupported | boolean | 本契约主接口是否已实现并部署 |
| studentScopeSupported | boolean | 是否已完成后台学生归属隔离 |
| features | object | 分功能真实可用开关，false或缺少时不调用 |
| features.capture | boolean | 采集资产接口是否已可用 |
| features.processing | boolean | 异步图片处理是否已可用 |
| features.printing | boolean | 模板与PDF任务是否已可用 |
| features.paperDraft | boolean | 是否启用服务器草稿；未启用使用本地草稿 |
| features.feedback | boolean | 反馈后台提交是否可用 |
| limits | object | 服务器技术限额；样例不是已确认商业套餐 |
| limits.maxUploadBytes | integer | 单张上传二进制字节上限 |
| limits.maxPhotosPerBatch | integer | 单采集批次最多有效照片数 |
| limits.maxBatchItems | integer | 单次批量保存最多项数 |
| limits.maxPrintCopies | integer | 打印最大份数 |

### Subject

个人科目，修改带revision，同学生去重。

| 字段 | 类型 | 含义 |
|---|---|---|
| id | string | 业务主键，字符串传输 |
| studentId | string | 所属学生ID，必须属于登录账号 |
| name | string | 显示名称 |
| systemKey | string | 旧预设科目标识，自定义科目可空 |
| status | string | 实体或任务状态，值域见对应DTO／状态机 |
| sortOrder | integer | 展示顺序，非负整数 |
| revision | integer | 分类修订号；修改／排序必填 |
| topicCount | integer | 科目所属主题数量 |
| entryCount | integer | 当前范围有效错题数 |
| updatedAt | string | 更新时间UTC ISO8601 |

### Topic

主题必须属于选定科目及学生。

| 字段 | 类型 | 含义 |
|---|---|---|
| id | string | 业务主键，字符串传输 |
| subjectId | string | 科目ID，必须归属本学生 |
| studentId | string | 所属学生ID，必须属于登录账号 |
| name | string | 显示名称 |
| status | string | 实体或任务状态，值域见对应DTO／状态机 |
| sortOrder | integer | 展示顺序，非负整数 |
| revision | integer | 分类修订号；修改／排序必填 |
| entryCount | integer | 当前范围有效错题数 |
| updatedAt | string | 更新时间UTC ISO8601 |

### ErrorType

错误类型；code服务端生成，drawGroupCode兼容旧四类。

| 字段 | 类型 | 含义 |
|---|---|---|
| id | string | 业务主键，字符串传输 |
| studentId | string | 所属学生ID，必须属于登录账号 |
| code | string | 稳定代码；微信登录时为一次性wx.login code |
| name | string | 显示名称 |
| drawGroupCode | string | CARELESS／UNFAMILIAR／CONCEPT／OTHER，旧四类兼容映射 |
| status | string | 实体或任务状态，值域见对应DTO／状态机 |
| sortOrder | integer | 展示顺序，非负整数 |
| revision | integer | 分类修订号；修改／排序必填 |
| updatedAt | string | 更新时间UTC ISO8601 |

### Asset

私有媒体元数据，不返回永久云密钥或把objectKey用作访问凭据。

| 字段 | 类型 | 含义 |
|---|---|---|
| id | string | 业务主键，字符串传输 |
| purpose | string | ORIGINAL等资产用途；用户仅允许图片业务用途 |
| mimeType | string | 真实文件MIME，不以扩展名代替内容检查 |
| width | integer | 图片像素或几何相对宽，依所在DTO约定 |
| height | integer | 图片像素或几何相对高，依所在DTO约定 |
| sizeBytes | integer | 实际二进制字节数，正整数，限额由能力配置返回 |
| checksumSha256 | string | 64位十六进制内容摘要，声明仍需服务端校验 |
| status | string | 实体或任务状态，值域见对应DTO／状态机 |

### AssetAccess

短期签名地址；也可指向后端鉴权代理，不长期存储URL。

| 字段 | 类型 | 含义 |
|---|---|---|
| assetId | string | 通过服务端验证的资产ID |
| url | string | 短期签名或鉴权代理地址，不长期存储 |
| expiresAt | string | 到期UTC时间，URL／草稿均按各自类型 |
| contentType | string | 下载或访问响应MIME |

### ImageRevision

不可变版本，原图不会覆盖。

| 字段 | 类型 | 含义 |
|---|---|---|
| id | string | 业务主键，字符串传输 |
| photoId | string | 采集照片ID |
| parentRevisionId | string／null | 父图片版本，可空 |
| asset | object | 关联文件对象，字段见 MediaAssetDTO |
| asset.id | string | 业务主键，字符串传输 |
| asset.purpose | string | ORIGINAL等资产用途；用户仅允许图片业务用途 |
| asset.mimeType | string | 真实文件MIME，不以扩展名代替内容检查 |
| asset.width | integer | 图片像素或几何相对宽，依所在DTO约定 |
| asset.height | integer | 图片像素或几何相对高，依所在DTO约定 |
| asset.sizeBytes | integer | 实际二进制字节数，正整数，限额由能力配置返回 |
| asset.checksumSha256 | string | 64位十六进制内容摘要，声明仍需服务端校验 |
| asset.status | string | 实体或任务状态，值域见对应DTO／状态机 |
| operation | string | ORIGINAL／CORRECT／ENHANCE／ERASE／SPLIT／PAPER_PROCESS，按DTO上下文 |
| parameters | object | 工具参数快照，字段见参数契约 |
| parameters.schemaVersion | integer | 嵌套JSON结构版本，接口用camelCase，后端转换持久字段 |
| createdAt | string | 创建时间UTC ISO8601 |

### Photo

照片当前指针与原图引用，显示序号由有效照片顺序推导。

| 字段 | 类型 | 含义 |
|---|---|---|
| id | string | 业务主键，字符串传输 |
| batchId | string | 采集批次ID |
| studentId | string | 所属学生ID，必须属于登录账号 |
| source | string | CAMERA／ALBUM |
| sortOrder | integer | 展示顺序，非负整数 |
| originalAssetId | string | 不可变原始图资产 |
| currentRevisionId | string | 当前处理图版本指针 |
| uploadedAt | string | 服务端上传确认时间 |

### CaptureBatch

采集批次张数按有效照片统计，学生归属固定。

| 字段 | 类型 | 含义 |
|---|---|---|
| id | string | 业务主键，字符串传输 |
| studentId | string | 所属学生ID，必须属于登录账号 |
| mode | string | 批次SINGLE／MULTI；练习PRACTICE／REVIEW |
| status | string | 实体或任务状态，值域见对应DTO／状态机 |
| photoCount | integer | 当前批次有效照片数量 |
| finishedAt | string | 任务终态时间，可空 |
| createdAt | string | 创建时间UTC ISO8601 |
| updatedAt | string | 更新时间UTC ISO8601 |

### QuestionRegion

题框相对坐标绑定确切版本，裁切资产尚未生成时可空。

| 字段 | 类型 | 含义 |
|---|---|---|
| id | string | 业务主键，字符串传输 |
| photoId | string | 采集照片ID |
| revisionId | string | 题框／显示绑定的确切版本 |
| cropAssetId | string／null | 题框裁切图资产，尚未生成时为空 |
| geometry | object | schemaVersion=1归一化题框，详见模型geometry_json |
| geometry.schemaVersion | integer | 嵌套JSON结构版本，接口用camelCase，后端转换持久字段 |
| geometry.shape | string | RECTANGLE，后续POLYGON需明确实现能力 |
| geometry.coordinateSpace | string | NORMALIZED，相对指定图片版本 |
| geometry.x | number | 左上角相对横坐标，0到1，x+width不大于1 |
| geometry.y | number | 左上角相对纵坐标，0到1，y+height不大于1 |
| geometry.width | number | 图片像素或几何相对宽，依所在DTO约定 |
| geometry.height | number | 图片像素或几何相对高，依所在DTO约定 |
| sortOrder | integer | 展示顺序，非负整数 |
| origin | string | AUTO自动框或MANUAL手动框 |
| status | string | 实体或任务状态，值域见对应DTO／状态机 |

### ProcessingJobItem

每图子任务；阶段结果对象字段见ProcessingStep。

| 字段 | 类型 | 含义 |
|---|---|---|
| id | string | 业务主键，字符串传输 |
| photoId | string | 采集照片ID |
| inputRevisionId | string | 固定输入图片版本，PHOTO来源必填 |
| outputRevisionId | string／null | 成功输出版本，纯框题可空 |
| status | string | 实体或任务状态，值域见对应DTO／状态机 |
| attemptCount | integer | 执行尝试次数 |
| stepResults | array | 处理阶段结果数组，见ProcessingStep |
| errorCode | string／integer／null（任务错误码为string，调用日志为integer） | 业务错误码，可空 |
| errorMessage | string／null | 用户可读脱敏失败信息，可空 |

### ProcessingStep

一次阶段结果，RequestId按实际阶段返回；重试审计保留在调用日志。

| 字段 | 类型 | 含义 |
|---|---|---|
| operation | string | ORIGINAL／CORRECT／ENHANCE／ERASE／SPLIT／PAPER_PROCESS，按DTO上下文 |
| applied | boolean | 步骤是否实际应用 |
| success | boolean | 实际调用是否成功，不能直接当作计费依据 |
| provider | string | TENCENT_OCR／DASHSCOPE，按实际能力 |
| requestId | string | 批量保存请求UUID，配合clientId幂等 |
| fallbackUsed | boolean | 阶段是否明确降级回退 |
| errorCode | string／integer／null（任务错误码为string，调用日志为integer） | 业务错误码，可空 |
| errorMessage | string／null | 用户可读脱敏失败信息，可空 |

### ProcessingJob

任务异步返回；PAPER_PROCESS按增强→切题→去手写编排。

| 字段 | 类型 | 含义 |
|---|---|---|
| id | string | 业务主键，字符串传输 |
| studentId | string | 所属学生ID，必须属于登录账号 |
| batchId | string | 采集批次ID |
| operation | string | ORIGINAL／CORRECT／ENHANCE／ERASE／SPLIT／PAPER_PROCESS，按DTO上下文 |
| applyScope | string | CURRENT／ALL，目标集合在提交时冻结 |
| status | string | 实体或任务状态，值域见对应DTO／状态机 |
| traceId | string | 调用链追踪ID |
| items | array | 有序资源或请求项数组，结构按对应DTO解析 |
| items[].id | string | 业务主键，字符串传输 |
| items[].photoId | string | 采集照片ID |
| items[].inputRevisionId | string | 固定输入图片版本，PHOTO来源必填 |
| items[].outputRevisionId | string／null | 成功输出版本，纯框题可空 |
| items[].status | string | 实体或任务状态，值域见对应DTO／状态机 |
| items[].attemptCount | integer | 执行尝试次数 |
| items[].stepResults | array | 处理阶段结果数组，见ProcessingStep |
| items[].errorCode | string／integer／null（任务错误码为string，调用日志为integer） | 业务错误码，可空 |
| items[].errorMessage | string／null | 用户可读脱敏失败信息，可空 |
| pollAfterMs | integer | 建议轮询间隔，毫秒，尊重后端退避 |
| createdAt | string | 创建时间UTC ISO8601 |
| finishedAt | string／null | 任务终态时间，可空 |

### Entry

列表与详情使用同一错题类型；新记录必有主题，旧迁移记录topic可空。

| 字段 | 类型 | 含义 |
|---|---|---|
| id | string | 业务主键，字符串传输 |
| studentId | string | 所属学生ID，必须属于登录账号 |
| grade | integer | 年级1～12 |
| term | integer | 学期1上／2下 |
| subject | object | 科目对象，包含ID和名称 |
| subject.id | string | 业务主键，字符串传输 |
| subject.name | string | 显示名称 |
| subject.status | string | 实体或任务状态，值域见对应DTO／状态机 |
| topic | object | 主题对象，包含ID和名称 |
| topic.id | string | 业务主键，字符串传输 |
| topic.name | string | 显示名称 |
| topic.status | string | 实体或任务状态，值域见对应DTO／状态机 |
| errorType | object | 错误类型对象，包含ID和名称 |
| errorType.id | string | 业务主键，字符串传输 |
| errorType.name | string | 显示名称 |
| errorType.status | string | 实体或任务状态，值域见对应DTO／状态机 |
| imageAssetId | string | 冻结题图资产ID |
| thumbnailAssetId | string／null | 缩略图资产ID，可空 |
| answer | string | 参考答案字符串，空串清除 |
| remark | string | 用户备注字符串，空串清除 |
| masteryStatus | string | UNPRACTICED／PRACTICING／MASTERED |
| practiceCount | integer | 实际提交练习次数，不以打开页面计数 |
| correctCount | integer | 正确提交次数 |
| lastPracticedAt | string／null | 最近练习时间，可空 |
| version | integer | 乐观锁或资源版本；修改时传当前版本 |
| createdAt | string | 创建时间UTC ISO8601 |
| updatedAt | string | 更新时间UTC ISO8601 |

### Practice

提交结果才计数，correct是用户确认结果；自动判题另有能力协议。

| 字段 | 类型 | 含义 |
|---|---|---|
| id | string | 业务主键，字符串传输 |
| entryId | string | 错题ID |
| studentId | string | 所属学生ID，必须属于登录账号 |
| mode | string | 批次SINGLE／MULTI；练习PRACTICE／REVIEW |
| answerContent | string | 此次用户作答内容 |
| correct | boolean | 用户确认该次正误；不是自动判题结果 |
| durationSeconds | integer | 作答时长，非负整数，可空 |
| practicedAt | string | 实际提交时间 |
| entryVersion | integer | 错题当前版本，用于并发校验 |
| practiceCount | integer | 实际提交练习次数，不以打开页面计数 |
| correctCount | integer | 正确提交次数 |
| masteryStatus | string | UNPRACTICED／PRACTICING／MASTERED |

### DraftItem

来源 ENTRY／REGION／PHOTO 三选一；直接照片未分类时名称可空。

| 字段 | 类型 | 含义 |
|---|---|---|
| id | string | 业务主键，字符串传输 |
| sortOrder | integer | 展示顺序，非负整数 |
| sourceType | string | 对象上下文：来源项ENTRY／REGION／PHOTO；任务COLLECTION／CAPTURE／RANDOM |
| sourceId | string | sourceType对应的ENTRY／REGION／PHOTO ID |
| inputRevisionId | string／null | 固定输入图片版本，PHOTO来源必填 |
| imageAssetId | string | 冻结题图资产ID |
| subjectName | string | 科目名称快照，可空 |
| topicName | string | 主题名称快照，可空 |
| errorTypeName | string | 错误类型名称快照，可空 |

### PaperDraft

可选服务器草稿，未启用paperDraft时使用本地草稿及直接sources提交打印。

| 字段 | 类型 | 含义 |
|---|---|---|
| id | string | 业务主键，字符串传输 |
| studentId | string | 所属学生ID，必须属于登录账号 |
| sourceType | string | 对象上下文：来源项ENTRY／REGION／PHOTO；任务COLLECTION／CAPTURE／RANDOM |
| selectedTemplateId | string／null | 草稿／学生偏好预选模板，可空 |
| items | array | 有序资源或请求项数组，结构按对应DTO解析 |
| items[].id | string | 业务主键，字符串传输 |
| items[].sortOrder | integer | 展示顺序，非负整数 |
| items[].sourceType | string | 对象上下文：来源项ENTRY／REGION／PHOTO；任务COLLECTION／CAPTURE／RANDOM |
| items[].sourceId | string | sourceType对应的ENTRY／REGION／PHOTO ID |
| items[].inputRevisionId | string／null | 固定输入图片版本，PHOTO来源必填 |
| items[].imageAssetId | string | 冻结题图资产ID |
| items[].subjectName | string | 科目名称快照，可空 |
| items[].topicName | string | 主题名称快照，可空 |
| items[].errorTypeName | string | 错误类型名称快照，可空 |
| version | integer | 乐观锁或资源版本；修改时传当前版本 |
| status | string | 实体或任务状态，值域见对应DTO／状态机 |
| expiresAt | string | 到期UTC时间，URL／草稿均按各自类型 |

### Template

分类列表内的模板摘要，SVG可公开只读，题图不随之公开。

| 字段 | 类型 | 含义 |
|---|---|---|
| id | string | 业务主键，字符串传输 |
| categoryId | string | 模板分类ID |
| code | string | 稳定代码；微信登录时为一次性wx.login code |
| name | string | 显示名称 |
| status | string | 实体或任务状态，值域见对应DTO／状态机 |
| sortOrder | integer | 展示顺序，非负整数 |
| currentVersionId | string | 该模板当前已发布版本ID |
| previewSvgAssetId | string | 已校验系统SVG资产ID |
| slotsPerPage | integer | 每页目标题框数，正整数 |

### TemplateCategory

每类一行，可横向分页；分类不写死为A4／B5枚举。

| 字段 | 类型 | 含义 |
|---|---|---|
| id | string | 业务主键，字符串传输 |
| code | string | 稳定代码；微信登录时为一次性wx.login code |
| name | string | 显示名称 |
| sortOrder | integer | 展示顺序，非负整数 |
| templates | array | 分类首屏模板摘要数组，见Template |
| templates[].id | string | 业务主键，字符串传输 |
| templates[].categoryId | string | 模板分类ID |
| templates[].code | string | 稳定代码；微信登录时为一次性wx.login code |
| templates[].name | string | 显示名称 |
| templates[].status | string | 实体或任务状态，值域见对应DTO／状态机 |
| templates[].sortOrder | integer | 展示顺序，非负整数 |
| templates[].currentVersionId | string | 该模板当前已发布版本ID |
| templates[].previewSvgAssetId | string | 已校验系统SVG资产ID |
| templates[].slotsPerPage | integer | 每页目标题框数，正整数 |
| nextTemplateCursor | string／null | 同分类下一页模板游标，可空 |
| hasMoreTemplates | boolean | 当前分类是否还有更多模板 |

### TemplateVersion

用户端只返回排版说明／预览；代码全文仅管理员可读。layout完整schema沿用模型文档，示例slots仅示意，不可用作发布输入。

| 字段 | 类型 | 含义 |
|---|---|---|
| id | string | 业务主键，字符串传输 |
| templateId | string | 模板稳定ID |
| versionNo | integer | 模板内部递增版本号 |
| status | string | 实体或任务状态，值域见对应DTO／状态机 |
| paperWidthMm | string | 十进制毫米字符串，必须大于0 |
| paperHeightMm | string | 十进制毫米字符串，必须大于0 |
| orientation | string | PORTRAIT／LANDSCAPE |
| slotsPerPage | integer | 每页目标题框数，正整数 |
| layout | object | 布局结构，与数据库layout_json映射 |
| layout.schemaVersion | integer | 嵌套JSON结构版本，接口用camelCase，后端转换持久字段 |
| layout.unit | string | 布局长度单位，固定mm |
| layout.slots | array | 题目区域配置数组，渲染时按区域顺序放入题目 |
| previewSvgAssetId | string | 已校验系统SVG资产ID |
| rendererType | string | DECLARATIVE／HTML_CSS等受控引擎类型 |
| rendererVersion | string | 固定渲染引擎版本 |
| codeHash | string／null | 代码内容SHA256，可空 |
| publishedAt | string | 模板发布时间，可空 |

### PrintItem

内容快照由后端从可信来源生成，客户端不得自己提交任意对象键。

| 字段 | 类型 | 含义 |
|---|---|---|
| id | string | 业务主键，字符串传输 |
| sortOrder | integer | 展示顺序，非负整数 |
| sourceType | string | 对象上下文：来源项ENTRY／REGION／PHOTO；任务COLLECTION／CAPTURE／RANDOM |
| sourceId | string | sourceType对应的ENTRY／REGION／PHOTO ID |
| imageAssetId | string | 冻结题图资产ID |
| contentSnapshot | object | 后端由可信来源生成的题目快照 |
| contentSnapshot.schemaVersion | integer | 嵌套JSON结构版本，接口用camelCase，后端转换持久字段 |
| contentSnapshot.subject | object | 科目对象，包含ID和名称 |
| contentSnapshot.subject.id | string | 业务主键，字符串传输 |
| contentSnapshot.subject.name | string | 显示名称 |
| contentSnapshot.topic | object | 主题对象，包含ID和名称 |
| contentSnapshot.topic.id | string | 业务主键，字符串传输 |
| contentSnapshot.topic.name | string | 显示名称 |
| contentSnapshot.errorType | object | 错误类型对象，包含ID和名称 |
| contentSnapshot.errorType.id | string | 业务主键，字符串传输 |
| contentSnapshot.errorType.name | string | 显示名称 |

### PrintTask

READY只表示PDF可用，PRINT_CONFIRMED需用户明确确认或服务回执。

| 字段 | 类型 | 含义 |
|---|---|---|
| id | string | 业务主键，字符串传输 |
| studentId | string | 所属学生ID，必须属于登录账号 |
| templateVersionId | string | 明确发布版本ID，不能仅传模板ID |
| templateName | string | 创建打印任务时的模板名快照 |
| sourceType | string | 对象上下文：来源项ENTRY／REGION／PHOTO；任务COLLECTION／CAPTURE／RANDOM |
| status | string | 实体或任务状态，值域见对应DTO／状态机 |
| itemCount | integer | 实际题目数量，必须大于0 |
| copies | integer | 打印份数1～能力配置上限 |
| pageCount | integer／null | 实际PDF页数，生成前为空 |
| outputPdfAssetId | string／null | 已生成PDF资产，生成前为空 |
| errorCode | string／integer／null（任务错误码为string，调用日志为integer） | 业务错误码，可空 |
| errorMessage | string／null | 用户可读脱敏失败信息，可空 |
| createdAt | string | 创建时间UTC ISO8601 |
| finishedAt | string／null | 任务终态时间，可空 |
| printConfirmedAt | string／null | 用户明确确认或回执时间，可空 |
| pollAfterMs | integer | 建议轮询间隔，毫秒，尊重后端退避 |

### AgentResult

目标Agent返回业务结果，不把缓存subjectKey当科目ID；模型与提示词变化后不复用旧结果。

| 字段 | 类型 | 含义 |
|---|---|---|
| id | string | 业务主键，字符串传输 |
| entryId | string | 错题ID |
| resultType | string | EXPLAIN／ANALOGY |
| status | string | 实体或任务状态，值域见对应DTO／状态机 |
| cached | boolean | 是否命中输入版本缓存 |
| provider | string | TENCENT_OCR／DASHSCOPE，按实际能力 |
| model | string | 实际配置的模型名 |
| inputContentHash | string | 当前题图与题目输入版本的摘要 |
| result | object | 结构化模型结果，不自动修改错题 |
| result.schemaVersion | integer | 嵌套JSON结构版本，接口用camelCase，后端转换持久字段 |
| result.text | string | 安全展示文本，不解释执行为脚本 |
| traceId | string | 调用链追踪ID |
| createdAt | string | 创建时间UTC ISO8601 |

### Feedback

账号级反馈；adminNote为内部字段，不返回用户。

| 字段 | 类型 | 含义 |
|---|---|---|
| id | string | 业务主键，字符串传输 |
| content | string | 意见内容1～1000字符 |
| status | string | 实体或任务状态，值域见对应DTO／状态机 |
| createdAt | string | 创建时间UTC ISO8601 |

## 4. 接口逐项定义与请求响应样例

### 身份与公共信息

<a id="auth-01"></a>

#### AUTH-01 · 获取图形验证码

`GET /api/auth/captcha`

**状态／认证：**已有／公开。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| 无 | — | — | — | 不需要请求参数或请求体 |

**规则与失败边界：**已有接口；一次即废，失败也必须换验证码。不是短信验证码。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| captchaId | string | 服务端图形验证码挑战ID |
| imageBase64 | string | 已有图形验证码PNG data URI，不是题图长期存储 |
| expiresInSeconds | integer | 验证码有效秒数 |

**请求样例：**

```http
GET /api/auth/captcha HTTP/1.1
Host: api.example.test
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "captchaId": "captcha-id",
    "imageBase64": "data:image/png;base64,iVBOR...",
    "expiresInSeconds": 300
  }
}
```

<a id="auth-02"></a>

#### AUTH-02 · 协商小程序接口能力

`GET /api/mini/v1/capabilities`

**状态／认证：**已实现／公开。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| 无 | — | — | — | 不需要请求参数或请求体 |

**规则与失败边界：**旧服务返回404时视为不支持；本版本返回真实配置能力。

**输出data：**Capabilities（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
GET /api/mini/v1/capabilities HTTP/1.1
Host: api.example.test
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "contractVersion": "1.0.1",
    "serverVersion": "1.22.0",
    "miniappApiSupported": true,
    "studentScopeSupported": true,
    "features": {
      "capture": true,
      "processing": false,
      "printing": true,
      "paperDraft": true,
      "feedback": true,
      "wechatLogin": false
    },
    "limits": {
      "maxUploadBytes": 20971520,
      "maxPhotosPerBatch": 30,
      "maxBatchItems": 100,
      "maxPrintCopies": 20
    }
  }
}
```

<a id="auth-03"></a>

#### AUTH-03 · 交换微信身份会话

`POST /api/mini/v1/auth/wechat`

**状态／认证：**已实现／公开。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| code | Body | string | 是 | 稳定代码；微信登录时为一次性wx.login code |
| clientLabel | Body | string | 否 | 当前客户端展示名称 |

**规则与失败边界：**微信code仅一次使用；未绑定返回短期身份令牌，不具备账号资源权限。绑定时仍明确执行AUTH-04；本接口不自动踢出账号会话。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| identityToken | string | 短期身份令牌，不能访问账号资源 |
| identityExpiresIn | integer | 身份令牌有效秒数 |
| bound | boolean | 微信身份是否已绑定账号 |
| account | object／null | 账号资料对象，未绑定时null，见Account |

**请求样例：**

```http
POST /api/mini/v1/auth/wechat HTTP/1.1
Host: api.example.test
Content-Type: application/json

{
  "code": "wx-login-code",
  "clientLabel": "拾星微信小程序"
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "identityToken": "opaque-identity-token",
    "identityExpiresIn": 300,
    "bound": false,
    "account": null
  }
}
```

<a id="auth-04"></a>

#### AUTH-04 · 微信身份绑定会员账号并登录

`POST /api/mini/v1/auth/member-bind`

**状态／认证：**已实现／身份令牌＋验证码／账号凭据。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| identityToken | Body | string | 是 | 短期身份令牌，不能访问账号资源 |
| phone | Body | string | 是 | 11位手机号 |
| password | Body | string | 是 | 密码，6～64字符，不保存明文 |
| captchaId | Body | string | 是 | 服务端图形验证码挑战ID |
| captchaCode | Body | string | 是 | 图片中字符，不是短信码 |
| clientLabel | Body | string | 否 | 当前客户端展示名称 |

**规则与失败边界：**校验手机号密码后原子绑定，既有不同账号绑定返回冲突；沿用现有单会话登录／刷新政策，普通和到期用户不因memberActive=false被当成密码错误。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| token | string | 账号访问令牌，Authorization Bearer使用 |
| expiresIn | integer | 访问令牌有效秒数 |
| refreshToken | string | 仅用于刷新，不放业务Authorization |
| refreshExpiresIn | integer | 刷新令牌有效秒数 |
| account | object | 账号资料对象，未绑定时null，见Account |
| account.id | string | 业务主键，字符串传输 |
| account.memberName | string | 服务端会员显示名，独立于学生昵称 |
| account.memberNo | string | 会员号，不作为鉴权凭据 |
| account.memberExpireAt | string | 会员到期日DATE，可空 |
| account.memberActive | boolean | 会员权益是否有效，不等同AI授权 |
| account.role | string | USER／ADMIN |
| account.aiEnabled | boolean | 独立AI开关 |
| account.lastStudentId | string | 最近使用学生偏好，不代替请求studentId |

**请求样例：**

```http
POST /api/mini/v1/auth/member-bind HTTP/1.1
Host: api.example.test
Content-Type: application/json

{
  "identityToken": "opaque-identity-token",
  "phone": "13911112222",
  "password": "example-password",
  "captchaId": "captcha-id",
  "captchaCode": "7K3P",
  "clientLabel": "拾星小程序"
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "token": "access-token",
    "expiresIn": 7200,
    "refreshToken": "refresh-token",
    "refreshExpiresIn": 2592000,
    "account": {
      "id": "101",
      "memberName": "拾星家长",
      "memberNo": "SX000101",
      "memberExpireAt": "2026-12-31",
      "memberActive": true,
      "role": "USER",
      "aiEnabled": true,
      "lastStudentId": "student-uuid"
    }
  }
}
```

<a id="auth-05"></a>

#### AUTH-05 · 登录已绑定微信账号

`POST /api/mini/v1/auth/login`

**状态／认证：**已实现／已绑定身份令牌。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| identityToken | Body | string | 是 | 短期身份令牌，不能访问账号资源 |
| clientLabel | Body | string | 否 | 当前客户端展示名称 |

**规则与失败边界：**AUTH-03返回bound=true后调用；生成账号会话并遵守单会话规则，绝不接受客户端userId选择账号。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| token | string | 账号访问令牌，Authorization Bearer使用 |
| expiresIn | integer | 访问令牌有效秒数 |
| refreshToken | string | 仅用于刷新，不放业务Authorization |
| refreshExpiresIn | integer | 刷新令牌有效秒数 |
| account | object | 账号资料对象，未绑定时null，见Account |
| account.id | string | 业务主键，字符串传输 |
| account.memberName | string | 服务端会员显示名，独立于学生昵称 |
| account.memberNo | string | 会员号，不作为鉴权凭据 |
| account.memberExpireAt | string | 会员到期日DATE，可空 |
| account.memberActive | boolean | 会员权益是否有效，不等同AI授权 |
| account.role | string | USER／ADMIN |
| account.aiEnabled | boolean | 独立AI开关 |
| account.lastStudentId | string | 最近使用学生偏好，不代替请求studentId |

**请求样例：**

```http
POST /api/mini/v1/auth/login HTTP/1.1
Host: api.example.test
Content-Type: application/json

{
  "identityToken": "opaque-identity-token",
  "clientLabel": "拾星小程序"
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "token": "access-token",
    "expiresIn": 7200,
    "refreshToken": "refresh-token",
    "refreshExpiresIn": 2592000,
    "account": {
      "id": "101",
      "memberName": "拾星家长",
      "memberNo": "SX000101",
      "memberExpireAt": "2026-12-31",
      "memberActive": true,
      "role": "USER",
      "aiEnabled": true,
      "lastStudentId": "student-uuid"
    }
  }
}
```

<a id="auth-06"></a>

#### AUTH-06 · 刷新账号令牌

`POST /api/auth/refresh`

**状态／认证：**已有／刷新令牌。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| refreshToken | Body | string | 是 | 仅用于刷新，不放业务Authorization |

**规则与失败边界：**现有120秒轮换宽限期；4011禁止再刷新。真实默认有效期以服务端配置为准。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| token | string | 账号访问令牌，Authorization Bearer使用 |
| expiresIn | integer | 访问令牌有效秒数 |
| refreshToken | string | 仅用于刷新，不放业务Authorization |
| refreshExpiresIn | integer | 刷新令牌有效秒数 |

**请求样例：**

```http
POST /api/auth/refresh HTTP/1.1
Host: api.example.test
Content-Type: application/json

{
  "refreshToken": "refresh-token"
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "token": "new-access-token",
    "expiresIn": 7200,
    "refreshToken": "new-refresh-token",
    "refreshExpiresIn": 2592000
  }
}
```

<a id="auth-07"></a>

#### AUTH-07 · 读取账号、权益及最近学生

`GET /api/mini/v1/me`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| 无 | — | — | — | 不需要请求参数或请求体 |

**规则与失败边界：**此返回不是现有/api/auth/me的响应升级；旧me无memberName和学生信息。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| account | object | 账号资料对象，未绑定时null，见Account |
| account.id | string | 业务主键，字符串传输 |
| account.memberName | string | 服务端会员显示名，独立于学生昵称 |
| account.memberNo | string | 会员号，不作为鉴权凭据 |
| account.memberExpireAt | string | 会员到期日DATE，可空 |
| account.memberActive | boolean | 会员权益是否有效，不等同AI授权 |
| account.role | string | USER／ADMIN |
| account.aiEnabled | boolean | 独立AI开关 |
| account.lastStudentId | string | 最近使用学生偏好，不代替请求studentId |
| capabilities | object | 服务端当前账号能力，不由客户端会员标签推断 |
| capabilities.aiEnabled | boolean | 独立AI开关 |
| capabilities.canReadOwnData | boolean | 有效账号是否可读本人数据，不等于会员未到期 |
| lastStudentId | string | 最近使用学生偏好，不代替请求studentId |

**请求样例：**

```http
GET /api/mini/v1/me HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "account": {
      "id": "101",
      "memberName": "拾星家长",
      "memberNo": "SX000101",
      "memberExpireAt": "2026-12-31",
      "memberActive": true,
      "role": "USER",
      "aiEnabled": true,
      "lastStudentId": "student-uuid"
    },
    "capabilities": {
      "aiEnabled": true,
      "canReadOwnData": true
    },
    "lastStudentId": "student-uuid"
  }
}
```

<a id="auth-08"></a>

#### AUTH-08 · 退出账号会话

`POST /api/auth/logout`

**状态／认证：**已有／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| 无 | — | — | — | 不需要请求参数或请求体 |

**规则与失败边界：**返回data无数据时可能省略；退出只撤销会话，不解绑微信或删除学生。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| 无 | object | 无数据返回{}，已有接口可省略data |

**请求样例：**

```http
POST /api/auth/logout HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

<a id="auth-09"></a>

#### AUTH-09 · 修改账号密码

`POST /api/auth/password`

**状态／认证：**已有／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| oldPassword | Body | string | 是 | 当前会员密码 |
| newPassword | Body | string | 是 | 新会员密码，规则同现有修改密码接口 |

**规则与失败边界：**新密码6～64位；成功后清令牌，重新登录。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| 无 | object | 无数据返回{}，已有接口可省略data |

**请求样例：**

```http
POST /api/auth/password HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
Content-Type: application/json

{
  "oldPassword": "old-password",
  "newPassword": "new-password"
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

<a id="public-01"></a>

#### PUBLIC-01 · 读取使用帮助或关于内容

`GET /api/mini/v1/content/{key}`

**状态／认证：**已实现／公开。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| key | Path | string | 是 | help／about |

**规则与失败边界：**key仅help／about；静态配置即可，无须新增内容表。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| key | string | 静态内容help／about |
| title | string | 内容显示标题 |
| version | string | 乐观锁或资源版本；修改时传当前版本 |
| blocks | array | 安全内容块数组 |
| blocks[].type | string | 静态块类型，如paragraph |
| blocks[].text | string | 安全展示文本，不解释执行为脚本 |

**请求样例：**

```http
GET /api/mini/v1/content/help HTTP/1.1
Host: api.example.test
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "key": "help",
    "title": "使用帮助",
    "version": "1",
    "blocks": [
      {
        "type": "paragraph",
        "text": "拍照后可保存到错题集或直接打印。"
      }
    ]
  }
}
```

### 学生与分类

<a id="stu-01"></a>

#### STU-01 · 学生卡片列表

`GET /api/mini/v1/students`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| status | Query | string | 否 | ACTIVE／ARCHIVED；默认ACTIVE |

**输出data：**Student（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
GET /api/mini/v1/students?status=ACTIVE HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": "student-uuid",
        "nickname": "星星",
        "avatarAssetId": null,
        "grade": 5,
        "term": 1,
        "status": "ACTIVE",
        "sortOrder": 0,
        "createdAt": "2026-10-10T08:00:00.000Z",
        "updatedAt": "2026-10-10T08:00:00.000Z"
      }
    ],
    "lastStudentId": "student-uuid"
  }
}
```

<a id="stu-02"></a>

#### STU-02 · 增加学生

`POST /api/mini/v1/students`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| nickname | Body | string | 是 | 学生昵称 |
| grade | Body | integer | 是 | 年级1～12 |
| term | Body | integer | 是 | 学期1上／2下 |

**规则与失败边界：**昵称1～64字符；允许同名，grade1～12、term1／2。

**输出data：**Student（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
POST /api/mini/v1/students HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
Content-Type: application/json

{
  "nickname": "星星",
  "grade": 5,
  "term": 1
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "student-uuid",
    "nickname": "星星",
    "avatarAssetId": null,
    "grade": 5,
    "term": 1,
    "status": "ACTIVE",
    "sortOrder": 0,
    "createdAt": "2026-10-10T08:00:00.000Z",
    "updatedAt": "2026-10-10T08:00:00.000Z"
  }
}
```

<a id="stu-03"></a>

#### STU-03 · 查看学生资料

`GET /api/mini/v1/students/{studentId}`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |

**输出data：**Student（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
GET /api/mini/v1/students/student-uuid HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "student-uuid",
    "nickname": "星星",
    "avatarAssetId": null,
    "grade": 5,
    "term": 1,
    "status": "ACTIVE",
    "sortOrder": 0,
    "createdAt": "2026-10-10T08:00:00.000Z",
    "updatedAt": "2026-10-10T08:00:00.000Z"
  }
}
```

<a id="stu-04"></a>

#### STU-04 · 编辑学生资料或归档

`PATCH /api/mini/v1/students/{studentId}`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| nickname | Body | string | 否 | 学生昵称 |
| grade | Body | integer | 否 | 年级1～12 |
| term | Body | integer | 否 | 学期1上／2下 |
| status | Body | string | 否 | 实体或任务状态，值域见对应DTO／状态机 |

**规则与失败边界：**至少一个字段，status仅ACTIVE／ARCHIVED；修改年级不改历史错题快照，归档不删历史。

**输出data：**Student（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
PATCH /api/mini/v1/students/student-uuid HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
Content-Type: application/json

{
  "nickname": "星星",
  "grade": 6,
  "term": 1,
  "status": "ACTIVE"
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "student-uuid",
    "nickname": "星星",
    "avatarAssetId": null,
    "grade": 5,
    "term": 1,
    "status": "ACTIVE",
    "sortOrder": 0,
    "createdAt": "2026-10-10T08:00:00.000Z",
    "updatedAt": "2026-10-10T08:00:00.000Z"
  }
}
```

<a id="stu-05"></a>

#### STU-05 · 记录最近使用学生

`PUT /api/mini/v1/me/current-student`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Body | string | 是 | 所属学生ID，必须属于登录账号 |

**规则与失败边界：**只是偏好，其他接口仍显式studentId；归档学生不能设为当前。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| lastStudentId | string | 最近使用学生偏好，不代替请求studentId |

**请求样例：**

```http
PUT /api/mini/v1/me/current-student HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
Content-Type: application/json

{
  "studentId": "student-uuid"
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "lastStudentId": "student-uuid"
  }
}
```

<a id="stu-06"></a>

#### STU-06 · 读取首页学生错题摘要

`GET /api/mini/v1/students/{studentId}/summary`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| recentLimit | Query | integer | 否 | 消灭错题展示数量，1～20 |

**规则与失败边界：**派生查询，不建首页汇总表；不恢复我的成长卡片。

**输出data：**Student、Entry（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
GET /api/mini/v1/students/student-uuid/summary?recentLimit=3 HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "student": {
      "id": "student-uuid",
      "nickname": "星星",
      "avatarAssetId": null,
      "grade": 5,
      "term": 1,
      "status": "ACTIVE",
      "sortOrder": 0,
      "createdAt": "2026-10-10T08:00:00.000Z",
      "updatedAt": "2026-10-10T08:00:00.000Z"
    },
    "entryCount": 2,
    "recentEntries": [
      {
        "id": "entry-uuid",
        "studentId": "student-uuid",
        "grade": 5,
        "term": 1,
        "subject": {
          "id": "201",
          "name": "数学",
          "status": "ACTIVE"
        },
        "topic": {
          "id": "301",
          "name": "分数加减法",
          "status": "ACTIVE"
        },
        "errorType": {
          "id": "error-uuid",
          "name": "计算错误",
          "status": "ACTIVE"
        },
        "imageAssetId": "asset-uuid",
        "thumbnailAssetId": null,
        "answer": "参考答案",
        "remark": "注意通分",
        "masteryStatus": "UNPRACTICED",
        "practiceCount": 0,
        "correctCount": 0,
        "lastPracticedAt": null,
        "version": 0,
        "createdAt": "2026-10-10T08:00:00.000Z",
        "updatedAt": "2026-10-10T08:00:00.000Z"
      }
    ]
  }
}
```

<a id="sub-01"></a>

#### SUB-01 · 查询科目列表

`GET /api/mini/v1/students/{studentId}/subjects`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| status | Query | string | 否 | ACTIVE／ARCHIVED／ALL，默认ACTIVE |

**输出data：**Subject（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
GET /api/mini/v1/students/student-uuid/subjects?status=ACTIVE HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": "201",
        "studentId": "student-uuid",
        "name": "数学",
        "systemKey": "MATH",
        "status": "ACTIVE",
        "sortOrder": 0,
        "revision": 0,
        "topicCount": 1,
        "entryCount": 2,
        "updatedAt": "2026-10-10T08:00:00.000Z"
      }
    ]
  }
}
```

<a id="sub-02"></a>

#### SUB-02 · 增加科目

`POST /api/mini/v1/students/{studentId}/subjects`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| name | Body | string | 是 | 显示名称 |

**规则与失败边界：**同学生规范化名称去重；错误类型code由后端生成，未指定兼容大类默认OTHER。

**输出data：**Subject（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
POST /api/mini/v1/students/student-uuid/subjects HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "数学"
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "201",
    "studentId": "student-uuid",
    "name": "数学",
    "systemKey": "MATH",
    "status": "ACTIVE",
    "sortOrder": 0,
    "revision": 0,
    "topicCount": 1,
    "entryCount": 2,
    "updatedAt": "2026-10-10T08:00:00.000Z"
  }
}
```

<a id="sub-03"></a>

#### SUB-03 · 更新／停用科目

`PATCH /api/mini/v1/students/{studentId}/subjects/{id}`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| id | Path | string | 是 | 业务主键，字符串传输 |
| name | Body | string | 否 | 显示名称 |
| status | Body | string | 否 | 实体或任务状态，值域见对应DTO／状态机 |
| revision | Body | integer | 是 | 分类修订号；修改／排序必填 |

**规则与失败边界：**revision必填，名称／状态至少一项。科目旧状态兼容DISABLED；目标API将停用映射ARCHIVED，适配已有实体10位状态字段。

**输出data：**Subject（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
PATCH /api/mini/v1/students/student-uuid/subjects/resource-id HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "数学",
  "status": "ACTIVE",
  "revision": 0
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "201",
    "studentId": "student-uuid",
    "name": "数学",
    "systemKey": "MATH",
    "status": "ACTIVE",
    "sortOrder": 0,
    "revision": 0,
    "topicCount": 1,
    "entryCount": 2,
    "updatedAt": "2026-10-10T08:00:00.000Z"
  }
}
```

<a id="sub-04"></a>

#### SUB-04 · 调整科目顺序

`PUT /api/mini/v1/students/{studentId}/subjects/order`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| items | Body | array | 是 | 有序资源或请求项数组，结构按对应DTO解析 |
| items[].id | Body | string | 是 | 业务主键，字符串传输 |
| items[].revision | Body | integer | 是 | 分类修订号；修改／排序必填 |

**规则与失败边界：**当前scope全量ID排列，revision逐项必填，原子更新；不允许漏项或他人ID。

**输出data：**Subject（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
PUT /api/mini/v1/students/student-uuid/subjects/order HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
Content-Type: application/json

{
  "items": [
    {
      "id": "201",
      "revision": 0
    }
  ]
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": "201",
        "studentId": "student-uuid",
        "name": "数学",
        "systemKey": "MATH",
        "status": "ACTIVE",
        "sortOrder": 0,
        "revision": 0,
        "topicCount": 1,
        "entryCount": 2,
        "updatedAt": "2026-10-10T08:00:00.000Z"
      }
    ]
  }
}
```

<a id="sub-05"></a>

#### SUB-05 · 删除无引用科目

`DELETE /api/mini/v1/students/{studentId}/subjects/{id}`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| id | Path | string | 是 | 业务主键，字符串传输 |

**规则与失败边界：**存在引用返回4092并建议停用；不级联删除错题。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| 无 | object | 无数据返回{}，已有接口可省略data |

**请求样例：**

```http
DELETE /api/mini/v1/students/student-uuid/subjects/resource-id HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

<a id="err-01"></a>

#### ERR-01 · 查询错误类型列表

`GET /api/mini/v1/students/{studentId}/error-types`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| status | Query | string | 否 | ACTIVE／ARCHIVED／ALL，默认ACTIVE |

**输出data：**ErrorType（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
GET /api/mini/v1/students/student-uuid/error-types?status=ACTIVE HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": "error-uuid",
        "studentId": "student-uuid",
        "code": "CUSTOM_CALC",
        "name": "计算错误",
        "drawGroupCode": "CARELESS",
        "status": "ACTIVE",
        "sortOrder": 0,
        "revision": 0,
        "updatedAt": "2026-10-10T08:00:00.000Z"
      }
    ]
  }
}
```

<a id="err-02"></a>

#### ERR-02 · 增加错误类型

`POST /api/mini/v1/students/{studentId}/error-types`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| name | Body | string | 是 | 显示名称 |
| drawGroupCode | Body | string | 否 | CARELESS／UNFAMILIAR／CONCEPT／OTHER，旧四类兼容映射 |

**规则与失败边界：**同学生规范化名称去重；错误类型code由后端生成，未指定兼容大类默认OTHER。

**输出data：**ErrorType（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
POST /api/mini/v1/students/student-uuid/error-types HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "计算错误",
  "drawGroupCode": "CARELESS"
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "error-uuid",
    "studentId": "student-uuid",
    "code": "CUSTOM_CALC",
    "name": "计算错误",
    "drawGroupCode": "CARELESS",
    "status": "ACTIVE",
    "sortOrder": 0,
    "revision": 0,
    "updatedAt": "2026-10-10T08:00:00.000Z"
  }
}
```

<a id="err-03"></a>

#### ERR-03 · 更新／停用错误类型

`PATCH /api/mini/v1/students/{studentId}/error-types/{id}`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| id | Path | string | 是 | 业务主键，字符串传输 |
| name | Body | string | 否 | 显示名称 |
| status | Body | string | 否 | 实体或任务状态，值域见对应DTO／状态机 |
| revision | Body | integer | 是 | 分类修订号；修改／排序必填 |

**规则与失败边界：**revision必填，名称／状态至少一项。科目旧状态兼容DISABLED；目标API将停用映射ARCHIVED，适配已有实体10位状态字段。

**输出data：**ErrorType（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
PATCH /api/mini/v1/students/student-uuid/error-types/resource-id HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "计算错误",
  "status": "ACTIVE",
  "revision": 0
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "error-uuid",
    "studentId": "student-uuid",
    "code": "CUSTOM_CALC",
    "name": "计算错误",
    "drawGroupCode": "CARELESS",
    "status": "ACTIVE",
    "sortOrder": 0,
    "revision": 0,
    "updatedAt": "2026-10-10T08:00:00.000Z"
  }
}
```

<a id="err-04"></a>

#### ERR-04 · 调整错误类型顺序

`PUT /api/mini/v1/students/{studentId}/error-types/order`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| items | Body | array | 是 | 有序资源或请求项数组，结构按对应DTO解析 |
| items[].id | Body | string | 是 | 业务主键，字符串传输 |
| items[].revision | Body | integer | 是 | 分类修订号；修改／排序必填 |

**规则与失败边界：**当前scope全量ID排列，revision逐项必填，原子更新；不允许漏项或他人ID。

**输出data：**ErrorType（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
PUT /api/mini/v1/students/student-uuid/error-types/order HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
Content-Type: application/json

{
  "items": [
    {
      "id": "error-uuid",
      "revision": 0
    }
  ]
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": "error-uuid",
        "studentId": "student-uuid",
        "code": "CUSTOM_CALC",
        "name": "计算错误",
        "drawGroupCode": "CARELESS",
        "status": "ACTIVE",
        "sortOrder": 0,
        "revision": 0,
        "updatedAt": "2026-10-10T08:00:00.000Z"
      }
    ]
  }
}
```

<a id="err-05"></a>

#### ERR-05 · 删除无引用错误类型

`DELETE /api/mini/v1/students/{studentId}/error-types/{id}`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| id | Path | string | 是 | 业务主键，字符串传输 |

**规则与失败边界：**存在引用返回4092并建议停用；不级联删除错题。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| 无 | object | 无数据返回{}，已有接口可省略data |

**请求样例：**

```http
DELETE /api/mini/v1/students/student-uuid/error-types/resource-id HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

<a id="top-01"></a>

#### TOP-01 · 读取科目主题

`GET /api/mini/v1/students/{studentId}/subjects/{subjectId}/topics`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| subjectId | Path | string | 是 | 科目ID，必须归属本学生 |
| status | Query | string | 否 | ACTIVE／ARCHIVED／ALL |

**输出data：**Topic（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
GET /api/mini/v1/students/student-uuid/subjects/subject-uuid/topics?status=ACTIVE HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": "301",
        "subjectId": "201",
        "studentId": "student-uuid",
        "name": "分数加减法",
        "status": "ACTIVE",
        "sortOrder": 0,
        "revision": 0,
        "entryCount": 2,
        "updatedAt": "2026-10-10T08:00:00.000Z"
      }
    ]
  }
}
```

<a id="top-02"></a>

#### TOP-02 · 增加科目主题

`POST /api/mini/v1/students/{studentId}/subjects/{subjectId}/topics`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| subjectId | Path | string | 是 | 科目ID，必须归属本学生 |
| name | Body | string | 是 | 显示名称 |

**输出data：**Topic（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
POST /api/mini/v1/students/student-uuid/subjects/subject-uuid/topics HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "分数加减法"
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "301",
    "subjectId": "201",
    "studentId": "student-uuid",
    "name": "分数加减法",
    "status": "ACTIVE",
    "sortOrder": 0,
    "revision": 0,
    "entryCount": 2,
    "updatedAt": "2026-10-10T08:00:00.000Z"
  }
}
```

<a id="top-03"></a>

#### TOP-03 · 编辑／停用主题

`PATCH /api/mini/v1/students/{studentId}/topics/{id}`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| id | Path | string | 是 | 业务主键，字符串传输 |
| name | Body | string | 否 | 显示名称 |
| status | Body | string | 否 | 实体或任务状态，值域见对应DTO／状态机 |
| revision | Body | integer | 是 | 分类修订号；修改／排序必填 |

**规则与失败边界：**同科目名称去重；停用映射与科目一致。

**输出data：**Topic（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
PATCH /api/mini/v1/students/student-uuid/topics/resource-id HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "分数加减法",
  "status": "ACTIVE",
  "revision": 0
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "301",
    "subjectId": "201",
    "studentId": "student-uuid",
    "name": "分数加减法",
    "status": "ACTIVE",
    "sortOrder": 0,
    "revision": 0,
    "entryCount": 2,
    "updatedAt": "2026-10-10T08:00:00.000Z"
  }
}
```

<a id="top-04"></a>

#### TOP-04 · 调整主题顺序

`PUT /api/mini/v1/students/{studentId}/subjects/{subjectId}/topics/order`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| subjectId | Path | string | 是 | 科目ID，必须归属本学生 |
| items | Body | array | 是 | 有序资源或请求项数组，结构按对应DTO解析 |
| items[].id | Body | string | 是 | 业务主键，字符串传输 |
| items[].revision | Body | integer | 是 | 分类修订号；修改／排序必填 |

**规则与失败边界：**该科目全量原子排序。

**输出data：**Topic（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
PUT /api/mini/v1/students/student-uuid/subjects/subject-uuid/topics/order HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
Content-Type: application/json

{
  "items": [
    {
      "id": "301",
      "revision": 0
    }
  ]
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": "301",
        "subjectId": "201",
        "studentId": "student-uuid",
        "name": "分数加减法",
        "status": "ACTIVE",
        "sortOrder": 0,
        "revision": 0,
        "entryCount": 2,
        "updatedAt": "2026-10-10T08:00:00.000Z"
      }
    ]
  }
}
```

<a id="top-05"></a>

#### TOP-05 · 删除无引用主题

`DELETE /api/mini/v1/students/{studentId}/topics/{id}`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| id | Path | string | 是 | 业务主键，字符串传输 |

**规则与失败边界：**有关联错题返回4092，改为停用。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| 无 | object | 无数据返回{}，已有接口可省略data |

**请求样例：**

```http
DELETE /api/mini/v1/students/student-uuid/topics/resource-id HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

<a id="pref-01"></a>

#### PREF-01 · 查询学生分类与模板偏好

`GET /api/mini/v1/students/{studentId}/preferences`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |

**规则与失败边界：**新student_taxonomy_pref，不读账号偏好作为隐式学生作用域；模板偏好为可选能力，不恢复我的菜单入口。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| defaultSubjectId | string | 学生默认科目，可空 |
| defaultTopicId | string | 学生默认主题，可空 |
| defaultTemplateId | string／null | 可选学生模板偏好，可空 |

**请求样例：**

```http
GET /api/mini/v1/students/student-uuid/preferences HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "defaultSubjectId": "201",
    "defaultTopicId": "301",
    "defaultTemplateId": null
  }
}
```

<a id="pref-02"></a>

#### PREF-02 · 保存学生偏好

`PUT /api/mini/v1/students/{studentId}/preferences`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| defaultSubjectId | Body | string | 是 | 学生默认科目，可空 |
| defaultTopicId | Body | string | 是 | 学生默认主题，可空 |
| defaultTemplateId | Body | string／null | 是 | 可选学生模板偏好，可空 |

**规则与失败边界：**三字段显式提交，null清除；主题必须属于默认科目，模板必须可用。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| defaultSubjectId | string | 学生默认科目，可空 |
| defaultTopicId | string | 学生默认主题，可空 |
| defaultTemplateId | string／null | 可选学生模板偏好，可空 |

**请求样例：**

```http
PUT /api/mini/v1/students/student-uuid/preferences HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
Content-Type: application/json

{
  "defaultSubjectId": "201",
  "defaultTopicId": "301",
  "defaultTemplateId": null
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "defaultSubjectId": "201",
    "defaultTopicId": "301",
    "defaultTemplateId": null
  }
}
```

### 上传与采集

<a id="ast-01"></a>

#### AST-01 · 创建COS上传会话

`POST /api/mini/v1/students/{studentId}/uploads`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| clientRequestId | Body | string | 是 | 同业务请求重试保持相同的UUID |
| purpose | Body | string | 是 | ORIGINAL等资产用途；用户仅允许图片业务用途 |
| fileName | Body | string | 是 | 原文件显示名，不直接拼成COS路径 |
| mimeType | Body | string | 是 | 真实文件MIME，不以扩展名代替内容检查 |
| sizeBytes | Body | integer | 是 | 实际二进制字节数，正整数，限额由能力配置返回 |
| checksumSha256 | Body | string | 否 | 64位十六进制内容摘要，声明仍需服务端校验 |

**规则与失败边界：**小程序不持有永久SecretKey；目标契约采用签名URL代理上传，若生产采用STS需另修订upload结构，不能客户端猜SDK参数。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| uploadId | string | 上传会话主键 |
| assetId | string | 通过服务端验证的资产ID |
| expiresAt | string | 到期UTC时间，URL／草稿均按各自类型 |
| upload | object | 签名直传描述，严格按method／url／headers执行 |
| upload.method | string | 签名上传允许HTTP方法 |
| upload.url | string | 短期签名或鉴权代理地址，不长期存储 |
| upload.headers | object | 签名上传请求头，不附应用Bearer |
| upload.headers.Content-Type | string | 对象上传时必须匹配签名的文件MIME类型 |
| upload.fields | object | POST表单方式的签名字段，PUT方式为空 |

**请求样例：**

```http
POST /api/mini/v1/students/student-uuid/uploads HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
Content-Type: application/json

{
  "clientRequestId": "upload-request-uuid",
  "purpose": "ORIGINAL",
  "fileName": "photo.png",
  "mimeType": "image/png",
  "sizeBytes": 1024,
  "checksumSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "uploadId": "upload-uuid",
    "assetId": "asset-uuid",
    "expiresAt": "2026-10-10T08:05:00.000Z",
    "upload": {
      "method": "PUT",
      "url": "https://files.example.test/signed/upload",
      "headers": {
        "Content-Type": "image/png"
      },
      "fields": {}
    }
  }
}
```

<a id="ast-02"></a>

#### AST-02 · 确认上传并校验对象

`POST /api/mini/v1/students/{studentId}/uploads/{uploadId}/complete`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| uploadId | Path | string | 是 | 上传会话主键 |
| checksumSha256 | Body | string | 否 | 64位十六进制内容摘要，声明仍需服务端校验 |

**规则与失败边界：**服务端HEAD并检查大小／类型／内容摘要；客户端摘要不代替服务端校验。重复完成幂等。

**输出data：**Asset（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
POST /api/mini/v1/students/student-uuid/uploads/upload-uuid/complete HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
Content-Type: application/json

{
  "checksumSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "asset-uuid",
    "purpose": "ORIGINAL",
    "mimeType": "image/png",
    "width": 1200,
    "height": 800,
    "sizeBytes": 1024,
    "checksumSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
    "status": "AVAILABLE"
  }
}
```

<a id="ast-03"></a>

#### AST-03 · 读取资产元数据

`GET /api/mini/v1/students/{studentId}/assets/{assetId}`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| assetId | Path | string | 是 | 通过服务端验证的资产ID |

**输出data：**Asset（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
GET /api/mini/v1/students/student-uuid/assets/asset-uuid HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "asset-uuid",
    "purpose": "ORIGINAL",
    "mimeType": "image/png",
    "width": 1200,
    "height": 800,
    "sizeBytes": 1024,
    "checksumSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
    "status": "AVAILABLE"
  }
}
```

<a id="ast-04"></a>

#### AST-04 · 申请私有图片或PDF访问地址

`POST /api/mini/v1/students/{studentId}/assets/{assetId}/access`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| assetId | Path | string | 是 | 通过服务端验证的资产ID |
| disposition | Body | string | 是 | INLINE／ATTACHMENT |

**规则与失败边界：**INLINE／ATTACHMENT；访问历史快照资产按父任务权限，已软删来源不自动失去任务快照访问权。

**输出data：**AssetAccess（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
POST /api/mini/v1/students/student-uuid/assets/asset-uuid/access HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
Content-Type: application/json

{
  "disposition": "INLINE"
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "assetId": "asset-uuid",
    "url": "https://files.example.test/signed/object?signature=example",
    "expiresAt": "2026-10-10T08:05:00.000Z",
    "contentType": "image/png"
  }
}
```

<a id="cap-01"></a>

#### CAP-01 · 建立单张／多页采集批次

`POST /api/mini/v1/students/{studentId}/capture-batches`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| clientRequestId | Body | string | 是 | 同业务请求重试保持相同的UUID |
| mode | Body | string | 是 | 批次SINGLE／MULTI；练习PRACTICE／REVIEW |

**规则与失败边界：**mode SINGLE／MULTI；打开拍摄时固定studentId。

**输出data：**CaptureBatch（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
POST /api/mini/v1/students/student-uuid/capture-batches HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
Content-Type: application/json

{
  "clientRequestId": "capture-request-uuid",
  "mode": "MULTI"
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "batch-uuid",
    "studentId": "student-uuid",
    "mode": "MULTI",
    "status": "DRAFT",
    "photoCount": 0,
    "finishedAt": null,
    "createdAt": "2026-10-10T08:00:00.000Z",
    "updatedAt": "2026-10-10T08:00:00.000Z"
  }
}
```

<a id="cap-02"></a>

#### CAP-02 · 最近拍摄批次列表

`GET /api/mini/v1/students/{studentId}/capture-batches`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| cursor | Query | string | 否 | 上一页nextCursor；首请求省略 |
| limit | Query | integer | 否 | 默认20，范围1～100；以能力配置为准 |

**输出data：**CaptureBatch（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
GET /api/mini/v1/students/student-uuid/capture-batches?cursor=cursor-token&limit=20 HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": "batch-uuid",
        "studentId": "student-uuid",
        "mode": "MULTI",
        "status": "READY",
        "photoCount": 1,
        "finishedAt": "2026-10-10T08:00:00.000Z",
        "createdAt": "2026-10-10T08:00:00.000Z",
        "updatedAt": "2026-10-10T08:00:00.000Z"
      }
    ],
    "nextCursor": null,
    "hasMore": false
  }
}
```

<a id="cap-03"></a>

#### CAP-03 · 读取照片处理工作区

`GET /api/mini/v1/students/{studentId}/capture-batches/{batchId}`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| batchId | Path | string | 是 | 采集批次ID |

**输出data：**CaptureBatch、Photo（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
GET /api/mini/v1/students/student-uuid/capture-batches/batch-uuid HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "batch": {
      "id": "batch-uuid",
      "studentId": "student-uuid",
      "mode": "MULTI",
      "status": "READY",
      "photoCount": 1,
      "finishedAt": "2026-10-10T08:00:00.000Z",
      "createdAt": "2026-10-10T08:00:00.000Z",
      "updatedAt": "2026-10-10T08:00:00.000Z"
    },
    "photos": [
      {
        "id": "photo-uuid",
        "batchId": "batch-uuid",
        "studentId": "student-uuid",
        "source": "CAMERA",
        "sortOrder": 0,
        "originalAssetId": "asset-uuid",
        "currentRevisionId": "revision-uuid",
        "uploadedAt": "2026-10-10T08:00:00.000Z"
      }
    ]
  }
}
```

<a id="cap-04"></a>

#### CAP-04 · 把上传成功照片加入批次

`POST /api/mini/v1/students/{studentId}/capture-batches/{batchId}/photos`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| batchId | Path | string | 是 | 采集批次ID |
| clientRequestId | Body | string | 是 | 同业务请求重试保持相同的UUID |
| assetId | Body | string | 是 | 通过服务端验证的资产ID |
| source | Body | string | 是 | CAMERA／ALBUM |
| capturedAt | Body | string | 否 | 设备拍摄时间，仅用于展示，不作审计 |

**规则与失败边界：**source CAMERA／ALBUM；服务端分配sortOrder、建立ORIGINAL版本；按批次与assetId保证重试不重复。clientRequestId用于请求去重，不新增未建模照片字段。

**输出data：**Photo（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
POST /api/mini/v1/students/student-uuid/capture-batches/batch-uuid/photos HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
Content-Type: application/json

{
  "clientRequestId": "photo-request-uuid",
  "assetId": "asset-uuid",
  "source": "CAMERA",
  "capturedAt": "2026-10-10T08:00:00.000Z"
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "photo-uuid",
    "batchId": "batch-uuid",
    "studentId": "student-uuid",
    "source": "CAMERA",
    "sortOrder": 0,
    "originalAssetId": "asset-uuid",
    "currentRevisionId": "revision-uuid",
    "uploadedAt": "2026-10-10T08:00:00.000Z"
  }
}
```

<a id="cap-05"></a>

#### CAP-05 · 结束拍摄进入处理

`POST /api/mini/v1/students/{studentId}/capture-batches/{batchId}/finish`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| batchId | Path | string | 是 | 采集批次ID |

**规则与失败边界：**必须至少一张AVAILABLE照片；重复完成幂等，状态READY且保存finishedAt。无请求体。

**输出data：**CaptureBatch（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
POST /api/mini/v1/students/student-uuid/capture-batches/batch-uuid/finish HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "batch-uuid",
    "studentId": "student-uuid",
    "mode": "MULTI",
    "status": "READY",
    "photoCount": 1,
    "finishedAt": "2026-10-10T08:00:00.000Z",
    "createdAt": "2026-10-10T08:00:00.000Z",
    "updatedAt": "2026-10-10T08:00:00.000Z"
  }
}
```

<a id="cap-06"></a>

#### CAP-06 · 调整照片显示顺序

`PUT /api/mini/v1/students/{studentId}/capture-batches/{batchId}/photos/order`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| batchId | Path | string | 是 | 采集批次ID |
| photoIds | Body | array | 是 | 本批次有效照片全量有序ID |

**规则与失败边界：**有效照片全量排列，原子分配未复用sortOrder；有排序约束时采用安全临时序号再提交。

**输出data：**Photo（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
PUT /api/mini/v1/students/student-uuid/capture-batches/batch-uuid/photos/order HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
Content-Type: application/json

{
  "photoIds": [
    "photo-uuid"
  ]
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "photos": [
      {
        "id": "photo-uuid",
        "batchId": "batch-uuid",
        "studentId": "student-uuid",
        "source": "CAMERA",
        "sortOrder": 0,
        "originalAssetId": "asset-uuid",
        "currentRevisionId": "revision-uuid",
        "uploadedAt": "2026-10-10T08:00:00.000Z"
      }
    ]
  }
}
```

<a id="cap-07"></a>

#### CAP-07 · 删除工作区照片

`DELETE /api/mini/v1/students/{studentId}/photos/{photoId}`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| photoId | Path | string | 是 | 采集照片ID |

**规则与失败边界：**软删，任务／错题引用不删除；成功后重新读取CAP-03修正当前索引。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| remainingPhotoCount | integer | 删除后该批次有效照片数 |

**请求样例：**

```http
DELETE /api/mini/v1/students/student-uuid/photos/photo-uuid HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "remainingPhotoCount": 0
  }
}
```

<a id="cap-08"></a>

#### CAP-08 · 放弃或移除采集工作区

`DELETE /api/mini/v1/students/{studentId}/capture-batches/{batchId}`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| batchId | Path | string | 是 | 采集批次ID |

**规则与失败边界：**标记ABANDONED／软删除；不是物理删除所有资产。运行中处理需显式取消，任务可能已执行的上游调用仍保留日志。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| 无 | object | 无数据返回{}，已有接口可省略data |

**请求样例：**

```http
DELETE /api/mini/v1/students/student-uuid/capture-batches/batch-uuid HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

### 图片处理与题框

<a id="img-01"></a>

#### IMG-01 · 读取原图与处理版本

`GET /api/mini/v1/students/{studentId}/photos/{photoId}/revisions`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| photoId | Path | string | 是 | 采集照片ID |

**输出data：**ImageRevision（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
GET /api/mini/v1/students/student-uuid/photos/photo-uuid/revisions HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": "revision-uuid",
        "photoId": "photo-uuid",
        "parentRevisionId": null,
        "asset": {
          "id": "asset-uuid",
          "purpose": "ORIGINAL",
          "mimeType": "image/png",
          "width": 1200,
          "height": 800,
          "sizeBytes": 1024,
          "checksumSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
          "status": "AVAILABLE"
        },
        "operation": "ORIGINAL",
        "parameters": {
          "schemaVersion": 1
        },
        "createdAt": "2026-10-10T08:00:00.000Z"
      }
    ],
    "currentRevisionId": "revision-uuid",
    "originalRevisionId": "revision-uuid"
  }
}
```

<a id="img-02"></a>

#### IMG-02 · 选择有效处理版本

`PUT /api/mini/v1/students/{studentId}/photos/{photoId}/current-revision`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| photoId | Path | string | 是 | 采集照片ID |
| revisionId | Body | string | 是 | 题框／显示绑定的确切版本 |
| expectedCurrentRevisionId | Body | string | 是 | CAS预期当前图片指针，冲突不强行覆盖 |

**规则与失败边界：**CAS指针更新，必须同照片；按住原图只本地展示，不调用本接口。

**输出data：**Photo（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
PUT /api/mini/v1/students/student-uuid/photos/photo-uuid/current-revision HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
Content-Type: application/json

{
  "revisionId": "revision-uuid",
  "expectedCurrentRevisionId": "revision-uuid"
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "photo-uuid",
    "batchId": "batch-uuid",
    "studentId": "student-uuid",
    "source": "CAMERA",
    "sortOrder": 0,
    "originalAssetId": "asset-uuid",
    "currentRevisionId": "revision-uuid",
    "uploadedAt": "2026-10-10T08:00:00.000Z"
  }
}
```

<a id="job-01"></a>

#### JOB-01 · 提交单张／整批智能处理

`POST /api/mini/v1/students/{studentId}/processing-jobs`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| requestKey | Body | string | 是 | 处理幂等请求键 |
| batchId | Body | string | 是 | 采集批次ID |
| operation | Body | string | 是 | ORIGINAL／CORRECT／ENHANCE／ERASE／SPLIT／PAPER_PROCESS，按DTO上下文 |
| applyScope | Body | string | 是 | CURRENT／ALL，目标集合在提交时冻结 |
| targets | Body | array | 是 | photoId/inputRevisionId数组，均属于同批次同学生 |
| targets[].photoId | Body | string | 是 | 采集照片ID |
| targets[].inputRevisionId | Body | string | 是 | 固定输入图片版本，PHOTO来源必填 |
| parameters | Body | object | 是 | 工具参数快照，字段见参数契约 |
| parameters.schemaVersion | Body | integer | 是 | 嵌套JSON结构版本，接口用camelCase，后端转换持久字段 |

**规则与失败边界：**返回HTTP202；operation CORRECT／ENHANCE／ERASE／SPLIT／PAPER_PROCESS；ALL时targets必须包含提交时全部有效照片，参数schema见下方专节。服务端检查AI授权、配置和限额。

**输出data：**ProcessingJob（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
POST /api/mini/v1/students/student-uuid/processing-jobs HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
Content-Type: application/json

{
  "requestKey": "processing-request-uuid",
  "batchId": "batch-uuid",
  "operation": "ERASE",
  "applyScope": "CURRENT",
  "targets": [
    {
      "photoId": "photo-uuid",
      "inputRevisionId": "revision-uuid"
    }
  ],
  "parameters": {
    "schemaVersion": 1
  }
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "job-uuid",
    "studentId": "student-uuid",
    "batchId": "batch-uuid",
    "operation": "ERASE",
    "applyScope": "CURRENT",
    "status": "QUEUED",
    "traceId": "trace-id",
    "items": [
      {
        "id": "job-item-uuid",
        "photoId": "photo-uuid",
        "inputRevisionId": "revision-uuid",
        "outputRevisionId": null,
        "status": "QUEUED",
        "attemptCount": 0,
        "stepResults": [],
        "errorCode": null,
        "errorMessage": null
      }
    ],
    "pollAfterMs": 1500,
    "createdAt": "2026-10-10T08:00:00.000Z",
    "finishedAt": null
  }
}
```

<a id="job-02"></a>

#### JOB-02 · 轮询处理状态

`GET /api/mini/v1/students/{studentId}/processing-jobs/{jobId}`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| jobId | Path | string | 是 | 目标资源ID，需校验当前账号及父路径归属 |

**规则与失败边界：**按pollAfterMs退避轮询，页面离开可暂停；终态停止。

**输出data：**ProcessingJob（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
GET /api/mini/v1/students/student-uuid/processing-jobs/job-uuid HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "job-uuid",
    "studentId": "student-uuid",
    "batchId": "batch-uuid",
    "operation": "ERASE",
    "applyScope": "CURRENT",
    "status": "QUEUED",
    "traceId": "trace-id",
    "items": [
      {
        "id": "job-item-uuid",
        "photoId": "photo-uuid",
        "inputRevisionId": "revision-uuid",
        "outputRevisionId": null,
        "status": "QUEUED",
        "attemptCount": 0,
        "stepResults": [],
        "errorCode": null,
        "errorMessage": null
      }
    ],
    "pollAfterMs": 1500,
    "createdAt": "2026-10-10T08:00:00.000Z",
    "finishedAt": null
  }
}
```

<a id="job-03"></a>

#### JOB-03 · 重试失败的处理项

`POST /api/mini/v1/students/{studentId}/processing-jobs/{jobId}/retry`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| jobId | Path | string | 是 | 目标资源ID，需校验当前账号及父路径归属 |
| clientRequestId | Body | string | 是 | 同业务请求重试保持相同的UUID |
| itemIds | Body | array | 是 | 该任务／草稿当前项ID数组 |

**规则与失败边界：**HTTP202；仅FAILED项，固定原inputRevision；不以最新照片替换输入。请求键可使用outbox事件ID／缓存去重，服务实现不得伪造模型已有专用重试表。

**输出data：**ProcessingJob（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
POST /api/mini/v1/students/student-uuid/processing-jobs/job-uuid/retry HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
Content-Type: application/json

{
  "clientRequestId": "job-retry-uuid",
  "itemIds": [
    "job-item-uuid"
  ]
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "job-uuid",
    "studentId": "student-uuid",
    "batchId": "batch-uuid",
    "operation": "ERASE",
    "applyScope": "CURRENT",
    "status": "QUEUED",
    "traceId": "trace-id",
    "items": [
      {
        "id": "job-item-uuid",
        "photoId": "photo-uuid",
        "inputRevisionId": "revision-uuid",
        "outputRevisionId": null,
        "status": "QUEUED",
        "attemptCount": 0,
        "stepResults": [],
        "errorCode": null,
        "errorMessage": null
      }
    ],
    "pollAfterMs": 1500,
    "createdAt": "2026-10-10T08:00:00.000Z",
    "finishedAt": null
  }
}
```

<a id="job-04"></a>

#### JOB-04 · 请求取消处理任务

`POST /api/mini/v1/students/{studentId}/processing-jobs/{jobId}/cancel`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| jobId | Path | string | 是 | 目标资源ID，需校验当前账号及父路径归属 |

**规则与失败边界：**尽力取消排队或未执行项，已提交云请求不保证可撤销／退费。结果竞态最终以轮询为准。

**输出data：**ProcessingJob（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
POST /api/mini/v1/students/student-uuid/processing-jobs/job-uuid/cancel HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "job-uuid",
    "studentId": "student-uuid",
    "batchId": "batch-uuid",
    "operation": "ERASE",
    "applyScope": "CURRENT",
    "status": "QUEUED",
    "traceId": "trace-id",
    "items": [
      {
        "id": "job-item-uuid",
        "photoId": "photo-uuid",
        "inputRevisionId": "revision-uuid",
        "outputRevisionId": null,
        "status": "QUEUED",
        "attemptCount": 0,
        "stepResults": [],
        "errorCode": null,
        "errorMessage": null
      }
    ],
    "pollAfterMs": 1500,
    "createdAt": "2026-10-10T08:00:00.000Z",
    "finishedAt": null
  }
}
```

<a id="reg-01"></a>

#### REG-01 · 读取指定图片版本题框

`GET /api/mini/v1/students/{studentId}/photos/{photoId}/regions`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| photoId | Path | string | 是 | 采集照片ID |
| revisionId | Query | string | 是 | 确切版本ID |

**输出data：**QuestionRegion（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
GET /api/mini/v1/students/student-uuid/photos/photo-uuid/regions?revisionId=revision-uuid HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": "region-uuid",
        "photoId": "photo-uuid",
        "revisionId": "revision-uuid",
        "cropAssetId": null,
        "geometry": {
          "schemaVersion": 1,
          "shape": "RECTANGLE",
          "coordinateSpace": "NORMALIZED",
          "x": 0.1,
          "y": 0.2,
          "width": 0.8,
          "height": 0.25
        },
        "sortOrder": 0,
        "origin": "MANUAL",
        "status": "ACTIVE"
      }
    ]
  }
}
```

<a id="reg-02"></a>

#### REG-02 · 保存手动题框与修改

`PUT /api/mini/v1/students/{studentId}/photos/{photoId}/regions`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| photoId | Path | string | 是 | 采集照片ID |
| clientRequestId | Body | string | 是 | 同业务请求重试保持相同的UUID |
| revisionId | Body | string | 是 | 题框／显示绑定的确切版本 |
| items | Body | array | 是 | 有序资源或请求项数组，结构按对应DTO解析 |
| items[].clientRegionId | Body | string | 是 | 手工区域客户端标识 |
| items[].geometry | Body | object | 是 | schemaVersion=1归一化题框，详见模型geometry_json |
| items[].geometry.schemaVersion | Body | integer | 是 | 嵌套JSON结构版本，接口用camelCase，后端转换持久字段 |
| items[].geometry.shape | Body | string | 是 | RECTANGLE，后续POLYGON需明确实现能力 |
| items[].geometry.coordinateSpace | Body | string | 是 | NORMALIZED，相对指定图片版本 |
| items[].geometry.x | Body | number | 是 | 左上角相对横坐标，0到1，x+width不大于1 |
| items[].geometry.y | Body | number | 是 | 左上角相对纵坐标，0到1，y+height不大于1 |
| items[].geometry.width | Body | number | 是 | 图片像素或几何相对宽，依所在DTO约定 |
| items[].geometry.height | Body | number | 是 | 图片像素或几何相对高，依所在DTO约定 |
| items[].sortOrder | Body | integer | 是 | 展示顺序，非负整数 |

**规则与失败边界：**全量替换该版本有效框；创建新区域并将旧区域SUPERSEDED，避免修改历史打印来源。空items合法表示不保留框。缺裁切图按保存／打印时生成。

**输出data：**QuestionRegion（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
PUT /api/mini/v1/students/student-uuid/photos/photo-uuid/regions HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
Content-Type: application/json

{
  "clientRequestId": "regions-request-uuid",
  "revisionId": "revision-uuid",
  "items": [
    {
      "clientRegionId": "client-region-uuid",
      "geometry": {
        "schemaVersion": 1,
        "shape": "RECTANGLE",
        "coordinateSpace": "NORMALIZED",
        "x": 0.1,
        "y": 0.2,
        "width": 0.8,
        "height": 0.25
      },
      "sortOrder": 0
    }
  ]
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": "region-uuid",
        "photoId": "photo-uuid",
        "revisionId": "revision-uuid",
        "cropAssetId": null,
        "geometry": {
          "schemaVersion": 1,
          "shape": "RECTANGLE",
          "coordinateSpace": "NORMALIZED",
          "x": 0.1,
          "y": 0.2,
          "width": 0.8,
          "height": 0.25
        },
        "sortOrder": 0,
        "origin": "MANUAL",
        "status": "ACTIVE"
      }
    ]
  }
}
```

### 错题与练习

<a id="ent-01"></a>

#### ENT-01 · 分类批量保存题框或照片

`POST /api/mini/v1/students/{studentId}/entries/batch`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| requestId | Body | string | 是 | 批量保存请求UUID，配合clientId幂等 |
| subjectId | Body | string | 是 | 科目ID，必须归属本学生 |
| topicId | Body | string | 是 | 主题ID，必须属于所选科目 |
| errorTypeId | Body | string | 是 | 错误类型ID，必须归属本学生 |
| items | Body | array | 是 | 有序资源或请求项数组，结构按对应DTO解析 |
| items[].clientId | Body | string | 是 | 同批量保存项的稳定UUID |
| items[].sourceType | Body | string | 是 | 对象上下文：来源项ENTRY／REGION／PHOTO；任务COLLECTION／CAPTURE／RANDOM |
| items[].sourceId | Body | string | 是 | sourceType对应的ENTRY／REGION／PHOTO ID |
| items[].inputRevisionId | Body | string | 是 | 固定输入图片版本，PHOTO来源必填 |
| items[].remark | Body | string | 否 | 用户备注字符串，空串清除 |
| items[].answer | Body | string | 否 | 参考答案字符串，空串清除 |

**规则与失败边界：**逐项事务，SUCCESS／DUPLICATE／FAILED；年级学期从批次固定学生读取，客户端不能覆盖为别人的档案。sourceType仅REGION／PHOTO；PHOTO必须inputRevisionId；分类必填。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| results | array | 逐项处理结果，SUCCESS／DUPLICATE／FAILED |
| results[].clientId | string | 同批量保存项的稳定UUID |
| results[].status | string | 实体或任务状态，值域见对应DTO／状态机 |
| results[].entryId | string | 错题ID |
| results[].code | integer | 稳定代码；微信登录时为一次性wx.login code |
| results[].message | string | 结果说明或失败原因 |

**请求样例：**

```http
POST /api/mini/v1/students/student-uuid/entries/batch HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
Content-Type: application/json

{
  "requestId": "save-request-uuid",
  "subjectId": "201",
  "topicId": "301",
  "errorTypeId": "error-uuid",
  "items": [
    {
      "clientId": "save-item-uuid",
      "sourceType": "PHOTO",
      "sourceId": "photo-uuid",
      "inputRevisionId": "revision-uuid",
      "remark": "注意通分",
      "answer": ""
    }
  ]
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "results": [
      {
        "clientId": "save-item-uuid",
        "status": "SUCCESS",
        "entryId": "entry-uuid",
        "code": 0,
        "message": "ok"
      }
    ]
  }
}
```

<a id="ent-02"></a>

#### ENT-02 · 筛选错题卡片列表

`GET /api/mini/v1/students/{studentId}/entries`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| cursor | Query | string | 否 | 上一页nextCursor；首请求省略 |
| limit | Query | integer | 否 | 默认20，范围1～100；以能力配置为准 |
| subjectId | Query | string | 否 | 科目ID |
| topicId | Query | string | 否 | 主题ID |
| errorTypeId | Query | string | 否 | 错误类型ID |
| topicKeyword | Query | string | 否 | 主题关键词，最长80 |
| startDate | Query | date | 否 | 上海时区开始日，含 |
| endDate | Query | date | 否 | 上海时区结束日，含 |
| masteryStatus | Query | string | 否 | UNPRACTICED／PRACTICING／MASTERED |

**规则与失败边界：**createdAt DESC,id ASC游标；日期后端转换UTC半开区间，返回items不混入其他学生。

**输出data：**Entry（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
GET /api/mini/v1/students/student-uuid/entries?cursor=cursor-token&limit=20&subjectId=201&topicId=301&errorTypeId=error-uuid&topicKeyword=分数&startDate=2026-10-01&endDate=2026-10-10&masteryStatus=UNPRACTICED HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": "entry-uuid",
        "studentId": "student-uuid",
        "grade": 5,
        "term": 1,
        "subject": {
          "id": "201",
          "name": "数学",
          "status": "ACTIVE"
        },
        "topic": {
          "id": "301",
          "name": "分数加减法",
          "status": "ACTIVE"
        },
        "errorType": {
          "id": "error-uuid",
          "name": "计算错误",
          "status": "ACTIVE"
        },
        "imageAssetId": "asset-uuid",
        "thumbnailAssetId": null,
        "answer": "参考答案",
        "remark": "注意通分",
        "masteryStatus": "UNPRACTICED",
        "practiceCount": 0,
        "correctCount": 0,
        "lastPracticedAt": null,
        "version": 0,
        "createdAt": "2026-10-10T08:00:00.000Z",
        "updatedAt": "2026-10-10T08:00:00.000Z"
      }
    ],
    "nextCursor": null,
    "hasMore": false
  }
}
```

<a id="ent-03"></a>

#### ENT-03 · 统一错题详情

`GET /api/mini/v1/students/{studentId}/entries/{entryId}`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| entryId | Path | string | 是 | 错题ID |

**规则与失败边界：**首页／错题集／打印来源均可使用同一详情；软删或归属不符404。

**输出data：**Entry（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
GET /api/mini/v1/students/student-uuid/entries/entry-uuid HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "entry-uuid",
    "studentId": "student-uuid",
    "grade": 5,
    "term": 1,
    "subject": {
      "id": "201",
      "name": "数学",
      "status": "ACTIVE"
    },
    "topic": {
      "id": "301",
      "name": "分数加减法",
      "status": "ACTIVE"
    },
    "errorType": {
      "id": "error-uuid",
      "name": "计算错误",
      "status": "ACTIVE"
    },
    "imageAssetId": "asset-uuid",
    "thumbnailAssetId": null,
    "answer": "参考答案",
    "remark": "注意通分",
    "masteryStatus": "UNPRACTICED",
    "practiceCount": 0,
    "correctCount": 0,
    "lastPracticedAt": null,
    "version": 0,
    "createdAt": "2026-10-10T08:00:00.000Z",
    "updatedAt": "2026-10-10T08:00:00.000Z"
  }
}
```

<a id="ent-04"></a>

#### ENT-04 · 修改错题分类或内容

`PATCH /api/mini/v1/students/{studentId}/entries/{entryId}`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| entryId | Path | string | 是 | 错题ID |
| version | Body | integer | 是 | 乐观锁或资源版本；修改时传当前版本 |
| subjectId | Body | string | 否 | 科目ID，必须归属本学生 |
| topicId | Body | string | 否 | 主题ID，必须属于所选科目 |
| errorTypeId | Body | string | 否 | 错误类型ID，必须归属本学生 |
| answer | Body | string | 否 | 参考答案字符串，空串清除 |
| remark | Body | string | 否 | 用户备注字符串，空串清除 |

**规则与失败边界：**version必填；至少一个改动字段；换科目必须显式带topicId，新记录主题不能清空。answer／remark空串清除，null不接受。冲突4091返回当前Entry。

**输出data：**Entry（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
PATCH /api/mini/v1/students/student-uuid/entries/entry-uuid HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
Content-Type: application/json

{
  "version": 0,
  "subjectId": "201",
  "topicId": "301",
  "errorTypeId": "error-uuid",
  "answer": "参考答案",
  "remark": "注意通分"
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "entry-uuid",
    "studentId": "student-uuid",
    "grade": 5,
    "term": 1,
    "subject": {
      "id": "201",
      "name": "数学",
      "status": "ACTIVE"
    },
    "topic": {
      "id": "301",
      "name": "分数加减法",
      "status": "ACTIVE"
    },
    "errorType": {
      "id": "error-uuid",
      "name": "计算错误",
      "status": "ACTIVE"
    },
    "imageAssetId": "asset-uuid",
    "thumbnailAssetId": null,
    "answer": "参考答案",
    "remark": "注意通分",
    "masteryStatus": "UNPRACTICED",
    "practiceCount": 0,
    "correctCount": 0,
    "lastPracticedAt": null,
    "version": 0,
    "createdAt": "2026-10-10T08:00:00.000Z",
    "updatedAt": "2026-10-10T08:00:00.000Z"
  }
}
```

<a id="ent-05"></a>

#### ENT-05 · 软删除错题

`DELETE /api/mini/v1/students/{studentId}/entries/{entryId}`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| entryId | Path | string | 是 | 错题ID |

**规则与失败边界：**重复删除幂等，404用于他人／从未存在的ID；练习历史、打印快照不级联删除。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| 无 | object | 无数据返回{}，已有接口可省略data |

**请求样例：**

```http
DELETE /api/mini/v1/students/student-uuid/entries/entry-uuid HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

<a id="pra-01"></a>

#### PRA-01 · 提交做题结果

`POST /api/mini/v1/students/{studentId}/entries/{entryId}/practices`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| entryId | Path | string | 是 | 错题ID |
| clientRequestId | Body | string | 是 | 同业务请求重试保持相同的UUID |
| answerContent | Body | string | 是 | 此次用户作答内容 |
| correct | Body | boolean | 是 | 用户确认该次正误；不是自动判题结果 |
| mode | Body | string | 是 | 批次SINGLE／MULTI；练习PRACTICE／REVIEW |
| durationSeconds | Body | integer | 否 | 作答时长，非负整数，可空 |

**规则与失败边界：**自己确认正误，非自动AI判卷接口；不允许用打开题目或查看讲解调用计数。重复请求不重复计数。

**输出data：**Practice（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
POST /api/mini/v1/students/student-uuid/entries/entry-uuid/practices HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
Content-Type: application/json

{
  "clientRequestId": "practice-request-uuid",
  "answerContent": "我的作答",
  "correct": true,
  "mode": "PRACTICE",
  "durationSeconds": 60
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "501",
    "entryId": "entry-uuid",
    "studentId": "student-uuid",
    "mode": "PRACTICE",
    "answerContent": "我的作答",
    "correct": true,
    "durationSeconds": 60,
    "practicedAt": "2026-10-10T08:00:00.000Z",
    "entryVersion": 1,
    "practiceCount": 1,
    "correctCount": 1,
    "masteryStatus": "PRACTICING"
  }
}
```

<a id="pra-02"></a>

#### PRA-02 · 查询单题练习历史

`GET /api/mini/v1/students/{studentId}/entries/{entryId}/practices`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| entryId | Path | string | 是 | 错题ID |
| cursor | Query | string | 否 | 上一页nextCursor；首请求省略 |
| limit | Query | integer | 否 | 默认20，范围1～100；以能力配置为准 |

**输出data：**Practice（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
GET /api/mini/v1/students/student-uuid/entries/entry-uuid/practices?cursor=cursor-token&limit=20 HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": "501",
        "entryId": "entry-uuid",
        "studentId": "student-uuid",
        "mode": "PRACTICE",
        "answerContent": "我的作答",
        "correct": true,
        "durationSeconds": 60,
        "practicedAt": "2026-10-10T08:00:00.000Z",
        "entryVersion": 1,
        "practiceCount": 1,
        "correctCount": 1,
        "masteryStatus": "PRACTICING"
      }
    ],
    "nextCursor": null,
    "hasMore": false
  }
}
```

<a id="pra-03"></a>

#### PRA-03 · 查询学生跨题练习历史

`GET /api/mini/v1/students/{studentId}/practices`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| cursor | Query | string | 否 | 上一页nextCursor；首请求省略 |
| limit | Query | integer | 否 | 默认20，范围1～100；以能力配置为准 |
| startDate | Query | date | 否 | 开始日 |
| endDate | Query | date | 否 | 结束日 |

**输出data：**Practice（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
GET /api/mini/v1/students/student-uuid/practices?cursor=cursor-token&limit=20&startDate=2026-10-01&endDate=2026-10-10 HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": "501",
        "entryId": "entry-uuid",
        "studentId": "student-uuid",
        "mode": "PRACTICE",
        "answerContent": "我的作答",
        "correct": true,
        "durationSeconds": 60,
        "practicedAt": "2026-10-10T08:00:00.000Z",
        "entryVersion": 1,
        "practiceCount": 1,
        "correctCount": 1,
        "masteryStatus": "PRACTICING"
      }
    ],
    "nextCursor": null,
    "hasMore": false
  }
}
```

<a id="agt-01"></a>

#### AGT-01 · 请求讲解

`POST /api/mini/v1/students/{studentId}/entries/{entryId}/explanation`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| entryId | Path | string | 是 | 错题ID |
| forceRefresh | Body | boolean | 否 | 是否绕过缓存重新调用，默认false，可能重新耗费额度 |

**规则与失败边界：**目标同步接口，命中缓存可直接返回；未命中等待上游，超时504重新请求可读已完成缓存。仅forceRefresh=true重复调用可能再次消耗额度，不做自动重试。结果结构text为基础，变式可含questions数组，不自动保存错题。

**输出data：**AgentResult（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
POST /api/mini/v1/students/student-uuid/entries/entry-uuid/explanation HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
Content-Type: application/json

{
  "forceRefresh": false
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "601",
    "entryId": "entry-uuid",
    "resultType": "EXPLAIN",
    "status": "SUCCEEDED",
    "cached": true,
    "provider": "DASHSCOPE",
    "model": "configured-model",
    "inputContentHash": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
    "result": {
      "schemaVersion": 1,
      "text": "先通分再进行加减运算。"
    },
    "traceId": "trace-id",
    "createdAt": "2026-10-10T08:00:00.000Z"
  }
}
```

<a id="agt-02"></a>

#### AGT-02 · 请求举一反三

`POST /api/mini/v1/students/{studentId}/entries/{entryId}/analogies`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| entryId | Path | string | 是 | 错题ID |
| forceRefresh | Body | boolean | 否 | 是否绕过缓存重新调用，默认false，可能重新耗费额度 |

**规则与失败边界：**目标同步接口，命中缓存可直接返回；未命中等待上游，超时504重新请求可读已完成缓存。仅forceRefresh=true重复调用可能再次消耗额度，不做自动重试。结果结构text为基础，变式可含questions数组，不自动保存错题。

**输出data：**AgentResult（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
POST /api/mini/v1/students/student-uuid/entries/entry-uuid/analogies HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
Content-Type: application/json

{
  "forceRefresh": false
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "601",
    "entryId": "entry-uuid",
    "resultType": "ANALOGY",
    "status": "SUCCEEDED",
    "cached": true,
    "provider": "DASHSCOPE",
    "model": "configured-model",
    "inputContentHash": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
    "result": {
      "schemaVersion": 1,
      "questions": [
        {
          "questionText": "计算 1/3 + 1/6",
          "answer": "1/2",
          "explanation": "通分为 2/6 + 1/6。"
        }
      ]
    },
    "traceId": "trace-id",
    "createdAt": "2026-10-10T08:00:00.000Z"
  }
}
```

### 组卷草稿

<a id="rnd-01"></a>

#### RND-01 · 查询各错误类型可抽题数

`POST /api/mini/v1/students/{studentId}/random-papers/availability`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| subjectId | Body | string | 否 | 科目ID，必须归属本学生 |
| topicId | Body | string | 否 | 主题ID，必须属于所选科目 |

**规则与失败边界：**查询无副作用；topicId必须属于subjectId。按errorTypeId计数，不以中文字符串归组。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| types | array | 可抽错误类型和数量数组 |
| types[].errorTypeId | string | 错误类型ID，必须归属本学生 |
| types[].name | string | 显示名称 |
| types[].availableCount | integer | 当前筛选下某错误类型可抽题数 |

**请求样例：**

```http
POST /api/mini/v1/students/student-uuid/random-papers/availability HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
Content-Type: application/json

{
  "subjectId": "201",
  "topicId": "301"
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "types": [
      {
        "errorTypeId": "error-uuid",
        "name": "计算错误",
        "availableCount": 15
      }
    ]
  }
}
```

<a id="rnd-02"></a>

#### RND-02 · 随机组卷并返回有序来源

`POST /api/mini/v1/students/{studentId}/random-papers`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| clientRequestId | Body | string | 是 | 同业务请求重试保持相同的UUID |
| subjectId | Body | string | 否 | 科目ID，必须归属本学生 |
| topicId | Body | string | 否 | 主题ID，必须属于所选科目 |
| counts | Body | array | 是 | errorTypeId/count数组，不允许重复类型 |
| counts[].errorTypeId | Body | string | 是 | 错误类型ID，必须归属本学生 |
| counts[].count | Body | integer | 是 | 该错误类型请求题数，整数0～服务端上限 |

**规则与失败边界：**优先少练，同次数随机，count0不抽，不足返回实际；不计练习。请求键缓存本次结果以保持重试同卷；缓存到期提示重组，不能同键重抽不同卷。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| studentId | string | 所属学生ID，必须属于登录账号 |
| sourceType | string | 对象上下文：来源项ENTRY／REGION／PHOTO；任务COLLECTION／CAPTURE／RANDOM |
| sources | array | 有序打印来源数组，每项sourceType/sourceId及PHOTO输入版本 |
| sources[].sourceType | string | 对象上下文：来源项ENTRY／REGION／PHOTO；任务COLLECTION／CAPTURE／RANDOM |
| sources[].sourceId | string | sourceType对应的ENTRY／REGION／PHOTO ID |
| sources[].inputRevisionId | string／null | 固定输入图片版本，PHOTO来源必填 |
| requestedCount | integer | 组卷请求总题数 |
| actualCount | integer | 组卷实际返回题数，可不足 |
| shortages | array | 各错误类型缺题提示数组 |
| shortages[].errorTypeId | string | 错误类型ID，必须归属本学生 |
| shortages[].requested | integer | 某类型请求题数 |
| shortages[].actual | integer | 某类型实际题数 |

**请求样例：**

```http
POST /api/mini/v1/students/student-uuid/random-papers HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
Content-Type: application/json

{
  "clientRequestId": "random-request-uuid",
  "subjectId": "201",
  "topicId": "301",
  "counts": [
    {
      "errorTypeId": "error-uuid",
      "count": 5
    }
  ]
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "studentId": "student-uuid",
    "sourceType": "RANDOM",
    "sources": [
      {
        "sourceType": "ENTRY",
        "sourceId": "entry-uuid",
        "inputRevisionId": null
      }
    ],
    "requestedCount": 5,
    "actualCount": 1,
    "shortages": [
      {
        "errorTypeId": "error-uuid",
        "requested": 5,
        "actual": 1
      }
    ]
  }
}
```

<a id="drf-01"></a>

#### DRF-01 · 建立可恢复打印草稿（可选）

`POST /api/mini/v1/students/{studentId}/paper-drafts`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| clientRequestId | Body | string | 是 | 同业务请求重试保持相同的UUID |
| sourceType | Body | string | 是 | 对象上下文：来源项ENTRY／REGION／PHOTO；任务COLLECTION／CAPTURE／RANDOM |
| sources | Body | array | 是 | 有序打印来源数组，每项sourceType/sourceId及PHOTO输入版本 |
| sources[].sourceType | Body | string | 是 | 对象上下文：来源项ENTRY／REGION／PHOTO；任务COLLECTION／CAPTURE／RANDOM |
| sources[].sourceId | Body | string | 是 | sourceType对应的ENTRY／REGION／PHOTO ID |
| sources[].inputRevisionId | Body | string／null | 是 | 固定输入图片版本，PHOTO来源必填 |

**规则与失败边界：**需features.paperDraft=true；sourceType COLLECTION／CAPTURE／RANDOM，sources可混ENTRY/REGION/PHOTO但同学生，任务来源类型需与业务入口一致。

**输出data：**PaperDraft（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
POST /api/mini/v1/students/student-uuid/paper-drafts HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
Content-Type: application/json

{
  "clientRequestId": "draft-request-uuid",
  "sourceType": "COLLECTION",
  "sources": [
    {
      "sourceType": "ENTRY",
      "sourceId": "entry-uuid",
      "inputRevisionId": null
    }
  ]
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "draft-uuid",
    "studentId": "student-uuid",
    "sourceType": "COLLECTION",
    "selectedTemplateId": null,
    "items": [
      {
        "id": "draft-item-uuid",
        "sortOrder": 0,
        "sourceType": "ENTRY",
        "sourceId": "entry-uuid",
        "inputRevisionId": null,
        "imageAssetId": "asset-uuid",
        "subjectName": "数学",
        "topicName": "分数加减法",
        "errorTypeName": "计算错误"
      }
    ],
    "version": 0,
    "status": "ACTIVE",
    "expiresAt": "2026-10-11T08:00:00.000Z"
  }
}
```

<a id="drf-02"></a>

#### DRF-02 · 恢复有序选题草稿

`GET /api/mini/v1/students/{studentId}/paper-drafts/{draftId}`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| draftId | Path | string | 是 | 服务端草稿ID，可选分支 |

**规则与失败边界：**到期410，前端清理失效草稿，不把过期草稿误用给另一学生。

**输出data：**PaperDraft（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
GET /api/mini/v1/students/student-uuid/paper-drafts/draft-uuid HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "draft-uuid",
    "studentId": "student-uuid",
    "sourceType": "COLLECTION",
    "selectedTemplateId": null,
    "items": [
      {
        "id": "draft-item-uuid",
        "sortOrder": 0,
        "sourceType": "ENTRY",
        "sourceId": "entry-uuid",
        "inputRevisionId": null,
        "imageAssetId": "asset-uuid",
        "subjectName": "数学",
        "topicName": "分数加减法",
        "errorTypeName": "计算错误"
      }
    ],
    "version": 0,
    "status": "ACTIVE",
    "expiresAt": "2026-10-11T08:00:00.000Z"
  }
}
```

<a id="drf-03"></a>

#### DRF-03 · 选择草稿预选模板

`PATCH /api/mini/v1/students/{studentId}/paper-drafts/{draftId}`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| draftId | Path | string | 是 | 服务端草稿ID，可选分支 |
| version | Body | integer | 是 | 乐观锁或资源版本；修改时传当前版本 |
| selectedTemplateId | Body | string | 是 | 草稿／学生偏好预选模板，可空 |

**规则与失败边界：**null清除预选；仅更新草稿，不创建打印任务。

**输出data：**PaperDraft（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
PATCH /api/mini/v1/students/student-uuid/paper-drafts/draft-uuid HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
Content-Type: application/json

{
  "version": 0,
  "selectedTemplateId": "template-uuid"
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "draft-uuid",
    "studentId": "student-uuid",
    "sourceType": "COLLECTION",
    "selectedTemplateId": null,
    "items": [
      {
        "id": "draft-item-uuid",
        "sortOrder": 0,
        "sourceType": "ENTRY",
        "sourceId": "entry-uuid",
        "inputRevisionId": null,
        "imageAssetId": "asset-uuid",
        "subjectName": "数学",
        "topicName": "分数加减法",
        "errorTypeName": "计算错误"
      }
    ],
    "version": 0,
    "status": "ACTIVE",
    "expiresAt": "2026-10-11T08:00:00.000Z"
  }
}
```

<a id="drf-04"></a>

#### DRF-04 · 调整草稿题序

`PUT /api/mini/v1/students/{studentId}/paper-drafts/{draftId}/items/order`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| draftId | Path | string | 是 | 服务端草稿ID，可选分支 |
| version | Body | integer | 是 | 乐观锁或资源版本；修改时传当前版本 |
| itemIds | Body | array | 是 | 该任务／草稿当前项ID数组 |

**规则与失败边界：**全量排列，版本冲突返回当前草稿。

**输出data：**PaperDraft（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
PUT /api/mini/v1/students/student-uuid/paper-drafts/draft-uuid/items/order HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
Content-Type: application/json

{
  "version": 0,
  "itemIds": [
    "draft-item-uuid"
  ]
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "draft-uuid",
    "studentId": "student-uuid",
    "sourceType": "COLLECTION",
    "selectedTemplateId": null,
    "items": [
      {
        "id": "draft-item-uuid",
        "sortOrder": 0,
        "sourceType": "ENTRY",
        "sourceId": "entry-uuid",
        "inputRevisionId": null,
        "imageAssetId": "asset-uuid",
        "subjectName": "数学",
        "topicName": "分数加减法",
        "errorTypeName": "计算错误"
      }
    ],
    "version": 0,
    "status": "ACTIVE",
    "expiresAt": "2026-10-11T08:00:00.000Z"
  }
}
```

<a id="drf-05"></a>

#### DRF-05 · 从草稿删除打印项

`DELETE /api/mini/v1/students/{studentId}/paper-drafts/{draftId}/items/{itemId}`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| draftId | Path | string | 是 | 服务端草稿ID，可选分支 |
| itemId | Path | string | 是 | 目标资源ID，需校验当前账号及父路径归属 |
| version | Query | integer | 是 | 当前草稿版本 |

**规则与失败边界：**只删草稿项，不删错题；删除所有项后打印禁用。

**输出data：**PaperDraft（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
DELETE /api/mini/v1/students/student-uuid/paper-drafts/draft-uuid/items/item-uuid?version=0 HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "draft-uuid",
    "studentId": "student-uuid",
    "sourceType": "COLLECTION",
    "selectedTemplateId": null,
    "items": [
      {
        "id": "draft-item-uuid",
        "sortOrder": 0,
        "sourceType": "ENTRY",
        "sourceId": "entry-uuid",
        "inputRevisionId": null,
        "imageAssetId": "asset-uuid",
        "subjectName": "数学",
        "topicName": "分数加减法",
        "errorTypeName": "计算错误"
      }
    ],
    "version": 0,
    "status": "ACTIVE",
    "expiresAt": "2026-10-11T08:00:00.000Z"
  }
}
```

<a id="drf-06"></a>

#### DRF-06 · 放弃草稿

`DELETE /api/mini/v1/students/{studentId}/paper-drafts/{draftId}`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| draftId | Path | string | 是 | 服务端草稿ID，可选分支 |

**规则与失败边界：**仅逻辑删除／标EXPIRED，不删除来源资产。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| 无 | object | 无数据返回{}，已有接口可省略data |

**请求样例：**

```http
DELETE /api/mini/v1/students/student-uuid/paper-drafts/draft-uuid HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

### 模板与打印

<a id="tpl-01"></a>

#### TPL-01 · 分类加载模板示意图

`GET /api/mini/v1/template-categories`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| templatesPerCategory | Query | integer | 否 | 每类首屏数量，1～20 |

**规则与失败边界：**返回可用分类与首屏模板；PUBLIC资源但用户端账号认证便于授权，不能附私有题图。

**输出data：**TemplateCategory、Template（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
GET /api/mini/v1/template-categories?templatesPerCategory=8 HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": "category-uuid",
        "code": "A4",
        "name": "A4",
        "sortOrder": 0,
        "templates": [
          {
            "id": "template-uuid",
            "categoryId": "category-uuid",
            "code": "A4_TWO",
            "name": "A4双题",
            "status": "PUBLISHED",
            "sortOrder": 0,
            "currentVersionId": "template-version-uuid",
            "previewSvgAssetId": "preview-asset-uuid",
            "slotsPerPage": 2
          }
        ],
        "nextTemplateCursor": null,
        "hasMoreTemplates": false
      }
    ]
  }
}
```

<a id="tpl-02"></a>

#### TPL-02 · 横向加载同分类更多模板

`GET /api/mini/v1/template-categories/{categoryId}/templates`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| categoryId | Path | string | 是 | 模板分类ID |
| cursor | Query | string | 否 | 上一页nextCursor；首请求省略 |
| limit | Query | integer | 否 | 默认20，范围1～100；以能力配置为准 |

**输出data：**Template（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
GET /api/mini/v1/template-categories/category-uuid/templates?cursor=cursor-token&limit=20 HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": "template-uuid",
        "categoryId": "category-uuid",
        "code": "A4_TWO",
        "name": "A4双题",
        "status": "PUBLISHED",
        "sortOrder": 0,
        "currentVersionId": "template-version-uuid",
        "previewSvgAssetId": "preview-asset-uuid",
        "slotsPerPage": 2
      }
    ],
    "nextCursor": null,
    "hasMore": false
  }
}
```

<a id="tpl-03"></a>

#### TPL-03 · 读取指定发布模板版本

`GET /api/mini/v1/templates/{templateId}/versions/{versionId}`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| templateId | Path | string | 是 | 模板稳定ID |
| versionId | Path | string | 是 | 发布／草稿版本ID |

**规则与失败边界：**新任务只用PUBLISHED且模板启用的版本；旧任务版本详情按任务授权允许历史访问。

**输出data：**TemplateVersion（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
GET /api/mini/v1/templates/template-uuid/versions/version-uuid HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "template-version-uuid",
    "templateId": "template-uuid",
    "versionNo": 1,
    "status": "PUBLISHED",
    "paperWidthMm": "210.00",
    "paperHeightMm": "297.00",
    "orientation": "PORTRAIT",
    "slotsPerPage": 2,
    "layout": {
      "schemaVersion": 1,
      "unit": "mm",
      "slots": []
    },
    "previewSvgAssetId": "preview-asset-uuid",
    "rendererType": "DECLARATIVE",
    "rendererVersion": "1",
    "codeHash": null,
    "publishedAt": "2026-10-10T08:00:00.000Z"
  }
}
```

<a id="tpl-04"></a>

#### TPL-04 · 获取模板SVG示意图

`GET /api/mini/v1/templates/{templateId}/preview`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| templateId | Path | string | 是 | 模板稳定ID |
| versionId | Query | string | 是 | 确切版本ID |

**规则与失败边界：**返回contentType=image/svg+xml；示例访问结构同资产，SVG清理不安全外链／脚本。未发布草稿只管理员可见。

**输出data：**AssetAccess（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
GET /api/mini/v1/templates/template-uuid/preview?versionId=template-version-uuid HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "assetId": "preview-asset-uuid",
    "url": "https://files.example.test/signed/object?signature=example",
    "expiresAt": "2026-10-10T08:05:00.000Z",
    "contentType": "image/svg+xml"
  }
}
```

<a id="prt-01"></a>

#### PRT-01 · 选模板后创建正式打印任务

`POST /api/mini/v1/students/{studentId}/print-tasks`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| clientRequestId | Body | string | 是 | 同业务请求重试保持相同的UUID |
| templateVersionId | Body | string | 是 | 明确发布版本ID，不能仅传模板ID |
| sourceType | Body | string | 是 | 对象上下文：来源项ENTRY／REGION／PHOTO；任务COLLECTION／CAPTURE／RANDOM |
| copies | Body | integer | 是 | 打印份数1～能力配置上限 |
| sources | Body | array | 是 | 有序打印来源数组，每项sourceType/sourceId及PHOTO输入版本 |
| sources[].sourceType | Body | string | 是 | 对象上下文：来源项ENTRY／REGION／PHOTO；任务COLLECTION／CAPTURE／RANDOM |
| sources[].sourceId | Body | string | 是 | sourceType对应的ENTRY／REGION／PHOTO ID |
| sources[].inputRevisionId | Body | string／null | 是 | 固定输入图片版本，PHOTO来源必填 |

**规则与失败边界：**HTTP202；sources与draftId二选一，可替换整个sources为draftId；使用draftId须带draftVersion并成功后标SUBMITTED。不接受客户端templateSnapshot／objectKey。EMPTY输入400，旧来源版本409。

**输出data：**PrintTask（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
POST /api/mini/v1/students/student-uuid/print-tasks HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
Content-Type: application/json

{
  "clientRequestId": "print-request-uuid",
  "templateVersionId": "template-version-uuid",
  "sourceType": "COLLECTION",
  "copies": 1,
  "sources": [
    {
      "sourceType": "ENTRY",
      "sourceId": "entry-uuid",
      "inputRevisionId": null
    }
  ]
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "task-uuid",
    "studentId": "student-uuid",
    "templateVersionId": "template-version-uuid",
    "templateName": "A4双题",
    "sourceType": "COLLECTION",
    "status": "QUEUED",
    "itemCount": 1,
    "copies": 1,
    "pageCount": null,
    "outputPdfAssetId": null,
    "errorCode": null,
    "errorMessage": null,
    "createdAt": "2026-10-10T08:00:00.000Z",
    "finishedAt": null,
    "printConfirmedAt": null,
    "pollAfterMs": 1500
  }
}
```

<a id="prt-02"></a>

#### PRT-02 · 打印菜单任务列表

`GET /api/mini/v1/students/{studentId}/print-tasks`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| cursor | Query | string | 否 | 上一页nextCursor；首请求省略 |
| limit | Query | integer | 否 | 默认20，范围1～100；以能力配置为准 |
| status | Query | string | 否 | 可选打印状态 |

**输出data：**PrintTask（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
GET /api/mini/v1/students/student-uuid/print-tasks?cursor=cursor-token&limit=20&status=READY HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": "task-uuid",
        "studentId": "student-uuid",
        "templateVersionId": "template-version-uuid",
        "templateName": "A4双题",
        "sourceType": "COLLECTION",
        "status": "QUEUED",
        "itemCount": 1,
        "copies": 1,
        "pageCount": null,
        "outputPdfAssetId": null,
        "errorCode": null,
        "errorMessage": null,
        "createdAt": "2026-10-10T08:00:00.000Z",
        "finishedAt": null,
        "printConfirmedAt": null,
        "pollAfterMs": 1500
      }
    ],
    "nextCursor": null,
    "hasMore": false
  }
}
```

<a id="prt-03"></a>

#### PRT-03 · 轮询任务或读取快照详情

`GET /api/mini/v1/students/{studentId}/print-tasks/{taskId}`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| taskId | Path | string | 是 | 目标资源ID，需校验当前账号及父路径归属 |

**规则与失败边界：**终态停止轮询，pageCount/PDF只有生成后可用。

**输出data：**PrintTask、PrintItem（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
GET /api/mini/v1/students/student-uuid/print-tasks/task-uuid HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "task": {
      "id": "task-uuid",
      "studentId": "student-uuid",
      "templateVersionId": "template-version-uuid",
      "templateName": "A4双题",
      "sourceType": "COLLECTION",
      "status": "QUEUED",
      "itemCount": 1,
      "copies": 1,
      "pageCount": null,
      "outputPdfAssetId": null,
      "errorCode": null,
      "errorMessage": null,
      "createdAt": "2026-10-10T08:00:00.000Z",
      "finishedAt": null,
      "printConfirmedAt": null,
      "pollAfterMs": 1500
    },
    "items": [
      {
        "id": "print-item-uuid",
        "sortOrder": 0,
        "sourceType": "ENTRY",
        "sourceId": "entry-uuid",
        "imageAssetId": "asset-uuid",
        "contentSnapshot": {
          "schemaVersion": 1,
          "subject": {
            "id": "201",
            "name": "数学"
          },
          "topic": {
            "id": "301",
            "name": "分数加减法"
          },
          "errorType": {
            "id": "error-uuid",
            "name": "计算错误"
          }
        }
      }
    ]
  }
}
```

<a id="prt-04"></a>

#### PRT-04 · 原快照重试生成PDF

`POST /api/mini/v1/students/{studentId}/print-tasks/{taskId}/retry`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| taskId | Path | string | 是 | 目标资源ID，需校验当前账号及父路径归属 |
| clientRequestId | Body | string | 是 | 同业务请求重试保持相同的UUID |

**规则与失败边界：**仅FAILED可重试，HTTP202，重试状态QUEUED且记事件；换模板另建任务。

**输出data：**PrintTask（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
POST /api/mini/v1/students/student-uuid/print-tasks/task-uuid/retry HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
Content-Type: application/json

{
  "clientRequestId": "print-retry-uuid"
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "task-uuid",
    "studentId": "student-uuid",
    "templateVersionId": "template-version-uuid",
    "templateName": "A4双题",
    "sourceType": "COLLECTION",
    "status": "QUEUED",
    "itemCount": 1,
    "copies": 1,
    "pageCount": null,
    "outputPdfAssetId": null,
    "errorCode": null,
    "errorMessage": null,
    "createdAt": "2026-10-10T08:00:00.000Z",
    "finishedAt": null,
    "printConfirmedAt": null,
    "pollAfterMs": 1500
  }
}
```

<a id="prt-05"></a>

#### PRT-05 · 取消可取消的打印任务

`POST /api/mini/v1/students/{studentId}/print-tasks/{taskId}/cancel`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| taskId | Path | string | 是 | 目标资源ID，需校验当前账号及父路径归属 |

**规则与失败边界：**QUEUED／RENDERING尽力取消，READY及PRINT_CONFIRMED不能退回取消；409提示当前状态。

**输出data：**PrintTask（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
POST /api/mini/v1/students/student-uuid/print-tasks/task-uuid/cancel HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "task-uuid",
    "studentId": "student-uuid",
    "templateVersionId": "template-version-uuid",
    "templateName": "A4双题",
    "sourceType": "COLLECTION",
    "status": "QUEUED",
    "itemCount": 1,
    "copies": 1,
    "pageCount": null,
    "outputPdfAssetId": null,
    "errorCode": null,
    "errorMessage": null,
    "createdAt": "2026-10-10T08:00:00.000Z",
    "finishedAt": null,
    "printConfirmedAt": null,
    "pollAfterMs": 1500
  }
}
```

<a id="prt-06"></a>

#### PRT-06 · 获得生成PDF下载地址

`POST /api/mini/v1/students/{studentId}/print-tasks/{taskId}/download`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| taskId | Path | string | 是 | 目标资源ID，需校验当前账号及父路径归属 |

**规则与失败边界：**只允许READY／PRINT_CONFIRMED；签名过期可重新申请，申请／下载不当作已打印。记录DOWNLOADED事件表示文件取用请求，不声称已完成文件传输。

**输出data：**AssetAccess（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
POST /api/mini/v1/students/student-uuid/print-tasks/task-uuid/download HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "assetId": "pdf-asset-uuid",
    "url": "https://files.example.test/signed/object?signature=example",
    "expiresAt": "2026-10-10T08:05:00.000Z",
    "contentType": "application/pdf"
  }
}
```

<a id="prt-07"></a>

#### PRT-07 · 用户明确确认纸张已打印

`POST /api/mini/v1/students/{studentId}/print-tasks/{taskId}/confirm`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| taskId | Path | string | 是 | 目标资源ID，需校验当前账号及父路径归属 |
| clientRequestId | Body | string | 是 | 同业务请求重试保持相同的UUID |

**规则与失败边界：**用户主动点击确认才调用；打印服务回执为另外的签名服务接入，不由客户端伪造actorType。

**输出data：**PrintTask（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
POST /api/mini/v1/students/student-uuid/print-tasks/task-uuid/confirm HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
Content-Type: application/json

{
  "clientRequestId": "print-confirm-uuid"
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "task-uuid",
    "studentId": "student-uuid",
    "templateVersionId": "template-version-uuid",
    "templateName": "A4双题",
    "sourceType": "COLLECTION",
    "status": "PRINT_CONFIRMED",
    "itemCount": 1,
    "copies": 1,
    "pageCount": null,
    "outputPdfAssetId": null,
    "errorCode": null,
    "errorMessage": null,
    "createdAt": "2026-10-10T08:00:00.000Z",
    "finishedAt": null,
    "printConfirmedAt": "2026-10-10T08:00:00.000Z",
    "pollAfterMs": 1500
  }
}
```

<a id="prt-08"></a>

#### PRT-08 · 从用户列表隐藏历史任务

`DELETE /api/mini/v1/students/{studentId}/print-tasks/{taskId}`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| taskId | Path | string | 是 | 目标资源ID，需校验当前账号及父路径归属 |

**规则与失败边界：**软删／隐藏，生成中需先取消；不立即删除PDF或快照图片。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| 无 | object | 无数据返回{}，已有接口可省略data |

**请求样例：**

```http
DELETE /api/mini/v1/students/student-uuid/print-tasks/task-uuid HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

<a id="prt-09"></a>

#### PRT-09 · 查看本人打印任务状态历史

`GET /api/mini/v1/students/{studentId}/print-tasks/{taskId}/events`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| studentId | Path | string | 是 | 所属学生ID，必须属于登录账号 |
| taskId | Path | string | 是 | 目标资源ID，需校验当前账号及父路径归属 |
| cursor | Query | string | 否 | 上一页nextCursor；首请求省略 |
| limit | Query | integer | 否 | 默认20，范围1～100；以能力配置为准 |

**规则与失败边界：**返回用户可读事件，不泄露工作节点内部日志。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| items | array | 有序资源或请求项数组，结构按对应DTO解析 |
| items[].id | string | 业务主键，字符串传输 |
| items[].eventType | string | 任务状态或投递事件类型 |
| items[].fromStatus | string／null | 发生前任务状态，可空 |
| items[].toStatus | string | 发生后任务状态，可空 |
| items[].occurredAt | string | 事件发生UTC时间 |
| nextCursor | string／null | 下一页游标，最后一页为null |
| hasMore | boolean | 是否还有下一页 |

**请求样例：**

```http
GET /api/mini/v1/students/student-uuid/print-tasks/task-uuid/events?cursor=cursor-token&limit=20 HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": "event-uuid",
        "eventType": "CREATED",
        "fromStatus": null,
        "toStatus": "QUEUED",
        "occurredAt": "2026-10-10T08:00:00.000Z"
      }
    ],
    "nextCursor": null,
    "hasMore": false
  }
}
```

### 反馈与支持

<a id="fdb-01"></a>

#### FDB-01 · 提交意见反馈

`POST /api/mini/v1/feedbacks`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| clientRequestId | Body | string | 是 | 同业务请求重试保持相同的UUID |
| content | Body | string | 是 | 意见内容1～1000字符 |
| appVersion | Body | string | 否 | 客户端版本号 |
| platform | Body | string | 否 | 客户端平台 |

**规则与失败边界：**账号级不要求studentId，内容1～1000字符；成功后台持久化后才提示已提交。

**输出data：**Feedback（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
POST /api/mini/v1/feedbacks HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
Content-Type: application/json

{
  "clientRequestId": "feedback-request-uuid",
  "content": "希望支持更多模板",
  "appVersion": "0.1.0",
  "platform": "WECHAT_MINIAPP"
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "feedback-uuid",
    "content": "希望支持更多模板",
    "status": "NEW",
    "createdAt": "2026-10-10T08:00:00.000Z"
  }
}
```

<a id="fdb-02"></a>

#### FDB-02 · 查询本人反馈及处理状态

`GET /api/mini/v1/feedbacks`

**状态／认证：**已实现／账号 Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| cursor | Query | string | 否 | 上一页nextCursor；首请求省略 |
| limit | Query | integer | 否 | 默认20，范围1～100；以能力配置为准 |

**规则与失败边界：**不返回内部adminNote。当前UI未展示历史入口，作为后续客户端可用能力。

**输出data：**Feedback（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
GET /api/mini/v1/feedbacks?cursor=cursor-token&limit=20 HTTP/1.1
Host: api.example.test
Authorization: Bearer <token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": "feedback-uuid",
        "content": "希望支持更多模板",
        "status": "NEW",
        "createdAt": "2026-10-10T08:00:00.000Z"
      }
    ],
    "nextCursor": null,
    "hasMore": false
  }
}
```

### 管理员

<a id="adm-01"></a>

#### ADM-01 · 管理查询普通／会员账号

`GET /api/admin/mini/v1/accounts`

**状态／认证：**已实现／ADMIN Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| cursor | Query | string | 否 | 上一页nextCursor；首请求省略 |
| limit | Query | integer | 否 | 默认20，范围1～100；以能力配置为准 |
| phone | Query | string | 否 | 手机号精确／受控查询 |
| memberActive | Query | boolean | 否 | 会员是否有效 |
| enabled | Query | boolean | 否 | 启用状态 |

**规则与失败边界：**新namespace返回字符串ID及会员名；现有/api/admin/users分页结构不等同此契约。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| items | array | 有序资源或请求项数组，结构按对应DTO解析 |
| items[].id | string | 业务主键，字符串传输 |
| items[].memberName | string | 服务端会员显示名，独立于学生昵称 |
| items[].memberNo | string | 会员号，不作为鉴权凭据 |
| items[].memberExpireAt | string | 会员到期日DATE，可空 |
| items[].memberActive | boolean | 会员权益是否有效，不等同AI授权 |
| items[].role | string | USER／ADMIN |
| items[].aiEnabled | boolean | 独立AI开关 |
| items[].lastStudentId | string | 最近使用学生偏好，不代替请求studentId |
| items[].phone | string | 11位手机号 |
| items[].enabled | boolean | 配置或账号是否启用 |
| items[].cancelled | boolean | 任务是否已取消 |
| nextCursor | string／null | 下一页游标，最后一页为null |
| hasMore | boolean | 是否还有下一页 |

**请求样例：**

```http
GET /api/admin/mini/v1/accounts?cursor=cursor-token&limit=20&phone=13911112222&memberActive=true&enabled=true HTTP/1.1
Host: api.example.test
Authorization: Bearer <admin-token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": "101",
        "memberName": "拾星家长",
        "memberNo": "SX000101",
        "memberExpireAt": "2026-12-31",
        "memberActive": true,
        "role": "USER",
        "aiEnabled": true,
        "lastStudentId": "student-uuid",
        "phone": "13911112222",
        "enabled": true,
        "cancelled": false
      }
    ],
    "nextCursor": null,
    "hasMore": false
  }
}
```

<a id="adm-02"></a>

#### ADM-02 · 管理查看账号详情

`GET /api/admin/mini/v1/accounts/{userId}`

**状态／认证：**已实现／ADMIN Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| userId | Path | string | 是 | 账号ID；仅管理端可作为查询条件 |

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| id | string | 业务主键，字符串传输 |
| memberName | string | 服务端会员显示名，独立于学生昵称 |
| memberNo | string | 会员号，不作为鉴权凭据 |
| memberExpireAt | string | 会员到期日DATE，可空 |
| memberActive | boolean | 会员权益是否有效，不等同AI授权 |
| role | string | USER／ADMIN |
| aiEnabled | boolean | 独立AI开关 |
| lastStudentId | string | 最近使用学生偏好，不代替请求studentId |
| phone | string | 11位手机号 |
| enabled | boolean | 配置或账号是否启用 |
| cancelled | boolean | 任务是否已取消 |

**请求样例：**

```http
GET /api/admin/mini/v1/accounts/101 HTTP/1.1
Host: api.example.test
Authorization: Bearer <admin-token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "101",
    "memberName": "拾星家长",
    "memberNo": "SX000101",
    "memberExpireAt": "2026-12-31",
    "memberActive": true,
    "role": "USER",
    "aiEnabled": true,
    "lastStudentId": "student-uuid",
    "phone": "13911112222",
    "enabled": true,
    "cancelled": false
  }
}
```

<a id="adm-03"></a>

#### ADM-03 · 管理创建账号

`POST /api/admin/mini/v1/accounts`

**状态／认证：**已实现／ADMIN Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| phone | Body | string | 是 | 11位手机号 |
| password | Body | string | 是 | 密码，6～64字符，不保存明文 |
| memberName | Body | string | 是 | 服务端会员显示名，独立于学生昵称 |
| memberNo | Body | string | 否 | 会员号，不作为鉴权凭据 |
| memberExpireAt | Body | string | 否 | 会员到期日DATE，可空 |
| aiEnabled | Body | boolean | 否 | 独立AI开关 |

**规则与失败边界：**手机号唯一；密码后端哈希，响应不返回密码。管理员审计能力上线前补齐。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| id | string | 业务主键，字符串传输 |
| memberName | string | 服务端会员显示名，独立于学生昵称 |
| memberNo | string | 会员号，不作为鉴权凭据 |
| memberExpireAt | string | 会员到期日DATE，可空 |
| memberActive | boolean | 会员权益是否有效，不等同AI授权 |
| role | string | USER／ADMIN |
| aiEnabled | boolean | 独立AI开关 |
| lastStudentId | string | 最近使用学生偏好，不代替请求studentId |
| phone | string | 11位手机号 |
| enabled | boolean | 配置或账号是否启用 |
| cancelled | boolean | 任务是否已取消 |

**请求样例：**

```http
POST /api/admin/mini/v1/accounts HTTP/1.1
Host: api.example.test
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "phone": "13911112222",
  "password": "initial-password",
  "memberName": "拾星家长",
  "memberNo": "SX000101",
  "memberExpireAt": "2026-12-31",
  "aiEnabled": true
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "101",
    "memberName": "拾星家长",
    "memberNo": "SX000101",
    "memberExpireAt": "2026-12-31",
    "memberActive": true,
    "role": "USER",
    "aiEnabled": true,
    "lastStudentId": "student-uuid",
    "phone": "13911112222",
    "enabled": true,
    "cancelled": false
  }
}
```

<a id="adm-04"></a>

#### ADM-04 · 更新资料、权益和AI授权

`PATCH /api/admin/mini/v1/accounts/{userId}`

**状态／认证：**已实现／ADMIN Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| userId | Path | string | 是 | 账号ID；仅管理端可作为查询条件 |
| memberName | Body | string | 否 | 服务端会员显示名，独立于学生昵称 |
| memberNo | Body | string | 否 | 会员号，不作为鉴权凭据 |
| memberExpireAt | Body | string | 否 | 会员到期日DATE，可空 |
| aiEnabled | Body | boolean | 否 | 独立AI开关 |
| enabled | Body | boolean | 否 | 配置或账号是否启用 |

**规则与失败边界：**至少一字段；显式null可清会员号／到期日，aiEnabled独立，停用撤销会话。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| id | string | 业务主键，字符串传输 |
| memberName | string | 服务端会员显示名，独立于学生昵称 |
| memberNo | string | 会员号，不作为鉴权凭据 |
| memberExpireAt | string | 会员到期日DATE，可空 |
| memberActive | boolean | 会员权益是否有效，不等同AI授权 |
| role | string | USER／ADMIN |
| aiEnabled | boolean | 独立AI开关 |
| lastStudentId | string | 最近使用学生偏好，不代替请求studentId |
| phone | string | 11位手机号 |
| enabled | boolean | 配置或账号是否启用 |
| cancelled | boolean | 任务是否已取消 |

**请求样例：**

```http
PATCH /api/admin/mini/v1/accounts/101 HTTP/1.1
Host: api.example.test
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "memberName": "拾星家长",
  "memberNo": "SX000101",
  "memberExpireAt": "2026-12-31",
  "aiEnabled": true,
  "enabled": true
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "101",
    "memberName": "拾星家长",
    "memberNo": "SX000101",
    "memberExpireAt": "2026-12-31",
    "memberActive": true,
    "role": "USER",
    "aiEnabled": true,
    "lastStudentId": "student-uuid",
    "phone": "13911112222",
    "enabled": true,
    "cancelled": false
  }
}
```

<a id="adm-05"></a>

#### ADM-05 · 管理重置密码并撤销会话

`PUT /api/admin/mini/v1/accounts/{userId}/password`

**状态／认证：**已实现／ADMIN Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| userId | Path | string | 是 | 账号ID；仅管理端可作为查询条件 |
| password | Body | string | 是 | 密码，6～64字符，不保存明文 |

**规则与失败边界：**敏感操作须审计，不在客户端普通资料接口实现。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| 无 | object | 无数据返回{}，已有接口可省略data |

**请求样例：**

```http
PUT /api/admin/mini/v1/accounts/101/password HTTP/1.1
Host: api.example.test
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "password": "new-password"
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

<a id="adm-06"></a>

#### ADM-06 · 管理撤销会员权益

`DELETE /api/admin/mini/v1/accounts/{userId}/membership`

**状态／认证：**已实现／ADMIN Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| userId | Path | string | 是 | 账号ID；仅管理端可作为查询条件 |

**规则与失败边界：**目标与现有撤销语义保持清memberNo／memberExpireAt并关闭AI，保留历史归属；具体有效权益政策若修改须升文档版本。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| id | string | 业务主键，字符串传输 |
| memberName | string | 服务端会员显示名，独立于学生昵称 |
| memberNo | string | 会员号，不作为鉴权凭据 |
| memberExpireAt | string | 会员到期日DATE，可空 |
| memberActive | boolean | 会员权益是否有效，不等同AI授权 |
| role | string | USER／ADMIN |
| aiEnabled | boolean | 独立AI开关 |
| lastStudentId | string | 最近使用学生偏好，不代替请求studentId |
| phone | string | 11位手机号 |
| enabled | boolean | 配置或账号是否启用 |
| cancelled | boolean | 任务是否已取消 |

**请求样例：**

```http
DELETE /api/admin/mini/v1/accounts/101/membership HTTP/1.1
Host: api.example.test
Authorization: Bearer <admin-token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "101",
    "memberName": "拾星家长",
    "memberNo": "SX000101",
    "memberExpireAt": "2026-12-31",
    "memberActive": true,
    "role": "USER",
    "aiEnabled": true,
    "lastStudentId": "student-uuid",
    "phone": "13911112222",
    "enabled": true,
    "cancelled": false
  }
}
```

<a id="adm-07"></a>

#### ADM-07 · 只读微信绑定排查

`GET /api/admin/mini/v1/accounts/{userId}/identities`

**状态／认证：**已实现／ADMIN Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| userId | Path | string | 是 | 账号ID；仅管理端可作为查询条件 |

**规则与失败边界：**不提供未经确认的解绑／账号合并／共管入口。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| items | array | 有序资源或请求项数组，结构按对应DTO解析 |
| items[].id | string | 业务主键，字符串传输 |
| items[].provider | string | TENCENT_OCR／DASHSCOPE，按实际能力 |
| items[].appId | string | 目标微信小程序AppID |
| items[].maskedOpenid | string | 后台脱敏微信标识，不是登录凭证 |
| items[].status | string | 实体或任务状态，值域见对应DTO／状态机 |
| items[].boundAt | string | 身份绑定成功UTC时间 |

**请求样例：**

```http
GET /api/admin/mini/v1/accounts/101/identities HTTP/1.1
Host: api.example.test
Authorization: Bearer <admin-token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": "identity-uuid",
        "provider": "WECHAT_MINIAPP",
        "appId": "app-id",
        "maskedOpenid": "o***123",
        "status": "BOUND",
        "boundAt": "2026-10-10T08:00:00.000Z"
      }
    ]
  }
}
```

<a id="adm-08"></a>

#### ADM-08 · 只读账号学生诊断

`GET /api/admin/mini/v1/accounts/{userId}/students`

**状态／认证：**已实现／ADMIN Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| userId | Path | string | 是 | 账号ID；仅管理端可作为查询条件 |

**输出data：**Student（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
GET /api/admin/mini/v1/accounts/101/students HTTP/1.1
Host: api.example.test
Authorization: Bearer <admin-token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": "student-uuid",
        "nickname": "星星",
        "avatarAssetId": null,
        "grade": 5,
        "term": 1,
        "status": "ACTIVE",
        "sortOrder": 0,
        "createdAt": "2026-10-10T08:00:00.000Z",
        "updatedAt": "2026-10-10T08:00:00.000Z"
      }
    ]
  }
}
```

<a id="adm-09"></a>

#### ADM-09 · 查询归属与迁移诊断

`GET /api/admin/mini/v1/integrity-checks`

**状态／认证：**已实现／ADMIN Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| userId | Query | string | 否 | 指定账号 |
| studentId | Query | string | 否 | 指定学生 |

**规则与失败边界：**只读诊断，不由GET自动回填／删除历史数据。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| missingStudentCount | integer | 缺学生归属的历史记录数量 |
| crossStudentReferenceCount | integer | 跨学生非法引用数量 |
| orphanAssetCount | integer | 无引用候选资产数，不等同可立即删 |
| legacyIndexWarnings | array | 旧唯一索引阻断新作用域的诊断说明 |

**请求样例：**

```http
GET /api/admin/mini/v1/integrity-checks?userId=101&studentId=student-uuid HTTP/1.1
Host: api.example.test
Authorization: Bearer <admin-token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "missingStudentCount": 0,
    "crossStudentReferenceCount": 0,
    "orphanAssetCount": 0,
    "legacyIndexWarnings": []
  }
}
```

<a id="adm-10"></a>

#### ADM-10 · 管理模板分类列表

`GET /api/admin/mini/v1/template-categories`

**状态／认证：**已实现／ADMIN Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| 无 | — | — | — | 不需要请求参数或请求体 |

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| items | array | 有序资源或请求项数组，结构按对应DTO解析 |
| items[].id | string | 业务主键，字符串传输 |
| items[].code | string | 稳定代码；微信登录时为一次性wx.login code |
| items[].name | string | 显示名称 |
| items[].sortOrder | integer | 展示顺序，非负整数 |
| items[].templates | array | 分类首屏模板摘要数组，见Template |
| items[].templates[].id | string | 业务主键，字符串传输 |
| items[].templates[].categoryId | string | 模板分类ID |
| items[].templates[].code | string | 稳定代码；微信登录时为一次性wx.login code |
| items[].templates[].name | string | 显示名称 |
| items[].templates[].status | string | 实体或任务状态，值域见对应DTO／状态机 |
| items[].templates[].sortOrder | integer | 展示顺序，非负整数 |
| items[].templates[].currentVersionId | string | 该模板当前已发布版本ID |
| items[].templates[].previewSvgAssetId | string | 已校验系统SVG资产ID |
| items[].templates[].slotsPerPage | integer | 每页目标题框数，正整数 |
| items[].nextTemplateCursor | string／null | 同分类下一页模板游标，可空 |
| items[].hasMoreTemplates | boolean | 当前分类是否还有更多模板 |
| items[].enabled | boolean | 配置或账号是否启用 |

**请求样例：**

```http
GET /api/admin/mini/v1/template-categories HTTP/1.1
Host: api.example.test
Authorization: Bearer <admin-token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": "category-uuid",
        "code": "A4",
        "name": "A4",
        "sortOrder": 0,
        "templates": [
          {
            "id": "template-uuid",
            "categoryId": "category-uuid",
            "code": "A4_TWO",
            "name": "A4双题",
            "status": "PUBLISHED",
            "sortOrder": 0,
            "currentVersionId": "template-version-uuid",
            "previewSvgAssetId": "preview-asset-uuid",
            "slotsPerPage": 2
          }
        ],
        "nextTemplateCursor": null,
        "hasMoreTemplates": false,
        "enabled": true
      }
    ]
  }
}
```

<a id="adm-11"></a>

#### ADM-11 · 管理创建模板分类

`POST /api/admin/mini/v1/template-categories`

**状态／认证：**已实现／ADMIN Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| code | Body | string | 是 | 稳定代码；微信登录时为一次性wx.login code |
| name | Body | string | 是 | 显示名称 |
| sortOrder | Body | integer | 否 | 展示顺序，非负整数 |
| enabled | Body | boolean | 否 | 配置或账号是否启用 |

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| id | string | 业务主键，字符串传输 |
| code | string | 稳定代码；微信登录时为一次性wx.login code |
| name | string | 显示名称 |
| sortOrder | integer | 展示顺序，非负整数 |
| templates | array | 分类首屏模板摘要数组，见Template |
| templates[].id | string | 业务主键，字符串传输 |
| templates[].categoryId | string | 模板分类ID |
| templates[].code | string | 稳定代码；微信登录时为一次性wx.login code |
| templates[].name | string | 显示名称 |
| templates[].status | string | 实体或任务状态，值域见对应DTO／状态机 |
| templates[].sortOrder | integer | 展示顺序，非负整数 |
| templates[].currentVersionId | string | 该模板当前已发布版本ID |
| templates[].previewSvgAssetId | string | 已校验系统SVG资产ID |
| templates[].slotsPerPage | integer | 每页目标题框数，正整数 |
| nextTemplateCursor | string／null | 同分类下一页模板游标，可空 |
| hasMoreTemplates | boolean | 当前分类是否还有更多模板 |
| enabled | boolean | 配置或账号是否启用 |

**请求样例：**

```http
POST /api/admin/mini/v1/template-categories HTTP/1.1
Host: api.example.test
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "code": "A4",
  "name": "A4",
  "sortOrder": 0,
  "enabled": true
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "category-uuid",
    "code": "A4",
    "name": "A4",
    "sortOrder": 0,
    "templates": [
      {
        "id": "template-uuid",
        "categoryId": "category-uuid",
        "code": "A4_TWO",
        "name": "A4双题",
        "status": "PUBLISHED",
        "sortOrder": 0,
        "currentVersionId": "template-version-uuid",
        "previewSvgAssetId": "preview-asset-uuid",
        "slotsPerPage": 2
      }
    ],
    "nextTemplateCursor": null,
    "hasMoreTemplates": false,
    "enabled": true
  }
}
```

<a id="adm-12"></a>

#### ADM-12 · 管理修改分类显示／启停

`PATCH /api/admin/mini/v1/template-categories/{categoryId}`

**状态／认证：**已实现／ADMIN Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| categoryId | Path | string | 是 | 模板分类ID |
| name | Body | string | 否 | 显示名称 |
| sortOrder | Body | integer | 否 | 展示顺序，非负整数 |
| enabled | Body | boolean | 否 | 配置或账号是否启用 |

**规则与失败边界：**code创建后不改；停用拒绝新任务，历史仍可读取。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| id | string | 业务主键，字符串传输 |
| code | string | 稳定代码；微信登录时为一次性wx.login code |
| name | string | 显示名称 |
| sortOrder | integer | 展示顺序，非负整数 |
| templates | array | 分类首屏模板摘要数组，见Template |
| templates[].id | string | 业务主键，字符串传输 |
| templates[].categoryId | string | 模板分类ID |
| templates[].code | string | 稳定代码；微信登录时为一次性wx.login code |
| templates[].name | string | 显示名称 |
| templates[].status | string | 实体或任务状态，值域见对应DTO／状态机 |
| templates[].sortOrder | integer | 展示顺序，非负整数 |
| templates[].currentVersionId | string | 该模板当前已发布版本ID |
| templates[].previewSvgAssetId | string | 已校验系统SVG资产ID |
| templates[].slotsPerPage | integer | 每页目标题框数，正整数 |
| nextTemplateCursor | string／null | 同分类下一页模板游标，可空 |
| hasMoreTemplates | boolean | 当前分类是否还有更多模板 |
| enabled | boolean | 配置或账号是否启用 |

**请求样例：**

```http
PATCH /api/admin/mini/v1/template-categories/category-uuid HTTP/1.1
Host: api.example.test
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "name": "A4",
  "sortOrder": 0,
  "enabled": true
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "category-uuid",
    "code": "A4",
    "name": "A4",
    "sortOrder": 0,
    "templates": [
      {
        "id": "template-uuid",
        "categoryId": "category-uuid",
        "code": "A4_TWO",
        "name": "A4双题",
        "status": "PUBLISHED",
        "sortOrder": 0,
        "currentVersionId": "template-version-uuid",
        "previewSvgAssetId": "preview-asset-uuid",
        "slotsPerPage": 2
      }
    ],
    "nextTemplateCursor": null,
    "hasMoreTemplates": false,
    "enabled": true
  }
}
```

<a id="adm-13"></a>

#### ADM-13 · 管理模板列表

`GET /api/admin/mini/v1/templates`

**状态／认证：**已实现／ADMIN Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| cursor | Query | string | 否 | 上一页nextCursor；首请求省略 |
| limit | Query | integer | 否 | 默认20，范围1～100；以能力配置为准 |
| categoryId | Query | string | 否 | 模板分类 |
| status | Query | string | 否 | DRAFT／PUBLISHED／DISABLED |

**输出data：**Template（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
GET /api/admin/mini/v1/templates?cursor=cursor-token&limit=20&categoryId=category-uuid&status=PUBLISHED HTTP/1.1
Host: api.example.test
Authorization: Bearer <admin-token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": "template-uuid",
        "categoryId": "category-uuid",
        "code": "A4_TWO",
        "name": "A4双题",
        "status": "PUBLISHED",
        "sortOrder": 0,
        "currentVersionId": "template-version-uuid",
        "previewSvgAssetId": "preview-asset-uuid",
        "slotsPerPage": 2
      }
    ],
    "nextCursor": null,
    "hasMore": false
  }
}
```

<a id="adm-14"></a>

#### ADM-14 · 管理创建模板身份

`POST /api/admin/mini/v1/templates`

**状态／认证：**已实现／ADMIN Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| categoryId | Body | string | 是 | 模板分类ID |
| code | Body | string | 是 | 稳定代码；微信登录时为一次性wx.login code |
| name | Body | string | 是 | 显示名称 |
| sortOrder | Body | integer | 否 | 展示顺序，非负整数 |

**输出data：**Template（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
POST /api/admin/mini/v1/templates HTTP/1.1
Host: api.example.test
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "categoryId": "category-uuid",
  "code": "A4_TWO",
  "name": "A4双题",
  "sortOrder": 0
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "template-uuid",
    "categoryId": "category-uuid",
    "code": "A4_TWO",
    "name": "A4双题",
    "status": "DRAFT",
    "sortOrder": 0,
    "currentVersionId": null,
    "previewSvgAssetId": "preview-asset-uuid",
    "slotsPerPage": 2
  }
}
```

<a id="adm-15"></a>

#### ADM-15 · 修改模板摘要／停用

`PATCH /api/admin/mini/v1/templates/{templateId}`

**状态／认证：**已实现／ADMIN Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| templateId | Path | string | 是 | 模板稳定ID |
| name | Body | string | 否 | 显示名称 |
| sortOrder | Body | integer | 否 | 展示顺序，非负整数 |
| status | Body | string | 否 | 实体或任务状态，值域见对应DTO／状态机 |

**规则与失败边界：**不能PATCH自行设PUBLISHED绕过发布校验；启用需已有已发布版本。

**输出data：**Template（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
PATCH /api/admin/mini/v1/templates/template-uuid HTTP/1.1
Host: api.example.test
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "name": "A4双题",
  "sortOrder": 0,
  "status": "DISABLED"
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "template-uuid",
    "categoryId": "category-uuid",
    "code": "A4_TWO",
    "name": "A4双题",
    "status": "DISABLED",
    "sortOrder": 0,
    "currentVersionId": "template-version-uuid",
    "previewSvgAssetId": "preview-asset-uuid",
    "slotsPerPage": 2
  }
}
```

<a id="adm-16"></a>

#### ADM-16 · 新建模板草稿版本

`POST /api/admin/mini/v1/templates/{templateId}/versions`

**状态／认证：**已实现／ADMIN Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| templateId | Path | string | 是 | 模板稳定ID |
| paperWidthMm | Body | string | 是 | 十进制毫米字符串，必须大于0 |
| paperHeightMm | Body | string | 是 | 十进制毫米字符串，必须大于0 |
| orientation | Body | string | 是 | PORTRAIT／LANDSCAPE |
| slotsPerPage | Body | integer | 是 | 每页目标题框数，正整数 |
| layout | Body | object | 是 | 布局结构，与数据库layout_json映射 |
| layout.schemaVersion | Body | integer | 是 | 嵌套JSON结构版本，接口用camelCase，后端转换持久字段 |
| layout.unit | Body | string | 是 | 布局长度单位，固定mm |
| layout.paper | Body | object | 是 | 纸张宽高配置，单位mm |
| layout.paper.width | Body | integer | 是 | 图片像素或几何相对宽，依所在DTO约定 |
| layout.paper.height | Body | integer | 是 | 图片像素或几何相对高，依所在DTO约定 |
| layout.paper.orientation | Body | string | 是 | PORTRAIT／LANDSCAPE |
| layout.margins | Body | object | 是 | 纸张四边边距，单位mm |
| layout.margins.top | Body | integer | 是 | 上边距，单位mm |
| layout.margins.right | Body | integer | 是 | 右边距，单位mm |
| layout.margins.bottom | Body | integer | 是 | 下边距，单位mm |
| layout.margins.left | Body | integer | 是 | 左边距，单位mm |
| layout.slots | Body | array | 是 | 题目区域配置数组，渲染时按区域顺序放入题目 |
| layout.slots[].x | Body | integer | 是 | 左上角相对横坐标，0到1，x+width不大于1 |
| layout.slots[].y | Body | integer | 是 | 左上角相对纵坐标，0到1，y+height不大于1 |
| layout.slots[].width | Body | integer | 是 | 图片像素或几何相对宽，依所在DTO约定 |
| layout.slots[].height | Body | integer | 是 | 图片像素或几何相对高，依所在DTO约定 |
| layout.imageFit | Body | string | 是 | 图片适配方式，CONTAIN为保持比例完整显示 |
| layout.showAnswer | Body | boolean | 是 | 打印内容是否包含答案 |
| rendererType | Body | string | 是 | DECLARATIVE／HTML_CSS等受控引擎类型 |
| rendererVersion | Body | string | 是 | 固定渲染引擎版本 |
| codeContent | Body | string／null | 否 | 受控模板代码全文，只有后台可读写 |
| previewSvgAssetId | Body | string | 否 | 已校验系统SVG资产ID |

**规则与失败边界：**服务端分配versionNo；预览资产需通过ADM-19上传；发布前需生成可用SVG。

**输出data：**TemplateVersion（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
POST /api/admin/mini/v1/templates/template-uuid/versions HTTP/1.1
Host: api.example.test
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "paperWidthMm": "210.00",
  "paperHeightMm": "297.00",
  "orientation": "PORTRAIT",
  "slotsPerPage": 2,
  "layout": {
    "schemaVersion": 1,
    "unit": "mm",
    "paper": {
      "width": 210,
      "height": 297,
      "orientation": "PORTRAIT"
    },
    "margins": {
      "top": 12,
      "right": 12,
      "bottom": 12,
      "left": 12
    },
    "slots": [
      {
        "x": 12,
        "y": 12,
        "width": 186,
        "height": 130
      },
      {
        "x": 12,
        "y": 155,
        "width": 186,
        "height": 130
      }
    ],
    "imageFit": "CONTAIN",
    "showAnswer": false
  },
  "rendererType": "DECLARATIVE",
  "rendererVersion": "1",
  "codeContent": null,
  "previewSvgAssetId": "preview-asset-uuid"
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "template-version-uuid",
    "templateId": "template-uuid",
    "versionNo": 1,
    "status": "DRAFT",
    "paperWidthMm": "210.00",
    "paperHeightMm": "297.00",
    "orientation": "PORTRAIT",
    "slotsPerPage": 2,
    "layout": {
      "schemaVersion": 1,
      "unit": "mm",
      "paper": {
        "width": 210,
        "height": 297,
        "orientation": "PORTRAIT"
      },
      "margins": {
        "top": 12,
        "right": 12,
        "bottom": 12,
        "left": 12
      },
      "slots": [
        {
          "x": 12,
          "y": 12,
          "width": 186,
          "height": 130
        },
        {
          "x": 12,
          "y": 155,
          "width": 186,
          "height": 130
        }
      ],
      "imageFit": "CONTAIN",
      "showAnswer": false
    },
    "previewSvgAssetId": "preview-asset-uuid",
    "rendererType": "DECLARATIVE",
    "rendererVersion": "1",
    "codeHash": null,
    "publishedAt": null
  }
}
```

<a id="adm-17"></a>

#### ADM-17 · 管理读取版本及代码

`GET /api/admin/mini/v1/templates/{templateId}/versions`

**状态／认证：**已实现／ADMIN Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| templateId | Path | string | 是 | 模板稳定ID |

**规则与失败边界：**管理端可返回代码全文，用户TPL-03不能返回。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| items | array | 有序资源或请求项数组，结构按对应DTO解析 |
| items[].id | string | 业务主键，字符串传输 |
| items[].templateId | string | 模板稳定ID |
| items[].versionNo | integer | 模板内部递增版本号 |
| items[].status | string | 实体或任务状态，值域见对应DTO／状态机 |
| items[].paperWidthMm | string | 十进制毫米字符串，必须大于0 |
| items[].paperHeightMm | string | 十进制毫米字符串，必须大于0 |
| items[].orientation | string | PORTRAIT／LANDSCAPE |
| items[].slotsPerPage | integer | 每页目标题框数，正整数 |
| items[].layout | object | 布局结构，与数据库layout_json映射 |
| items[].layout.schemaVersion | integer | 嵌套JSON结构版本，接口用camelCase，后端转换持久字段 |
| items[].layout.unit | string | 布局长度单位，固定mm |
| items[].layout.slots | array | 题目区域配置数组，渲染时按区域顺序放入题目 |
| items[].previewSvgAssetId | string | 已校验系统SVG资产ID |
| items[].rendererType | string | DECLARATIVE／HTML_CSS等受控引擎类型 |
| items[].rendererVersion | string | 固定渲染引擎版本 |
| items[].codeHash | string／null | 代码内容SHA256，可空 |
| items[].publishedAt | string | 模板发布时间，可空 |
| items[].codeContent | string／null | 受控模板代码全文，只有后台可读写 |

**请求样例：**

```http
GET /api/admin/mini/v1/templates/template-uuid/versions HTTP/1.1
Host: api.example.test
Authorization: Bearer <admin-token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": "template-version-uuid",
        "templateId": "template-uuid",
        "versionNo": 1,
        "status": "PUBLISHED",
        "paperWidthMm": "210.00",
        "paperHeightMm": "297.00",
        "orientation": "PORTRAIT",
        "slotsPerPage": 2,
        "layout": {
          "schemaVersion": 1,
          "unit": "mm",
          "slots": []
        },
        "previewSvgAssetId": "preview-asset-uuid",
        "rendererType": "DECLARATIVE",
        "rendererVersion": "1",
        "codeHash": null,
        "publishedAt": "2026-10-10T08:00:00.000Z",
        "codeContent": null
      }
    ]
  }
}
```

<a id="adm-18"></a>

#### ADM-18 · 修改未发布完整版本

`PUT /api/admin/mini/v1/templates/{templateId}/versions/{versionId}`

**状态／认证：**已实现／ADMIN Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| templateId | Path | string | 是 | 模板稳定ID |
| versionId | Path | string | 是 | 发布／草稿版本ID |
| paperWidthMm | Body | string | 是 | 十进制毫米字符串，必须大于0 |
| paperHeightMm | Body | string | 是 | 十进制毫米字符串，必须大于0 |
| orientation | Body | string | 是 | PORTRAIT／LANDSCAPE |
| slotsPerPage | Body | integer | 是 | 每页目标题框数，正整数 |
| layout | Body | object | 是 | 布局结构，与数据库layout_json映射 |
| layout.schemaVersion | Body | integer | 是 | 嵌套JSON结构版本，接口用camelCase，后端转换持久字段 |
| layout.unit | Body | string | 是 | 布局长度单位，固定mm |
| layout.paper | Body | object | 是 | 纸张宽高配置，单位mm |
| layout.paper.width | Body | integer | 是 | 图片像素或几何相对宽，依所在DTO约定 |
| layout.paper.height | Body | integer | 是 | 图片像素或几何相对高，依所在DTO约定 |
| layout.paper.orientation | Body | string | 是 | PORTRAIT／LANDSCAPE |
| layout.margins | Body | object | 是 | 纸张四边边距，单位mm |
| layout.margins.top | Body | integer | 是 | 上边距，单位mm |
| layout.margins.right | Body | integer | 是 | 右边距，单位mm |
| layout.margins.bottom | Body | integer | 是 | 下边距，单位mm |
| layout.margins.left | Body | integer | 是 | 左边距，单位mm |
| layout.slots | Body | array | 是 | 题目区域配置数组，渲染时按区域顺序放入题目 |
| layout.slots[].x | Body | integer | 是 | 左上角相对横坐标，0到1，x+width不大于1 |
| layout.slots[].y | Body | integer | 是 | 左上角相对纵坐标，0到1，y+height不大于1 |
| layout.slots[].width | Body | integer | 是 | 图片像素或几何相对宽，依所在DTO约定 |
| layout.slots[].height | Body | integer | 是 | 图片像素或几何相对高，依所在DTO约定 |
| layout.imageFit | Body | string | 是 | 图片适配方式，CONTAIN为保持比例完整显示 |
| layout.showAnswer | Body | boolean | 是 | 打印内容是否包含答案 |
| rendererType | Body | string | 是 | DECLARATIVE／HTML_CSS等受控引擎类型 |
| rendererVersion | Body | string | 是 | 固定渲染引擎版本 |
| codeContent | Body | string／null | 否 | 受控模板代码全文，只有后台可读写 |
| previewSvgAssetId | Body | string | 否 | 已校验系统SVG资产ID |

**规则与失败边界：**仅DRAFT允许修改，已发布409；字段完整替换，布局、代码、引擎必须一致。

**输出data：**TemplateVersion（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
PUT /api/admin/mini/v1/templates/template-uuid/versions/version-uuid HTTP/1.1
Host: api.example.test
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "paperWidthMm": "210.00",
  "paperHeightMm": "297.00",
  "orientation": "PORTRAIT",
  "slotsPerPage": 2,
  "layout": {
    "schemaVersion": 1,
    "unit": "mm",
    "paper": {
      "width": 210,
      "height": 297,
      "orientation": "PORTRAIT"
    },
    "margins": {
      "top": 12,
      "right": 12,
      "bottom": 12,
      "left": 12
    },
    "slots": [
      {
        "x": 12,
        "y": 12,
        "width": 186,
        "height": 130
      },
      {
        "x": 12,
        "y": 155,
        "width": 186,
        "height": 130
      }
    ],
    "imageFit": "CONTAIN",
    "showAnswer": false
  },
  "rendererType": "DECLARATIVE",
  "rendererVersion": "1",
  "codeContent": null,
  "previewSvgAssetId": "preview-asset-uuid"
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "template-version-uuid",
    "templateId": "template-uuid",
    "versionNo": 1,
    "status": "DRAFT",
    "paperWidthMm": "210.00",
    "paperHeightMm": "297.00",
    "orientation": "PORTRAIT",
    "slotsPerPage": 2,
    "layout": {
      "schemaVersion": 1,
      "unit": "mm",
      "paper": {
        "width": 210,
        "height": 297,
        "orientation": "PORTRAIT"
      },
      "margins": {
        "top": 12,
        "right": 12,
        "bottom": 12,
        "left": 12
      },
      "slots": [
        {
          "x": 12,
          "y": 12,
          "width": 186,
          "height": 130
        },
        {
          "x": 12,
          "y": 155,
          "width": 186,
          "height": 130
        }
      ],
      "imageFit": "CONTAIN",
      "showAnswer": false
    },
    "previewSvgAssetId": "preview-asset-uuid",
    "rendererType": "DECLARATIVE",
    "rendererVersion": "1",
    "codeHash": null,
    "publishedAt": "2026-10-10T08:00:00.000Z"
  }
}
```

<a id="adm-19"></a>

#### ADM-19 · 上传受控系统SVG预览资产

`POST /api/admin/mini/v1/assets`

**状态／认证：**已实现／ADMIN Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| purpose | Body | string | 是 | ORIGINAL等资产用途；用户仅允许图片业务用途 |
| mimeType | Body | string | 是 | 真实文件MIME，不以扩展名代替内容检查 |
| svg | Body | string | 是 | 模板示意图SVG源码，服务端校验后存储并生成预览资产 |

**规则与失败边界：**服务端清理脚本／外链，校验大小再写COS。系统asset userId/studentId为空；不使用学生上传接口伪装公共资源。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| id | string | 业务主键，字符串传输 |
| purpose | string | ORIGINAL等资产用途；用户仅允许图片业务用途 |
| mimeType | string | 真实文件MIME，不以扩展名代替内容检查 |
| width | integer | 图片像素或几何相对宽，依所在DTO约定 |
| height | integer | 图片像素或几何相对高，依所在DTO约定 |
| sizeBytes | integer | 实际二进制字节数，正整数，限额由能力配置返回 |
| checksumSha256 | string | 64位十六进制内容摘要，声明仍需服务端校验 |
| status | string | 实体或任务状态，值域见对应DTO／状态机 |

**请求样例：**

```http
POST /api/admin/mini/v1/assets HTTP/1.1
Host: api.example.test
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "purpose": "TEMPLATE_PREVIEW",
  "mimeType": "image/svg+xml",
  "svg": "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"210\" height=\"297\"><rect x=\"12\" y=\"12\" width=\"186\" height=\"130\" fill=\"none\" stroke=\"#17447c\"/></svg>"
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "preview-asset-uuid",
    "purpose": "TEMPLATE_PREVIEW",
    "mimeType": "image/svg+xml",
    "width": 1200,
    "height": 800,
    "sizeBytes": 1024,
    "checksumSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
    "status": "AVAILABLE"
  }
}
```

<a id="adm-20"></a>

#### ADM-20 · 校验并发布不可变模板版本

`POST /api/admin/mini/v1/templates/{templateId}/versions/{versionId}/publish`

**状态／认证：**已实现／ADMIN Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| templateId | Path | string | 是 | 模板稳定ID |
| versionId | Path | string | 是 | 发布／草稿版本ID |

**规则与失败边界：**尺寸、slots、schema、受控代码、SVG及渲染验证通过后发布；旧任务仍绑定旧版本。

**输出data：**TemplateVersion（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
POST /api/admin/mini/v1/templates/template-uuid/versions/version-uuid/publish HTTP/1.1
Host: api.example.test
Authorization: Bearer <admin-token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "template-version-uuid",
    "templateId": "template-uuid",
    "versionNo": 1,
    "status": "PUBLISHED",
    "paperWidthMm": "210.00",
    "paperHeightMm": "297.00",
    "orientation": "PORTRAIT",
    "slotsPerPage": 2,
    "layout": {
      "schemaVersion": 1,
      "unit": "mm",
      "paper": {
        "width": 210,
        "height": 297,
        "orientation": "PORTRAIT"
      },
      "margins": {
        "top": 12,
        "right": 12,
        "bottom": 12,
        "left": 12
      },
      "slots": [
        {
          "x": 12,
          "y": 12,
          "width": 186,
          "height": 130
        },
        {
          "x": 12,
          "y": 155,
          "width": 186,
          "height": 130
        }
      ],
      "imageFit": "CONTAIN",
      "showAnswer": false
    },
    "previewSvgAssetId": "preview-asset-uuid",
    "rendererType": "DECLARATIVE",
    "rendererVersion": "1",
    "codeHash": null,
    "publishedAt": "2026-10-10T08:00:00.000Z"
  }
}
```

<a id="adm-21"></a>

#### ADM-21 · 查询模型和提示词配置

`GET /api/admin/mini/v1/agents`

**状态／认证：**已实现／ADMIN Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| 无 | — | — | — | 不需要请求参数或请求体 |

**规则与失败边界：**现有/api/admin/agents可复用服务，但此目标响应补provider和版本字段，不能声称当前旧DTO已有。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| items | array | 有序资源或请求项数组，结构按对应DTO解析 |
| items[].agentKey | string | Agent配置键EXPLAIN／ANALOGY等 |
| items[].name | string | 显示名称 |
| items[].systemPrompt | string | 后台系统提示词 |
| items[].userPromptTemplate | string | 后台用户提示词模板 |
| items[].provider | string | TENCENT_OCR／DASHSCOPE，按实际能力 |
| items[].model | string | 实际配置的模型名 |
| items[].temperature | number | 模型温度，值域由供应商与配置校验 |
| items[].maxTokens | integer | 模型输出token上限，正整数 |
| items[].enabled | boolean | 配置或账号是否启用 |
| items[].configVersion | integer | 配置修订号，修改使用预期当前版本 |
| items[].updatedAt | string | 更新时间UTC ISO8601 |

**请求样例：**

```http
GET /api/admin/mini/v1/agents HTTP/1.1
Host: api.example.test
Authorization: Bearer <admin-token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [
      {
        "agentKey": "EXPLAIN",
        "name": "做题精讲",
        "systemPrompt": "你是学习辅导助手。",
        "userPromptTemplate": "讲解以下题目。",
        "provider": "DASHSCOPE",
        "model": "configured-model",
        "temperature": 0.7,
        "maxTokens": 4096,
        "enabled": true,
        "configVersion": 1,
        "updatedAt": "2026-10-10T08:00:00.000Z"
      }
    ]
  }
}
```

<a id="adm-22"></a>

#### ADM-22 · 维护模型提示词并使缓存失效

`PUT /api/admin/mini/v1/agents/{agentKey}`

**状态／认证：**已实现／ADMIN Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| agentKey | Path | string | 是 | Agent配置键EXPLAIN／ANALOGY等 |
| name | Body | string | 是 | 显示名称 |
| systemPrompt | Body | string | 是 | 后台系统提示词 |
| userPromptTemplate | Body | string | 是 | 后台用户提示词模板 |
| provider | Body | string | 是 | TENCENT_OCR／DASHSCOPE，按实际能力 |
| model | Body | string | 是 | 实际配置的模型名 |
| temperature | Body | number | 是 | 模型温度，值域由供应商与配置校验 |
| maxTokens | Body | integer | 是 | 模型输出token上限，正整数 |
| enabled | Body | boolean | 是 | 配置或账号是否启用 |
| configVersion | Body | integer | 是 | 配置修订号，修改使用预期当前版本 |

**规则与失败边界：**configVersion作为预期版本，冲突4091；保存后服务端递增。密钥不在配置体内。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| agentKey | string | Agent配置键EXPLAIN／ANALOGY等 |
| name | string | 显示名称 |
| systemPrompt | string | 后台系统提示词 |
| userPromptTemplate | string | 后台用户提示词模板 |
| provider | string | TENCENT_OCR／DASHSCOPE，按实际能力 |
| model | string | 实际配置的模型名 |
| temperature | number | 模型温度，值域由供应商与配置校验 |
| maxTokens | integer | 模型输出token上限，正整数 |
| enabled | boolean | 配置或账号是否启用 |
| configVersion | integer | 配置修订号，修改使用预期当前版本 |
| updatedAt | string | 更新时间UTC ISO8601 |

**请求样例：**

```http
PUT /api/admin/mini/v1/agents/agentKey-uuid HTTP/1.1
Host: api.example.test
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "name": "做题精讲",
  "systemPrompt": "你是学习辅导助手。",
  "userPromptTemplate": "讲解以下题目。",
  "provider": "DASHSCOPE",
  "model": "configured-model",
  "temperature": 0.7,
  "maxTokens": 4096,
  "enabled": true,
  "configVersion": 1
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "agentKey": "EXPLAIN",
    "name": "做题精讲",
    "systemPrompt": "你是学习辅导助手。",
    "userPromptTemplate": "讲解以下题目。",
    "provider": "DASHSCOPE",
    "model": "configured-model",
    "temperature": 0.7,
    "maxTokens": 4096,
    "enabled": true,
    "configVersion": 1,
    "updatedAt": "2026-10-10T08:00:00.000Z"
  }
}
```

<a id="adm-23"></a>

#### ADM-23 · 逐次调用日志查询

`GET /api/admin/mini/v1/ai-calls`

**状态／认证：**已实现／ADMIN Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| cursor | Query | string | 否 | 上一页nextCursor；首请求省略 |
| limit | Query | integer | 否 | 默认20，范围1～100；以能力配置为准 |
| userId | Query | string | 否 | 账号ID |
| studentId | Query | string | 否 | 学生ID |
| provider | Query | string | 否 | TENCENT_OCR／DASHSCOPE |
| traceId | Query | string | 否 | 调用链ID |
| success | Query | boolean | 否 | 调用是否成功 |
| start | Query | datetime | 否 | UTC开始时间 |
| end | Query | datetime | 否 | UTC结束时间 |

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| items | array | 有序资源或请求项数组，结构按对应DTO解析 |
| items[].id | string | 业务主键，字符串传输 |
| items[].userId | string | 账号ID；仅管理端可作为查询条件 |
| items[].studentId | string | 所属学生ID，必须属于登录账号 |
| items[].provider | string | TENCENT_OCR／DASHSCOPE，按实际能力 |
| items[].aiType | string | AI调用业务类型 |
| items[].apiAction | string | 真实腾讯云Action或模型动作 |
| items[].requestId | string | 批量保存请求UUID，配合clientId幂等 |
| items[].traceId | string | 调用链追踪ID |
| items[].success | boolean | 实际调用是否成功，不能直接当作计费依据 |
| items[].attemptNo | integer | 阶段的实际第几次上游尝试 |
| items[].durationMs | integer | 本次调用耗时，毫秒 |
| items[].inputBytes | integer | 实际输入字节计量，口径依调用类型 |
| items[].outputBytes | integer | 实际输出字节计量，纯坐标可为0 |
| items[].inputTokens | integer／null | 真实输入token，供应商未返回时为null |
| items[].outputTokens | integer／null | 真实输出token，供应商未返回时为null |
| items[].errorCode | string／integer／null（任务错误码为string，调用日志为integer） | 业务错误码，可空 |
| items[].upstreamErrorCode | string／null | 腾讯云原始字符串错误码，仅脱敏管理员诊断 |
| items[].createdAt | string | 创建时间UTC ISO8601 |
| nextCursor | string／null | 下一页游标，最后一页为null |
| hasMore | boolean | 是否还有下一页 |

**请求样例：**

```http
GET /api/admin/mini/v1/ai-calls?cursor=cursor-token&limit=20&userId=101&studentId=student-uuid&provider=TENCENT_OCR&traceId=trace-id&success=true&start=2026-10-10T08:00:00.000Z&end=2026-10-10T08:00:00.000Z HTTP/1.1
Host: api.example.test
Authorization: Bearer <admin-token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": "701",
        "userId": "101",
        "studentId": "student-uuid",
        "provider": "TENCENT_OCR",
        "aiType": "ERASE",
        "apiAction": "EraseHandwrittenImageOCR",
        "requestId": "tencent-request-id",
        "traceId": "trace-id",
        "success": true,
        "attemptNo": 1,
        "durationMs": 1500,
        "inputBytes": 1024,
        "outputBytes": 800,
        "inputTokens": null,
        "outputTokens": null,
        "errorCode": null,
        "upstreamErrorCode": null,
        "createdAt": "2026-10-10T08:00:00.000Z"
      }
    ],
    "nextCursor": null,
    "hasMore": false
  }
}
```

<a id="adm-24"></a>

#### ADM-24 · AI流量及成功失败统计

`GET /api/admin/mini/v1/ai-statistics`

**状态／认证：**已实现／ADMIN Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| start | Query | datetime | 是 | UTC开始，含 |
| end | Query | datetime | 是 | UTC结束，不含 |
| userId | Query | string | 否 | 账号ID |

**规则与失败边界：**汇总实际记录，未知token／实际账单金额为空；不得凭success计算真实账单。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| callCount | integer | 筛选范围实际调用总次数 |
| successCount | integer | 筛选范围成功调用数 |
| failureCount | integer | 筛选范围失败调用数 |
| inputBytes | integer | 实际输入字节计量，口径依调用类型 |
| outputBytes | integer | 实际输出字节计量，纯坐标可为0 |
| inputTokens | integer／null | 真实输入token，供应商未返回时为null |
| outputTokens | integer／null | 真实输出token，供应商未返回时为null |
| billingAmount | string／null | 真实结算金额，未取得账单时为空 |

**请求样例：**

```http
GET /api/admin/mini/v1/ai-statistics?start=2026-10-10T08:00:00.000Z&end=2026-10-11T08:00:00.000Z&userId=101 HTTP/1.1
Host: api.example.test
Authorization: Bearer <admin-token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "callCount": 10,
    "successCount": 8,
    "failureCount": 2,
    "inputBytes": 10240,
    "outputBytes": 8000,
    "inputTokens": null,
    "outputTokens": null,
    "billingAmount": null
  }
}
```

<a id="adm-25"></a>

#### ADM-25 · 后台查询处理任务

`GET /api/admin/mini/v1/processing-jobs`

**状态／认证：**已实现／ADMIN Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| cursor | Query | string | 否 | 上一页nextCursor；首请求省略 |
| limit | Query | integer | 否 | 默认20，范围1～100；以能力配置为准 |
| userId | Query | string | 否 | 账号ID |
| studentId | Query | string | 否 | 学生ID |
| status | Query | string | 否 | 任务状态 |

**输出data：**ProcessingJob（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
GET /api/admin/mini/v1/processing-jobs?cursor=cursor-token&limit=20&userId=101&studentId=student-uuid&status=FAILED HTTP/1.1
Host: api.example.test
Authorization: Bearer <admin-token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": "job-uuid",
        "studentId": "student-uuid",
        "batchId": "batch-uuid",
        "operation": "ERASE",
        "applyScope": "CURRENT",
        "status": "QUEUED",
        "traceId": "trace-id",
        "items": [
          {
            "id": "job-item-uuid",
            "photoId": "photo-uuid",
            "inputRevisionId": "revision-uuid",
            "outputRevisionId": null,
            "status": "QUEUED",
            "attemptCount": 0,
            "stepResults": [],
            "errorCode": null,
            "errorMessage": null
          }
        ],
        "pollAfterMs": 1500,
        "createdAt": "2026-10-10T08:00:00.000Z",
        "finishedAt": null
      }
    ],
    "nextCursor": null,
    "hasMore": false
  }
}
```

<a id="adm-26"></a>

#### ADM-26 · 后台查看任务固定输入／快照

`GET /api/admin/mini/v1/processing-jobs/{taskId}`

**状态／认证：**已实现／ADMIN Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| taskId | Path | string | 是 | 目标资源ID，需校验当前账号及父路径归属 |

**规则与失败边界：**打印详情另含items，处理详情本体含items；读取私有图片另走受审计授权，不在列表全量暴露图。

**输出data：**ProcessingJob（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
GET /api/admin/mini/v1/processing-jobs/task-uuid HTTP/1.1
Host: api.example.test
Authorization: Bearer <admin-token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "job-uuid",
    "studentId": "student-uuid",
    "batchId": "batch-uuid",
    "operation": "ERASE",
    "applyScope": "CURRENT",
    "status": "QUEUED",
    "traceId": "trace-id",
    "items": [
      {
        "id": "job-item-uuid",
        "photoId": "photo-uuid",
        "inputRevisionId": "revision-uuid",
        "outputRevisionId": null,
        "status": "QUEUED",
        "attemptCount": 0,
        "stepResults": [],
        "errorCode": null,
        "errorMessage": null
      }
    ],
    "pollAfterMs": 1500,
    "createdAt": "2026-10-10T08:00:00.000Z",
    "finishedAt": null
  }
}
```

<a id="adm-27"></a>

#### ADM-27 · 后台有原因重试失败任务

`POST /api/admin/mini/v1/processing-jobs/{taskId}/retry`

**状态／认证：**已实现／ADMIN Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| taskId | Path | string | 是 | 目标资源ID，需校验当前账号及父路径归属 |
| clientRequestId | Body | string | 是 | 同业务请求重试保持相同的UUID |
| reason | Body | string | 是 | 管理／重试原因，必填非空；不能包含密钥 |
| itemIds | Body | array | 是 | 该任务／草稿当前项ID数组 |

**规则与失败边界：**HTTP202，固定输入／原快照；管理员变更由独立操作审计记录，云调用日志另行保留。

**输出data：**ProcessingJob（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
POST /api/admin/mini/v1/processing-jobs/task-uuid/retry HTTP/1.1
Host: api.example.test
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "clientRequestId": "admin-retry-uuid",
  "reason": "已修复临时上游问题",
  "itemIds": [
    "job-item-uuid"
  ]
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "job-uuid",
    "studentId": "student-uuid",
    "batchId": "batch-uuid",
    "operation": "ERASE",
    "applyScope": "CURRENT",
    "status": "QUEUED",
    "traceId": "trace-id",
    "items": [
      {
        "id": "job-item-uuid",
        "photoId": "photo-uuid",
        "inputRevisionId": "revision-uuid",
        "outputRevisionId": null,
        "status": "QUEUED",
        "attemptCount": 0,
        "stepResults": [],
        "errorCode": null,
        "errorMessage": null
      }
    ],
    "pollAfterMs": 1500,
    "createdAt": "2026-10-10T08:00:00.000Z",
    "finishedAt": null
  }
}
```

<a id="adm-28"></a>

#### ADM-28 · 后台查询打印任务

`GET /api/admin/mini/v1/print-tasks`

**状态／认证：**已实现／ADMIN Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| cursor | Query | string | 否 | 上一页nextCursor；首请求省略 |
| limit | Query | integer | 否 | 默认20，范围1～100；以能力配置为准 |
| userId | Query | string | 否 | 账号ID |
| studentId | Query | string | 否 | 学生ID |
| status | Query | string | 否 | 任务状态 |

**输出data：**PrintTask（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
GET /api/admin/mini/v1/print-tasks?cursor=cursor-token&limit=20&userId=101&studentId=student-uuid&status=FAILED HTTP/1.1
Host: api.example.test
Authorization: Bearer <admin-token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": "task-uuid",
        "studentId": "student-uuid",
        "templateVersionId": "template-version-uuid",
        "templateName": "A4双题",
        "sourceType": "COLLECTION",
        "status": "QUEUED",
        "itemCount": 1,
        "copies": 1,
        "pageCount": null,
        "outputPdfAssetId": null,
        "errorCode": null,
        "errorMessage": null,
        "createdAt": "2026-10-10T08:00:00.000Z",
        "finishedAt": null,
        "printConfirmedAt": null,
        "pollAfterMs": 1500
      }
    ],
    "nextCursor": null,
    "hasMore": false
  }
}
```

<a id="adm-29"></a>

#### ADM-29 · 后台查看任务固定输入／快照

`GET /api/admin/mini/v1/print-tasks/{taskId}`

**状态／认证：**已实现／ADMIN Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| taskId | Path | string | 是 | 目标资源ID，需校验当前账号及父路径归属 |

**规则与失败边界：**打印详情另含items，处理详情本体含items；读取私有图片另走受审计授权，不在列表全量暴露图。

**输出data：**PrintTask（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
GET /api/admin/mini/v1/print-tasks/task-uuid HTTP/1.1
Host: api.example.test
Authorization: Bearer <admin-token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "task-uuid",
    "studentId": "student-uuid",
    "templateVersionId": "template-version-uuid",
    "templateName": "A4双题",
    "sourceType": "COLLECTION",
    "status": "QUEUED",
    "itemCount": 1,
    "copies": 1,
    "pageCount": null,
    "outputPdfAssetId": null,
    "errorCode": null,
    "errorMessage": null,
    "createdAt": "2026-10-10T08:00:00.000Z",
    "finishedAt": null,
    "printConfirmedAt": null,
    "pollAfterMs": 1500
  }
}
```

<a id="adm-30"></a>

#### ADM-30 · 后台有原因重试失败任务

`POST /api/admin/mini/v1/print-tasks/{taskId}/retry`

**状态／认证：**已实现／ADMIN Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| taskId | Path | string | 是 | 目标资源ID，需校验当前账号及父路径归属 |
| clientRequestId | Body | string | 是 | 同业务请求重试保持相同的UUID |
| reason | Body | string | 是 | 管理／重试原因，必填非空；不能包含密钥 |

**规则与失败边界：**HTTP202，固定输入／原快照；管理员变更由独立操作审计记录，云调用日志另行保留。

**输出data：**PrintTask（字段定义见第3节）；集合／详情外层以样例为准。

**请求样例：**

```http
POST /api/admin/mini/v1/print-tasks/task-uuid/retry HTTP/1.1
Host: api.example.test
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "clientRequestId": "admin-retry-uuid",
  "reason": "已修复临时上游问题"
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "task-uuid",
    "studentId": "student-uuid",
    "templateVersionId": "template-version-uuid",
    "templateName": "A4双题",
    "sourceType": "COLLECTION",
    "status": "QUEUED",
    "itemCount": 1,
    "copies": 1,
    "pageCount": null,
    "outputPdfAssetId": null,
    "errorCode": null,
    "errorMessage": null,
    "createdAt": "2026-10-10T08:00:00.000Z",
    "finishedAt": null,
    "printConfirmedAt": null,
    "pollAfterMs": 1500
  }
}
```

<a id="adm-31"></a>

#### ADM-31 · 管理查询资产与清理候选

`GET /api/admin/mini/v1/assets`

**状态／认证：**已实现／ADMIN Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| cursor | Query | string | 否 | 上一页nextCursor；首请求省略 |
| limit | Query | integer | 否 | 默认20，范围1～100；以能力配置为准 |
| userId | Query | string | 否 | 账号ID |
| studentId | Query | string | 否 | 学生ID |
| status | Query | string | 否 | AVAILABLE／DELETE_PENDING等 |
| purpose | Query | string | 否 | 资产用途 |

**规则与失败边界：**referenceCount由实时引用查询计算，不是已建模永久计数字段。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| items | array | 有序资源或请求项数组，结构按对应DTO解析 |
| items[].id | string | 业务主键，字符串传输 |
| items[].purpose | string | ORIGINAL等资产用途；用户仅允许图片业务用途 |
| items[].mimeType | string | 真实文件MIME，不以扩展名代替内容检查 |
| items[].width | integer | 图片像素或几何相对宽，依所在DTO约定 |
| items[].height | integer | 图片像素或几何相对高，依所在DTO约定 |
| items[].sizeBytes | integer | 实际二进制字节数，正整数，限额由能力配置返回 |
| items[].checksumSha256 | string | 64位十六进制内容摘要，声明仍需服务端校验 |
| items[].status | string | 实体或任务状态，值域见对应DTO／状态机 |
| items[].storageProvider | string | 对象存储提供方，当前为COS |
| items[].bucket | string | 对象存储桶名称 |
| items[].region | string | 对象存储地域 |
| items[].objectKey | string | 存储对象键，仅供授权后台查看 |
| items[].referenceCount | integer | 实时资产引用数，不是已建模永久计数 |
| nextCursor | string／null | 下一页游标，最后一页为null |
| hasMore | boolean | 是否还有下一页 |

**请求样例：**

```http
GET /api/admin/mini/v1/assets?cursor=cursor-token&limit=20&userId=101&studentId=student-uuid&status=DELETE_PENDING&purpose=LEGACY_PACKAGE HTTP/1.1
Host: api.example.test
Authorization: Bearer <admin-token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": "asset-uuid",
        "purpose": "ORIGINAL",
        "mimeType": "image/png",
        "width": 1200,
        "height": 800,
        "sizeBytes": 1024,
        "checksumSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "status": "AVAILABLE",
        "storageProvider": "COS",
        "bucket": "test-bucket",
        "region": "ap-beijing",
        "objectKey": "users/101/object.png",
        "referenceCount": 1
      }
    ],
    "nextCursor": null,
    "hasMore": false
  }
}
```

<a id="adm-32"></a>

#### ADM-32 · 请求无引用资产清理

`POST /api/admin/mini/v1/assets/{assetId}/cleanup`

**状态／认证：**已实现／ADMIN Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| assetId | Path | string | 是 | 通过服务端验证的资产ID |
| clientRequestId | Body | string | 是 | 同业务请求重试保持相同的UUID |
| reason | Body | string | 是 | 管理／重试原因，必填非空；不能包含密钥 |

**规则与失败边界：**有引用／运行任务4092；状态可由AST-03对应管理列表检查。不得提供force=true绕过引用保护，COS失败可恢复。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| assetId | string | 通过服务端验证的资产ID |
| status | string | 实体或任务状态，值域见对应DTO／状态机 |

**请求样例：**

```http
POST /api/admin/mini/v1/assets/asset-uuid/cleanup HTTP/1.1
Host: api.example.test
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "clientRequestId": "cleanup-uuid",
  "reason": "过期无引用上传"
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "assetId": "asset-uuid",
    "status": "DELETE_PENDING"
  }
}
```

<a id="adm-33"></a>

#### ADM-33 · 管理查询反馈

`GET /api/admin/mini/v1/feedbacks`

**状态／认证：**已实现／ADMIN Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| cursor | Query | string | 否 | 上一页nextCursor；首请求省略 |
| limit | Query | integer | 否 | 默认20，范围1～100；以能力配置为准 |
| status | Query | string | 否 | NEW／REVIEWING／RESOLVED／CLOSED |

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| items | array | 有序资源或请求项数组，结构按对应DTO解析 |
| items[].id | string | 业务主键，字符串传输 |
| items[].content | string | 意见内容1～1000字符 |
| items[].status | string | 实体或任务状态，值域见对应DTO／状态机 |
| items[].createdAt | string | 创建时间UTC ISO8601 |
| items[].userId | string | 账号ID；仅管理端可作为查询条件 |
| items[].adminNote | string／null | 后台内部处理备注，不返回用户 |
| nextCursor | string／null | 下一页游标，最后一页为null |
| hasMore | boolean | 是否还有下一页 |

**请求样例：**

```http
GET /api/admin/mini/v1/feedbacks?cursor=cursor-token&limit=20&status=NEW HTTP/1.1
Host: api.example.test
Authorization: Bearer <admin-token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": "feedback-uuid",
        "content": "希望支持更多模板",
        "status": "NEW",
        "createdAt": "2026-10-10T08:00:00.000Z",
        "userId": "101",
        "adminNote": null
      }
    ],
    "nextCursor": null,
    "hasMore": false
  }
}
```

<a id="adm-34"></a>

#### ADM-34 · 处理反馈并保存内部备注

`PATCH /api/admin/mini/v1/feedbacks/{feedbackId}`

**状态／认证：**已实现／ADMIN Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| feedbackId | Path | string | 是 | 目标资源ID，需校验当前账号及父路径归属 |
| status | Body | string | 是 | 实体或任务状态，值域见对应DTO／状态机 |
| adminNote | Body | string | 是 | 后台内部处理备注，不返回用户 |

**规则与失败边界：**内部备注不返回普通用户；不自动发送短信／邮件／站外通知。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| id | string | 业务主键，字符串传输 |
| content | string | 意见内容1～1000字符 |
| status | string | 实体或任务状态，值域见对应DTO／状态机 |
| createdAt | string | 创建时间UTC ISO8601 |
| adminNote | string | 后台内部处理备注，不返回用户 |

**请求样例：**

```http
PATCH /api/admin/mini/v1/feedbacks/feedback-uuid HTTP/1.1
Host: api.example.test
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "status": "REVIEWING",
  "adminNote": "已记录并排查"
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "feedback-uuid",
    "content": "希望支持更多模板",
    "status": "REVIEWING",
    "createdAt": "2026-10-10T08:00:00.000Z",
    "adminNote": "已记录并排查"
  }
}
```

<a id="adm-35"></a>

#### ADM-35 · 查询队列投递和失败重试

`GET /api/admin/mini/v1/outbox-events`

**状态／认证：**已实现／ADMIN Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| cursor | Query | string | 否 | 上一页nextCursor；首请求省略 |
| limit | Query | integer | 否 | 默认20，范围1～100；以能力配置为准 |
| status | Query | string | 否 | PENDING／SENT／FAILED |

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| items | array | 有序资源或请求项数组，结构按对应DTO解析 |
| items[].id | string | 业务主键，字符串传输 |
| items[].aggregateType | string | PROCESSING_JOB／PRINT_TASK等消息聚合类型 |
| items[].aggregateId | string | 关联任务主键 |
| items[].eventType | string | 任务状态或投递事件类型 |
| items[].status | string | 实体或任务状态，值域见对应DTO／状态机 |
| items[].attemptCount | integer | 执行尝试次数 |
| items[].nextAttemptAt | string | 下一次消息投递重试UTC时间 |
| nextCursor | string／null | 下一页游标，最后一页为null |
| hasMore | boolean | 是否还有下一页 |

**请求样例：**

```http
GET /api/admin/mini/v1/outbox-events?cursor=cursor-token&limit=20&status=FAILED HTTP/1.1
Host: api.example.test
Authorization: Bearer <admin-token>
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": "outbox-uuid",
        "aggregateType": "PRINT_TASK",
        "aggregateId": "task-uuid",
        "eventType": "RENDER_REQUESTED",
        "status": "FAILED",
        "attemptCount": 2,
        "nextAttemptAt": "2026-10-10T08:00:00.000Z"
      }
    ],
    "nextCursor": null,
    "hasMore": false
  }
}
```

<a id="adm-36"></a>

#### ADM-36 · 恢复任务消息投递

`POST /api/admin/mini/v1/outbox-events/{eventId}/retry`

**状态／认证：**已实现／ADMIN Bearer。

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| eventId | Path | string | 是 | 目标资源ID，需校验当前账号及父路径归属 |
| reason | Body | string | 是 | 管理／重试原因，必填非空；不能包含密钥 |

**规则与失败边界：**不重新创建任务，消费者仍幂等；敏感操作由独立审计记录。

**输出data字段：**

| 字段 | 类型 | 说明 |
|---|---|---|
| id | string | 业务主键，字符串传输 |
| status | string | 实体或任务状态，值域见对应DTO／状态机 |

**请求样例：**

```http
POST /api/admin/mini/v1/outbox-events/event-uuid/retry HTTP/1.1
Host: api.example.test
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "reason": "队列已恢复"
}
```

**响应样例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "outbox-uuid",
    "status": "PENDING"
  }
}
```

## 5. 工具参数与特殊传输契约

### 5.1 JOB-01 parameters

所有参数以camelCase提交，持久化时保留schemaVersion；服务端按operation白名单转换腾讯云参数，不接受直接传任意Action或URL。

| operation | parameters字段 | 业务与云调用 |
|---|---|---|
| CORRECT | schemaVersion=1，crop=true，deskew=true，adjustOrientation=false | CropEnhanceImageOCR，校正／切边，可只返回位置 |
| ENHANCE | 上述字段＋enhanceType | CropEnhanceImageOCR；现有值-1不增强、1增亮、2增强锐化、3黑白、4灰度、5去阴影、6点阵，接入时核验供应商支持 |
| ERASE | schemaVersion=1 | EraseHandwrittenImageOCR，服务端EXIF旋转／归一化／重试 |
| SPLIT | schemaVersion=1，useNewModel=true | QuestionSplitLayoutOCR，生成question_region |
| PAPER_PROCESS | schemaVersion=1，enhance=true，erase=true，split=true | 按现有增强→切题→去手写；增强失败明确降级，其他关键阶段失败不发布整体成功 |

增强／校正不得覆盖原图。每阶段、每实际上游重试记录ai_call_log；若结果改变几何，要正确转换框或重新检测。临时上游结果URL需后端取回并校验存COS，不直接传客户端当长期原图。

### 5.2 COS上传调用（AST-01与AST-02之间）

这个步骤是COS请求，不套code/message/data：严格使用AST-01返回method、url、headers上传二进制文件，收到COS成功状态后调用AST-02。示意：

```http
PUT https://files.example.test/signed/upload HTTP/1.1
Content-Type: image/png

<photo.png的二进制内容>
```

成功通常为空响应体；按签名返回值确定允许请求头，不自行添加Authorization: Bearer应用令牌到COS。微信上传／下载域名需配置到小程序合法域名列表，若平台接口不支持该上传方式，使用后端代理适配而不是把永久密钥塞客户端。直传失败／签名过期可重新申请会话，但原完成状态先确认，避免重复对象。

AST-01示例采用签名PUT，不等于wx.uploadFile固定POST表单。客户端可用支持PUT的二进制请求；若采用wx.uploadFile，后端需明确返回POST签名表单fields并升契约，不得混用。当前CosBookStorage仍固定ZIP MIME，新资产接口必须真实MIME改造后才能宣称可用。

### 5.3 打印创建的服务器草稿分支

PRT-01直接sources模式示例已在详情给出；启用paperDraft后可以改为：

```json
{
  "clientRequestId":"print-request-uuid",
  "templateVersionId":"template-version-uuid",
  "sourceType":"RANDOM",
  "copies":1,
  "draftId":"draft-uuid",
  "draftVersion":2
}
```

sources与draftId严格互斥。服务器重新校验草稿学生、版本、未过期及源可用性；写任务、不可变快照及草稿SUBMITTED在同事务。模板停用／版本不合法409，不能静默换模板。正式任务创建后，删草稿项／改错题／模板升级都不修改任务。

## 6. 按业务整合的调用流程

本节每个步骤对应第1／4节接口编号。独立读取可并行，写入按依赖顺序执行；任何业务均不由“接口调用成功toast”代替真实结果状态。

| 业务 | 调用顺序 | 客户端完成条件／恢复 |
|---|---|---|
| 启动及未绑定身份 | wx.login → AUTH-02能力 → AUTH-03身份 | AUTH-02返回404仅表明未发布新契约；未绑定显示绑定入口，不调用学生API |
| 图形验证码绑定 | AUTH-01 → 用户填写手机号密码及图形码 → AUTH-04 → AUTH-07 → STU-01 | 账号信息真实返回；任何绑定失败换验证码，保留手机号但不持久保存密码 |
| 已绑定微信登录 | wx.login → AUTH-03(bound=true) → AUTH-05 → AUTH-07 → STU-01 | 不用客户端userId登录；遵守单会话替换，身份令牌不可访问学生数据 |
| 令牌过期 | 并发401汇集 → 单次AUTH-06 → 更新两Token → 重放可安全请求 | 4011不刷新；刷新失败清会话和旧账号草稿；登录请求不自动循环重试 |
| 新增／编辑学生 | STU-01 → STU-02或STU-03＋STU-04 → 刷新STU-01 | 首页昵称、年级从服务端学生对象读取，历史题年级不重写 |
| 切换学生 | STU-01选择 → STU-05 → STU-06＋SUB-01＋ERR-01＋ENT-02＋PRT-02 | 清理当前选题／页游标；进行中批次固定原学生，不能把结果保存给新学生 |
| 配置科目与主题 | SUB-01 → SUB-02 → TOP-01 → TOP-02 → 刷新分类 | 保存页读取新的列表；修改使用revision，4091展示当前版本 |
| 配置错误类型 | ERR-01 → ERR-02 → 刷新ERR-01及RND-01 | 新类型以ID参与搜索／组卷，不硬编码中文四类 |
| 单张拍摄 | CAP-01(SINGLE) → 相机拍照 → AST-01 → COS上传 → AST-02 → CAP-04 → CAP-05 → CAP-03 | 单张自动跳处理页；上传／校验失败不声称照片可处理 |
| 多页／相册导入 | CAP-01(MULTI) → 每张重复AST-01／COS／AST-02／CAP-04 → 用户完成CAP-05 → CAP-03 | 右下张数按成功加入批次照片显示；取消不自动删除已保存历史 |
| 最近拍摄恢复 | CAP-02 → CAP-03 → IMG-01 → AST-04 | 恢复同学生批次；资产过期不可读时提示，不借用他人地址 |
| 查看原图／删照片 | CAP-03＋IMG-01 → AST-04获取原／当前图URL；按住只切本地；CAP-07 → CAP-03 | 索引重新计算，无照片时禁保存／打印；不调用IMG-02来实现按住查看 |
| 单图／所有图AI处理 | IMG-01固定输入 → JOB-01 → JOB-02轮询 → IMG-01＋REG-01 → AST-04 | 整批固定targets；成功显示新版本，部分失败可JOB-03，取消JOB-04 |
| 手动框题 | CAP-03＋IMG-01 → REG-01 → 用户改框 → REG-02 | 区域绑定revision；后续几何变化不能沿用旧坐标，空框允许整图保存 |
| 分类保存错题 | SUB-01＋对应TOP-01＋ERR-01 → ENT-01 → 仅重试FAILED项 → ENT-02 | 选中区域按REGION或整图按PHOTO；所有成功再提示成功，不重复保存 |
| 首页和统一详情 | STU-06 → 用户点击ENT-03 → AST-04 | 首页消灭错题与错题集复用详情对象；已删题返回列表 |
| 搜索、全选、修改删除 | ENT-02筛选／分页 → 当前显示全选 → ENT-04或ENT-05 → ENT-02 | 全选默认当前加载／筛选可见项，不暗示服务器全量选中；若需全量选题另确认契约 |
| 做、讲、练 | ENT-03 → PRA-01／AGT-01／AGT-02 → PRA-02或刷新ENT-03 | 讲／练调用不计作答；生成变式不自动保存，主动保存变式需扩展来源契约再开发 |
| 错题集打印所选／详情打印 | ENT-02／ENT-03确定ENTRY sources → TPL-01＋TPL-02 → TPL-03＋TPL-04 → 本地删题或DRF-01～05 → PRT-01 | 点击模板不建任务；删草稿不是ENT-05；无模板／无题目禁提交 |
| 照片直接打印 | CAP-03／REG-01固定PHOTO或REGION sources → 通用TPL流程 → PRT-01 | 不强制先保存ENT-01，不必先有分类；PHOTO携带输入版本 |
| 随机组卷打印 | SUB-01＋TOP-01＋ERR-01 → RND-01 → RND-02 → 通用TPL流程 → PRT-01(sourceType=RANDOM) | count0不抽、不足按实际；同请求键同卷，过期要明确重新组卷 |
| 打印菜单与输出 | PRT-02 → PRT-03轮询 → READY后PRT-06 → 下载二进制PDF → 用户确认PRT-07 | 不将下载／分享当纸张完成；FAILED可PRT-04；去选题回错题集 |
| 打印取消／隐藏 | 非终态PRT-05 → 查PRT-03；历史PRT-08 → 刷新PRT-02 | 生成竞态按最新状态处理，隐藏不触发清理引用资产 |
| 用户帮助／反馈／关于 | PUBLIC-01(help/about)；FDB-01可选FDB-02 | 本地未接入反馈不提示后台提交成功，未绑定提交引导绑定 |
| 管理账号和权益 | 管理员先已有AUTH-01＋/api/auth/login → ADM-01／02 → ADM-03～06 | 后端ADMIN校验与审计；普通和会员都是USER，不能由前端切角色 |
| 管理模板发布 | ADM-10／11 → ADM-14 → ADM-19上传SVG → ADM-16／18 → ADM-20 → 用户TPL-01验证 | 发布前布局／渲染验证，发布后不可改，换版另建草稿 |
| 管理AI配置与排障 | ADM-21／22 → ADM-23／24 → ADM-25／26 → ADM-27（独立审计） | 保留实际Tencent Action、RequestId及逐次失败；后台不伪造未调用状态 |
| 管理打印故障 | ADM-28／29 → 原快照ADM-30 → ADM-28 | 改模板不重写旧任务；重试不复制正式任务，事件可追溯 |
| 资产清理／可靠投递 | ADM-31 → 引用校验ADM-32；ADM-35 → ADM-36 | 禁force误删；Outbox重投不重复执行业务任务 |
| 管理反馈／升级核查 | ADM-33 → ADM-34；ADM-07／08／09结合ORM升级检查 | 内部备注不泄露，未回填学生归属前不宣称可安全联调多学生 |

### 6.1 端到端示例：多页拍摄直接打印

```text
studentId固定为S
CAP-01 → batchId
[photo1, photo2]分别：AST-01 → 上传 → AST-02 → CAP-04
CAP-05 → CAP-03 → photos/currentRevisionId
用户选择去手写并勾选应用所有 → JOB-01(targets固定两张输入)
JOB-02轮询 → 若一张失败，只对失败item使用JOB-03
读取IMG-01／REG-01，用户确认范围
TPL-01 → TPL-04展示SVG → 选定templateVersionId
PRT-01(sources包含确认PHOTO／REGION)
PRT-03直到READY → PRT-06 → 下载PDF
仅用户明确确认打印后PRT-07
```

### 6.2 缓存、状态与恢复

- 当前学生记录只是偏好；在采集／草稿创建时冻结studentId并保留到结束。换账号清理旧账号Token和私有URL缓存。
- 列表缓存键包含账号、学生、筛选及版本；重复滑页不能混入另一个筛选结果。切换孩子重新请求，不复制旧列表。
- 照片临时路径仅作上传来源；完成后页面按assetId和revisionId恢复。签名URL到期重新申请AST-04，不覆盖资产对象。
- 写请求网络错误不等于失败；用原请求键重试或查询已返回任务ID。后台生成是异步，用户离开页面不取消服务端任务。
- 全选当前项的语义需UI表达清楚；若产品要求全筛选结果全选，不能让客户端靠分页无限拉取来暗中实现，应新增筛选快照批量选题协议。

## 7. 现有接口兼容与实施边界

新增契约不改旧 `/api/auth`、`/api/book`、`/api/ai`、`/api/agent`、`/api/v2` 的入出参。旧科目／主题是账号作用域，不能给它追加studentId参数就声称支持多学生；旧Base64保存和同步AI服务可以服务端复用，但新资产、来源、版本和异步任务必须显式实现。

| 当前已有族 | 已实现用途 | 客户端注意 |
|---|---|---|
| /api/auth | captcha/login/refresh/me/password/logout | 本文件复用的验证码与刷新保持原行为；微信绑定与新me使用新增路径 |
| /api/book/entries、/api/book/practices/history | 单条错题、图片、练习历史、旧随机 | 旧ZIP图片接口可继续读；不支持新学生路径与资产快照契约 |
| /api/v2/subjects/topics/preferences/book | 账号科目主题、批量保存、分类重编、查询抽题 | 保留账号作用域，查询仅覆盖student_id为空的历史记录，不用于新多学生客户端生产写入 |
| /api/ai | erase/crop-enhance/split-questions/paper-process | 现有同步Base64接口；新JOB队列、资产引用和逐次内层重试审计使用新增路径 |
| /api/agent | analogy/explain | 当前Agent协议由API.md说明，不能把旧body当本文件新entryId路径契约 |
| /api/admin/users/agents/ai-stats/keepalives | 用户、Agent、统计、心跳管理 | 新增管理接口见ADM章节 |
| /api/user/ability、/api/v2/user/ability、/api/client/keepalive | 原能力查询、客户端心跳 | 保留既有接口，不恢复已移除我的成长入口 |

现有全部路由与精确参数样例在API.md中继续保留；新增接口已实现，共享接口保持原行为。API.md是后台总入口，本文件是其新增小程序业务章节的完整子契约，避免两份同路径定义分叉。

### 7.1 实施顺序与发布门槛

1. 身份／微信会话／账号绑定＋学生隔离＋分类；普通会员边界以USER／权益／AI开关分别实现。
2. 上传资产／采集版本／手工框题／错题保存读取＋练习；旧ZIP保持兼容。
3. 受控模板版本／SVG／快照任务／PDF渲染／下载；Outbox或等价持久轮询保证恢复。
4. 腾讯云异步处理＋Agent结果缓存，配额与逐次日志；前端通过features决定真实按钮可用性。
5. 管理新接口、反馈、学生偏好／可选服务器草稿；管理员写操作已由mini_admin_audit记录。

发布必须同步Controller／DTO／Service／API文档并有鉴权、隔离、幂等、状态和失败恢复测试；未实现接口features=false。ORM不负责历史归属回填／旧唯一索引清理，按ORM-MAPPING.md核查完成后再开放多学生业务。

### 7.2 尚未冻结参数

本契约的limits示例是技术演示值，普通免费范围、会员专属范围、AI配额周期／失败计费、账号绑定微信数、存储与日志保留时长、掌握规则仍见USER-STORIES.md待确认项。模板纸张、范围和请求类型已定义；示例不代替商业确认。

变式主动保存来源不在当前协议中。服务器草稿、24小时持久请求回执和管理审计已实现，技术实体见DATABASE-MODEL.md。

### 7.3 服务端运行边界

模板首期只支持DECLARATIVE、rendererVersion=1、schemaVersion=1、毫米坐标和CONTAIN图片布局，showAnswer只能为false。代码片段可存储为不可变版本，但不会执行任意代码。copies已展开进PDF；READY表示文件生成成功，用户主动确认才进入PRINT_CONFIRMED，后台不检测物理出纸。

云识别、Agent与微信登录需要真实凭据，普通自动化测试采用上游桩。上传先写临时对象，验证后转为独立不可变资产，旧PUT地址不能覆盖已确认图片。临时对象清理需配置COS生命周期规则。本地存储额外提供上传／下载二进制端点，使用接口返回URL即可。Worker提供至少一次投递、10分钟租约与续租，基础设施失败最多3次自动投递；业务失败通过重试接口恢复。详见SERVER-IMPLEMENTATION.md。
