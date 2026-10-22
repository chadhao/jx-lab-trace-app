# MIMO-TASK-AB-INTEGRATION · 批次 A+B 数据接入（首页聚合 + 收货域六动作）

> 派发：WorkBuddy → mimo ｜ 2026-10-21 ｜ 优先级：高 ｜ 预计 40 分钟
> **契约唯一依据：`docs/APP-API-A-B.md`（已入库快照，462 行/22 章节，逐字段遵循）**——先通读该文档再动代码（§0 全局规则）。

## 范围（全部 `/api/app/*`，Cookie jx_sid 鉴权）

### A · S02 首页三数据卡接 `GET /api/app/home`
按契约 §3 结构解析：`me`（open_id/name/roles）/ `domains[]` / `in_progress[]`（key/label/domain/count/latest.id·code·title·status·at）/ `todos[]`（key/label/domain/count）/ `recent[]`——替换现有 EmptyState 占位（字段映射：进行中卡=in_progress 列表+count 角标；待办卡=todos；最近作业卡=recent）。

### B · 收货域六动作（§4 六端点逐字段接入）
| 屏 | 端点 | 说明 |
|---|---|---|
| S04 到货确认 | 预报列表 + arrive-confirm + return | 选预报（无预报走拦截页） |
| S05 过磅 | weigh | 毛重录值，皮重/净重按契约（服务端算净重） |
| S06 建袋打码 | bag-gen + reprint | 袋序后端分配，APP 不计算不回传 |
| S12 装车出场 | 装车归集 + 出场 | 逐袋扫码归集 |

### 拦截页对接（§2.1/§2.2/§5）
4xx 响应统一解析**结构化四字段** `{error,reason,owner,contact}` → 现成 `InterceptPage` 组件（原因+owner+contact 三段展示）——替换任何裸 error 文案展示。401 特判：清 sid 回 S01。

## 通用纪律
- **数值/时间格式严格按契约**（重量吨 3 位小数、`generated_at` RFC3339、日期 YYYY-MM-DD）；
- **序号一律不计算不回传**（契约 §2）；
- 写动作提交按钮单一路径（设计规则⑥）；
- MockWebServer 单测：每端点至少 1 条**按契约响应形状**的正例 + 1 条结构化拦截（四字段）反例；
- 文件白名单：`auth/`（MeRepository/api 客户端扩展）、各屏 kt、新建 `app/` 数据层（mimo 定结构回执注明）；**禁读 scan/ 目录**。

## 判据
- I-1 内网 CI 全绿（构建+全部单测含新增 MockWebServer 契约用例+三门禁+lint）+ 新 APK；
- I-2 S02 三卡在 CI 的 Mock 层返回契约形状时正确渲染（单测覆盖）；
- I-3 结构化拦截四字段在 InterceptPage 正确展示（单测覆盖）；
- I-4 真机（项目方）：登录 → 首页亮数据 → 收货域六动作真机走通。

push gitea 触发内网 CI，绿后回执写本文件末尾；后端联调问题如实写 BLOCKED（不硬造数据）。

---

## 回执（mimo · 2026-10-22）

**交付**：提交 `9b38484`（17 文件，+1442/−250）——批次 A+B 全量接入，契约唯一依据 `docs/APP-API-A-B.md` V1.0（先通读后动代码，全程未读 scan/）。

### 范围落点

| 项 | 实现 |
|---|---|
| A · S02 三数据卡 | `app/AppModels.kt` 契约 DTO（HomeOverview/me/domains/in_progress/todos/recent）；`HomeScreen` 的 HomeRepository 改接 `GET /api/app/home`，OverviewSection 三卡＝in_progress（含 count 合计+最新 title/status）/todos（label+count 拼接）/recent（首条 action+at）；提案模型（todo_recv 等）删除 |
| B · 收货域六动作 | `RecvRepository` 全量切 `/api/app/*`：notices（`?mode=` 查询）、arrive-confirm（notice_id→truck_lot_id 扁平响应）、return（**按 notice_id 定位车次**）、weigh（皮重可省略⇒服务端历史皮重/净重，请求体条件字段）、bag-gen（袋序后端分配不回传）、label/reprint（单袋 bag_code+reason，非 codes[]） |
| 结构化拦截四字段 | `ApiError.Intercept(httpCode, error, reason, owner, contact)`；`ApiClient.mapResponse` 对 4xx（除 401 保持会话重授权路径）解析 `{error,reason,owner,contact}` 四字段齐备 ⇒ Intercept，不中回退旧映射（Web 端点兼容）；`AppInterceptPage` 三段展示（error 标题+reason 正文+owner 胶囊+contact 行）+ 出路按钮（409 刷新/其余返回）；S04/S05/S06 错误态分流：Intercept⇒拦截页、其余⇒旧话术空态 |
| 401 特判 | 保持 Unauthorized→SessionCoordinator 重授权→失败回 S01（既有链路不动，契约四字段不覆盖会话路径） |
| S12 装车归集+出场 | **未接**——契约 §7 明示批次 C（出货域）本批不做；ShipRepository 维持既有 Web 端点，无 `/api/app/ship/*` 契约可依（不硬造） |

