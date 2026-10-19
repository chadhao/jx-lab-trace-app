# MIMO-NEXT-BATCH-03 · 批次 B3（M05 收货线 + M06 取样 + M09 打印 · 两级出口）

> 派发：WorkBuddy → mimo ｜ 日期：2026-10-20
> 前置：B1/B2 已验收（网络层/组件/扫码内核/错误话术全部就绪）。
> 设计依据：`design/screens/index.html` 的 **S04、S05、S06、S07、S15** 五节（本批 UI 事实）。
> 打印事实（已实测）：KM-118BT · TSPL 主通道 · 实际可打宽度 ≈99mm ⇒ **位图 800×800、内容区 ≤96×96mm、右缘 2mm 禁区**。详见 `docs/PRINTER-KM118BT.md`。

## 0. 动手前必读

1. `COLLAB.md` 2. `MIMO-NEXT-BATCH-01/02.md` 回执（B1/B2 交付能力清单）3. `docs/SCOPE-APP.md`
4. `design/screens/index.html#s04 #s05 #s06 #s07 #s15`
5. 后端事实：`GET /api/recv/notices` · `POST /api/recv/arrivals` · `POST /api/recv/trucks/:id/weigh` · `POST /api/recv/trucks/:id/bags` · `POST /api/recv/labels/print` · `POST /api/recv/labels/reprint`（body 须 reason）· `POST /api/recv/trucks/:id/return` · `GET /api/sample/targets` · `POST /api/sample/take`

## 1. M05 收货线（S04/S05/S06）

- **S04 到货确认**：`NoticesViewModel` 拉 `GET /api/recv/notices`（本地过滤当日+按车牌，D23）；点选 → 确认对话框 → `POST arrivals`；**无预报拦截页**：输入车牌 → 本地过滤最近 3 条 → 一键拨号（`Intent.ACTION_DIAL`，占位号 `0730-xxxxxxx` 常量集中一处待替换）；退车入口放车次详情菜单（二次确认）。
- **S05 过磅**：车次上下文卡 + 皮重（历史默认）/毛重（NumPad 录入）/净重自动；提交 `POST weigh`；成功后跳 S06。
- **S06 建袋打码**：袋数默认按净重折算（0.6t/袋，可改）→ `POST bags`（幂等：失败重试不重复建，本地以车次+序号去重）→ **连打队列**：逐袋调 `POST labels/print`，进度条/当前码/异常列表；`POST labels/reprint` 补打页（原因三选一 + 说明必填 + 张数）。

### 判据（M05）
- M5-1 无预报拦截页：不产生任何写请求（单测断言零 POST）+ 拨号 Intent 构造正确。
- M5-2 建袋幂等：MockWebServer 桩「第 13 袋 500」→ 重试后车次袋数仍 40 且无重复序号（单测）。
- M5-3 补打：reason 为空 ⇒ 提交按钮禁用（纯逻辑单测）；reprint 请求体含 reason。
- M5-4 退车：二次确认对话框（组件复用）+ `POST return` 单测。

## 2. M06 取样（S07 · 生产班组）

- 对象三选（车次/生产批/成品批，来自 `GET /api/sample/targets`）→ 扫码或手输定位（复用 M04 ScanViewModel，`confirmLabel="确认取样"`）→ 取样类型（份样默认/大样/保留样）+ 取样量（默认 500g）+ 份样数（默认按约定）→ `POST /api/sample/take`。
- `sampled_by`＝会话账号，UI 不出现编辑入口。**无留样入库入口**（验收方会 grep `retain` 应为 0）。

### 判据（M6）
- M6-1 取样表单必填 ≤1（单测断言默认值填充后表单有效）。
- M6-2 `POST take` 请求体断言：含 target_type/target_id/sample_weight/samples，**不含** sampled_by（由后端会话定）。
- M6-3 全仓 grep `retain` 在 `android/app/src/main` 零命中。

## 3. M09 打印（S15 · 随 B3 同批，两级出口 D21）

- **PrinterProfile**：`{dotsPerMm=8, maxWidthDots=800, safeMarginDots=16, protocol=TSPL}` 集中常量（无魔法数）。
- **LabelRenderer（纯函数，可单测）**：输入袋码字段（车码人读行+对象类型+日期+序号）→ 输出 `BooleanArray(800*800)`：布局按 S06 设计稿的标签版式（QR ≥6 dots/module、右缘 2mm 禁区、内容区 96×96）。
  - 单测：①尺寸恒 800×800；②四角安全区全 0；③QR 定位图案存在（左上 finder pattern 抽样断言）。
