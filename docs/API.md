# 盈盈错题库后台 · 接口文档

版本：v1.21.0 · 服务：`yycuotiku-server` · 面向：错题打印客户端（Electron）开发者

> 版本变更记录见文末第 8 节。

- Base URL：`https://<部署域名>`（本地开发 `http://127.0.0.1:8080`）
- 数据格式：所有请求/响应均为 `application/json; charset=utf-8`
- 认证方式：`Authorization: Bearer <token>`（登录接口返回的 JWT）

## 1. 通用约定

### 1.1 响应包装

所有接口统一返回：

```json
{ "code": 0, "message": "ok", "data": { } }
```

- `code = 0` 表示成功，`data` 为业务数据（无数据时省略该字段）。
- `code != 0` 表示失败，`message` 为可直接展示给用户的中文提示。
- 业务错误码与 HTTP 状态码对应（见 1.3），`code=4011` 是唯一例外（HTTP 401）。

### 1.2 认证与踢出机制（双 Token）

登录成功后服务端签发两个令牌：

| 令牌 | 默认有效期 | 用途 |
| --- | --- | --- |
| `token`（访问令牌） | 2 小时 | 所有业务请求携带 `Authorization: Bearer <token>` |
| `refreshToken`（刷新令牌） | 30 天 | 仅用于调用 `POST /api/auth/refresh` 换发新令牌，**不能作为访问令牌使用** |

规则：

1. 访问令牌过期（`401`）时，客户端用刷新令牌静默换发一对新令牌，用户无感知；只要 30 天内使用过一次，桌面端即可一直保持登录（刷新令牌每次刷新后轮换，有效期重新计算）。
2. **同一账号仅允许一个客户端在线**。新登录成功后，旧的一对令牌立即失效，旧客户端下一次请求会收到：

```json
{ "code": 4011, "message": "该账号已在其他设备登录，当前设备已退出。如非本人操作，请立即修改密码" }
```

3. 客户端收到 `4011` 应清除本地两个令牌、终止当前任务，并提示用户"该账号已在其他设备登录"（附改密引导），跳转登录页。**收到 `4011` 后不要尝试刷新**，刷新同样会失败（`401`）。
4. **刷新轮换宽限期（120 秒）**：静默刷新会轮换令牌 jti；为避免客户端并发请求在轮换瞬间被误判为"其他设备登录"，轮换后 120 秒内旧访问令牌仍被接受（返回正常业务响应），旧刷新令牌在该窗口内调用刷新会重发当前会话令牌而不报错。仅当会话被**新登录**替换时才返回 `4011`。
4. 以下操作同样会使当前一对令牌全部失效：管理员重置密码、修改密码、停用账号、删除账号、用户退出登录。
5. 刷新令牌本身无效/过期/已轮换时，刷新接口返回 `401`，客户端应跳转登录页。

### 1.3 错误码总表

| code | HTTP 状态 | 含义 | 客户端处理建议 |
| --- | --- | --- | --- |
| 0 | 200 | 成功 | - |
| 400 | 400 | 参数错误 / 验证码错误 | 提示 `message` |
| 401 | 401 | 未登录、token 无效或过期、账号已删除 | 跳转登录页 |
| 403 | 403 | 无权限（非管理员 / 未开通 AI / 账号被停用） | 提示 `message` |
| 404 | 404 | 资源不存在 | 提示 `message` |
| 409 | 409 | 冲突（手机号已存在） | 提示 `message` |
| 4011 | 401 | 被踢下线（他端登录） | 清 token，提示"该账号已在其他设备登录"并引导改密，跳登录页 |
| 500 | 500 | 服务器内部错误 | 提示稍后重试 |
| 502 | 502 | 腾讯云上游错误 | 提示 `message`（含腾讯云错误翻译） |
| 503 | 503 | 后台未配置腾讯云凭据 | 提示联系管理员 |
| 504 | 504 | 调用腾讯云超时 | 提示稍后重试 |

### 1.4 时间格式

所有时间字段为字符串 `yyyy-MM-dd HH:mm:ss`（服务器时区）。

## 2. 认证接口

### 2.1 获取图形验证码

```
GET /api/auth/captcha
```

无需认证。**本接口为图形验证码（服务端生成 PNG 图片，用户肉眼识别输入），不是短信验证码**，客户端无需对接任何短信通道。

验证码 5 分钟有效，且**一次即废**：无论校验成功还是失败，该 `captchaId` 立即作废，每次登录尝试前都必须重新获取（点击验证码图片刷新）。

响应 `data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| captchaId | string | 验证码 ID，登录时原样传回 |
| imageBase64 | string | PNG 图片 data URI，可直接作为 `<img src>`，如 `data:image/png;base64,iVBOR...` |
| expiresInSeconds | number | 有效期（秒），当前为 300 |

示例：

```json
{
  "code": 0, "message": "ok",
  "data": { "captchaId": "373914f4...", "imageBase64": "data:image/png;base64,iVBOR...", "expiresInSeconds": 300 }
}
```

### 2.2 登录

```
POST /api/auth/login
```

无需认证。请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| phone | string | 是 | 11 位手机号，`1` 开头 |
| password | string | 是 | 6-64 位 |
| captchaId | string | 是 | 2.1 返回的验证码 ID |
| captchaCode | string | 是 | 用户输入的 4 位验证码（不区分大小写） |
| clientLabel | string | 否 | 客户端标识（如设备名），便于后台识别登录来源 |

响应 `data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| token | string | 访问令牌（JWT），后续请求放入 `Authorization: Bearer` |
| expiresIn | number | 访问令牌有效期（秒），默认 7200 |
| refreshToken | string | 刷新令牌（JWT），仅用于 2.3 刷新接口 |
| refreshExpiresIn | number | 刷新令牌有效期（秒），默认 2592000（30 天） |
| user | object | 用户信息，见 2.7 UserDto |

示例：

```json
{
  "phone": "13911112222", "password": "test123456",
  "captchaId": "373914f4...", "captchaCode": "A3K9", "clientLabel": "盈盈错题库桌面端"
}
```

失败场景：

| code | message | 说明 |
| --- | --- | --- |
| 400 | 验证码错误或已过期，请重新获取 | 验证码错误/过期/重复使用；需重新拉取验证码 |
| 401 | 手机号或密码错误 | 账号不存在或密码错误（不区分，防枚举） |
| 403 | 账号已被停用，请联系管理员 | 管理员停用了该账号 |

> 登录成功即触发踢出：该账号此前签发的访问令牌与刷新令牌全部立即失效。

### 2.3 刷新令牌

```
POST /api/auth/refresh
```

无需认证（凭刷新令牌本身鉴权）。请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| refreshToken | string | 是 | 登录或上次刷新返回的刷新令牌 |

响应 `data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| token | string | 新访问令牌 |
| expiresIn | number | 新访问令牌有效期（秒） |
| refreshToken | string | 新刷新令牌（**轮换**：旧的立即作废，客户端必须覆盖保存） |
| refreshExpiresIn | number | 新刷新令牌有效期（秒） |

示例：

```json
{ "refreshToken": "eyJhbGciOi..." }
```

失败场景（均返回 `401`，客户端应清除令牌并跳转登录页）：

| message | 说明 |
| --- | --- |
| 刷新凭证无效或已过期，请重新登录 | 伪造、篡改或超过 30 天未使用 |
| 凭证类型不正确，请重新登录 | 误把访问令牌当刷新令牌传入 |
| 该账号已在其他设备登录，请重新登录 | 会话被新登录替换（踢出） |
| 登录已失效，请重新登录 | 密码被重置、账号停用、宽限期已过等 |
| 账号不存在或已被删除 | 用户已被删除 |

> 建议客户端在访问令牌收到 `401` 时自动调用本接口重试一次原请求；刷新也失败则跳登录页。注意刷新会轮换刷新令牌，多窗口/并发请求场景需串行化刷新并共享新令牌。

### 2.4 退出登录

```
POST /api/auth/logout
```

需要认证。使当前访问令牌与刷新令牌全部失效。响应 `data` 为空：`{ "code": 0, "message": "ok" }`。

### 2.5 修改登录密码（登录用户）

```
POST /api/auth/password
```

需要认证。登录用户自助修改密码（管理端重置走 4.5）。请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| oldPassword | string | 是 | 原密码，错误返回 `400 原密码错误` |
| newPassword | string | 是 | 新密码 6-64 位；与原密码相同返回 `400` |

成功后**当前访问令牌与刷新令牌立即失效**（相当于强制重新登录），客户端应清除本地令牌并引导用新密码登录。响应 `data` 为空。

### 2.6 获取授权信息（me）

```
GET /api/auth/me
```

需要认证。**客户端启动/恢复会话时应调用此接口校验访问令牌并获取授权**（是否开通 AI、会员号等）。

响应 `data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| phone | string | 手机号 |
| memberNo | string\|null | 会员号 |
| memberExpireAt | string\|null | 会员有效期（`yyyy-MM-dd`，含当天）；`null` 表示非会员或未设置 |
| memberActive | boolean | 会员是否在有效期内（`memberExpireAt >= 今天`）；客户端可据此展示会员状态/到期提醒 |
| role | string | `USER` 普通用户 / `ADMIN` 管理员 |
| aiEnabled | boolean | 是否开通 AI 去手写权限；`false` 时客户端应隐藏/置灰 AI 功能 |
| tokenExpiresAt | string\|null | 访问令牌到期时间 `yyyy-MM-dd HH:mm:ss` |

示例：

```json
{
  "code": 0, "message": "ok",
  "data": {
    "phone": "13911112222", "memberNo": "VIP001", "memberExpireAt": "2027-09-28",
    "memberActive": true, "role": "USER",
    "aiEnabled": true, "tokenExpiresAt": "2026-10-05 10:20:30"
  }
}
```

### 2.7 UserDto（登录响应中的用户对象）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | number | 用户 ID |
| phone | string | 手机号 |
| memberNo | string\|null | 会员号 |
| memberExpireAt | string\|null | 会员有效期 `yyyy-MM-dd` |
| memberActive | boolean | 会员是否在有效期内 |
| role | string | `USER` / `ADMIN` |
| aiEnabled | boolean | AI 权限 |
| enabled | boolean | 账号是否启用 |
| cancelled | boolean | 是否已注销（注销后无法登录，见 4.6） |
| clientLabel | string\|null | 最近登录的客户端标识 |
| lastLoginAt | string\|null | 最近登录时间 |
| createdAt | string\|null | 创建时间 |