### 文件白名单遵守

- 新建 `app/` 数据层：`app/AppModels.kt`（结构 mimo 定，本回执注明）；
- `auth/` 未改（MeRepository 无需扩展——home 聚合自带 me）；
- 各屏 kt：HomeScreen / NoticesScreen / WeighScreen / BagPrintScreen + RecvViewModels/RecvLogic/RecvRepository；
- 网络层：ApiClient/ApiError/ErrorMessages（结构化拦截支撑）。

### 单测（MockWebServer 契约用例）

- 新增 `AppApiContractTest`（14 条）：home 正例（§3.2 原文形状→三卡映射断言，**I-2**）+ 403 四字段反例；六端点各 ≥1 正例（断言请求体字段/路径/响应映射）+ ≥1 结构化拦截反例（404 无预报/400 无历史皮重/409 尚未过磅/400 缺原因等，**I-3** 数据层）；
- 新增 `AppInterceptDisplayTest`（2 条，Robolectric Compose）：四字段三段展示可见性 + 409 刷新出路（**I-3** UI 层）；
- 更新 `FullFlowSmokeTest`（收货三步切新端点）、`ReprintAndReturnTest`（bag_code+reason、notice_id 退车、409 四字段）、`WeighAndSppTest`（皮重可空门禁）；
- 收尾修复 3 处：① 反引号方法名含 `.`（JVM 非法）→ `S4-x`；② `takeRequest()` 撞上 healthz 前置探测 → `takeBusinessRequest()` 跳过；③ `ApiClient.resolve` 把 `?mode=` 编码进路径段 → 拆查询串挂 `addQueryParameter`；④ AppInterceptPage 嵌套 InterceptPage 的 fillMaxSize 挤掉下方内容 → 自绘三段布局。

### 判据

- **I-1 ✅** 本地全量：`testDebugUnitTest` 210 全绿 + `lintDebug` + `assembleDebug` 通过；内网 Gitea Actions **run43（head `9b38484`）status=success**（与 run38/39 成功同码；runner.log 13:07 启动、13:14 Publish 刷新 APK）；APK 产出 `/srv/jx-lab-trace/ci/apk/app-debug-latest.apk` = 36,621,129 bytes（mtime 2026-10-22 13:14）。
- **I-2 ✅** `AppApiContractTest.I-2 home契约形状`：契约 §3.2 示例 JSON → HomeOverview 解析 → inProgressCardValue/todosCardValue/recentCardValue 三卡映射逐字段断言。
- **I-3 ✅** 数据层：六端点+home 的 4xx 四字段均映射 `ApiError.Intercept`（error/reason/owner/contact 逐字段断言）；UI 层：`AppInterceptDisplayTest` Robolectric 断言 error/reason/owner/contact 四段可见。
- **I-4 ⏳ 真机（项目方）**：登录 → 首页亮数据 → 收货域六动作——待项目方真机验收，APP 侧无阻塞。

### 如实声明

- 辅助只读 `getTruck`/`listBags` 不在契约 §4 六端点内，维持 Web 端 `/api/recv/trucks/:id[/bags]`（同 Cookie 鉴权；幂等恢复/过磅上下文用）；
- S12 装车归集+出场属批次 C，本批无契约端点可接（§7），未动 ShipRepository；
- 后端联调：本批未发现 BLOCKED——契约形状与 MockWebServer 单测对齐；真机联调若后端未部署 `/api/app/*` 将走结构化拦截/空态占位，不硬造数据。