- **TsplPrinter**：`BITMAP` 指令封装（`SIZE 100 mm,100 mm` + `CLS` + `BITMAP` + `PRINT 1`），蓝牙 SPP 通道；连接管理（扫描/配对/记忆/断连提示「去连接」）。
- **自检页**：S15 的「自检打印」→ 发送 100mm 标尺位图（与 `docs/printer/ruler-100mm.png` 同布局）。
- **两级出口**：B3-a＝S04–S06 业务闭环 + **系统打印通道**（`PrintHelper` 打开位图）可用；B3-b＝蓝牙 TSPL 直发自检通过（真机验证，验收方持机配合）。

### 判据（M9）
- M9-1 LabelRenderer 三条单测（上）。
- M9-2 TsplPrinter 指令组装单测（不发真机：断言指令字节含 `SIZE 100 mm` 与 `PRINT 1`，位图行数 800）。
- M9-3 未连接打印机 ⇒ 打印入口显示「去连接」状态卡（Compose 状态单测）。
- M9-4 全仓无 `203`/`800` 魔法数散落（仅 PrinterProfile/常量文件可出现）。

## 4. 明确不做

预报新建/编辑、车辆建档、留样入库/借还、检测任何动作、撤销/销毁/审批、报告与导出（全 PC）。B4 再做：建批/投料/作业段/成品打码（M07）。

## 5. 纪律与回执

同前批次。**新增门禁已并入 CI**：check_colors.py。被过滤拦断 ⇒ 回执写 BLOCKED，不要反复重试。

---

## 回执 · BATCH-03

状态：**MIMO-DONE**

提交：68079ac（M09 打印核心）· c5c93de（M05 收货线）· 30b5117（M06 取样）· 3526b91（S15+蓝牙权限）· 34111a4（判据单测）· eb7e318（修复：PDF 换算/魔法数/副作用）· bf0a1e6（首轮 CI 编译修复）· 156a8d8（单测函数名非法字符）· 055a321（healthz 空响应/ViewModel 等待/substring）· 1215ff9（take envelope code 误判/IntRange/409 独立 server）

CI：https://github.com/chadhao/jx-lab-trace-app/actions/runs/39323697041 · 结论 **success**（head 1215ff9：assembleDebug + 113 条单测全绿 + check_tokens + check_colors 均过；artifact app-debug-apk 23,860,607 bytes）

### 判据

