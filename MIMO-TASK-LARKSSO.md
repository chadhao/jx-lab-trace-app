# MIMO-TASK-LARKSSO · 登录升级：LarkSSO SDK 集成（替代 deep-link 方案）

> 派发：WorkBuddy → mimo ｜ 日期：2026-10-20 ｜ 优先级：高
> **项目方裁决（2026-10-20 21:10）**：走 LarkSSO SDK 方案；**direct deep-link 方案废弃，不要保留废物代码**。后端 `POST /api/auth/feishu/exchange` 已上线（实测 401 错误语义正确），本任务直接衔接。

## 工作区现状处置（重要——先读）

工作区有上一轮**未提交**的 deep-link 实现改动（`AuthApplink.kt` 新增、`FeishuAuthRepo.kt` 新增、`LoginFlow.kt` 修改）。处置规则：
- **保留复用**：`state` 生成/校验、percent-encode、`exchange` 调用与错误处理、LoginFlow 的 `ExchangeRejected`/拒绝信号、WebView 兜底流程；
- **删除**：`redirect_uri=jxlabtrace://` 替换逻辑、Applink 包裹（`APPLINK_PREFIX`）、与 deep-link 相关的 manifest/intent-filter 计划——这些是飞书后台拒绝的方案专属物，不入库；
- `AuthApplink.kt` 建议重构为 `FeishuSsoLogin.kt`（或并入 FeishuAuthRepo，mimo 定，回执注明）。

## 需求

1. **SDK 引入**：下载 `larksso-3.0.10.aar`（飞书官方 SDK Guide 页 https://open.feishu.cn/document/common-capabilities/sso/mobile-app/sdk?lang=zh-CN 内的「下载 LarkSSO Android lib」链接）→ `android/app/libs/larksso-3.0.10.aar`；`app/build.gradle.kts` 加 `flatDir` 与 `implementation(files("libs/larksso-3.0.10.aar"))`。
   - 文档另要求的 `com.githang:status-bar-compat`：**jcenter 已只读**——优先找可达镜像坐标；确实不可得则评估是否真被 SDK 运行时依赖（反编译 aar 查引用），不是则不引入并回执注明。
   - 下载源不可达 ⇒ 写 BLOCKED，不硬造。
2. **Manifest**：
   - intent-filter：scheme = `cliaa40b8435439dd0c`（app_id 去下划线，官方规则）；
   - `<queries>`：声明飞书客户端包名可见性（`com.ss.android.lark` 等，按官方 Demo）。
3. **集成代码**（官方 SDK Guide 为准）：
   - `LarkSSO.Builder().setAppId(BuildConfig.FEISHU_CLIENT_ID).setServer("Feishu").setScopeList(["contact:user.base:readonly"]).setContext(...)`；
   - `startSSOVerify` 拉起飞书 App；`onResume/onNewIntent/onActivityResult` 三处 `parseIntent`；
   - SDK 回调 `code` ⇒ **复用现有** `FeishuAuthRepo` 的 exchange 调用（POST /api/auth/feishu/exchange {code,state}）⇒ Set-Cookie 由 OkHttp CookieJar 持有 ⇒ /api/me ⇒ Success。
4. **BuildConfig 注入**：`build.gradle.kts` 加 `buildConfigField("String","FEISHU_CLIENT_ID","\"cli_aa40b8435439dd0c\"")`（可环境变量 `JX_FEISHU_CLIENT_ID` 覆盖，与 BASE_URL 同法）。client_id 是公开标识符，BuildConfig 注入允许（修订 A-5 口径：禁的是业务代码硬编码，BuildConfig 注入合法）。
5. **WebView 兜底保留**：未装飞书（SDK startSSOVerify 失败/无响应）⇒ 回落现有 WebView 流程；WebView 兜底代码不删。

## 判据

- S-1 CI（Gitea）全绿：编译通过 + 166+ 单测 + 三门禁；
- S-2 exchange 单测保留且通过（MockWebServer 桩）；
- S-3 全仓 grep：`jxlabtrace://` = 0（deep-link 方案清除干净）；
- S-4 `git grep FEISHU_CLIENT_ID android/app/src/main --include=*.kt` 仅 BuildConfig 引用，无业务代码硬编码字面量；
- S-5 真机验证（拉起飞书 App → 授权 → 回 APP）由项目方执行，APP 侧交付以单测+CI 为准。

## 纪律
显式路径提交、`mimo: LarkSSO 集成` 前缀、push gitea remote 轮询内网 CI、回执写本文件末尾、拦断写 BLOCKED。
