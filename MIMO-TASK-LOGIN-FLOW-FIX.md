# MIMO-TASK-LOGIN-FLOW-FIX · 登录后导航缺陷修复（真机首验暴露）

> 派发：WorkBuddy → mimo ｜ 日期：2026-10-21 ｜ 类型：BugFix ｜ 优先级：高
> 背景：真机首次走通 LarkSSO 授权链路（拉起飞书授权→exchange→后端角色拒绝 AUTH_NO_ROLE——身份不匹配问题另行排查），暴露两个登录后流程缺陷：①拒绝确认后 APP 卡在 B1 占位主页（白屏只有应用名+版本，无法重试，杀进程重开仍卡）；②占位主页的 sid 判断只在 onCreate，从登录返回后不重新路由。

## 需求（3 项，最小改动）

1. **MainActivity 路由修正**：`onCreate` 的 sid 判断逻辑改为 `onCreate` + `onResume` 双检查（用 `Lifecycle` 或 `onResume` 覆盖——从 LoginWebViewActivity 返回时重新路由）：
   - `sidStore.read()` 为空 ⇒ `startActivity(LoginWebViewActivity)` 并保持自身无 UI 占位（现状行为）；
   - 非空 ⇒ 停留在占位主页（真实主页导航为后续批次，**本任务不接 NavHost**）。
   - **防抖**：LoginWebViewActivity 在前台时 MainActivity.onResume 不要重复 startActivity（用 resultLauncher/标志位，mimo 定实现，回执注明）。
2. **拒绝卡加「重试登录」**：`LoginUiState.Failed` 态（AUTH_NO_ROLE 等全部失败类型）在「我知道了」旁/下增加「重试登录」按钮 → 重新走 `beginAuthorize()`（SDK 主路径，内含 WebView 回落）。
3. **拒绝确认（我知道了）的行为**：从「停留」改为 `finish()` 返回 MainActivity——MainActivity 由第 1 项的 onResume 路由自动再次拉起登录屏，形成可恢复循环。

## 判据

- F-1 单测：Failed 态 UI 含「重试登录」动作回调；`我知道了` 触发 finish 信号。
- F-2 MainActivity 路由逻辑抽可测纯函数（如 `shouldRouteToLogin(sid: String?): Boolean`）并单测（null/空/非空三分支）。
- F-3 内网 CI 全绿（构建+单测+三门禁）。
- F-4 真机场景（项目方验证）：拒绝确认 → 自动回登录屏可重试；不再出现无法恢复的白屏占位页。

## 纪律
显式路径提交、push gitea 触发内网 CI、回执写本文件末尾、拦断写 BLOCKED。

## 回执（mimo · 2026-10-21）

### 交付
1. **① MainActivity 路由双检查 + 防抖**（`MainActivity.kt`）：
   - 纯函数 `shouldRouteToLogin(sid: String?): Boolean = sid.isNullOrBlank()`（F-2 单测入口；null/空串/纯空白均视为无会话）。
   - `onCreate` 末尾 + `onResume` 均调 `routeToLoginIfNeeded()`：空 sid ⇒ 拉起 `LoginWebViewActivity`，非空 ⇒ 停占位主页（未接 NavHost，按任务口径）。
   - **防抖实现（mimo 定，回执注明）**：`registerForActivityResult(StartActivityForResult)` 的 resultLauncher + `awaitingLoginResult` 标志位——launch 前置位，结果回填清位；Login 在前台期间 onResume 见位直接 return，不重复 startActivity；从登录屏关闭返回后清位，onResume 按最新 sid 决定是否再拉起。
2. **② Failed 态加「重试登录」**（`LoginScreen.kt` `FailedContent`）：
   - `canRetry=true`（网络/拒绝类）：主按钮已是 actionLabel（重试/重新登录）→ `onRetry`，未改动。
   - `canRetry=false`（NoRole）：原「我知道了」保留为主按钮（`onAcknowledge`）；下方新增 `OutlinedButton`「重试登录」→ `onRetry`（宿主已接 `beginAuthorize()`，SDK 主路径含 WebView 回落）。
   - **数据层不动**：`LoginFailure.canRetry` 仍为 false（M3-1 口径），仅 UI 加副按钮——`LoginFailureTest` 原 6 断言保持全绿。