## 3. AI 接口

腾讯云凭据全部由后台配置，客户端无需持有；两个接口均要求 `aiEnabled = true`（未开通返回 `403`），响应均携带 `requestId`（腾讯云上游 RequestId）与 `traceId`（服务端日志追踪 ID）用于排障。

### 3.1 手写擦除（去手写）

```
POST /api/ai/erase
```

需要认证，且要求 `aiEnabled = true`。腾讯云 SecretId/SecretKey 由后台配置，**客户端不再持有、不再直连腾讯云**。

请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| imageBase64 | string | 是 | 待擦除图片的 Base64。支持纯 Base64 或 `data:image/...;base64,` 前缀（服务端自动剥离）。解码后不得超过 **9MB** |

响应 `data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| imageBase64 | string | 擦除后图片（PNG）的纯 Base64（无 data URI 前缀） |
| requestId | string\|null | 腾讯云上游 RequestId，向腾讯云排障/提工单时提供此 ID |
| traceId | string | 服务端日志追踪 ID（16 位），反馈问题时提供，可在后台日志中检索本次调用 |

示例：

```json
{
  "imageBase64": "/9j/4AAQSkZJRg...",
  "requestId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "traceId": "3f9c1a2b4d5e6f70"
}
```

> 排障建议：客户端在 AI 调用失败或结果异常时，将 `traceId` 与 `requestId`（成功响应才有）随用户反馈一并上报；服务端日志中每次擦除的请求/成功/失败均带 `traceId`，上游错误带腾讯云 `RequestId`。

失败场景：

| code | message 示例 | 说明 |
| --- | --- | --- |
| 400 | 图片Base64内容无效 / 图片超过9MB限制，请减少本次错题数量 | 请求内容问题 |
| 403 | 当前账号未开通AI去手写权限，请联系管理员 | `aiEnabled=false` |
| 502 | 腾讯云错误 FailedOperation.EngineRecognizeTimeout（RequestId=xxx）：擦除引擎处理超时，请稍后重试。 | 上游错误，message 含中文翻译与 RequestId |
| 503 | 后台尚未配置腾讯云AI凭据，请联系管理员 | 服务端未配置凭据 |
| 504 | 调用腾讯云超时，请稍后重试 | 上游超时（服务端超时 90s） |

服务端防护（v1.18.1 起）：调用擦除引擎前自动归一化图片；**v1.18.2 起所有 AI 接口与错题入库均先按 EXIF Orientation 旋转像素**（手机竖拍/横拍照片不再出现成品旋转 90° 的问题），（转 RGB、去除 alpha/非常规色彩模型、超 4096 边或 1200 万像素时等比缩小、重编码标准 JPEG），降低上游 `InternalError` 概率；遇 `InternalError` 自动重试一次。

### 3.2 图像切边增强


```
POST /api/ai/crop-enhance
```

需要认证，且要求 `aiEnabled = true`。对应腾讯云「图像切边增强」（`CropEnhanceImageOCR`）：面向文档类图片的切边、矫正、阴影去除、摩尔纹去除与增强，适合拍照导入的错题原图在框选/去手写前做质量优化。

请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| imageBase64 | string | 是 | 图片 Base64（PNG/JPG/JPEG/BMP，支持 data URI 前缀自动剥离），Base64 需 ≤ 10M，分辨率建议 600×800 以上 |
| crop | boolean | 否 | 是否切边，默认 `true` |
| deskew | boolean | 否 | 是否弯曲矫正，默认 `true` |
| adjustOrientation | boolean | 否 | 是否矫正图像方向（0/90/180/270），默认 `false` |
| onlyPosition | boolean | 否 | `true` 时只返回角点坐标不返回图片（`imageBase64` 为 null），默认 `false` |
| enhanceType | number | 否 | 增强类型：`-1` 不增强（默认）/ `1` 增亮 / `2` 增强并锐化 / `3` 黑白 / `4` 灰度 / `5` 去阴影增强 / `6` 点阵图；其他值返回 400 |

响应 `data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| imageBase64 | string\|null | 处理后图片（**JPG**）的纯 Base64；`onlyPosition=true` 时为 null |
| width / height | number\|null | 处理后图片的宽/高（像素） |
| position | number[8]\|null | 切图区域 4 角点坐标（基于原图）：`[x1,y1,x2,y2,x3,y3,x4,y4]` 依次为左上、右上、右下、左下 |
| angle | number\|null | 图像方向角度（仅 `adjustOrientation=true` 时有值：0/90/180/270，-1 表示识别失败） |
| requestId | string\|null | 腾讯云上游 RequestId（排障用） |
| traceId | string | 服务端日志追踪 ID（排障用） |

示例：

```json
{ "imageBase64": "/9j/4AAQSkZJRg...", "enhanceType": 2, "adjustOrientation": true }
```

失败场景：

| code | 说明 |
| --- | --- |
| 400 | 图片Base64内容无效 / 超过腾讯云10M限制 / 增强类型非法 |
| 403 | 当前账号未开通AI权限 |
| 502 | 腾讯云上游错误（message 含中文翻译，如引擎超时、图片解码失败） |
| 503 | 后台未配置腾讯云凭据 |
| 504 | 调用腾讯云超时（服务端超时 90s） |

> 频率限制：腾讯云该接口默认 5 次/秒，客户端请避免高频并发调用。
> 服务端行为说明：腾讯云当前版本不再内联返回图片（`CroppedImage` 已废弃），改为返回临时 `CroppedImageUrl`；服务端已代为下载并转成 Base64 返回，客户端无需访问腾讯云域名。

### 3.3 自动切题检测（点选切题）

```
POST /api/ai/split-questions
```

需要认证，且要求 `aiEnabled = true`。对接腾讯云「试卷切题（仅检测）」（`QuestionSplitLayoutOCR`）：对整页试卷/练习册照片自动检测题目边框，返回坐标供客户端"点选切题"交互使用。**只返回坐标，不返回图片**——切图仍由客户端本地完成。

请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| imageBase64 | string | 是 | 整页图片 Base64（PNG/JPG，支持 data URI 前缀），Base64 ≤ 10M，分辨率建议 600×800 以上 |
| useNewModel | boolean | 否 | 默认 `true`（多模态模型，更快更准，返回每题最外层边框）；`false` 返回题内子结构归并后的题框 |

响应 `data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| width / height | number | 检测基准图尺寸（像素） |
| questions | array | 题框列表，按阅读序（自上而下、自左而右）编号，见下 |
| requestId | string\|null | 腾讯云上游 RequestId |
| traceId | string | 服务端日志追踪 ID |

questions 单条：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| index | number | 阅读序编号（从 1 起） |
| x / y / width / height | number | 像素坐标外接矩形 |
| nx / ny / nWidth / nHeight | number | **归一化 0-1 坐标**（保留 4 位小数），与客户端选框 `selectionRegionSchema`（x/y/width/height 为 0-1）同构，可直接注入选框模型 |

服务端归一化规则：四边形坐标取外接矩形 → 按图尺寸归一化 → 过滤碎框（边长 <40px 或面积 <0.5%）→ 重叠/包含框去重（IoU 或包含率 >0.9）→ 阅读序排序。检测不到题目时 `questions` 为空数组（客户端应提示并回退手动框选）。

> 坐标对齐约定：服务端固定关闭腾讯侧切边（`EnableImageCrop=false`），坐标与**传入图片**严格对齐。若需先切边增强，请先调用 3.2 并用其返回图再切题。
> 频率限制：腾讯云该接口 2 次/秒；按钮触发式调用即可，勿轮询。

客户端对接（点选切题交互）：

1. 拍照/导入整页 →（可选）`/api/ai/crop-enhance` 增强 → 调本接口
2. 将 `questions` 的归一化坐标渲染为候选选框（虚线），点按切换选中（实线+序号）；选中框直接作为现有 selections 使用，移动/缩放/删除/手动加框交互全部复用
3. 确认后走现有管线：裁剪 → 合成 → `/api/ai/erase` → 加入错题集/组卷
4. 接口失败或 `aiEnabled=false`：提示后回退纯手动框选（现有 UX 不变）

### 3.4 试卷处理（三合一）

```
POST /api/ai/paper-process
```

需要认证，且要求 `aiEnabled = true`。**一次调用串联三个腾讯云接口**：切边增强（3.2）→ 切题检测（3.3）→ 整页去手写（3.1），返回处理完成的整页图与题框，客户端一次请求即可拿到"干净试卷 + 题目坐标"。

请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| imageBase64 | string | 是 | 整页试卷照片 Base64（PNG/JPG，支持 data URI 前缀），Base64 ≤ 10M |
| enhance | boolean | 否 | 是否切边增强，默认 `true` |
| erase | boolean | 否 | 是否整页去手写，默认 `true` |
| split | boolean | 否 | 是否切题检测，默认 `true` |

处理顺序与容错：

1. **切边增强**：失败时**回退原图**继续后续步骤（步骤状态 `success=false`），不中断
2. **切题检测**：基于增强后（或回退的）图检测题框；上游失败则**整体失败**
3. **整页去手写**：对增强后（或回退的）图整页擦除一次；上游失败则**整体失败**

> 整页一次擦除替代"客户端裁剪→合成→擦除→拆回"旧链路：去手写调用次数与题数无关。题框为归一化坐标，与返回图尺寸变化无关，可直接用于在返回图上裁剪各题。

响应 `data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| traceId | string | 本次处理的服务端追踪 ID（三个步骤流水共用） |
| imageBase64 | string | 成品整页图 Base64（去手写开启时为擦除后图，否则为增强图或原图） |
| imageKind | string | 成品图类型：`erased`（已去手写）/ `enhanced`（仅增强）/ `original`（原图） |
| width / height | number | 成品图尺寸（像素） |
| questions | array | 题框列表，结构同 3.3（归一化坐标，阅读序编号） |
| enhance / erase / split | object | 三步骤状态：`{ applied, success, requestId, errorCode, errorMessage }`；`applied=false` 表示请求关闭了该步骤 |

