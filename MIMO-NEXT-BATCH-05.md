# MIMO-NEXT-BATCH-05 · 批次 B5（M10 出货 + M11 追溯报表 + M12 完整 + M13 验收发布）· 最终开发批

> 派发：WorkBuddy → mimo ｜ 日期：2026-10-20
> 前置：B1–B4 全部验收（145 单测基线）。设计依据：`design/screens/index.html#s12 #s13 #s14`。
> 后端事实：`POST /api/ship/shipments` · `POST /api/ship/shipments/:id/items` · `POST /api/ship/shipments/:id/depart` · `GET /api/trace/forward|backward` · `GET /api/trace/batch/:batchId` · `GET /api/prod/genealogy/bags/:code` · `GET /api/rpt/:name`。
> **注意**：`POST shipments` 系列若响应非 envelope（对照 B4 的 prod.go 经验，实读 ship.go 确认），按直连映射处理并计入 executeRaw 计数。

## 1. 做什么

- **M10 出货线（S12）**：建出货单（选客户/车，来自上下文）→ 逐袋扫码归集（复用 ScanViewModel，`confirmLabel="归集此袋"`）→ 重复袋即时拦截（本地集合 + 服务端二次校验）→ 汇总实时 → `POST depart` 出场。
- **M11 追溯与报表（S13/S14）**：
  - 追溯：ScanHero（已就绪）→ 解析后按对象类型路由 forward/backward/batch/genealogy 四接口 → 时间线 UI（S13 稿）；任一节点只读。
  - 报表：管理层首页变体（4 Tab）接 `GET /api/rpt/:name`；卡片只读刷新。
- **M12 完整**：四类拦截页数据模型齐全（码无效✓/无权限✓/无预报✓(B3)/无车辆档案——并入无预报拦截页的文案变体）；`ErrorMessages` 增补后端各业务 409 码映射表（ship.void 除外——那是 PC）。
- **M13 验收与发布**：
  - `scripts/check_app.sh`：聚合 check_tokens + check_colors + `gradlew lintDebug testDebugUnitTest assembleDebug`（CI 内跑）+ **白名单扫描脚本 `check_scope.py`**（`insp.*`/`notice.create`/`md.*`/`void`/`destroy`/`concession`/`urgent`/`conclusion`/`rework`/`export`/`retain` 在 android/app/src/main 零命中）。
  - workflow 增加上述步骤。
  - `docs/ACCEPTANCE-APP.md` 骨架：B1–B5 判据索引 + 录屏/截图占位。
  - 全流程 MockWebServer 冒烟单测：登录 → 到货 → 过磅 → 建袋 → 取样 → 建批 → 投料 → 成品 → 装车 → 出场 → 追溯（一条链贯通，断言每步关键请求）。

## 2. 判据（M10/M11/M13）

- M10-1 重复袋：MockWebServer 场景两次同码 ⇒ 第二次本地拦截（服务端计数仍 1）。
- M10-2 汇总实时：归集成功后袋数/净重本地即增。
- M11-1 谱系四接口适配单测（响应样例固定）。
- M11-2 报表只读：无导出/无写入口（grep `rpt/export`=0）。
- M13-1 check_app.sh 全绿；check_scope.py 通过。
- M13-2 全流程冒烟单测绿（≥11 步链）。
- M13-3 APK artifact 产出（CI）。

## 3. 纪律

同前批次。**这是最后一个开发批次**：回执 `MIMO-DONE` 后进入终验（验收方全量复核 + 交付总结）。

---

## 回执 · BATCH-05

状态：**MIMO-DONE**

提交：a04d15d（B5 主体：M10 ship + M11 trace/rpt + M12 完整 + M13 门禁/冒烟/验收骨架）· 4f77ace+2e338fa+036ff1f+227eb9b（TraceViewModels 编译修复迭代：语句式 when → if+raw 绑定）· f55e6ff（snake_case 重命名但 PowerShell 管道毁 UTF-8——已撤销）· a75756e（Python UTF-8 精确重做 has_flow/fg_lots 9 处）· 708f6be（ShipLoadTest String? orEmpty）· ecefe6d（lint MissingPermission ×6）· 97c6d9f（lint UnsafeOptInUsageError ×1）

CI：https://github.com/chadhao/jx-lab-trace-app/actions/runs/39353032078 · 结论 **success**（head 97c6d9f：lintDebug + assembleDebug + testDebugUnitTest 全绿；**168 条单测全绿 0 失败 0 跳过**（B4 基线 145 + B5 新增 23）；check_tokens OK + check_colors OK + **check_scope OK（11 组白名单模式零命中）**；artifact app-debug-apk **24,140,175 bytes**）

### 判据

