# 后端需求 04 · exchange 拒绝路径记录身份（排查用，1 行日志）

> 提出方：APP 侧 ｜ 2026-10-21 ｜ 优先级：高（真机身份不匹配排查的实锤手段）

## 背景
真机 LarkSSO 授权链路已跑通（SDK 授权 → exchange → 后端角色检查），但返回 AUTH_NO_ROLE。数据库核实：`s_user_role` 中 `ou_b490a605a715a6a514d5d87c9e9b73ed → sysadmin` 绑定正常，且飞书通讯录 API 实测该 open_id 对应「郝端」——绑定与飞书侧均正确。**嫌疑：exchange 换出的 open_id 与绑定不一致（如手机飞书多账号/租户切换）**，但后端拒绝路径无日志，无法实锤。

## 需求
`handleFeishuExchange` 的 `ErrLoginDenied` 分支（openSession 返回拒绝处）加一条日志，记录**本次 exchange 实际换到的 open_id**：
```go
c.Logger().Warn("exchange 拒绝: open_id=", ident.OpenID, " name=", name)
```
（无需改任何响应结构/流程，仅诊断日志。）

## 验证
部署后 APP 重试登录一次 → `logs/app.log` 出现该行 → 将日志行发回 APP 侧对比绑定 open_id，即可定位身份不匹配的根因（多账号/数据问题/解析 bug）。