- **M5-1** ✓ `NoNoticeInterceptTest` 3 条绿：拦截路径零 POST/PUT/PATCH/DELETE（MockWebServer 计数）；`DialIntent(action=android.intent.action.DIAL, uri=tel:0730-xxxxxxx)` 构造正确；`hasTodayNotice` 拦截判定（历史 3 条已完成 ⇒ 拦，今日有预报 ⇒ 不拦）。`NoNoticeModel` 结构上无任何写方法。
- **M5-2** ✓ `BagIdempotencyTest` 5 条绿：①`planRetry` 对 40 连号袋返回 `AlreadyDone` 不再 POST；②ViewModel load 后已有 40 袋 ⇒ `Built` 且序号 1..40 无重复；③merge 按 bag_seq 去重；④isContiguous 非连号/数量不符为 false；⑤500 桩后 GET 幂等采用。幂等入口：重试前先 `listBags`，`planRetry` 分 AlreadyDone/GenerateAll/Partial。
- **M5-3** ✓ `ReprintAndReturnTest`：空类别/空说明/非三选一 ⇒ `isValid=false`（提交按钮禁用门禁 `enabled = ReprintForm.isValid(...)`）；MockWebServer 捕获 reprint body 含 `"reason":"标签破损 · 撕破重贴，位置不变"` 与 `"codes"`。
- **M5-4** ✓ `ReprintAndReturnTest`：`ReturnFlow.CONFIRM_TITLE/MESSAGE` 二次确认文案固定（组件 `ConfirmDialog` 复用）；`POST /trucks/3/return` body 含 reason、响应 status=已退货；409 → `ApiError.Server` 且话术非空。
- **M6-1** ✓ `SampleFormTest`：默认 500g/5 份填充后定位目标即 `isValid=true`（必填 0）；取样量 0/份数 0/无 code/target_id=0 ⇒ 无效。
- **M6-2** ✓ `SampleFormTest`：POST take body 含 `target_type/target_id/sample_weight/samples`，**不含 sampled_by**；`SampleTakeBody` 序列化同源断言。★ 后端 `TakeInput` 现仅消费 `code`，APP 按任务包口径多带表单字段（Go `json.Decode` 忽略未知键）+ `code` 保现网可用；且 take 的 2xx 响应含 `"code":"<对象码>"` 会被 envelope 业务码检查误判 ⇒ `SampleRepository.take` 改直连状态码映射（与 ScanService 同口径），已在代码注释固化。
- **M6-3** ✓ `git grep retain -- android/app/src/main` = **0 命中**。
- **M9-1** ✓ `LabelRendererTest` 5 条绿：①`size == 800*800` 且 `PrinterProfile.MAX_WIDTH/HEIGHT_DOTS == 800`；②四角 SAFE_MARGIN(16) 区域全白 + 右缘 2mm 禁区全白；③QR 左上 finder 外圈黑/内环白/芯黑（编码器矩阵 + 渲染位图双断言）；自检标尺同尺寸同安全区。
- **M9-2** ✓ `TsplPrinterTest` 4 条绿：指令含 `SIZE 100 mm` 与 `PRINT 1`；`BITMAP 0,0,800,800,0,`；`bitmapRowCount == 800`；packBits 100 字节/行；尺寸不符抛异常。`WeighAndSppTest`：SPP 写流落完整 TSPL 指令、关流后写失败有话术、工厂 IOException ⇒ 连接失败话术。
- **M9-3** ✓ `PrinterGateTest` 6 条绿：`printerUiStateOf(false) ⇒ Disconnected` 且提示含「去连接」；Compose 断连状态卡显示「去连接」且点击回调；已连接显示设备名与「自检打印」。
- **M9-4** ✓ 全仓 `git grep -E "\b(203|800)\b" -- android/app/src/main`：**仅** `PrinterProfile.kt`（常量文件）与 `LabelRenderer.kt` 的 KDoc 注释出现；`ScanScreen`/`ScanViewModel` 的「800ms」为注释非魔法数（实义常量 `ECHO_VISIBLE_MS=1100`）；蓝牙 SPP UUID `00805F9B34FB` 含 800 子串但为标准 UUID 字面量。业务代码尺寸一律经 `PrinterProfile.*` 取值。

### 两级出口（D21）

- **B3-a（已完成，代码+单测）**：S04–S06 业务闭环 + `PrintHelper.printBitmapViaSystem`（系统打印通道打开位图 PDF 页）可用；断连时打印入口走「去连接」或系统打印。
- **B3-b（待验收方持机）**：蓝牙 TSPL 直发自检——`PrintSession.sendSelfTest` 发 100mm 标尺位图；真机验证需验收方配合（本机无 JDK/无打印机）。

### 依赖变更

无白名单外新增（蓝牙为平台 API；无新 Maven 依赖）。

### 设计疑问

1. 后端 `POST /api/sample/take` 的 `TakeInput` 仅 `{code}`，而任务包 M6-2 要求 body 含 `target_type/target_id/sample_weight/samples` —— APP 按任务包组装（多带字段后端忽略）+ 附带 `code`；若后端后续升级为全字段消费，APP 无需改动。
2. 后端 take 成功响应含 `"code":"<27位对象码>"` 与 envelope 业务码字段同名 —— `ApiClient.bizCodeOf` 会误判为 Biz；take 已改直连状态码映射。建议后端长期把对象码字段改名（如 `object_code`）以免其他 write 路径踩坑。
3. 退车 `POST return` 的 `reason` 后端可选；APP 二次确认对话框暂不强制填原因（与后端一致），若验收要求必填请裁决。

### 遗留

- B3-b 蓝牙真机自检（KM-118BT 实打出纸）留待验收方持机；自检位图与 `docs/printer/ruler-100mm.png` 同布局意图（满宽刻度+四角标记）。
- 过磅页皮重「历史默认」当前取该车次已有 `tare_weight`；跨车次同牌历史皮重（列表回查）待真机数据验证后可加。
- 导航图仍未接（MainActivity 占位）；S04→S05→S06 的页面跳转回调已就绪（`onConfirmed`/`onWeighed`），待 B4/B5 统一导航批次接线。
- 补打对话框为页面内联简化版（原因三选一 FilterChip + 说明输入）；完整 bottom-sheet 视觉可随设计稿二期细化。