- **M10-1** ✓ `ShipLoadTest` 绿：①`CollectedBagSet` 纯逻辑——同码第二次 `add` 返回 false；②MockWebServer ViewModel 双扫同码：首袋 `createCount=1`，第二次回调 `DUPLICATE_BAG_MESSAGE`（「该袋已在本次出货单中，不重复计数。」）且 **createCount 仍 1 / addItemCount 仍 0**（本地拦截零请求，服务端计数保持 1）；③换码再扫走 `addItem`（items 计数=1）。
- **M10-2** ✓ `ShipLoadTest` 绿：注入 `weightOf`（1.01/1.02 t）——首袋后 `bagCount=1 / netWeight=1.01`、次袋后 `2 / 2.03`，**全程仅 2 次 POST、零 GET 刷新**（本地即增不依赖刷新）；纯逻辑 `ShipSummary.add` 同步覆盖（含无重量源时净重 null 只计袋数）。
- **M11-1** ✓ `TraceAdapterTest` 7 条绿（响应样例固定，逐字段断言）：①forward 车次→批→成品→出货全链解析（source/batches/fg_lots/shipments/has_flow）；②forward query 带 `truck_lot_id=1`；③backward 含投料+现行检测行（inspection_no/is_current/results[].judge，**不读顶层结论键——白名单约束**）；④batchArchive 汇总计数按嵌套数组长度（投料2/作业1/成品批1/出货1）；⑤genealogy 袋→去向批；⑥has_flow=false ⇒ Success 非错误；⑦四接口 requestCount=4 均走 executeRead 正常通道（裸对象顶层无 code 键，bizCodeOf 不误判）。
- **M11-2** ✓ `ReportReadonlyTest` 5 条绿：①报表查询仅 GET + MockWebServer 写请求计数=0 + 列/行解析；②筛选拼 query；③空数据两态文案不同（has_data=false「数据未接入」/ true+空 rows「本条件下无记录」）且有数据时 emptyHint=null；④ViewModel 4 Tab 切换刷新全程零写请求；⑤五张报表名与后端目录逐字一致。`rpt/export` 字面零命中由 check_scope 门禁覆盖（`git grep` android/app/src/main = 0）。
- **M13-1** ✓ CI run 39353032078 全绿：`check_tokens` OK（Tokens.kt 4308 bytes 一致）+ `check_colors` OK（无裸色值）+ **`check_scope` OK（11 组 PC 专属词模式在 android/app/src/main 零命中）** + `lintDebug` success + `testDebugUnitTest` success + `assembleDebug` success。`scripts/check_app.sh` 聚合脚本已交付（CI 以分步等价执行以保留红行捕获与分步可观测；check_app.sh 供本地/终验一键复跑）。
- **M13-2** ✓ `FullFlowSmokeTest` 绿：**13 步链贯通**（登录 me → 到货 → 过磅 → 建袋 → 取样 take → 建批 → 投料 → 成品批 → 成品袋 → 装车建单 → 装车追加 → 出场 → 正向追溯），每步 assert Success + 整链 13 条关键请求逐条 contains 命中 + businessHits≥13（healthz 前置探测不计）。
- **M13-3** ✓ artifact `app-debug-apk` **24,140,175 bytes**（run 39353032078，90 天）。

### 后端契约核对（对照 jx-lab-trace internal/httpapi/ship.go + trace.go + rpt.go 实读）

- **ship 响应形状**：`{created,row}` / `{added,row}`（row 为整单）/ `{departed,row}` / `{row,items}` / `{count,rows}` —— **均无顶层 `code` 键**，不触发 bizCodeOf 误判 ⇒ 走 executeRead/executeWrite 正常通道（离线门禁+前置探测）。「非 envelope 直连映射」例外计数**仍为 1（仅 take）**，未触发 COLLAB 裁决的 executeRaw 抽取条件。
- **trace/rpt/genealogy**：三 trace 接口裸对象、genealogy `{found,row}`、rpt `{name,title,columns,rows,has_data,note}` —— 顶层均无 code 键，同上正常通道。
- **409 形态**：后端 jsonErrorHandler 统一 `{"error":"中文"}` 无机器码 ⇒ ApiClient 新增 HTTP 409 → `Biz("HTTP_409", 后端话术)` 映射；ErrorMessages 增补 `KNOWN_409_TEXTS`（12 条按原文包含匹配：已归集/已出场/无明细/跨客户/取号/退车联动/作废/已投料等，**不含出货撤销链——PC**）；机器码命中优先于文案匹配（两级优先级，单测固化）。
- **重量列缺口**：ShipmentItem 无重量列、Shipment 无净重列 ⇒ S12 净重累加走 `weightOf` 注入（生产默认 null 只展示袋数）——见设计疑问 1。
- **E/C 码追溯缺口**：forward 只收 B 袋码、backward 只收 D 批码、genealogy 收 B 袋码、batch 收数字 id ⇒ 扫 E/C 码给拦截指引（见设计疑问 3/4）。

### 依赖变更

无白名单外新增；无新 Maven 依赖；未动 `design/`、既有 `docs/`、`README.md`、`COLLAB.md`。（新增 `docs/ACCEPTANCE-APP.md` 为 M13 明令交付的**新文件**，未改任何既有文档。）

### CI 修复轮次（7 推，可复盘）

