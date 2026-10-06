# AGENTS.md · yycuotiku-server（拾星错题本后台）

面向自动化 Agent 与开发者的工程手册。仓库：`https://github.com/liuxu4321/cuotiku-server.git`

## 项目概览

- 技术栈：Java 17 · Maven 3 · Spring Boot 3.5 · Spring Security（JWT 双令牌）· Spring Data JPA · MySQL 8 · 腾讯云 COS/OCR · Spring AI Alibaba（DashScope qwen-vl-max）
- 产物：单个可执行 jar（`target/yycuotiku-server.jar`）+ 内嵌静态后台管理页（`src/main/resources/static/admin/index.html`，原生 JS 单文件）
- 接口文档：`docs/API.md`（唯一权威，改动接口必须同步）；部署与配置说明：`README.md`
- 版本语义：`pom.xml` 的 `<version>` = 镜像 tag = 文档变更记录版本（如 1.20.0）

## 常用命令

```bash
mvn test                          # 全量集成测试（需要本机 Docker：Testcontainers 拉起 MySQL 8）
mvn -DskipTests package           # 打包 jar
mvn spring-boot:run               # 本地运行（dev profile，需本地 MySQL localhost:3306/yycuotiku，root/root123456）
```

测试注意：

- 集成测试继承 `AbstractIntegrationTest`（共享单例 MySQL 容器）；新增接口必须补集成测试
- Testcontainers 已锁定 1.21.4（兼容 Docker Engine ≥28；更低版本会报 400）
- 测试用 httpclient5 会对 503 自动重试，断言失败次数时用 `>=` 而非 `==`
- 真实云服务冒烟为 opt-in：`TENCENT_SECRET_ID=... TENCENT_SECRET_KEY=... COS_REGION=ap-beijing mvn test -Dtest=TencentEraseLiveSmokeTest,CosLiveSmokeTest`（无环境变量自动跳过，CI/常规构建不要带密钥）

## 发版流程（版本 N，例 1.21.0）

1. `pom.xml`：`<version>` 改为 N
2. `docs/API.md`：头部「版本：vN」+ 第 8 节变更记录顶部新增 `### vN（日期）` 条目
3. `README.md`：镜像 tag 引用（构建/部署示例）更新为 N
4. 验证：`mvn test` 全绿 → `mvn -q -DskipTests package`
5. 构建镜像（**本机 BuildKit 拉取元数据会被 DNS 污染阻断，固定使用经典构建器**；jar 与架构无关，直接复用本地 jar）：

   ```bash
   DOCKER_BUILDKIT=0 docker build --platform linux/amd64 \
     -t jefferliu/yycuotiku-server:N -t jefferliu/yycuotiku-server:latest -f- . <<'EOF'
   FROM eclipse-temurin:17-jre
   WORKDIR /app
   ENV TZ=Asia/Shanghai PORT=80 SPRING_PROFILES_ACTIVE=prod
   COPY target/yycuotiku-server.jar app.jar
   RUN mkdir -p /app/data
   VOLUME ["/app/data"]
   EXPOSE 80
   ENTRYPOINT ["java", "-jar", "/app/app.jar"]
   EOF
   ```

   （仓库内多阶段 `Dockerfile` 为 CI/标准路径，本机交叉构建慢，仅 CI 使用）
6. 推送：`docker push jefferliu/yycuotiku-server:N && docker push jefferliu/yycuotiku-server:latest`
   （推送遇 `use of closed network connection`/EOF 为网络抖动，直接重试）
7. Git：`git add -A && git commit -m "release: vN（变更摘要）" && git tag vN && git push origin main && git push origin vN`
8. 部署：sealos 应用镜像 tag 改为 N（**用固定 tag，勿用 latest，节点缓存会拉旧镜像**）

## 编码约定

- 响应统一 `ApiResponse{code,message,data}`；业务错误码见 `docs/API.md` 1.3（4011=踢出、429=配额、502/503/504=上游）
- 新端点三件套：DTO（jakarta 校验）→ Service（`@Transactional`）→ Controller；管理端路径 `/api/admin/**`（ADMIN 角色自动拦截）
- 调试日志：`if (log.isDebugEnabled())` 包裹，模块标签前缀（`[登录]`/`[错题]`/`[组卷]`/`[Agent]`/`[COS]`…）；密钥/令牌/验证码答案永不落日志
- 所有腾讯云 AI 调用记 `ai_call_log`（含 tokens）；Agent 结果按题缓存（`ai_agent_result`，提示词指纹失效）
- 提示词/模型参数存 `ai_agent_config`（后台可改、即时生效），不硬编码
- 数据库变更仅靠 JPA `ddl-auto=update` 自动加列；不写迁移脚本（历史数据需兼容 null）
- 静态后台页为单文件原生 JS：新增管理功能 = 加菜单按钮 + section + `loadXxx()` 函数，沿用 `api()` 封装（401/403 自动回登录页）

## 环境变量（生产必填项缺失即启动失败）

`MYSQL_HOST` `MYSQL_USERNAME` `MYSQL_PASSWORD` `JWT_SECRET` `ADMIN_PHONE` `ADMIN_PASSWORD`；
可选：`TENCENT_SECRET_ID/KEY`（OCR+COS 共用）`COS_BUCKET`(默认 cuotiji-1257458058) `COS_REGION`(默认 ap-beijing) `DASHSCOPE_API_KEY`(Agent) `AGENT_DAILY_LIMIT`(默认20) `JWT_TTL_HOURS` `JWT_REFRESH_TTL_DAYS` `PORT`(镜像内置80)

## 已知坑

- Docker Desktop 本机：BuildKit 元数据解析超时 → 用 `DOCKER_BUILDKIT=0`（见发版流程）
- 腾讯云 `EraseHandwrittenImageOCR` 对超大图/带 EXIF 图易 `InternalError`：服务端已做归一化+EXIF 旋转+重试，改动图片链路时勿移除
- DashScope 多模态必须 `withMultiModel(true)`（带图时），否则 `url error`
- 单端登录：同账号新登录踢旧会话；刷新轮换有 120s 宽限（`JwtService.ROTATION_GRACE_SECONDS`），改令牌逻辑时注意测试 `refreshTokenFlow`