计费与统计：一次调用最多产生 3 次腾讯云调用，分别记入 `ai_call_log` 的 `CROP_ENHANCE` / `SPLIT_QUESTIONS` / `ERASE` 类型（共享 traceId），管理页统计可按类型查看。

失败场景：`403` 未开通 AI 权限；`400` 图片无效/超限；`502/504` 切题或去手写上游失败（message 含中文翻译与错误码）；`503` 后台未配置凭据。

客户端对接建议：拍照导入后直接调用本接口（超时 ≥120s）；用返回图替换画布原图，`questions` 注入候选选框进入点选模式；裁剪/合成/入错题集流程不变（无需再调去手写）。

### 3.5 AI Agent（举一反三 / 做题精讲）

基于 Spring AI Alibaba（DashScope 通义千问，默认 `qwen-vl-max` 多模态）的两个 Agent。**需要登录且 `aiEnabled = true`**；系统/用户提示词由后台「Agent管理」配置，即时生效。

```
POST /api/agent/analogy     举一反三：基于错题生成同考点变式题
POST /api/agent/explain     做题精讲：思路→分步讲解→知识点→易错提醒→总结
```

请求体（两接口相同）：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| entryId | string | 二选一 | 错题 ID（服务端自取错题原图与科目/年级/学期/错因，推荐） |
| imageBase64 | string | 二选一 | 直接传图（PNG/JPG，Base64 ≤ 10M）；此时 `subject`、`grade` 必填，`errorType` 可选 |
| subject | string | 传图时必填 | 语文 / 数学 / 英语 |
| grade | number | 传图时必填 | 1-9 |
| term | number | 否 | 1/2，仅影响提示词描述 |
| errorType | string | 否 | 马虎/不会/概念不清/其他，默认其他 |
| count | number | 否 | 仅 analogy：变式题数量 1-10，默认 3 |

`/api/agent/analogy` 响应 `data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| traceId | string | 服务端追踪 ID（缓存命中时为**首次分析**的 traceId） |
| model | string | 实际使用的模型 |
| cached | boolean | **是否命中缓存**（true 表示该题此前已分析过，本次未调用大模型） |
| items | array | `{ stem 题干, options[] 选项(填空可为空), answer 答案, analysis 解析, difficulty 1-3 }` |

`/api/agent/explain` 响应 `data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| traceId / model | string | 同上 |
| cached | boolean | 是否命中缓存 |
| analysis | string | 整体思路 |
| steps | array | `{ title, content }` 分步讲解 |
| knowledgePoints | string[] | 知识点 |
| commonMistakes | string[] | 易错提醒 |
| summary | string | 一句话总结 |

失败场景：`401` 未登录；`403` 未开通 AI 权限；`404` entryId 不存在；`400` 参数缺失/Agent 已停用；`429` 超出每日配额（默认每 Agent 20 次/天，`AGENT_DAILY_LIMIT` 可调）；`503` 未配置 `DASHSCOPE_API_KEY`；`502` 大模型调用/输出解析失败（重试一次后仍非合法 JSON）。

> 调用计入 `ai_call_log`（类型 `ANALOGY` / `EXPLAIN`），并记录 input/output tokens 供成本核算；客户端建议超时 ≥120s，失败展示 traceId 便于反馈。

**结果缓存（成本控制）**：每道错题每个 Agent **只调用一次大模型**，之后直接返回缓存结果：

- 缓存键 = Agent + 题目键 + 提示词指纹。题目键：传 `entryId` 时为 `entry:<id>`；直传图片时为 `img:<图片SHA-256>`（同一张图重复提交也命中）
- 提示词指纹 = 系统提示词 + 渲染后的用户提示词 + 模型 + 温度的 SHA-256：**后台修改提示词/模型/温度后旧缓存自动失效**，重新分析
- 命中缓存时：`cached=true`、不消耗每日配额、不产生大模型调用与 token 成本、不写 `ai_call_log`
- 管理端可手动清缓存：`DELETE /api/admin/agents/results?agentKey=&subjectKey=`（参数均可选，组合语义见 4.10），后台「Agent管理」每行提供「清空缓存」按钮

### 3.6 客户端对接说明（与现有流程的映射）

现有客户端 `src/main/services/tencent-erase.ts` 的流程保持不变，仅替换"调用腾讯云"这一步：

1. 客户端继续本地完成：裁剪（processCrops）→ 合成大图（compose，白底、gap=32）→ JPEG 压缩至接口限制（encodeForApi）。
2. 将合成图的 Base64 作为 `imageBase64` 调用 `POST /api/ai/erase`（替代原 `callEraseApi`，不再需要本地 SecretId/SecretKey 与 TC3 签名逻辑）。
3. 服务端返回擦除后整图 Base64，客户端继续按原 `placements` 与缩放比例容差逻辑拆分、增强（sharpen）。
4. 建议客户端请求超时设置 ≥ 100 秒（服务端上游超时 90 秒）。
5. 建议 Base64 体积控制与原逻辑一致（合成后 ≤ 9MB 解码上限，原客户端按 Base64 长度 9MB 限制，服务端按解码后字节数 9MB 限制，实际更宽松）。

## 4. 管理接口（仅 ADMIN）

以下接口需要认证且 `role = ADMIN`，否则返回 `403`。供「拾星错题本后台管理」页面（`/admin/index.html`，含用户管理、AI调用统计两个菜单）使用；桌面客户端一般无需对接，如客户端内嵌管理入口可参考。

### 4.1 分页查询用户

```
GET /api/admin/users?keyword={keyword}&page={page}&size={size}
```

| 参数 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| keyword | string | 空 | 模糊匹配手机号或会员号 |
| page | number | 0 | 页码，从 0 开始 |
| size | number | 20 | 每页条数，最大 200 |

响应 `data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| items | UserDto[] | 当前页用户列表（按 ID 倒序） |
| total | number | 总条数 |
| page | number | 当前页码 |
| size | number | 每页条数 |

### 4.2 查询单个用户

```
GET /api/admin/users/{id}
```

响应 `data`：UserDto。用户不存在返回 `404`。

### 4.3 创建用户

```
POST /api/admin/users
```

请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| phone | string | 是 | 11 位手机号，全局唯一，重复返回 `409` |
| memberNo | string | 否 | 会员号，最长 64 |
| memberExpireAt | string | 否 | 会员有效期 `yyyy-MM-dd`；**不传时默认为一年后**；格式错误返回 `400` |
| password | string | 是 | 初始密码，6-64 位 |
| aiEnabled | boolean | 否 | 是否开通 AI 权限，默认 `false` |
| enabled | boolean | 否 | 是否启用，默认 `true` |
| role | string | 否 | `USER`（默认）/ `ADMIN` |

响应 `data`：UserDto。

### 4.4 更新用户

```
PUT /api/admin/users/{id}
```

请求体（所有字段可选，仅更新传入的字段）：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| memberNo | string\|null | 会员号，传空字符串/null 表示清除 |
| memberExpireAt | string\|null | 会员有效期 `yyyy-MM-dd`；传空字符串表示清除（变为非会员）；不传（null）表示不修改；格式错误返回 `400` |
| password | string | 新密码（6-64 位）；修改后该用户被踢下线 |
| aiEnabled | boolean | AI 权限 |
| enabled | boolean | 停用（false）后该用户被踢下线且无法登录；不能停用自己 |
| role | string | `USER` / `ADMIN`；不能取消自己的管理员角色 |

响应 `data`：更新后的 UserDto。

保护规则（均返回 `400`）：不能停用自己；不能取消自己的 ADMIN 角色。

### 4.5 重置密码

```
PUT /api/admin/users/{id}/password
```

请求体：`{ "password": "newpass123" }`（6-64 位）。重置后该用户当前会话被踢下线。响应 `data` 为空。

### 4.6 一键注销会员

```
DELETE /api/admin/users/{id}/membership
```

注销该用户账号，效果：

1. 清除会员号与会员有效期，关闭 AI 权限（`aiEnabled=false`），标记 `cancelled=true`
2. **立即踢下线**：该用户当前的访问令牌与刷新令牌全部失效
3. **禁止登录**：注销用户登录时返回与「密码错误」完全相同的响应（`401 手机号或密码错误`），不区分提示以防账号探测；客户端无需任何特殊处理
4. 已登录客户端的下一次请求收到 `401`，走统一的重新登录流程（登录亦失败，同上）

**恢复方式**：管理员编辑该用户（4.4），重新填写会员号或会员有效期，账号自动恢复正常（`cancelled=false`），即可再次登录。

保护规则：不能注销自己的账号（返回 `400`）。响应 `data`：注销后的 UserDto（`cancelled=true`）。

### 4.7 删除用户

```
DELETE /api/admin/users/{id}
```

删除后该用户 token 立即失效（下次请求返回 `401`）。保护规则（返回 `400`）：不能删除自己；系统至少保留一个管理员。响应 `data` 为空。

### 4.10 Agent 管理

```
GET    /api/admin/agents                 Agent 配置列表（含可用模板变量）
PUT    /api/admin/agents/{key}           更新配置（key: ANALOGY / EXPLAIN）
POST   /api/admin/agents/{key}/reset     恢复内置默认提示词与模型
```

`PUT` 请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| systemPrompt | string | 是 | 系统提示词 |
| userPromptTemplate | string | 是 | 用户提示词模板，占位符 `{变量}`；ANALOGY 可用：subject/grade/term/errorType/count；EXPLAIN 可用：subject/grade/term/errorType |
| model | string | 是 | 模型名（默认 qwen-vl-max，可改 qwen-vl-plus/qwen-max 等） |
| temperature | number | 否 | 0-2 |
| maxTokens | number | 否 | 1-32000 |
| enabled | boolean | 否 | 停用后客户端调用返回 400 |

响应 `data`：`{ key, name, systemPrompt, userPromptTemplate, model, temperature, maxTokens, enabled, updatedAt, variables[] }`。保存即时生效，无需重启。后台管理页面「Agent管理」菜单提供可视化编辑、清空缓存与恢复默认。

```
DELETE /api/admin/agents/results?agentKey={agentKey}&subjectKey={subjectKey}
```

清空 Agent 结果缓存（用于提示词大改后强制重新分析等场景）。参数组合：两者都传=清该题该 Agent；仅 agentKey=清该 Agent 全部；都不传=清全部。响应 `data`：`{ deleted: 数量 }`。

### 4.9 客户端状态查询（客户端管理）

```
GET /api/admin/keepalives?onlineOnly={onlineOnly}&keyword={keyword}&page={page}&size={size}
```

查询客户端心跳（keepalive）记录，每个客户端一行最新记录；后台「客户端管理」菜单的数据源（当前实现状态查询，后续管理操作在此扩展）。见第 6 节上报接口。

| 参数 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| onlineOnly | boolean | false | 仅返回在线客户端（150 秒内有心跳） |
| keyword | string | 空 | 模糊匹配手机号或客户端 ID |
| page / size | number | 0 / 20 | 分页，按最近上报倒序 |

响应 `data.items` 单条：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| clientId | string | 客户端唯一 ID |
| phone | string\|null | 登录账号手机号；`null` 表示临时用户（未登录） |
| loggedIn | boolean | 最近一次心跳是否携带有效登录态 |
| appVersion / platform / osVersion | string\|null | 客户端自报信息 |
| state / detail | string\|null | 客户端自报状态与描述 |
| reportCount | number | 累计上报次数 |
| firstSeenAt / lastSeenAt | string | 首次/最近上报时间 |
| online | boolean | 是否在线（lastSeenAt 在 150 秒内） |

后台管理页面「客户端管理」菜单可视化本接口（关键字查询、仅在线过滤、30 秒自动刷新）。

### 4.8 AI 调用统计

```
GET /api/admin/ai-stats?phone={phone}&aiType={aiType}&start={start}&end={end}&page={page}&size={size}
```

查询 AI 接口（去手写 / 切边增强）的调用流水，支持按用户、AI 类型、时间范围组合筛选。服务端在每次 AI 调用（含失败）时自动记录流水（权限拒绝 403 不记录）。

| 参数 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| phone | string | 空 | 手机号模糊匹配 |
| aiType | string | 空 | `ERASE` / `CROP_ENHANCE` / `SPLIT_QUESTIONS` / `ANALOGY`（举一反三）/ `EXPLAIN`（做题精讲）；非法值返回 400 |
| start | string | 空 | 开始日期 `yyyy-MM-dd`（含当天 00:00:00）；格式错误返回 400 |
| end | string | 空 | 结束日期 `yyyy-MM-dd`（含当天 23:59:59） |
| page | number | 0 | 页码，从 0 开始 |
| size | number | 20 | 每页条数，最大 200 |

响应 `data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| items | array | 调用流水（按时间倒序），字段见下 |
| total / page / size | number | 分页信息 |
| summary | object | 当前筛选条件下的汇总：`{ total, success, failed }` |

