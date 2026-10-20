# 后端需求 05 · exchange 的 token 端点契约修正（SDK 移动端登录）＋ 拒绝路径日志

> 提出方：APP 侧 ｜ 2026-10-21 ｜ 优先级：**P0（SDK 登录链路阻塞）**
> 取代/合并：REQ-04（拒绝路径日志并入本需求）。

## 根因（官方文档证据）

真机 LarkSSO 授权链路已跑通（SDK 拉起飞书授权 → 回 APP 带 code → exchange），但 exchange 用**网页 OAuth 的 token 契约**处理了**移动端登录（SDK）的 code**：

| | 后端现实现 | 官方文档要求（移动端登录） |
|---|---|---|
| token 端点 | `open.feishu.cn/open-apis/authen/v2/oauth/token` | **`passport.feishu.cn/suite/passport/oauth/token`** |
| redirect_uri | 传了（`callbackURI(c)`） | **移动端登录：否（不传）** |
| 其余参数 | grant_type/client_id/client_secret/code | 同左 |

来源：飞书官方文档「获取 access_token」（app-login/api-reference/get-access_token）明确标注「redirect_uri —— 移动端登录：否；Web端登录：是」。

**后果**：v2 端点未报错但换出的身份与移动端授权语义错位 → user_info 返回错误 open_id → `RolesOf` 查无角色 → AUTH_NO_ROLE。（数据库绑定、飞书通讯录、APP 映射均已排查排除，见 APP 侧排查记录。）

## 需求

`handleFeishuExchange` 增加**移动端来源分支**（推荐：请求体加 `source:"sdk"` 字段由 APP 显式声明；后端也可按 header/字段自动探测）：

```go
// source == "sdk"（LarkSSO 移动端登录）：
//   token 端点：https://passport.feishu.cn/suite/passport/oauth/token
//   参数：grant_type=authorization_code, client_id, client_secret, code
//   ★ 不传 redirect_uri（移动端登录契约）
//   ★ 挑战码模式后续可扩展 code_verifier（P1）
// source 为空/其它：保持现行为（网页 OAuth v2 端点 + redirect_uri）——callback 流程兼容性红线
```

- access_token 换出后，user_info（`/open-apis/authen/v1/user_info`）流程不变（v2/passport 两种 token 均兼容该端点，若实测不兼容以官方移动端登录文档指定的用户信息端点为准）。
- **拒绝路径日志**（原 REQ-04）：`ErrLoginDenied` 分支加 `c.Logger().Warn("exchange 拒绝: open_id=", ident.OpenID, " name=", name)`。
- 响应结构/错误语义不变（REQ-03 兼容性保持）。

## 验证

1. 部署后 APP（source=sdk）重试登录 → 应返回 200 + Set-Cookie（open_id=ou_b490a605... 已有 sysadmin 绑定）→ APP 进主页。
2. `logs/app.log` 出现拒绝路径日志（若仍有拒绝）。
3. 网页 OAuth callback 流程回归不受影响。

## APP 侧配合
APP 将在 exchange 请求体加 `"source":"sdk"`（APP 侧开发流程同步进行，等本端点上线后联调）。
