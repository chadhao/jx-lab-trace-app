# MIMO-TASK-LOGIN-DIAG · 登录诊断增强（单任务包）

> 派发：WorkBuddy → mimo ｜ 日期：2026-10-20 ｜ 优先级：高（真机飞书授权卡点定位）
> 背景：真机 S01 登录点「用飞书账号登录」后，过渡屏「正在打开飞书授权…」持续转圈，WebView 从未渲染任何内容（录屏证据：新诊断小字显示「正在加载：(等待首个跳转)」——`onPageStarted` 一次都没触发）。
> 服务端探针正常（`vf726f74 · 10.0.0.12`）。当前卡点需要真机侧证据，本任务把诊断能力补全。

## 需求（3 项）

1. **导出诊断日志按钮**（登录过渡屏 Authorizing 态、「取消登录」上方）：
   - 点击 ⇒ `AppLogger.snapshot()` 全量（含 `LoginWeb` 事件）按 `HH:mm:ss.SSS LEVEL [tag] message` 逐行拼文本；
   - `Intent.ACTION_SEND`（`text/plain`，subject「登录诊断日志」）系统分享面板发出；
   - 空缓冲显示「(缓冲为空)」。
2. **WebChromeClient**：`onConsoleMessage` ⇒ `AppLogger.i("LoginWeb", "console[LEVEL] msg (source:line)")`；返回 true。
3. **WebView 远程调试**：`WebView.setWebContentsDebuggingEnabled(true)`（debug 排查用）。

## 判据

- D-1 导出文本格式化抽为纯函数（如 `LoginDiag.exportText(entries): String`），单测断言逐行格式与空缓冲兜底。
- D-2 WebChromeClient/onConsoleMessage 逻辑存在且写 AppLogger（可用 Robolectric 免——代码审查+编译即可，不强制单测）。
- D-3 `check_scope.py` / `check_colors.py` / lint 全绿。

## 纪律

同前（显式路径提交、`mimo: 诊断增强` 前缀、push 后用 `.env.local` token 轮询 CI 到 success）。完成后回执 `MIMO-DONE` 写在本文件末尾。

---

## MIMO-DONE（2026-10-20 · mimo）

三项需求全部落地：

1. **导出诊断日志按钮**：`LoginScreen.AuthorizingContent` 在「取消登录」上方新增「导出诊断日志」（新参数 `onExportDiag`，`LoginWebViewActivity` 接线）；点击 ⇒ `LoginDiag.exportText(AppLogger.snapshot())` 全量（含 `LoginWeb`）→ `Intent.ACTION_SEND`（`text/plain`，EXTRA_SUBJECT「登录诊断日志」）系统分享面板；空缓冲兜底「(缓冲为空)」。
2. **WebChromeClient**：`LoginWebViewActivity` 挂 `onConsoleMessage` ⇒ `AppLogger.i("LoginWeb", "console[${messageLevel}] ${message} (${sourceId}:${lineNumber})")`，返回 true。
3. **远程调试**：`onCreate` 中 WebView 创建前调用 `WebView.setWebContentsDebuggingEnabled(true)`。

判据证据：

- **D-1 ✅** 格式化抽纯函数 `LoginDiag.exportText(entries)` / `LoginDiag.formatLine`（`auth/LoginDiag.kt`，纯 JVM 无 Android 依赖）；单测 `LoginDiagTest.kt` 4 用例断言逐行格式 `HH:mm:ss.SSS LEVEL [tag] message`、多条按序拼接、空缓冲「(缓冲为空)」兜底。本地 `gradlew testDebugUnitTest` BUILD SUCCESSFUL：`LoginDiagTest tests=4 failures=0`，全套 166 用例 0 失败（test-results XML 实证）。
- **D-2 ✅** `onConsoleMessage → AppLogger.i` 落于 `LoginWebViewActivity.kt`（代码审查+`compileDebugKotlin` 通过；按判据不强制单测）。
- **D-3 ✅** 本地复跑：`check_scope.py` OK（11 组 PC 专属词零命中）、`check_colors.py` OK（零裸色值）、`check_tokens.py` OK；`gradlew testDebugUnitTest` 绿、Kotlin 编译零错（仅存量 deprecation 警告）。lint 由 CI `android.yml` `lintDebug` 执行，以本轮推送 CI success 为准（结果见下一行补记）。

CI 轮询结果：run [39402878900](https://github.com/chadhao/jx-lab-trace-app/actions/runs/39402878900)（head `66f300b`）conclusion=**success** —— `lintDebug` + `testDebugUnitTest` + `assembleDebug` + `check_tokens/check_colors/check_scope` 三门禁全绿。

证据汇总：D-1 纯函数 `LoginDiag.exportText` + `LoginDiagTest` 4/4 过（本地 XML `failures=0`，全套 166 绿）；D-2 `WebChromeClient.onConsoleMessage → AppLogger.i("LoginWeb", …)` 落码且随 CI 编译过；D-3 三脚本本地 OK + CI success。

设计疑问：无

---

## 后续任务（2026-10-20 18:10 追加）· BASE_URL 指向真实后端

**根因已定位（验收方 SSH 实证）**：真机连不上后端的根因是 **APP 的 `BASE_URL` 仍是设计稿示例地址 `http://10.0.0.12`**（gradle 默认值）——那是工厂网络里另一台设备（对 /healthz 误回 200 造成探针假阳性），真正的后端在 `http://192.168.10.50:18080`（已实测：healthz 200、/api/auth/feishu/start 正确 302 到飞书，redirect_uri=`http://192.168.10.50:18080/api/auth/feishu/callback`）。

**任务**：
1. `android/app/build.gradle.kts` 第 12 行默认值改为 `http://192.168.10.50:18080`（保留 JX_BASE_URL 环境变量覆盖能力）。
2. 单测/资源不动；判据：grep BASE_URL 构建值 = 新地址；CI 全绿。

**背景信息**：诊断日志实证手机 WebView 对 10.0.0.12 的 /api/auth/feishu/start 得到 `net::ERR_EMPTY_RESPONSE`（那台设备不认识该路径）；/healthz 的 200 是其它服务的假阳性。

---

## 追加任务二（2026-10-20 18:25）· BASE_URL 终值：公网端点

**公网端点已实测打通**（验收方 curl 实证）：`http://office.hunanyichu.com:5502/healthz` → 200（version 18c9c21）；`/api/auth/feishu/start` → 302 飞书，redirect_uri 自动为 `http://office.hunanyichu.com:5502/api/auth/feishu/callback`（反代透传 Host 正确）。

**任务**：`android/app/build.gradle.kts` 的 BASE_URL 默认值改为 **`http://office.hunanyichu.com:5502`**（保留 JX_BASE_URL 覆盖）。判据：CI 全绿 + 构建 APK 内 BASE_URL=新值。

**回执（mimo · 追加任务二）**：✅ 已完成 —— `build.gradle.kts` 默认值 `http://10.0.0.12` → `http://office.hunanyichu.com:5502`，`JX_BASE_URL` 环境变量覆盖保留（`System.getenv("JX_BASE_URL") ?: 默认值` 结构未动）；注释同步为「默认取验收环境域名」。CI 轮询结果：run [39407136444](https://github.com/chadhao/jx-lab-trace-app/actions/runs/39407136444)（head `37dde4a`）conclusion=**success**（lintDebug + 单测 + assembleDebug + 三门禁全绿，构建 APK 内 `BASE_URL` 即新默认值）。