items 单条字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | number | 流水 ID |
| phone | string | 调用用户手机号 |
| aiType | string | `ERASE` / `CROP_ENHANCE` |
| success | boolean | 是否成功 |
| errorCode / errorMessage | number\|null / string\|null | 失败时的业务错误码与信息 |
| traceId | string\|null | 服务端日志追踪 ID |
| requestId | string\|null | 腾讯云上游 RequestId（成功且有上游响应时，切题检测为 null） |
| inputBytes / outputBytes | number | 输入/输出图片大小（字节） |
| inputTokens / outputTokens | number\|null | 大模型输入/输出 token 数（仅 Agent 类调用） |
| durationMs | number | 调用耗时（毫秒） |
| createdAt | string | 调用时间 `yyyy-MM-dd HH:mm:ss` |

## 5. 错题管理接口

将客户端本地错题集（`~/.cuotiku` 的 `entries` 表 + PNG 图片，见客户端 `docs/book-data-structure.md`）迁移到服务端统一管理。所有接口需要认证，数据严格按用户隔离（只能访问自己的错题）。

端点总览：

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/book/entries` | POST | 添加错题（单条） |
| `/api/book/entries` | GET | 分页查询错题列表（年级/科目/错误类型筛选） |
| `/api/book/entries/{id}` | GET | 查询单条错题元数据 |
| `/api/book/entries/{id}/image` | GET | 下载单张图片（原图/缩略图，二进制） |
| `/api/book/entries/images` | POST | 批量获取图片（Base64，组卷打印场景） |
| `/api/book/entries/{id}` | PUT | 更新错题元数据 |
| `/api/book/entries/random` | POST | 公平随机组卷（最少练习优先） |
| `/api/book/entries/practice` | POST | 刷题次数 +1（批量） |
| `/api/book/entries/{id}` | DELETE | 删除错题 |
| `/api/user/ability` | GET | 用户能力模型（五边形雷达图数据，默认三科） |

存储设计：

- **元数据**存服务端 MySQL `book_entry` 表（字段与客户端 `entries` 表一一对应）
- **图片**打包为 zip（内含原图 `image.<fmt>` 与服务端生成的 480px 缩略图 `thumb.jpg`）后存入**腾讯云 COS**，对象键为 `book/{userId}/{yyyyMM}/{id}.zip`，以降低存储成本；未配置 COS 时回退为服务端本地目录（仅限开发环境）

### 5.1 添加错题（单条）

```
POST /api/book/entries
```

对应客户端「加入错题集」，每道错题单独调用一次。请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| grade | number | 是 | 年级 1-9 |
| term | number | 否 | 学期：`1`=上学期（如 7 年上）、`2`=下学期（如 7 年下）；不传为 `null`（未指定，兼容旧数据）；其他值返回 400 |
| subject | string | 是 | 科目：`语文` / `数学` / `英语` |
| imageBase64 | string | 是 | 错题图片 Base64（PNG/JPEG，支持 data URI 前缀），解码后 ≤ 12MB |
| errorType | string | 否 | 错误类型：`马虎` / `不会` / `概念不清` / `其他`，默认 `其他` |
| remark | string | 否 | 用户备注，最长 1000 字，默认空（null） |
| answer | string | 否 | 答案信息，最长 1000 字，默认空（null） |

服务端行为：解析图片得到宽高 → 生成缩略图 → 打包 zip → 上传对象存储 → 写入元数据；存储上传或写库失败时自动清理，不会产生脏数据。

响应 `data`：创建成功的 EntryDto（字段见 5.2）。

### 5.2 分页查询错题列表

```
GET /api/book/entries?grade={grade}&subject={subject}&errorType={errorType}&page={page}&size={size}
```

| 参数 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| grade | number | 空 | 按年级筛选 |
| term | number | 空 | 按学期筛选（1/2；只匹配明确指定了学期的错题，`null` 的历史数据不会命中） |
| subject | string | 空 | 按科目筛选（非法值返回 400） |
| errorType | string | 空 | 按错误类型筛选 |
| page | number | 0 | 页码，从 0 开始 |
| size | number | 50 | 每页条数，最大 200 |

排序：`createdAt` 倒序（与客户端本地列表一致）。响应 `data`：`{ items: EntryDto[], total, page, size }`。

EntryDto：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | string | 错题 UUID |
| grade | number | 年级 |
| term | number\|null | 学期：1=上学期、2=下学期、null=未指定 |
| subject | string | 科目 |
| errorType | string | 错误类型 |
| createdAt | number | 加入错题集时间（毫秒时间戳，与客户端字段一致） |
| createdAtText | string | `yyyy-MM-dd HH:mm:ss` |
| width / height | number | 原图像素尺寸 |
| practiceCount | number | 刷题次数 |
| remark | string\|null | 用户备注（未设置为 null） |
| answer | string\|null | 答案信息（未设置为 null） |
| recordCount | number | 刷题记录条数（附表聚合） |
| correctCount | number | 其中答对条数 |
| accuracy | number\|null | 正确率百分比（保留 1 位小数；无记录为 null） |
| lastPracticedAt | number\|null | 最近一次刷题时间（毫秒时间戳；无记录为 null） |
| imageUrl | string | 原图下载地址（相对路径，需带 Authorization 请求） |
| thumbUrl | string | 缩略图下载地址（相对路径，需带 Authorization 请求） |

### 5.3 查询单条错题

```
GET /api/book/entries/{id}
```

需要认证。响应 `data`：EntryDto（见 5.2）。错题不存在或不属于当前用户返回 `404`。

### 5.4 下载错题图片

```
GET /api/book/entries/{id}/image?kind=original|thumb
```

需要认证。返回**图片二进制**（非 JSON 包装）：原图 `Content-Type: image/png`（或 `image/jpeg`），缩略图 `image/jpeg`。`kind` 默认 `original`；无缩略图时 `kind=thumb` 回退返回原图。响应带 7 天私有缓存头。

> 注意：由于接口需要 Authorization 头，客户端不能直接把 URL 塞给 `<img src>`，应通过 HTTP 层下载为二进制/data URL（与现有 `thumbDataUrl` 渲染方式衔接）。

失败：错题不存在或不属于当前用户返回 `404`（JSON 包装）。

### 5.5 批量获取错题图片

```
POST /api/book/entries/images
```

需要认证。对应客户端组卷预览/打印时一次性获取多张原图（原 `getEntryBuffers` 场景），避免逐张下载。请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| ids | string[] | 是 | 错题 ID 列表，单次最多 50 条 |
| kind | string | 否 | `original`（默认）或 `thumb` |

响应 `data`：按请求 `ids` 顺序返回数组，不存在/无权访问/图片缺失的 ID 静默跳过：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | string | 错题 ID |
| contentType | string | `image/png` / `image/jpeg` |
| imageBase64 | string | 图片 Base64（纯 Base64，无 data URI 前缀） |

示例：

```json
{ "ids": ["uuid-1", "uuid-2"], "kind": "original" }
```

### 5.6 更新错题元数据

```
PUT /api/book/entries/{id}
```

请求体（均可选，仅更新传入字段）：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| grade | number | 年级 1-9 |
| term | number | 学期 1/2；传入即更新（不支持清空回 null） |
| subject | string | 科目 |
| errorType | string | 错误类型 |
| remark | string | 备注；传空字符串清除为 null；不传（null）表示不修改 |
| answer | string | 答案；传空字符串清除为 null；不传（null）表示不修改 |

响应 `data`：更新后的 EntryDto。

### 5.7 刷题次数 +1

```
POST /api/book/entries/practice
```

对应客户端组卷打印成功后的 `bumpPracticeCount`。请求体：

```json
{ "ids": ["uuid-1", "uuid-2"] }
```

单次最多 200 条；仅统计属于当前用户的错题，不存在的 ID 静默忽略。响应 `data`：`{ "updated": 2 }`（实际更新的条数）。

### 5.8 刷题记录附表

每道错题的答题练习明细（时间、答案内容、正确与否），与 `practiceCount` 联动：每插入一条记录，`practiceCount` 同事务 +1（公平组卷模型自动纳入答题练习）。

#### 5.8.1 提交刷题记录

```
POST /api/book/entries/{id}/practices
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| correct | boolean | 是 | 本次是否答对 |
| answerContent | string | 否 | 用户答案内容，最长 2000 字 |
| practicedAt | number | 否 | 刷题时间（毫秒时间戳，用于补录离线练习）；缺省为服务端当前时间，未来时间按当前时间处理 |

