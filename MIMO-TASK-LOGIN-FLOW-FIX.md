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