1. a04d15d 主体推送 → TraceViewModels `hasFlow`/`fgLots` Unresolved（**真因：模型为 snake_case `has_flow`/`fg_lots`，视图层误写 camelCase**）。
2–5. 4f77ace/2e338fa/036ff1f/227eb9b 误诊为「when 表达式公共类型塌缩」做的重构（语句式 when、if+raw 绑定）——重构保留无害，但未除根。
6. f55e6ff 用 PowerShell 管道做重命名 ⇒ **整文件 UTF-8 被毁**（中文注释/字符串乱码+断行）；教训：多字节文本替换必须用 Python 等显式 UTF-8 工具。
7. a75756e 从 227eb9b 恢复 + Python 精确替换 9 处 ⇒ 主源码编译过；708f6be 修 ShipLoadTest `String?`；ecefe6d 修 lint MissingPermission ×6（蓝牙入口已有 hasPermission 守卫，`@SuppressLint` 注明）；97c6d9f 修 lint UnsafeOptInUsageError ×1（`androidx.annotation.OptIn(markerClass=…)` 形态）⇒ **success**。

### 设计疑问

1. **S12 净重数据源**：后端 ship 全部接口无单袋重量/整单净重字段（ShipmentItem 无重量列）。APP 已实现 `weightOf` 注入累加（M10-2 单测证明本地即增机制）；生产接线默认 null ⇒ UI 只显示「已扫 N 袋」。若验收要求设计稿的「净重 xx.x t」KPI，请后端在 items 明细或单头暴露 `weight_allocated`/净重聚合，APP 一行注入即可点亮。
2. **S12 出场车牌/司机**：后端 `DepartInput.plate_no/driver` 必填非空，但建单接口无车辆字段、设计稿出场屏未见录入位（似假定自动带出）。APP 实现为出场表单两个必填输入框（空值禁提交）；若期望「车牌随建单带出」需后端在建单入参增加车辆字段。
3. **S13 扫 C 生产批码**：`GET /trace/batch/:batchId` 只收数字 id，无「批码→id」查询（GET batches 需 prod.batch.create READ，管理层/收货员无）。扫 C 码现给指引文案（从车次/成品批链路节点点开档案——节点已带 batch_id 可下钻）。若需扫 C 直查请后端加 code→id 解析或放开 batches 读权限。
4. **S13 扫 E 成品袋码**：设计稿 S13 头图示扫 E 码出全链时间线，但后端四接口均不收 E 码（forward 收 B、backward 收 D、genealogy 收 B）。APP 扫 E 给拦截指引（「请扫成品批码（D）反向追溯」）。**设计稿与后端能力不符，请裁决**：改设计稿 / 后端补 E 码入口 / 维持指引。
5. **追溯检测结论展示口径**：白名单门禁禁 `conclusion`/`urgent`/`concession` 字面量（check_scope 零命中硬门禁）⇒ APP **不建模** inspection 顶层结论键、urgent_release、archive.concession_* 字段；合格与否由 `results[].judge` 逐行展示（ignoreUnknownKeys 丢弃未建模字段）。若验收要求时间线显著标「让步/紧急」，需先裁决门禁词口径（例如允许只读展示词）。
6. **S14 4 Tab 选择**：5 张报表取 quality-trend / customer-recon / output-yield / sample-expiry 入 Tab；nonconform-stat 未入（Tab 定额 4）。如需替换或第 5 Tab 请指定。
7. **check_scope 模式口径**：对 `void`/`destroy`/`export` 用词边界与上下文约束（`\bvoid\b`、`retain\.destroy|destroy\.(init|approve)`、`\bexport\b`），避免误伤**已验收**的平台 API 与领域词（`webView.destroy()`、`android:exported`、`ALREADY_VOID`/`voided`——B2 起在案）；其余 8 组为字面直扫。模式设计已固化在 `scripts/check_scope.py` docstring，如需更严口径（例如连 `voided` 也禁）请裁决——那会波及 B2 已验收代码。
8. **lint 门禁首跑**：B1–B4 从未跑过 lintDebug；B5 首跑暴露既有代码 7 处问题（蓝牙 MissingPermission ×6、CameraX UnsafeOptIn ×1），均已按标准形态修复（运行时守卫 + Suppress 注明；androidx OptIn markerClass），未改行为语义。

### 遗留

- 导航图仍未接线（沿袭 B4 遗留）：S12 `LoadScreen`、S13 `TraceScreen`（ScanHero 入口回调就绪）、S14 `ReportScreen` 及各 VM 的 onConfirm/onStartScan 回调已就绪，待统一导航批次接线。
- B3-b 蓝牙直打真机自检、四类拦截页真机截图、全流程录屏：`docs/ACCEPTANCE-APP.md` 骨架已建（判据索引 + 占位表），待验收方真机填写。
- 本地无 JDK，编译/lint/168 单测全部经 CI 验证（B1–B4 相同惯例）；7 轮推送中 6 轮为当轮修复，毁码轮（f55e6ff）为工具链事故、次轮完整撤销。
