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