响应 `data`：`{ id, entryId, practicedAt, practicedAtText, answerContent, correct }`。错题不存在或不属于当前用户返回 `404`。

#### 5.8.2 查询某题刷题记录

```
GET /api/book/entries/{id}/practices?page=&size=
```

响应 `data`：`{ items: 记录[], total, page, size, recordCount, correctCount, accuracy, lastPracticedAt }`（汇总为该题全量统计，按刷题时间倒序分页）。

#### 5.8.3 跨题刷题历史

```
GET /api/book/practices/history?correct=&start=&end=&page=&size=
```

| 参数 | 说明 |
| --- | --- |
| correct | `true`/`false` 只看答对/答错；缺省全部 |
| start / end | `yyyy-MM-dd` 时间范围（含当天） |

响应 `data`：同 5.8.2 结构，其中 `recordCount/correctCount/accuracy/lastPracticedAt` 为**用户全量**统计（不受筛选影响），`items/total` 为筛选后分页。

> 删除错题（5.10）时其刷题记录级联删除。

### 5.9 公平随机组卷

```
POST /api/book/entries/random
```

需要认证。按错误类型配额抽题，用于「随机组卷」功能。

**公平算法（最少练习分层轮转）**：每类题池按 `practiceCount` 升序分层，从最少练习层开始抽取，同层内随机；一层不足时进入次低层。由此保证：

- **全覆盖轮转**——题库中所有错题都被练到 k 次之前，任何错题都不会被练到 k+1 次，不会出现"有的刷到了、有的没刷到"
- 同等练习次数的题被抽中概率相同（层内随机），避免固定顺序带来的偏置
- 公平闭环依赖计数推进：**本接口不增加刷题次数**，客户端应在组卷打印成功后调用 5.7 `/practice` 接口 +1（与现有客户端语义一致）

请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| grade | number | 否 | 限定年级 1-9 |
| term | number | 否 | 限定学期 1/2（只抽取明确指定了该学期的错题） |
| subject | string | 否 | 限定科目：`语文` / `数学` / `英语` |
| counts | object | 是 | 各错误类型抽取数量，键为 `马虎`/`不会`/`概念不清`/`其他` 的子集，值 0-100；总数 1-200 |

示例（对应客户端随机组卷弹窗的默认值）：

```json
{ "grade": 7, "term": 1, "subject": "数学", "counts": { "马虎": 5, "不会": 5, "概念不清": 5, "其他": 5 } }
```

响应 `data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| items | EntryDto[] | 抽中的错题（按 马虎→不会→概念不清→其他 分组排列），见 5.2 |
| requested | number | 请求抽取总数 |
| selected | number | 实际抽中数（题池不足时小于 requested） |
| byType | object | 每种错误类型的统计：`{ requested, selected, poolSize }`；`poolSize` 为该类型当前筛选下的题池总数，可直接用于组卷弹窗中各类型可抽上限展示 |

失败场景（均 `400`）：错误类型/科目非法、单类型数量超 0-100、总数为 0 或超 200。

客户端对接：替换本地 `confirmRandom` 的洗牌抽取为调用本接口；用返回的 `items[].id` 组卷，经 5.5 批量取图接口获取原图排版打印；打印成功后对 `items[].id` 调用 5.7 计数 +1。

### 5.12 用户能力模型（五边形雷达图）

```
GET /api/user/ability?grade={grade}&term={term}&subject={subject}&start={start}&end={end}
```

需要认证。**返回当前登录用户自己的能力模型数据**，用于客户端绘制能力五边形雷达图；数据按用户隔离，仅能查询自己。

| 参数 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| grade | number | 空 | 限定年级 1-9 |
| term | number | 空 | 限定学期 1/2 |
| subject | string | 空 | 限定科目；**不传时默认同时返回三科**（语文/数学/英语）+ 全科综合 |
| start / end | string | 空 | 时间范围 `yyyy-MM-dd`（含当天） |

响应 `data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| overall | AbilityModel | 当前筛选范围（默认全部三科）的综合模型 |
| subjects | AbilityModel[] | 分科目模型，默认 3 条（语文/数学/英语，顺序固定）；传 `subject` 时 1 条 |

AbilityModel：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| subject | string\|null | 科目；`null` 表示综合 |
| sampleSize | number | 样本量（该范围错题数）；**为 0 时客户端应显示"暂无数据"空态** |
| overall | number\|null | 五维平均分（保留 1 位小数）；无数据时为 null |
| dimensions | AbilityDimension[5] | 五个维度，**顺序固定**，见下表 |

AbilityDimension：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| key | string | 维度键，见下表 |
| label | string | 中文名（可直接用于雷达图轴标签） |
| score | number\|null | 0-100 得分；无数据时为 null |
| totalCount | number | 该维度对应错题数 |
| practiceCount | number | 该维度对应错题的累计刷题次数 |
| weightedCount | number | 时间加权错题量（保留 2 位小数，反映"近期"错题强度） |

五维定义（雷达图轴顺序 = 数组顺序）：

| 顺序 | key | label | 含义 |
| --- | --- | --- | --- |
| 1 | CAREFULNESS | 细心度 | 抗「马虎」能力 |
| 2 | COMPREHENSION | 理解力 | 抗「不会」能力 |
| 3 | CONCEPT | 概念清晰 | 抗「概念不清」能力 |
| 4 | STANDARD | 规范度 | 抗「其他」失误能力 |
| 5 | DILIGENCE | 练习勤奋度 | 刷题行为强度 |

**评分与动态规则**（客户端无需实现，仅供理解数值含义）：

- 时间衰减：错题权重 `w = 0.5^(距今天数/60)`，越近的错题影响越大，旧错题影响自然消退
- 短板：`weakness = W/(W+5)`（W 为该类型加权错题量），错题越多得分越低
- 刷题缓解：`relief = 0.6 × P/(P+10)`（P 为加权刷题量），**刷题次数增加会实时抬升对应维度得分**（最多缓解 60% 短板）
- 得分：`score = round(100 × (1 - weakness × (1 - relief)))`；该类型无错题为 100
- 勤奋度：`round(100 × P总/(P总+15))`，随刷题总量增长
- 模型实时计算，每次刷题/加错题后重新查询即反映变化

**客户端绘制指引**：

1. 五轴雷达图：按 `dimensions` 数组顺序布轴（正上方起顺时针），半径 = `score/100`；`score=null` 时绘制空态（不画数据多边形，提示"暂无错题数据"）
2. 默认响应含三科 + 综合共 4 个模型，可用 Tab/切换器展示；也可传 `subject` 只取一科
3. 轴标签用 `label`，顶点可标注 `score`；明细面板可展示 `totalCount`/`practiceCount`
4. 刷题成功（`/practice`）或加入错题后重新调用本接口即可刷新图形

示例响应（节选）：

```json
{
  "code": 0, "message": "ok",
  "data": {
    "overall": {
      "subject": null, "sampleSize": 4, "overall": 78.4,
      "dimensions": [
        { "key": "CAREFULNESS", "label": "细心度", "score": 63, "totalCount": 3, "practiceCount": 9, "weightedCount": 3.0 },
        { "key": "COMPREHENSION", "label": "理解力", "score": 71, "totalCount": 1, "practiceCount": 0, "weightedCount": 1.0 },
        { "key": "CONCEPT", "label": "概念清晰", "score": 100, "totalCount": 0, "practiceCount": 0, "weightedCount": 0.0 },
        { "key": "STANDARD", "label": "规范度", "score": 100, "totalCount": 0, "practiceCount": 0, "weightedCount": 0.0 },
        { "key": "DILIGENCE", "label": "练习勤奋度", "score": 38, "totalCount": 4, "practiceCount": 9, "weightedCount": 9.0 }
      ]
    },
    "subjects": [ { "subject": "语文", "sampleSize": 1, "overall": 80.0, "dimensions": [ ... ] } ]
  }
}
```

失败场景：科目/日期参数非法返回 `400`；未登录 `401`。

### 5.10 删除错题

```
DELETE /api/book/entries/{id}
```

删除元数据与对象存储中的 zip。响应 `data` 为空；重复删除返回 `404`。

### 5.11 与客户端本地结构的字段映射

| 客户端 `entries` 表 | 服务端 EntryDto | 说明 |
| --- | --- | --- |
| id | id | 均为 UUID；服务端添加时生成新 ID，本地数据迁移上云后应以服务端 ID 为准 |
| grade | grade | 一致 |
| （无，新增） | term | 学期 1=上/2=下/null=未指定；客户端需新增该字段 |
| subject | subject | 一致 |
| error_type | errorType | 一致 |
| created_at | createdAt | 毫秒时间戳，一致 |
| width / height | width / height | 一致 |
| practice_count | practiceCount | 一致 |
| file | imageUrl / thumbUrl | 本地文件名 → 服务端下载地址（批量场景用 5.5 接口） |
| （本地 thumbDataUrl） | thumbUrl | 缩略图改由服务端生成，客户端下载后可缓存 |

## 6. 客户端心跳上报（Keepalive）

客户端每分钟报送一次自身状态；**无需登录**即可上报；携带有效访问令牌时服务端记录登录账号，否则记为「临时用户」。每个 `clientId` 在数据库中仅保留一行最新记录（upsert），并累计上报次数。

### 6.1 上报心跳

```
POST /api/client/keepalive
```

无需认证（permitAll）。若请求携带 `Authorization: Bearer <有效访问令牌>`，服务端解析登录账号写入记录；令牌缺失/无效/过期/被踢时**不报错**，自动降级为临时用户记录（保证心跳不因会话问题中断）。

