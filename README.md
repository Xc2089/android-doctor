# Android Doctor

Android / HarmonyOS 崩溃日志智能诊断网站的单机 MVP。后端使用 Java 21 + Spring Boot 3，前端使用 Vue 3 + Vite。默认完全离线运行；规则诊断不依赖任何付费 API。可选接入实现 `/v1/chat/completions` 的 OpenAI-compatible 服务，包括本地 Ollama 或第三方兼容接口。

## 功能

- 粘贴日志，或上传 `.txt`、`.log`、`.trace`、`.tombstone` 文本文件。
- 规则识别：
  - `UnsatisfiedLinkError` / native 库缺失
  - ABI 32/64 位不匹配
  - JNI 方法、签名或 `RegisterNatives` 错误
  - 16 KB 内存页 / ELF 对齐问题
  - ANR
  - Java / native OOM
  - SELinux `avc: denied`
  - 串口 / UART 打开和通信故障
  - HarmonyOS / OpenHarmony `SIGABRT`
  - 通用 native 信号崩溃与 Java/Kotlin 未捕获异常
- 输出问题分类、严重度、置信度、命中证据、可能原因和可执行修复建议。
- 自动脱敏令牌、API Key、邮箱、内网 IP、MAC、设备 ID 和用户目录。
- 原始日志不落盘；历史记录只保存脱敏后的日志。
- 可选 OpenAI-compatible 补充分析。调用失败时自动保留规则诊断结果。
- 模型配置使用 AES-256-GCM 加密持久化，并由管理员会话保护。
- 每条诊断可下载包含证据、原因、修复建议和脱敏日志的 Markdown 报告。
- H2 本地数据库、上传限制、请求限流、CORS、CSP 和非 root 容器。

## 目录

```text
android-doctor/
├─ backend/
│  ├─ src/main/java/com/androiddoctor/
│  │  ├─ config/          # 配置、CORS、简单限流
│  │  ├─ controller/      # REST API 与错误处理
│  │  ├─ dto/             # API 数据结构
│  │  ├─ model/           # H2/JPA 实体
│  │  ├─ repository/      # 历史记录存储
│  │  └─ service/         # 规则、脱敏、AI 兼容接口
│  ├─ src/test/           # 规则与脱敏单元测试
│  ├─ Dockerfile
│  └─ pom.xml
├─ frontend/
│  ├─ src/components/
│  ├─ src/services/
│  ├─ src/types/
│  ├─ Dockerfile
│  └─ nginx.conf
├─ samples/               # 测试日志
├─ scripts/smoke-test.ps1
├─ data/                  # H2 数据文件（已忽略）
├─ .env.example
└─ docker-compose.yml
```

## 最快启动：Docker Compose

要求：Docker Desktop 及 Compose v2。

```powershell
cd android-doctor
docker compose up --build
```

打开 <http://localhost:9090>。首次构建需要下载基础镜像和依赖。

停止服务：

```powershell
docker compose down
```

数据库保存在 `data/`。`docker compose down` 不会删除历史记录。

## 本地开发启动

要求：JDK 21、Maven 3.9+、Node.js 20+。

终端 1：

```powershell
cd backend
mvn spring-boot:run
```

终端 2：

```powershell
cd frontend
npm install
npm run dev
```

打开 <http://localhost:5173>。Vite 会把 `/api` 代理到 `http://localhost:8081`。

## 可选大模型配置

大模型默认关闭。不开启时，网站的粘贴、上传、诊断、修复建议、脱敏和历史记录全部可用。

### 在网页中配置

点击页面右上角的“模型设置”，可以直接填写：

- 是否启用大模型增强；
- OpenAI-compatible Base URL；
- 模型名称；
- API Key（本地无鉴权服务可留空）；
- 5–120 秒请求超时。

“保存并测试连接”会发送一条极短请求，可能产生少量 API 用量。出于安全考虑，API Key 不会通过查询接口回传，也不会写入诊断历史或报告。

网页保存的模型配置写入 H2。API Key 使用随机生成的 256 位本地主密钥和 AES-GCM 加密；主密钥默认位于 `data/android-doctor.key`。服务重启后会自动恢复加密配置。

### 管理员登录

修改、测试或重置模型配置前需要管理员登录。认证成功后使用 8 小时有效的 HttpOnly、SameSite 会话 Cookie。