3. **③「我知道了」改 finish 返回**（`LoginWebViewActivity.kt`）：
   - `onAcknowledge` 从共用 `handleCancel` 拆出独立 `handleAcknowledge()`：清理 handler/webView/流程态后 **清会话（`sidStore.write(null)` + CookieManager 过期清 jx_sid）再 `finish()`**。
   - **清会话是本修复的关键**：AUTH_NO_ROLE 路径 exchange 已 2xx，`jx_sid` 已被 `JxCookieJar` 自动落盘——若只 finish 不清，主屏 onResume 判 sid 非空，仍停白屏占位页（真机首验 bug 根因即此）。清后 onResume 见空 sid 自动再拉登录屏，形成「拒绝 → 回登录 → 可重试」可恢复循环。
   - `handleCancel`（Authorizing 态「取消登录」）行为不变：非 reauth 回 Initial，reauth 回填桥后 finish。

### 判据
- **F-1 ✅** 新增 `LoginFlowFixTest.kt`（Robolectric Compose）：NoRole 失败卡「重试登录」显示且点击仅触发 `onRetry`；「我知道了」显示且点击仅触发 `onAcknowledge`（finish 信号）；两按钮互不串扰；数据层 canRetry=false/actionLabel=我知道了 不回归——7 条全 PASSED。
- **F-2 ✅** `shouldRouteToLogin` 三分支单测：null→true、""/"   "→true、非空→false——3 条全 PASSED。
- **F-3 ✅ 内网 CI run 21 全绿**：head `ca87073`，status=success（构建+单测+lint+三门禁）；本机同口径 `assembleDebug testDebugUnitTest lintDebug` BUILD SUCCESSFUL，**184 单测全绿**（基线 177 + 新增 7）；`check_tokens/check_colors/check_scope` OK；APK 已产出 `/srv/jx-lab-trace/ci/apk/app-debug-latest.apk`（36,506,275 B，02:53）。
- **F-4** 真机场景（项目方验证）：拒绝确认 → 自动回登录屏可重试；不再出现无法恢复的白屏占位页。APP 侧以单测 + CI 交付。

### 事实与风险留痕
- 提交：`ca87073` `mimo: 登录后导航缺陷修复（…）`，显式 5 路径（4 改 + 1 新测试），push gitea 触发 run 21。
- `onRetry` 在 NoRole 也走 `beginAuthorize()`：用户换账号或后台已补角色时可当场重试；`flowMode` 失败时已复位 Idle，防重入仍有效。
- Authorizing 态「取消登录」仍停留登录屏 Initial（未改）——任务只要求「我知道了」改 finish；若后续要 cancel 也返回主屏，属新需求。
- 本回执提交自身会触发 run 22（md-only），不作判据；判据跑为 run 21（head `ca87073`）。

---

## 追加任务（2026-10-21 09:05）· AUTH_NO_ROLE 判定漏 sys_roles（真机实锤根因）

**根因（后端/DB/飞书三层全查实锤）**：后端 /api/me 返回 `roles=[] + sys_roles=["sysadmin"]`（N-075 设计：sysadmin 是系统角色进 sys_roles）；Me 模型已有 `sysRoles` 与 `allRoles` 便捷属性（MeRepository.kt:18/23），**但 LoginFlow 的无角色判定只看 `me.roles.isEmpty() && me.permissions.isEmpty()`，漏了 sysRoles** ⇒ sysadmin 用户被误判 AUTH_NO_ROLE。10:03 的 s_session 两次会话均成功建立（open_id=郝端 ✓，ip=4G 出口）——授权/交换/建会话全成功，纯判定 bug。

**修复**：`loginOutcomeOf` 的 `LoginSignal.MeLoaded` 分支判定改为 `me.allRoles.isEmpty() && me.permissions.isEmpty()`（用现成 `allRoles`）。
**单测**：Me(roles=[], sysRoles=["sysadmin"]) ⇒ Success；Me(全空) ⇒ AUTH_NO_ROLE（两条都要）。
**判据**：内网 CI 全绿 + 新 APK。10 分钟。
