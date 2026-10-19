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
