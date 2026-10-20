# 后端需求 03 · 飞书授权码直换会话端点（APP Applink 接入依赖）

> 提出方：APP 设计侧 ｜ 日期：2026-10-20 ｜ 优先级：高（真机飞书授权体验升级的依赖项）
> **说明：本需求由项目方转达后端开发实施；APP 仓库会话不触碰服务器端代码。**
> **状态更新（2026-10-20 项目方裁决，见 MIMO-TASK-LARKSSO.md）**：APP 侧授权改为
> LarkSSO SDK 拉起飞书 App（回调 scheme = app_id 去下划线），下文 deep-link 链路描述作废；
> **本端点规格 `{code,state}` 与错误语义不变，仍为 APP 唯一换会话入口**（WebView 兜底走既有
> `GET /api/auth/feishu/callback`）。

## 背景

APP 端登录方式升级：由「WebView 网页账密登录」升级为「Applink 拉起飞书 App 授权」（飞书官方推荐，用户体验：免输账密，用飞书登录态一键授权）。链路：

```
APP → applink.feishu.cn/client/web_url/open?url=<authorize URL> → 拉起飞书 App 内授权页
→ 用户确认授权 → 飞书重定向 redirect_uri（APP 已注册 deep-link：jxlabtrace://auth/callback）
→ APP 收到 code → 【新增端点】POST /api/auth/feishu/exchange → 建会话
```

现有 `GET /api/auth/feishu/callback` 是为 **WebView Cookie 流**设计的（set-cookie 落在浏览器，APP 拿不到），无法服务 Applink 流程，故需要新端点。

## 需求：新增 `POST /api/auth/feishu/exchange`

### 请求
```json
{ "code": "<飞书授权码>", "state": "<APP 原样回传>" }
```
Content-Type: application/json。

### 行为（复用现有 handleFeishuCallback 的身份链路）
1. 用 `code` + `JX_FEISHU_APP_SECRET` 调飞书 `code 换 open_id`（与现有 callback 相同调用）；
2. `deny-by-default` 角色映射（未映射角色 ⇒ 拒绝，与现有判定完全一致）；
3. 通过 ⇒ 建会话（session TTL 同现有 12h 滑动）。

### 响应
- 成功：**`Set-Cookie: jx_sid=<sid>; HttpOnly; SameSite=Lax; Max-Age=43200`** + JSON `{"ok":true}`（APP 的 OkHttp CookieJar 会持有该 cookie，后续请求自动带会话；APP 成功后自行调 `GET /api/me` 取身份）。
- 失败：标准 JSON 错误（与现有错误结构一致）：
  - code 无效/过期：`401 {"error":"..."}`
  - 角色未映射：`403 {"error":"...","userMessage":"您的账号尚未分配角色，请联系综合运营部"}`
  - 飞书上游错误：`502 {"error":"..."}`（详情进服务端日志，不回传用户）

### 安全要求
- `state` 校验：与 authorize 时下发的一致（防 CSRF；APP 生成并内存保存）。
- **建议（P1）**：PKCE 支持——authorize 带 `code_challenge(S256)`，exchange 带 `code_verifier`，防授权码拦截。APP 侧已具备实现条件。
- 限速：同现有 auth 端点策略。

### 兼容性
- 不改动现有 `GET /api/auth/feishu/callback`（WebView 兜底登录仍依赖它）；
- `JX_DEV_MODE` 行为不变（dev 桩登录不受影响）。

## APP 侧依赖时序

该端点上线前，APP 的 Applink 接入**不上线**（当前继续走 WebView 兜底）。端点就绪后 APP 侧改动（deep-link 注册 + Applink 拼装 + exchange 调用）由 APP 仓库开发流程完成，预计 1 个批次。