请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| clientId | string | 是 | 客户端唯一 ID（安装时生成的持久化 UUID，最长 64 位） |
| appVersion | string | 否 | 客户端版本号（如 1.2.3） |
| platform | string | 否 | 平台：windows / macos / linux |
| osVersion | string | 否 | 操作系统版本 |
| state | string | 否 | 客户端状态，建议取值：`idle` 空闲 / `busy` 处理中 / `printing` 打印中 / `erasing` 去手写中 |
| detail | string | 否 | 补充描述（当前页面等），最长 256 位 |

响应：`{ "code": 0, "message": "ok" }`（data 省略）。参数校验失败返回 400。

服务端行为：

- 按 `clientId` upsert 单行：刷新自报字段与登录态、`lastSeenAt`，`reportCount + 1`；首报写入 `firstSeenAt`
- 在线判定：`lastSeenAt` 距今 ≤ 150 秒（上报间隔 60 秒 + 容错）
- 登录态以**服务端令牌解析结果**为准，客户端无需自报 loggedIn

### 6.2 客户端对接要点

1. 安装/首次启动生成 UUID 作为 `clientId`，持久化到本地配置（与 settings 同库）
2. `setInterval` 每 60 秒上报一次；启动时立即上报一次；状态变化（开始打印/去手写等）可即时补报
3. 已登录时在请求头附带当前访问令牌（刷新后的令牌同样有效）；未登录或令牌过期直接裸调
4. 上报失败静默重试下一周期，不提示用户

## 7. 客户端对接清单（建议实现顺序）

1. **会话存储**：登录成功后持久化 `token` 与 `refreshToken`（建议用 Electron `safeStorage` 加密，与现有 `tencentSecretKey` 存储方式一致）。
2. **登录页**：手机号 + 密码 + **图形验证码**（`<img>` 展示 `imageBase64`，点击图片刷新；一次即废，每次登录尝试前重新获取）；登录失败刷新验证码。
3. **统一请求层**：封装 `Authorization` 头；收到 `401` 先尝试静默刷新（`POST /api/auth/refresh`，成功后覆盖保存新的一对令牌并重放原请求一次），刷新失败或收到 `4011` 则清除全部令牌跳登录页，`4011` 单独提示"账号已在其他设备登录"。注意刷新令牌会轮换，并发请求需串行化刷新。
4. **启动校验**：应用启动或从设置页返回时调用 `GET /api/auth/me`（令牌过期则走刷新流程），按 `aiEnabled` 控制 AI 功能入口，按 `role` 控制管理入口。
5. **AI 去手写改造**：`eraseHandwriting` 中的 `callEraseApi(encoded, secretId, secretKey)` 替换为调用 `POST /api/ai/erase`；移除设置页中的腾讯云 SecretId/SecretKey 配置项；调用失败或结果异常时记录响应中的 `traceId`/`requestId` 便于排障。
6. **设置页新增**：服务器地址（baseUrl）、当前账号信息、退出登录按钮。
7. **超时与重试**：AI 接口超时 ≥ 100s；网络错误可提示重试，`502/504` 不建议自动重试（避免重复计费）。

## 8. curl 联调示例

```bash
BASE=http://127.0.0.1:8080

# 1. 验证码（imageBase64 为 data URI，肉眼识别）
curl -s $BASE/api/auth/captcha

# 2. 登录（captchaCode 替换为图片中的字符）
LOGIN=$(curl -s -X POST $BASE/api/auth/login -H 'Content-Type: application/json' \
  -d '{"phone":"13800000000","password":"admin123456","captchaId":"<ID>","captchaCode":"<CODE>","clientLabel":"curl"}')
TOKEN=$(echo "$LOGIN" | python3 -c 'import json,sys;print(json.load(sys.stdin)["data"]["token"])')
REFRESH=$(echo "$LOGIN" | python3 -c 'import json,sys;print(json.load(sys.stdin)["data"]["refreshToken"])')

# 3. 授权信息
curl -s $BASE/api/auth/me -H "Authorization: Bearer $TOKEN"

# 3.1 刷新令牌（返回新的一对令牌，旧 refreshToken 立即作废）
curl -s -X POST $BASE/api/auth/refresh -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH\"}"

# 4. AI 去手写（1x1 PNG）
curl -s -X POST $BASE/api/ai/erase -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"imageBase64":"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="}'

# 4.1 图像切边增强
curl -s -X POST $BASE/api/ai/crop-enhance -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"imageBase64":"<图片base64>","enhanceType":2}'

# 5. 管理端创建用户
curl -s -X POST $BASE/api/admin/users -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"phone":"13911112222","memberNo":"VIP001","password":"test123456","aiEnabled":true}'

# 6. 分页查询
curl -s "$BASE/api/admin/users?page=0&size=20&keyword=139" -H "Authorization: Bearer $TOKEN"

# 7. 一键注销会员（清会员号/有效期并关闭AI权限）
curl -s -X DELETE $BASE/api/admin/users/<ID>/membership -H "Authorization: Bearer $TOKEN"

# 8. 添加错题（单条，imageBase64 为错题图片）
curl -s -X POST $BASE/api/book/entries -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"grade":7,"term":1,"subject":"数学","imageBase64":"iVBORw0K...","errorType":"马虎"}'

# 9. 错题列表 / 单条查询 / 下载原图 / 下载缩略图 / 批量取图
curl -s "$BASE/api/book/entries?grade=7&term=1&subject=数学&page=0&size=50" -H "Authorization: Bearer $TOKEN"
curl -s "$BASE/api/book/entries/<ID>" -H "Authorization: Bearer $TOKEN"
curl -s "$BASE/api/book/entries/<ID>/image" -H "Authorization: Bearer $TOKEN" -o entry.png
curl -s "$BASE/api/book/entries/<ID>/image?kind=thumb" -H "Authorization: Bearer $TOKEN" -o thumb.jpg
curl -s -X POST $BASE/api/book/entries/images -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"ids":["<ID1>","<ID2>"],"kind":"original"}'

# 10. 公平随机组卷（按错误类型配额）
curl -s -X POST $BASE/api/book/entries/random -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"grade":7,"subject":"数学","counts":{"马虎":5,"不会":5,"概念不清":5,"其他":5}}'

# 11. 刷题次数 +1 / 删除
curl -s -X POST $BASE/api/client/keepalive -H 'Content-Type: application/json' \
  -d '{"clientId":"<持久化UUID>","appVersion":"1.2.3","platform":"windows","state":"idle"}'
curl -s -X POST $BASE/api/client/keepalive -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"clientId":"<持久化UUID>","state":"printing"}'
curl -s "$BASE/api/admin/keepalives?onlineOnly=true" -H "Authorization: Bearer $TOKEN"
curl -s -X POST $BASE/api/book/entries/practice -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"ids":["<ID>"]}'
curl -s -X DELETE $BASE/api/book/entries/<ID> -H "Authorization: Bearer $TOKEN"
```

## 9. v2 分类接口（产品 1.5.0 · 服务端 1.21.0）

个人科目与主题体系。冻结契约见共享规划 `project/versions/1.5.0/records/CONTRACT.md`（rev 2），本节为实施口径摘要。全部 Bearer 认证；归属不符统一 `404 分类不存在`。

### 9.1 能力协商

```
GET /api/v2/meta/capabilities → { taxonomyV2Supported, taxonomyV2EntryOpen, taxonomyV2Activated, serverVersion }
```

新客户端受限模式判定：`!supported || (!entryOpen && !activated)`。

### 9.2 科目与主题管理

```
GET    /api/v2/subjects                        首次调用惰性幂等初始化六科（不激活）
POST   /api/v2/subjects            { name }
PUT    /api/v2/subjects/{id}       { name?, status?, sortOrder?, revision }
PUT    /api/v2/subjects/reorder    { items:[{id,revision}] }   全量原子
DELETE /api/v2/subjects/{id}                   仅无主题且无错题关联
GET    /api/v2/subjects/{subjectId}/topics
POST   /api/v2/subjects/{subjectId}/topics  { name }
PUT    /api/v2/topics/{id}         { name?, status?, sortOrder?, revision }
PUT    /api/v2/subjects/{subjectId}/topics/reorder
DELETE /api/v2/topics/{id}                     仅无错题关联
```

SubjectDto `{id,name,systemKey|null,sortOrder,status,revision,topicCount,entryCount,updatedAt}`；TopicDto `{id,subjectId,name,sortOrder,status,revision,entryCount,updatedAt}`。名称规范化（NFC/去边距/空白折叠/小写折叠）后 1–30 码点；科目 ≤50/用户、主题 ≤200/科目；重名 `4093`；revision 冲突 `4091`（data=最新实体/全量列表）；有关联删除 `4092 存在关联数据，请改用停用`。停用科目连带禁止其主题用于新归类，但不改写子主题状态。

### 9.3 错题写入 v2

```
POST /api/v2/book/entries/batch     { requestId, items[≤20]: { clientId, grade 1-12, term?, subjectId, topicId?, imageBase64, errorType?, remark?, answer? } }
     → { results: [{ clientId, status: SUCCESS|DUPLICATE|FAILED, entryId?, code?, message? }] }
PUT  /api/v2/book/entries/{id}      { grade?, term?, subjectId?, topicId?, errorType?, remark?, answer? }
POST /api/v2/book/entries/reclassify { requestId, items: [{ entryId, subjectId, topicId(显式,null=清空) }] }
```

- 幂等键 `(userId, requestId, clientId)`：重试命中返回 DUPLICATE+原 entryId；逐项独立事务，部分失败不回滚已成功项；单块 ≤20 题且请求体 ≤20MB（`400 单次提交数据过大`）
- 编辑三态：`topicId` 缺失=保留、显式 null=清空、值=设定；换科目必须显式带 topicId
- 新归类校验父科目与主题均 ACTIVE；历史编辑可保留停用分类；归类变更不重置 practiceCount

### 9.4 查询 / 抽题 / 能力 v2

```
GET  /api/v2/book/entries?grade=&term=&subjectId=&topicId=&unclassified=&errorType=&page=&size=
POST /api/v2/book/entries/random   { grade?, term?, subjectId?, topicId?, unclassified?, counts }
GET  /api/v2/user/ability?grade=&term=&subjectId=&topicId=&unclassified=&start=&end=
```

