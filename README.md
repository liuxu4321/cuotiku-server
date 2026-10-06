# yycuotiku-server · 盈盈错题库后台

错题打印客户端（`../yycuotiku`）的配套后台服务。

技术栈：Maven 3 · Spring Boot 3.3 · Spring Security · MySQL 8 · Spring Data JPA · JWT

## 功能

- **登录**：手机号 + 密码 + 图形验证码（`GET /api/auth/captcha` 服务端生成，一次即废）
- **双令牌机制**：访问令牌（默认 2 小时）+ 刷新令牌（默认 30 天，轮换式），`POST /api/auth/refresh` 静默续期，桌面端可长期保持登录
- **单端登录踢出**：同一账号仅允许一个客户端在线，新登录后旧的一对令牌立即失效（错误码 `4011`）
- **授权信息**：`GET /api/auth/me` 返回手机号、会员号、角色、AI 权限、令牌到期时间
- **AI Agent**：举一反三（`/api/agent/analogy`）与做题精讲（`/api/agent/explain`），Spring AI Alibaba + qwen-vl-max 多模态；提示词/模型/温度后台「Agent管理」可配，登录+AI权限门控，每日配额默认20次；**结果按题缓存，每题每Agent仅调用一次大模型**（cached 标识命中，改提示词自动失效，后台可清缓存）
- **客户端心跳**：`POST /api/client/keepalive` 免登录上报（带有效令牌记账号、否则临时用户），每客户端单行最新记录；管理页「客户端管理」菜单查询客户端状态（关键字/仅在线筛选）
- **试卷处理（三合一）**：`POST /api/ai/paper-process`，一次调用串联切边增强→切题检测→整页去手写，返回成品整页图+归一化题框+三步骤状态
- **自动切题检测**：`POST /api/ai/split-questions`，对接腾讯云「试卷切题（仅检测）」，返回阅读序题框（像素+归一化坐标），支撑客户端拍照点选切题
- **图像切边增强**：`POST /api/ai/crop-enhance`，对接腾讯云 `CropEnhanceImageOCR`（切边/矫正/去阴影/多档增强），服务端代下载结果图转 Base64 返回
- **AI 去手写**：`POST /api/ai/erase`，腾讯云凭据（SecretId/SecretKey）由后台配置，客户端不再持有；仅 `aiEnabled` 用户可调用；响应携带腾讯云 `requestId` 与服务端 `traceId` 便于排障
- **会员有效期**：创建用户默认一年有效期（`memberExpireAt`），可修改/清除；`/api/auth/me` 返回 `memberActive` 供客户端展示会员状态；支持一键注销会员（清会员信息、关闭 AI 权限、踢下线并禁止登录，编辑重设会员信息可恢复）
- **用户能力模型**：`GET /api/user/ability`，客户端按登录态查询自己的能力五边形（细心度/理解力/概念清晰/规范度/练习勤奋度），默认同时返回三科+综合；含时间衰减与刷题动态缓解
- **公平随机组卷**：`POST /api/book/entries/random`，最少练习分层轮转算法（practiceCount 最低层优先、同层随机），保证错题全覆盖轮转，按错误类型配额抽题并返回各类型题池统计
- **错题管理**：`/api/book/*`，错题元数据存 `book_entry` 表（按用户隔离，含年级+学期 term=1上/2下），图片打包 zip（原图 + 480px 缩略图）存腾讯云 COS（未配置时回退本地目录），支持单条添加、分页筛选、图片下载、元数据更新、刷题计数与删除
- **AI 调用统计**：自动记录去手写/切边增强调用流水（成败、错误码、耗时、大小、traceId/RequestId），`GET /api/admin/ai-stats` 支持按用户/AI类型/时间范围查询并返回成功失败汇总
- **后台管理页面**：「拾星错题本后台管理」`/admin/index.html`（多菜单：用户管理 / AI调用统计 / 客户端管理 / Agent管理；管理员账号密码存于系统用户表 `sys_user`，`role=ADMIN`），支持用户增删改查、重置密码、开通 AI 权限、会员有效期管理、一键注销会员、停用/启用、角色调整

## 本地运行

先启动本地 MySQL（表结构由 JPA `ddl-auto=update` 自动创建）：

```bash
docker run -d --name yyc-mysql -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=root123456 -e MYSQL_DATABASE=yycuotiku mysql:8.0
```

再启动服务：

```bash
mvn spring-boot:run
```

默认管理员：手机号 `13800000000`，密码 `admin123456`（首次启动自动创建，生产环境务必通过环境变量修改）。

dev 默认连接 `localhost:3306/yycuotiku`（root/root123456），可用 `MYSQL_*` 环境变量覆盖。

## 配置（多环境 Profile）

配置分为三个文件：

