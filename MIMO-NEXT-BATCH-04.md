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

---

## 回执 · BATCH-04

状态：**MIMO-DONE**

提交：b70ca45（M07 生产线 S08–S11 + 判据单测）· f95d3f2（修复首轮 CI 编译：委托属性 message smart-cast）· ca7fa45（修复单测红：Date 头用例星期/日期不一致）

CI：https://github.com/chadhao/jx-lab-trace-app/actions/runs/39333676602 · 结论 **success**（head ca7fa45：assembleDebug + **145 条单测全绿（0 失败）** + check_tokens + check_colors 均过；artifact app-debug-apk 23,996,016 bytes。B3 基线 113 + B4 新增 32）

### 判据

- **M7-1** ✓ `ProdBatchCreateTest` 6 条绿：①纯逻辑：有上批 ⇒ customer_id/input_material_id/planned_output_material_id 三项默认沿用且 `isValid=true`（必填 0）；②无上批 ⇒ 三字段 0 + `validate` 逐项 3 条提示（客户/投入/产出）；③仅缺产出 ⇒ 只提示 1 项；④MockWebServer 桩 `GET /batches` 返回上批 ⇒ `BatchCreateViewModel.load` 后表单默认值来自上批（`Editing.previous.id=7`）；⑤桩空列表 ⇒ 空表单 + `validationErrors().size==3`，`rebuild()` 本地清空零请求；⑥POST 请求体含三项主数据、**不含 code**（批号后端生成）。作废重建：后端**无** PATCH `/batches/:id`（mountProd 仅 GET/POST/GET/:id）⇒ 提交前重建＝本地清空（零请求），提交后作废转 PC（见设计疑问 1）。
- **M7-2** ✓ `FeedProgressViewModelTest` 2 条绿：`FeedProgress.afterConfirm(19)==20`；ViewModel 桩 `POST /feeds` 成功后**不发起任何刷新请求**，`fedCount` 由 0 即时变 1 且新记录插入列表头部（MockWebServer 计数：feeds POST=1、GET feeds=1（仅 load））。
- **M7-3** ✓ `FeedShiftAndCorrectTest` 8 条绿：①`ShiftRules.withinSameShift` 同班次 true / 跨班次（06:00→10:00）false；②**手机时间未来场景**：判定函数签名只有两个服务端时间入参（`fedAtMillis`＝后端 fed_at、`serverNowMillis`＝注入的 ServerTimeSource），手机时钟无入口——注入服务端时间跨班次 ⇒ `FeedCorrectSession.withinShift=false`，且 `submitCorrect` 二次门禁保证**零 PATCH**（MockWebServer PATCH 计数=0）；③服务端时间不可达（null）⇒ 宁灰不错放（置灰）；④班次边界 07:59/08:00 分属班 0/班 1；⑤`ServerClock.parseHttpDate` 解析 RFC1123 Date 头往返一致。班次定义见设计疑问 7。
- **M7-4** ✓ `FeedShiftAndCorrectTest` 3 条绿：①MockWebServer 捕获 PATCH `/api/prod/feeds/5` 请求体含 `"feed_id":5`、`"new_weight":1.2`、`"reason":"磅读错 · 磅显示看错一位"`、`"version":2`，并携带后端消费字段 `feed_weight`（与 B3 take 同口径：任务包字段多带、后端 json.Decode 忽略未知键，见设计疑问 5）；②PATCH 成功后 `correctDone.version==2`（列表行显示 v2，APP 侧版本链：后端无 version 列、审计留痕，见 FeedRecord.version KDoc）；③`FeedCorrectForm` 原因三选一（磅读错/扫错袋/其它）+ 说明必填 + 新重量>0 门禁，空/非法/零量均禁提交。
- **M7-5** ✓ `OperationIdempotencyTest` 9 条绿：①纯逻辑 `OperationTracker`：重复 `onEnd()` 第二次起返回 false；②**ViewModel 连点 3 次「结束」⇒ MockWebServer POST /operations 计数=1**，Done 状态含 1 条段记录；③断网（500 桩）提交失败 ⇒ 状态回 Running（表单态保留），再点结束重试 POST 计数=2（合法重试≠幂等重复）；④未开始不能结束；⑤`reset()` 支持下一段；⑥请求体含 `start_at`/`end_at`/`operator`；⑦服务端时间取自 HEAD /healthz 的 `Date` 头（禁手机时间）。
- **M7-6** ✓ 全仓 `git grep -n "TsplPrinter" -- android/app/src/main`：**定义仅 1 处**（`print/TsplPrinter.kt:9 object TsplPrinter`）+ **调用 1 处**（`print/SppChannel.kt:58`）；**prod 包零引用**——S11 `FgPrintViewModel` 与 S06 `BagPrintViewModel` 同构：打印出口经 `printOne: suspend (String) -> ApiResult<Unit>` 注入 + `LabelRenderer.render`（同一实现，`FgPrintPlanTest` 断言渲染尺寸恒 `PrinterProfile` 800×800）。不出现第二套打印实现。
- **M7-7** ✓ `git grep -in "rework" -- android/app/src` = **0 命中**；`git grep -n "insp\." -- android/app/src` = **0 命中**。返工与检测全 PC，APP 无任何入口。