主题三态：`topicId`=具体 / `unclassified=true`=未分类 / 都不传=不限。EntryDtoV2 = v1 EntryDto + `{subjectId, subjectName, topicId, topicName, subjectStatus, topicStatus}`（名称恒为当前名）。排序冻结 createdAt DESC, id ASC。抽题先过滤后公平轮转（算法不变）。能力按 subjectId 分组（改名不拆组），subjects[] 含全部科目与 status，overall 对请求范围全科目完整计算。

### 9.5 账号默认选择

```
GET /api/v2/preferences → { defaultSubjectId|null, defaultTopicId|null }
PUT /api/v2/preferences { defaultSubjectId|null, defaultTopicId|null }
```

记忆最近一次成功加入的批量默认选择；校验归属+ACTIVE；读写不触发激活。

### 9.6 错误码增量与旧客户端兼容

`4091` revision 冲突（409）、`4092` 关联禁删（409）、`4093` 名称重复（409）、`4026` 旧客户端受限（**426**，冻结文案：通用写入阻断「此账号已启用新版科目与主题，请升级至 1.5.0 或以上版本后继续此操作。」；能力统计「旧版客户端无法完整展示当前范围的能力统计，请升级至 1.5.0 或以上版本。」）。

旧客户端（v1）兼容摘要（详见 CONTRACT §10）：未激活账号 v1 行为完全不变（add 双写 subjectId）；已激活账号 v1 add 阻断 426、v1 update 涉主题/自定义科目阻断 426、不含 subject 的 update 与预设间映射放行；v1 读六科返回模板规范名、自定义原样；v1 随机/能力范围含自定义题时 426。开关 `TAXONOMY_V2_ENABLED` 只控入口/新激活，回退不清激活、不回收已激活读写。

## 10. 版本更新记录

### v1.21.0（2026-10-06，产品 1.5.0 服务端）

- **v2 分类体系上线**（CONTRACT rev 2 实施）：user_subject/subject_topic/user_taxonomy_pref/book_add_idempotency 新表，book_entry +subject_id/topic_id，sys_user +taxonomy_v2_activated_at；六科惰性幂等初始化；历史数据按「用户+原名称」幂等回填（未知旧值建自定义科目保留，不激活）。
- 科目/主题管理、批量加入（逐项幂等+部分失败）、三态编辑、批量重新归类、v2 查询/抽题/能力、偏好、capabilities 全量接口（第 9 节）。
- AI Agent 提示词新增 `{topic}` 变量；entryId 路径服务端解析当前分类名称；缓存哈希含渲染提示词，改名/重新归类自动失效。
- 旧客户端兼容矩阵与 426 冻结文案落地；`TAXONOMY_V2_ENABLED` 入口开关（默认 false）。
- 性能实测（PERFORMANCE P1–P6/P9）全部低于预算，见 records/V150-BE-04-EVIDENCE.md。

### v1.20.0（2026-10-03）

- **新增刷题记录附表** `book_practice_record`：记录每次刷题的时间（`practicedAt`，支持补录）、答案内容（`answerContent`）、正确与否（`correct`）；插入记录同事务 `practiceCount+1`，公平组卷自动纳入答题练习。
- 新接口：`POST /api/book/entries/{id}/practices`（提交）、`GET /api/book/entries/{id}/practices`（单题明细+汇总）、`GET /api/book/practices/history`（跨题历史，按对错/日期筛选，附用户全量正确率）。
- EntryDto 新增聚合字段 `recordCount/correctCount/accuracy/lastPracticedAt`（列表/单条/组卷一次 group-by 查询，无 N+1）；删除错题级联删除其刷题记录。

### v1.19.0（2026-10-03）

- **错题数据结构新增字段**：`remark`（用户备注）与 `answer`（答案信息），默认空（null），最长 1000 字。
- 添加错题（5.1）可携带备注/答案；**修改错题接口**（`PUT /api/book/entries/{id}`，5.6）支持更新与清空（空字符串清除为 null，不传不修改）；列表/单条/组卷/批量取图等所有 EntryDto 均返回两字段。**删除错题接口**已存在（`DELETE /api/book/entries/{id}`，5.9），无需变更。
- 数据库 `book_entry` 表自动加列，历史数据两字段为 null，客户端无需迁移。

### v1.18.2（2026-10-03）

- **修复成品图旋转 90° 问题**：手机照片显示方向记录在 EXIF Orientation 中，而 ImageIO 解码、服务端重编码与腾讯云引擎输出均不保留 EXIF，导致处理结果回到传感器存储方向（表现为逆时针旋转 90°）。现服务端在去手写/切边增强/切题/试卷处理/错题入库全链路调用前按 EXIF 自动旋转像素（8 种方向全支持），成品图方向与原图显示一致。
- 新增 `ImageUtil` 与单元测试（orientation=6 旋转、180° 保持尺寸、无 EXIF 原样透传）。

### v1.18.1（2026-10-03）

- **去手写 InternalError 加固**：调用擦除引擎前自动归一化图片（RGB 化、去 alpha、超 4096 边/1200 万像素等比缩、重编码标准 JPEG），规避超大分辨率/非常规格式触发的上游 `InternalError`；`InternalError` 自动重试一次；所有腾讯云上游错误 message 附带 `RequestId` 便于提工单排查。

### v1.18.0（2026-10-03）

- **修复踢出误报**：静默刷新轮换令牌后，客户端并发在途的旧令牌此前会命中"jti 不匹配"被误报 `4011 账号已在其他设备登录`。现记录轮换原因（LOGIN/REFRESH）与旧 jti：REFRESH 轮换后 **120 秒宽限期**内旧访问令牌仍有效、旧刷新令牌重发当前会话令牌（不旋转），仅新登录替换会话才返回 4011。
- **4011 文案优化**：改为「该账号已在其他设备登录，当前设备已退出。如非本人操作，请立即修改密码」，降低用户"被盗号"恐慌并给出可操作引导；刷新接口失败文案同步区分踢出与其他失效原因。
- 用户表新增 `prev_session_jti`/`prev_refresh_jti`/`rotate_reason`/`rotated_at` 字段（JPA 自动加列）。

### v1.17.0（2026-10-03）

- **新增用户自助修改密码接口** `POST /api/auth/password`（2.5 节）：校验原密码、新密码 6-64 位且不得与原密码相同；成功后当前双令牌立即失效，客户端重新登录。

### v1.16.0（2026-10-02）

- **Agent 结果缓存（成本控制）**：新表 `ai_agent_result`，每道错题每个 Agent 仅调用一次大模型；命中缓存直接返回（`cached=true`、返回首次 traceId、不耗配额、零 token 成本）。缓存键含提示词指纹，后台改提示词/模型/温度后自动失效重算；直传图片按 SHA-256 去重。
- **管理端清缓存**：`DELETE /api/admin/agents/results`（按 Agent/题目/全部），后台「Agent管理」每行新增「清空缓存」按钮。
- 客户端可据 `cached` 字段区分"新分析/历史结果"展示。

### v1.15.2（2026-10-02）

- **修复 Agent 多模态端点选择**：带图调用必须使用 DashScope 多模态端点（spring-ai-alibaba 的 `withMultiModel(true)`），此前走纯文本端点导致 `HTTP 400 InvalidParameter: url error, please check url`。现按请求是否带图自动切换端点；同时改用 `DashScopeChatOptions`（model/temperature/maxToken/multiModel）替代通用 ChatOptions。

### v1.15.1（2026-10-02）

- **修复 Agent 图片传入方式**：spring-ai-alibaba 1.1.2.x 的媒体转换仅支持 `byte[]`（转 data URI）与 `String`（URL），原实现传 `ByteArrayResource` 被序列化为非法 url，导致 DashScope 返回 `HTTP 400 InvalidParameter: url error, please check url`。改为 `Media.builder().data(byte[])` 官方支持方式，多模态读图恢复正常。

### v1.15.0（2026-10-02）

- **新增 Agent 模块**：引入 Spring AI Alibaba（DashScope）最新稳定版，Spring Boot 升级至 3.5.x；上线两个多模态 Agent——**举一反三**（`POST /api/agent/analogy`）与**做题精讲**（`POST /api/agent/explain`），默认模型 `qwen-vl-max`，可直接读错题图。
- **提示词后台可配**：新表 `ai_agent_config` 种子内置默认系统/用户提示词模板；管理接口 `GET/PUT /api/admin/agents`、`POST /api/admin/agents/{key}/reset`；后台新增「Agent管理」菜单（提示词、模型、温度、maxTokens、启停，保存即时生效）。
- **权限与配额**：Agent 需登录 + `aiEnabled`；每用户每 Agent 每日配额默认 20 次（`AGENT_DAILY_LIMIT`），超限 429；密钥经 `DASHSCOPE_API_KEY` 注入，未配置返回 503 且不影响服务启动。
- **成本可视**：`ai_call_log` 新增 `input_tokens`/`output_tokens` 列与 `ANALOGY`/`EXPLAIN` 类型，统计接口与后台页面同步展示 Tokens。
- 输出契约：模型被约束仅输出 JSON，服务端容错解析（去代码围栏），解析失败自动追加约束重试一次。

### v1.14.0（2026-10-02）

- **后台「客户端心跳」菜单升级为「客户端管理」**：当前实现客户端状态查询——新增关键字查询（手机号/客户端ID 模糊匹配，`GET /api/admin/keepalives` 增加 `keyword` 参数），保留仅在线过滤与 30 秒自动刷新；后续客户端管理操作（如禁用、备注）将在此菜单下扩展。
- 认证过滤器重构：心跳路径令牌校验失败降级为匿名继续处理，其余路径行为不变。

### v1.13.0（2026-10-02）

- **新增客户端心跳（keepalive）接口** `POST /api/client/keepalive`（第 6 节）：免登录上报，每分钟一条；携带有效令牌记录登录账号，否则记临时用户；令牌无效/过期/被踢时自动降级匿名、不中断上报。每客户端单行 upsert（累计 reportCount、首/最近上报时间）。
- **管理端**：`GET /api/admin/keepalives`（4.9 节，支持仅在线过滤）；后台管理页面新增「客户端心跳」菜单（在线/离线标签、临时用户标识、30 秒自动刷新）。

### v1.12.0（2026-10-02）

