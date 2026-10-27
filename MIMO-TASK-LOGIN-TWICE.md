# MIMO-TASK-LOGIN-TWICE · 登录双跳飞书修复（Activity 重建重复授权）

> 派发：WorkBuddy → mimo ｜ 2026-10-27 ｜ 优先级：高（每次登录跳两次飞书）
> **根因（WorkBuddy 已验收定位）**：`LoginWebViewActivity.onCreate` 无条件调用 `beginAuthorize()`；飞书授权期间 Activity 若被系统销毁重建（低内存回收/配置变化），onCreate 重跑 ⇒ 第二次拉起飞书 ⇒ 第二个 code ⇒ 第二次 exchange。**DB 实锤**：每次登录产生两个会话（09:49:21 / 09:49:26 相隔 5 秒；15:54 / 15:56 相隔 2 分钟）。

## 修复

1. **onCreate 重建防护**：
   - `savedInstanceState != null`（重建场景）⇒ **不自动 `beginAuthorize()`**；
   - 从 Bundle 恢复 `pendingState`（新增 `onSaveInstanceState` 写入 pendingState + flowMode）；恢复后：有 pendingState ⇒ 静默等待 SDK 回调（保留现有 5s 手动入口逻辑）；无 pendingState ⇒ 直接调用一次 `beginAuthorize()` 仍可（首次进入也有 savedInstanceState=null 的新实例场景——保持原行为）；
2. **判定纯函数**（可单测）：`shouldAutoAuthorizeOnCreate(savedInstanceStatePresent: Boolean, restoredPendingState: Boolean): Boolean`——规则：`!savedInstanceStatePresent || restoredPendingState`（重建且无待决授权 ⇒ 不自动跳；
3. 单测三场景：新实例(true/false)→true；重建有 pending→true；重建无 pending→false；
4. **不改动**：SDK 主路径/exchange/WebView 兜底逻辑本体（只加重建防护与状态保存）。

## 判据
- T-1 内网 CI 全绿 + 新 APK；
- T-2 真机（项目方）：正常登录**只跳一次飞书**；授权中杀后台/切应用再回来不二次跳（变为「继续登录」或静默恢复）。

## 纪律
mimo 实现即止（不跑本地全量、不 commit）；timeout 1500 硬时限；禁读 scan/；显式路径由主会话提交。