- `application.yml`：公共配置（端口、JPA、验证码、腾讯云地域等），默认激活 `dev`
- `application-dev.yml`：开发/测试配置，带本地默认值，开箱即用
- `application-prod.yml`：生产配置，`JWT_SECRET`、`ADMIN_PHONE`、`ADMIN_PASSWORD`、`MYSQL_HOST`、`MYSQL_USERNAME`、`MYSQL_PASSWORD` **无默认值，必须注入环境变量**，缺失时启动失败

切换环境：`SPRING_PROFILES_ACTIVE=prod`（Docker 镜像已内置该变量；本地可用 `mvn spring-boot:run -Dspring-boot.run.profiles=prod` 或 `java -jar app.jar --spring.profiles.active=prod`）。

| 变量 | dev 默认值 | prod 默认值 | 说明 |
| --- | --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` | `prod`（镜像内置） | 激活的 profile |
| `PORT` | `8080` | `80`（镜像内置） | 服务端口 |
| `MYSQL_HOST` | `localhost` | **必填** | MySQL 主机 |
| `MYSQL_PORT` | `3306` | `3306` | MySQL 端口 |
| `MYSQL_DATABASE` | `yycuotiku` | `yycuotiku` | 数据库名（需提前创建，utf8mb4） |
| `MYSQL_USERNAME` | `root` | **必填** | 数据库用户 |
| `MYSQL_PASSWORD` | `root123456` | **必填** | 数据库密码 |
| `MYSQL_USE_SSL` | - | `false` | 生产库要求 SSL 时设为 `true` |
| `JWT_SECRET` | 内置开发密钥 | **必填** | JWT 签名密钥，≥32 字节 |
| `JWT_TTL_HOURS` | `2` | `2` | 访问令牌有效期（小时） |
| `JWT_REFRESH_TTL_DAYS` | `30` | `30` | 刷新令牌有效期（天） |
| `ADMIN_PHONE` / `ADMIN_PASSWORD` | `13800000000` / `admin123456` | **必填** | 首次启动初始化的管理员账号 |
| `TENCENT_SECRET_ID` / `TENCENT_SECRET_KEY` | 空 | 空 | 腾讯云 OCR（试卷手写擦除）凭据，为空时 AI 接口返回 503 |
| `TENCENT_OCR_REGION` | `ap-guangzhou` | `ap-guangzhou` | 腾讯云地域 |
| `COS_BUCKET` | `cuotiji-1257458058` | `cuotiji-1257458058` | 错题图片 COS 存储桶（置空则回退本地目录） |
| `COS_REGION` | `ap-beijing` | `ap-beijing` | COS 存储桶地域（cuotiji-1257458058 在北京） |
| `COS_SECRET_ID` / `COS_SECRET_KEY` | 复用 `TENCENT_*`（缺省为空则回退本地存储并告警） | 同左 | COS 访问密钥（需存储桶读写权限） |
| `BOOK_LOCAL_DIR` | `data/book-storage` | `data/book-storage` | COS 未启用时的本地存储目录 |
| `DASHSCOPE_API_KEY` | 哨兵值（Agent 返回 503） | 同左 | 通义千问 DashScope 密钥（Agent 能力） |
| `AGENT_DAILY_LIMIT` | `20` | `20` | 每用户每 Agent 每日调用配额 |

## v2 分类接口（产品 1.5.0）

科目/主题体系、批量加入幂等、三态编辑、重新归类、v2 查询/抽题/能力、偏好与 capabilities，见 [docs/API.md](docs/API.md) 第 9 节与冻结契约 `../project/versions/1.5.0/records/CONTRACT.md`。入口开关 `TAXONOMY_V2_ENABLED`（默认 false）。

## 客户端接口约定

完整接口文档（含字段说明、错误码、踢出机制、客户端对接清单与 curl 示例）见 [docs/API.md](docs/API.md)。

所有响应为 `{ "code": 0, "message": "ok", "data": ... }`，`code != 0` 表示失败；HTTP 401 且 `code=4011` 表示被踢下线。

```
GET  /api/auth/captcha          → { captchaId, imageBase64, expiresInSeconds }
POST /api/auth/login            ← { phone, password, captchaId, captchaCode, clientLabel? }
                                  → { token, expiresIn, refreshToken, refreshExpiresIn, user }
POST /api/auth/refresh          ← { refreshToken } → { token, expiresIn, refreshToken, refreshExpiresIn }
POST /api/auth/password         (Bearer token) ← { oldPassword, newPassword }（成功后当前令牌失效）
GET  /api/auth/me               (Bearer token) → { phone, memberNo, memberExpireAt, memberActive, role, aiEnabled, tokenExpiresAt }
POST /api/auth/logout           (Bearer token)
POST /api/ai/erase              (Bearer token) ← { imageBase64 } → { imageBase64, requestId, traceId }
POST /api/ai/paper-process      (Bearer token) ← { imageBase64, enhance?, erase?, split? }
                                  → { traceId, imageBase64, imageKind, width, height, questions[], enhance, erase, split }