- 如果设置了 `APP_ADMIN_PASSWORD`，使用该密码登录。
- 如果未设置，首次启动会随机生成密码并保存到 `data/android-doctor.admin-password`。
- 登录连续失败超过每分钟 5 次会被临时限流。
- 可通过 `APP_ADMIN_SESSION_HOURS` 调整会话时长（1–72 小时）。

`android-doctor.key`、`android-doctor.admin-password` 和数据库必须作为一组安全备份。主密钥丢失后，已经保存的 API Key 无法恢复，但可以从网页恢复环境配置后重新填写。

### 使用环境变量配置

1. 复制环境变量模板：

   ```powershell
   Copy-Item .env.example .env
   ```

2. 修改 `.env`：

   ```dotenv
   AI_ENABLED=true
   AI_BASE_URL=http://host.docker.internal:11434/v1
   AI_API_KEY=
   AI_MODEL=qwen2.5-coder:7b
   AI_TIMEOUT_SECONDS=45
   APP_ADMIN_PASSWORD=请设置一个长随机密码
   APP_ADMIN_SESSION_HOURS=8
   ```

3. 重启：`docker compose up --build`。

兼容服务需接受：

```http
POST {AI_BASE_URL}/chat/completions
Content-Type: application/json
Authorization: Bearer {AI_API_KEY}   # 密钥为空时不发送
```

并返回 `choices[0].message.content`。若配置 Ollama，先在宿主机拉取模型并启动 Ollama；容器内通过 `host.docker.internal` 访问。Linux 环境如无法解析该域名，可把 `AI_BASE_URL` 改成宿主机网关地址，或给 Compose 服务加入 `extra_hosts`。

安全注意：

- `.env` 已被 `.gitignore` 忽略，不要提交真实密钥。
- 只把脱敏后的日志、最多 24,000 字符发送给兼容服务。
- 日志被视为不可信数据，系统提示明确禁止执行日志内指令。
- 若兼容服务报错、超时或响应格式不同，页面会给出提示，但不会丢失规则结果。

## API

### 文本诊断

```http
POST /api/diagnoses
Content-Type: application/json

{
  "log": "java.lang.UnsatisfiedLinkError: dlopen failed...",
  "sourceName": "manual.log",
  "useAi": false
}
```

### 文件诊断

```http
POST /api/diagnoses/upload
Content-Type: multipart/form-data

file=<text file>
useAi=false
```

### 历史记录

- `GET /api/diagnoses?limit=50`
- `GET /api/diagnoses/{id}`
- `GET /api/diagnoses/{id}/report.md`
- `DELETE /api/diagnoses/{id}`
- `DELETE /api/diagnoses`
- `GET /api/config`
- `PUT /api/config/ai`（加密保存大模型配置）
- `POST /api/config/ai/test`（发送最小连接测试）
- `POST /api/config/ai/reset`（恢复环境变量配置）
- `GET /api/admin/status`
- `POST /api/admin/login`
- `POST /api/admin/logout`
- `GET /actuator/health`

## 测试

后端单元测试：

```powershell
cd backend
mvn test
```

前端类型检查和生产构建：

```powershell
cd frontend
npm install
npm run build
```

服务启动后执行端到端冒烟测试：

```powershell
.\scripts\smoke-test.ps1
```

如果不是默认地址：

```powershell
$env:ANDROID_DOCTOR_URL = 'http://localhost:9090'
.\scripts\smoke-test.ps1
```

也可以直接在网页上传 `samples/` 中的三个示例文件。

## 安全与 MVP 边界

- 文本最多 1,000,000 字符，上传最多 2 MB；只接受常见纯文本日志扩展名。
- 单个来源默认每分钟最多 30 个写请求。该限流适合单机 MVP，不适合多节点部署。
- 历史记录默认最多返回 100 条；数据库未加密，生产环境应配合磁盘加密与访问控制。
- 当前没有登录系统。若暴露到公网，应先增加身份认证、反向代理 TLS、可信代理 IP 配置、持久化限流和审计策略。
- 规则引擎用于快速定位，不替代符号化、heap dump、Perfetto 或真机复现。

## 增加新规则

在 `backend/src/main/java/com/androiddoctor/service/RuleEngineService.java` 的规则列表添加：

1. 唯一规则代码与标题；
2. 分类、严重度和置信度；
3. 匹配函数；
4. 可能原因；
5. 修复建议。

随后在 `RuleEngineServiceTest` 添加对应日志样例，执行 `mvn test`。