- **新增试卷处理三合一接口** `POST /api/ai/paper-process`（3.4 节）：一次调用串联切边增强→切题检测→整页去手写，返回成品整页图（`imageKind` 标明处理级别）、归一化题框与三步骤状态（含各自腾讯云 RequestId）。
- 容错策略：切边增强失败回退原图不中断；切题/去手写上游失败整体失败。整页一次擦除使去手写调用次数与题数无关，替代客户端"裁剪→合成→擦除→拆回"旧链路。
- 三个上游调用分别记入 `ai_call_log`（CROP_ENHANCE/SPLIT_QUESTIONS/ERASE），共享 traceId，统计口径不变。

### v1.11.0（2026-10-02）

- **新增自动切题检测接口** `POST /api/ai/split-questions`（3.3 节）：对接腾讯云「试卷切题（仅检测）」`QuestionSplitLayoutOCR`，返回整页试卷的题目边框（像素 + 归一化 0-1 双坐标、阅读序编号），支撑客户端"拍照→自动框题→点选确认→切题"交互；归一化坐标与客户端选框模型同构，可直接注入。
- 服务端完成外接矩形化、碎框过滤、重叠/包含去重、阅读序排序；固定关闭腾讯侧切边保证坐标与传入图对齐；检测为空返回空数组供客户端回退手动框选。
- 调用计入 AI 权限与调用流水：`ai_call_log` 新增类型 `SPLIT_QUESTIONS`，管理页统计与 `ai-stats` 的 aiType 筛选同步支持「自动切题」。

### v1.10.0（2026-09-30）

- **新增用户能力模型接口** `GET /api/user/ability`（5.11 节）：客户端按登录态查询自己的能力五边形数据，默认同时返回三科 + 综合共 4 个模型；五维 = 细心度/理解力/概念清晰/规范度（四类错题反向）+ 练习勤奋度（刷题正向）。
- 评分含时间衰减（半衰期 60 天）与刷题缓解（刷题实时抬升对应维度，上限 60%），模型实时计算、随刷题/加题动态变化；无数据时 `score=null` 供客户端渲染空态。
- 数据严格按用户隔离，仅能查询本人；该能力面向客户端展示，不提供管理端入口。

### v1.9.0（2026-09-30）

- **后台管理更名为「拾星错题本后台管理」**，页面改为多菜单布局（侧边栏：用户管理 / AI调用统计）。
- **新增 AI 调用统计**：服务端自动记录每次 AI 调用流水（`ai_call_log` 表：用户、AI类型、成败、错误码、耗时、输入/输出大小、traceId、腾讯云 RequestId）；管理接口 `GET /api/admin/ai-stats`（4.8 节）支持按手机号、AI 类型（ERASE/CROP_ENHANCE）、时间范围（yyyy-MM-dd）组合查询，返回分页流水与成功/失败汇总；统计页含汇总卡片与流水表格。
- 权限拒绝（403 未开通AI）不记入流水；流水写入失败不影响 AI 调用主流程。

### v1.8.0（2026-09-30）

- **注销会员语义强化**：`DELETE /api/admin/users/{id}/membership` 现在会标记账号 `cancelled=true`、立即踢下线并禁止登录；登录失败响应与密码错误完全一致（`401 手机号或密码错误`），客户端无需改动即表现为"账号或密码错误"。管理员编辑用户重新填写会员号/有效期即自动恢复。不能注销自己的账号。
- UserDto 新增 `cancelled` 字段；后台管理页面状态列显示「已注销」（红色标签），已注销用户隐藏「注销会员」按钮，编辑弹窗提示恢复方式。
- 客户端兼容性：无协议破坏性变更；已注销用户的在线客户端下一次请求收到 `401`，走既有重新登录流程。

### v1.7.0（2026-09-28）

- **新增图像切边增强接口** `POST /api/ai/crop-enhance`（3.2 节）：对接腾讯云 `CropEnhanceImageOCR`，支持切边、弯曲矫正、方向矫正、仅取角点坐标与 7 档增强类型（增亮/锐化/黑白/灰度/去阴影/点阵图）。
- 服务端代为下载腾讯云返回的临时 `CroppedImageUrl` 并转 Base64 返回（腾讯云已废弃内联 `CroppedImage` 字段），客户端无需访问腾讯云域名；响应含角点坐标 `position`、处理后尺寸与 `requestId`/`traceId` 排障字段。
- 权限与 AI 去手写一致（`aiEnabled`）；内部重构：腾讯云调用层泛化为 `TencentOcrClient`（统一 TC3 签名与错误翻译），去手写接口协议不变。

### v1.6.0（2026-09-28）

- **错题新增学期字段 `term`**：`1`=上学期（如 7 年上）、`2`=下学期（如 7 年下）、`null`=未指定（历史数据兼容，DDL 自动加列无需迁移）。
- 添加（5.1）、更新（5.6）、列表筛选（5.2）、随机组卷（5.8）均支持 `term`；EntryDto 返回该字段。非法取值（非 1/2）返回 400。
- 注意：`term` 筛选只命中明确指定学期的错题，未指定的历史数据在按学期筛选时不出现；可通过 5.6 更新接口补填。
- 客户端调整指引见本节下方说明（v1.6.0 发布说明附客户端对接清单）。

### v1.5.0（2026-09-28）

- **新增公平随机组卷接口** `POST /api/book/entries/random`（5.8 节）：按错误类型配额抽题，采用「最少练习分层轮转」公平算法——practiceCount 最低层优先、同层随机，保证题库全覆盖轮转（所有错题练到 k 次之前不会有错题练到 k+1 次），杜绝部分错题长期抽不到。
- 响应含 `byType.poolSize` 各类型题池统计，可直接驱动客户端组卷弹窗的可抽数量展示；接口本身不增加刷题计数，打印成功后由客户端调用 `/practice` 推进公平闭环。
- 客户端对接方式：替换 `BookView.confirmRandom` 的本地洗牌逻辑（纯随机无公平保证），改调本接口 + 5.5 批量取图。

### v1.4.0（2026-09-28）

- **数据库由 SQLite 迁移为 MySQL 8**：连接信息通过 `MYSQL_HOST`/`MYSQL_PORT`/`MYSQL_DATABASE`/`MYSQL_USERNAME`/`MYSQL_PASSWORD` 环境变量注入（生产必填）；表结构仍由 JPA `ddl-auto=update` 自动建表；应用容器不再需要持久卷（无状态化）。**接口协议无任何变化，客户端无需改动**。
- 注意：原 SQLite 数据不会自动迁移；如已有正式数据需自行导出导入。

### v1.3.0（2026-09-28）

- **新增错题管理接口**（第 5 节）：错题从客户端本地迁移到服务端统一管理，数据按用户隔离。
  - `POST /api/book/entries` 单条添加（服务端解析宽高、生成 480px 缩略图、打包 zip 上传，失败自动清理）
  - `GET /api/book/entries` 分页列表（年级/科目/错误类型筛选，createdAt 倒序）
  - `GET /api/book/entries/{id}` 单条查询
  - `GET /api/book/entries/{id}/image?kind=original|thumb` 图片二进制下载（7 天私有缓存头）
  - `POST /api/book/entries/images` 批量取图（≤50 张，Base64 返回，对应组卷打印场景）
  - `PUT /api/book/entries/{id}` 更新元数据；`POST /api/book/entries/practice` 刷题次数 +1；`DELETE /api/book/entries/{id}` 删除
- **图片存储**：zip 压缩（原图 + 缩略图）后存腾讯云 COS（`COS_BUCKET`/`COS_REGION`/`COS_SECRET_ID`/`COS_SECRET_KEY`，凭据缺省复用 AI 的腾讯云密钥），对象键 `book/{userId}/{yyyyMM}/{id}.zip`；未配置 COS 时回退本地目录 `BOOK_LOCAL_DIR`（仅限开发）。
- 元数据表 `book_entry` 字段与客户端 `entries` 表对齐（见 5.7 映射表），`createdAt` 沿用毫秒时间戳。

### v1.2.0（2026-09-28）

- **会员有效期**：用户新增 `memberExpireAt`（`yyyy-MM-dd`）与 `memberActive`（是否在有效期内）字段，出现在 UserDto 与 `/api/auth/me` 响应中。创建用户不传有效期时**默认一年**；更新用户可修改或清除（传空字符串）有效期；格式错误返回 `400`。
- **一键注销会员**：新增 `DELETE /api/admin/users/{id}/membership`，清除会员号与有效期并关闭 AI 权限，不踢下线（授权实时生效）。
- **后台管理页面**：用户列表新增「会员有效期」列（有效/已过期/非会员标签）；创建表单新增有效期选择（默认一年后）；编辑表单支持修改/清除有效期；操作列新增「注销会员」按钮。
- 兼容性说明：均为新增字段与新增接口，旧客户端不受影响；客户端可根据 `memberActive` 增加会员状态展示与到期提醒。

### v1.1.0（2026-09-28）

- **新增刷新令牌机制**：登录响应新增 `refreshToken`、`refreshExpiresIn` 字段；新增 `POST /api/auth/refresh` 接口（刷新令牌轮换）。访问令牌默认有效期由 7 天调整为 **2 小时**，刷新令牌默认 **30 天**，桌面端可通过静默刷新长期保持登录。退出登录、被踢出、密码重置/修改、账号停用时，访问令牌与刷新令牌一并失效。
- **AI 擦除响应新增排障字段**：`POST /api/ai/erase` 响应 `data` 新增 `requestId`（腾讯云上游 RequestId）与 `traceId`（服务端日志追踪 ID），用于与腾讯云上游及服务端日志对齐排障。
- **文档澄清**：明确 `GET /api/auth/captcha` 为**图形验证码**（非短信验证码），且一次即废，每次登录尝试前需重新获取。
- 兼容性说明：登录响应为新增字段，旧客户端不受影响；但访问令牌 TTL 缩短为 2 小时后，未实现刷新逻辑的客户端需每 2 小时重新登录，**建议客户端同步升级**。

### v1.0.0（2026-09-28）

- 首个版本：图形验证码、登录（手机号+密码+验证码）、单端登录踢出（`4011`）、授权信息 `/api/auth/me`、退出登录、AI 去手写 `/api/ai/erase`（腾讯云凭据后台配置）、管理端用户增删改查/重置密码、后台管理页面 `/admin/index.html`。
