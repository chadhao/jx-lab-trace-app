# MIMO-TASK-APPLINK · 登录升级：Applink 拉起飞书 App 授权（依赖已解除）

> 派发：WorkBuddy → mimo ｜ 日期：2026-10-20 ｜ 优先级：高（产品体验升级）
> **前置已全部就绪**：①后端 `POST /api/auth/feishu/exchange` 已上线并实测（假 code → 401 `{"error":"授权码无效或已过期"}`，错误语义符合 REQ-03）；②飞书后台白名单已含 `http://office.hunanyichu.com:5502/api/auth/feishu/callback`；③**新增待加**：`jxlabtrace://auth/callback`（项目方正在飞书后台尝试添加，见 §5）。

## 设计（官方 Applink 方案，零 SDK 依赖）

### 流程
```
APP 组装 authorize URL（client_id=cli_aa40b8435439dd0c 公开值、
  redirect_uri=jxlabtrace://auth/callback、scope=contact:user.base:readonly、state=随机）
→ 包裹 Applink：https://applink.feishu.cn/client/web_url/open?mode=window&url=<urlencode(authorize URL)>
→ ACTION_VIEW 拉起飞书 App（App 内开授权页，复用飞书登录态，免输账密）
→ 用户确认授权 → 飞书重定向 jxlabtrace://auth/callback?code=…&state=…
→ APP deep-link 接收 → POST /api/auth/feishu/exchange {code,state}
→ Set-Cookie jx_sid（OkHttp CookieJar 持有）→ GET /api/me → 进主页
```

### 改动面
1. **Manifest**：MainActivity 增 `intent-filter`（scheme `jxlabtrace`，host `auth`，path `/callback`，`exported` 相关按 deep-link 规范，`launchMode=singleTop` 或独立 AuthCallbackActivity——mimo 定，回执注明）。
2. **登录启动逻辑**（LoginWebViewActivity 或新 LoginCoordinator，mimo 定）：
   - OkHttp 客户端 `followRedirects=false` 请求 `GET {BASE_URL}/api/auth/feishu/start`——**取 302 Location 作为 authorize URL**（client_id/scope 由后端统一维护，APP 不硬编码 client_id）；
   - **把其中 `redirect_uri` 参数替换为 `jxlabtrace://auth/callback`**（其余参数原样保留）——这样 client_id/scope 仍由后端控制，APP 只改回调目标；
   - 生成随机 `state` 存内存（登录会话期），encode 授权 URL 后包裹 Applink 并 `startActivity`；
   - `ActivityNotFoundException`（未装飞书）⇒ **回落现有 WebView 流程**（保留不删）。
3. **回调处理**：deep-link 收到后校验 `state` ⇒ `POST /api/auth/feishu/exchange`（body `{code,state}`）⇒ 成功（Set-Cookie 已被 OkHttp CookieJar 持有）⇒ `/api/me` ⇒ Success 态进主页；`error=access_denied` ⇒ 拒绝卡；401 ⇒ 显示后端 `error` 文案。
4. **防重入**：Applink 与 WebView 两条流程互斥（同一时间只有一条在跑）。

### 明确不做
- PKCE（后端 REQ-03 标注 P1，二期加固）；
- 删除 WebView 登录（它是无飞书 App 设备的兜底）；
- 触碰后端仓库/服务器。

## 判据

- A-1 Applink URL 组装纯函数单测：authorize URL encode 正确（不双重编码）、redirect_uri 替换正确、state 随机性。
- A-2 deep-link 校验：state 不匹配 ⇒ 拒绝处理；error=access_denied ⇒ 拒绝卡文案。
- A-3 exchange 成功路径：MockWebServer 桩 200+Set-Cookie ⇒ CookieJar 持有 sid ⇒ /api/me 调用带 cookie。
- A-4 未装飞书回落：ActivityNotFoundException ⇒ WebView 流程启动。
- A-5 全仓 grep：client_id 硬编码 = 0（client_id 只来自后端 302）。

## 前置（项目方）
- 飞书后台「重定向 URL」添加 `jxlabtrace://auth/callback`（若飞书 UI 拒绝自定义 scheme ⇒ 通知验收方，走备选方案：后端 callback 成功页 JS 二次跳转 deep-link，方案已备）。

## 纪律
同前（显式路径、mimo: 前缀、push gitea remote 触发内网 CI、token 轮询、回执本文件末尾、拦断写 BLOCKED）。真机验证（拉起飞书 App）由项目方配合，APP 侧交付以单测+CI 为准。
