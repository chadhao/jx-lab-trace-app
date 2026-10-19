# MIMO-NEXT-BATCH-04 · 批次 B4（M07 生产线：S08/S09/S10/S11）

> 派发：WorkBuddy → mimo ｜ 日期：2026-10-20
> 前置：B1/B2/B3 已验收。设计依据：`design/screens/index.html#s08 #s09 #s10 #s11`。
> 后端事实：`POST /api/prod/batches` · `POST /api/prod/batches/:id/feeds` · `PATCH /api/prod/feeds/:id` · `POST /api/prod/batches/:id/operations` · `POST /api/prod/batches/:id/fg-lots` · `POST /api/prod/fg-lots/:id/bags` · `POST /api/prod/fg-lots/:id/print` · `GET /api/prod/batches?status=active`。

## 0. 必读

`COLLAB.md` · `MIMO-NEXT-BATCH-03.md` 回执（含 **take 非 envelope 例外与 executeRaw 约束**——若 feeds/fg-lots 响应同样非 envelope，**同样适用直连映射并计入例外计数**）· `design/screens` 四节。

## 1. 做什么

- **S08 建批**：三默认（上批配方/设备、登录班组）+ 批号只读；`POST batches`；提交前可作废重建（调 `PATCH batches/:id` 状态或按后端能力，进回执说明）。
- **S09 投料**：连续扫码模式（复用 M04 ScanViewModel，`confirmLabel="确认投料"`）；每袋 `POST feeds`；进度 = 本地累计 + 提交成功回调刷新；**更正**：列表每条入口 → `PATCH feeds/:id`（reason 必填三选一 + 说明）；**班次判定用服务端时间**（`Date` 头或 `/healthz` 响应时间，禁用手机时间），超班次 ⇒ 入口置灰 + PC 指引（不发请求）。
- **S10 作业段**：开始/结束两个按钮 ⇒ `POST operations`；本地计时仅展示；断网时按钮可用但提交拦截（表单态保留）。
- **S11 成品打码**：`POST fg-lots`（批号自动）→ 袋数（折算默认）→ `POST fg-lots/:id/bags` → 连打复用 M09 队列与 `POST fg-lots/:id/print`；谱系自动挂接（后端完成）。

## 2. 判据（M7）

- M7-1 建批页零输入：默认值来自上批（MockWebServer 桩单测）；无上批 ⇒ 字段空 + 提交校验逐项提示。
- M7-2 投料进度：扫码确认后本地即时 +1，不依赖刷新（ViewModel 单测）。
- M7-3 班次判定：手机时间改为未来 ⇒ 更正入口仍置灰（服务端时间注入单测）。
- M7-4 更正请求体：含 feed_id/new_weight/reason/version；PATCH 成功后列表显示 v2。
- M7-5 作业段：重复点「结束」幂等（第二次不发请求）。
- M7-6 成品打码复用 M09 队列：不出现第二套打印实现（grep `TsplPrinter(` 引用仅 1 处定义 + N 处调用）。
- M7-7 白名单：无 `rework`、无 `insp.*`（返工与检测全 PC）。

## 3. 纪律

同前批次。被过滤拦断 ⇒ 回执 BLOCKED。CI 门禁：check_tokens/check_colors 已在。