POST /api/ai/split-questions    (Bearer token) ← { imageBase64, useNewModel? } → { width, height, questions[{index,x,y,width,height,nx,ny,nWidth,nHeight}], requestId, traceId }
POST /api/ai/crop-enhance       (Bearer token) ← { imageBase64, crop?, deskew?, adjustOrientation?, onlyPosition?, enhanceType? }
                                  → { imageBase64, width, height, position, angle, requestId, traceId }

POST /api/book/entries          (Bearer token) ← { grade, term?, subject, imageBase64, errorType? } → EntryDto
GET  /api/book/entries          (Bearer token) ?grade=&term=&subject=&errorType=&page=&size= → { items, total, page, size }
GET  /api/book/entries/{id}     (Bearer token) → EntryDto
GET  /api/book/entries/{id}/image?kind=original|thumb → 图片二进制
POST /api/book/entries/images   (Bearer token) ← { ids, kind? } → [{ id, contentType, imageBase64 }]
PUT  /api/book/entries/{id}     (Bearer token) ← { grade?, subject?, errorType? }
POST /api/book/entries/random   (Bearer token) ← { grade?, term?, subject?, counts{错误类型:数量} } → { items, requested, selected, byType }
POST /api/book/entries/practice (Bearer token) ← { ids } → { updated }
DELETE /api/book/entries/{id}   (Bearer token)
GET  /api/user/ability          (Bearer token) ?grade=&term=&subject=&start=&end= → { overall, subjects[3] }（五维雷达图数据）
POST /api/agent/analogy         (Bearer token) ← { entryId | imageBase64+subject+grade, count? } → { traceId, model, items[] }
POST /api/agent/explain         (Bearer token) ← { entryId | imageBase64+subject+grade } → { traceId, model, analysis, steps[], knowledgePoints[], commonMistakes[], summary }
GET  /api/admin/agents          (Bearer ADMIN)；PUT /api/admin/agents/{key}；POST /api/admin/agents/{key}/reset
POST /api/client/keepalive      (免登录，可带 Bearer token) ← { clientId, appVersion?, platform?, osVersion?, state?, detail? }
GET  /api/admin/keepalives      (Bearer ADMIN) ?onlineOnly=&keyword=&page=&size= → 客户端状态列表
```

`/api/ai/erase` 的 `imageBase64` 为客户端合成后的整图（与客户端现有 `EraseHandwrittenImageOCR` 调用一致），服务端使用后台配置的腾讯云凭据转发调用并返回擦除后的图片 Base64，拆分仍由客户端完成。

管理接口（仅 `ADMIN`）：`GET/POST /api/admin/users`，`GET/PUT/DELETE /api/admin/users/{id}`，`PUT /api/admin/users/{id}/password`，`DELETE /api/admin/users/{id}/membership`（一键注销会员），`GET /api/admin/ai-stats`（AI 调用统计）。

## 构建镜像与 sealos 部署

镜像已发布到 Docker Hub：`jefferliu/yycuotiku-server`（linux/amd64）。

本地重新构建并推送：

```bash
mvn -DskipTests package
docker build --platform linux/amd64 -t jefferliu/yycuotiku-server:1.21.1 -t jefferliu/yycuotiku-server:latest .
docker push jefferliu/yycuotiku-server:1.21.0
docker push jefferliu/yycuotiku-server:latest
```

sealos 容器平台部署要点：

1. 准备 MySQL 8：在 sealos 用「数据库 App」创建 MySQL 实例（或接入已有云数据库），创建 `yycuotiku` 库（`utf8mb4`）及账号。
2. 在 sealos「应用管理 / App Launchpad」创建应用，镜像填 `jefferliu/yycuotiku-server:1.21.0`（或 `latest`），暴露端口 `80`（镜像内置 `PORT=80`）。
3. 在环境变量中配置 `MYSQL_HOST`（集群内服务名）、`MYSQL_USERNAME`、`MYSQL_PASSWORD`、`JWT_SECRET`、`ADMIN_PHONE`、`ADMIN_PASSWORD`、`TENCENT_SECRET_ID`、`TENCENT_SECRET_KEY`。
4. 开启外网访问（或集群内 Ingress），客户端 `baseUrl` 指向该地址；后台管理页面为 `https://<域名>/admin/index.html`。

数据全部在 MySQL 与 COS 中，应用容器无状态，无需挂持久卷（仅当 `COS_BUCKET` 置空回退本地存储时才需要挂卷到 `/app/data`）。

单实例部署即可满足踢出机制（会话 jti 持久化在 MySQL 用户表中，重启后旧令牌依然可校验）。

## 测试

集成测试通过 Testcontainers 自动拉起真实 MySQL 8 容器（需本机 Docker 可用；若 Docker Engine ≥ 28 请配合 Testcontainers ≥ 1.21.4，本工程已锁定）：

```bash
mvn test
mvn package
```