### 后端契约核对（对照 jx-lab-trace internal/httpapi/prod.go + internal/store/production.go 实读）

- 响应形状：`{created,row}` / `{fed,row}` / `{corrected,row}` / `{count,rows}` —— **无顶层 `code` 键**，不触发 `ApiClient.bizCodeOf` 误判（与 take 的 `"code":"<对象码>"` 冲突不同）⇒ **走 executeRead/executeWrite 正常通道**（自带离线门禁+前置探测）。「非 envelope 直连映射」例外计数**仍为 1（仅 take）**，未触发 COLLAB 裁决的 executeRaw 抽取条件（第 3 个需直连映射的接口未出现）。
- 状态字面量：后端为中文（进行中/已完成/已作废）；任务包 `status=active` 对应取「进行中」；「上一批」默认取不带过滤的最近一条（ORDER BY id DESC）。

### 依赖变更

无白名单外新增；无新 Maven 依赖；未动 `design/`、`docs/`、`README.md`、`COLLAB.md`。

### 设计疑问

1. **批作废**：后端无 `PATCH /api/prod/batches/:id`（仅 GET/POST/GET/:id）——「提交前可作废重建」实现为**本地清空重建**（零请求）；提交后作废无 APP 入口（转 PC）。若后端后续补状态接口，APP 一行可接。
2. **S08「设备」字段**：后端 `CreateBatchInput` 无设备列，三项默认映射为 客户/投入物料/计划产出物料；「设备（沿用上批）」UI 未展示（无数据源）。班组为登录会话占位展示（`/api/me` 无班组字段）。
3. **作业段接口形态**：后端仅 `POST operations`（一次插入 start_at+end_at），无 PATCH——「开始/结束两点」实现为：**开始**取服务端时间作 start_at（本地计时仅展示）、**结束**一次性 POST 完整段；M7-5 幂等落在「Ended 后再点结束零请求」。若要求开始即落库（开放段），需后端补 update 接口。
4. **班次边界**：任务包/设计稿未定义班制——APP 按自然 8 小时三班（00–08 / 08–16 / 16–24，服务端时区）实现，常量集中在 `ProdLogic.ShiftRules.SHIFT_HOURS`；若现场为两班 12h（如 08–20/20–08）请裁决，改动一处即可。
5. **M7-4 请求体**：按判据含 `feed_id/new_weight/reason/version`（后端 `FeedCorrectInput` 忽略未知键），同时携带后端实际消费的 `feed_weight/remark/reason`（与 B3 take 的「多带字段保现网」同口径）。
6. **S09 进度分母**：设计稿「19/40」的计划总数后端无字段 ⇒ 展示「已投 N 袋（实时）」无分母；若需满度进度条请后端在批详情暴露计划袋数。
7. **S11 补打**：后端 print 首打后重复打须 `is_reprint+reason`（409）——S11 一期无补打入（补打转 PC），连打失败清单仅提示。

### 遗留

- 导航图仍未接（MainActivity 占位）；S08 `onCreated`、S09 `onStartScan`（连续扫码宿主：`ScanViewModel(confirmLabel="确认投料")` + `submitBag→submitFinished`）、S11 `printerState/onGoPrinter` 回调已就绪，待统一导航批次接线。
- 班次判定的 `zone` 取设备默认时区（服务端时间戳 + 手机时区换算）；若服务端与现场时区不一致需固定 `ZoneId`（一处可改）。
- 本地无 JDK，编译与 145 条单测全部经 CI 验证（与 B1–B3 相同惯例）；两次红均当轮修复（smart-cast ×4、Date 头用例 DOW 不一致）。
