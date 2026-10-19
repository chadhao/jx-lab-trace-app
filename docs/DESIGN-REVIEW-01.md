# 设计全量复核报告 01（换模型后重查）

> 复核日期：2026-10-19。方式：不采信此前自报，逐份重读全部产物，并与后端仓 `jx-lab-trace` 的规格/路由/种子**重新对账**。
> 范围：README、DECISIONS、SCOPE-APP、MODULES、PRINTER-KM118BT、BACKEND-REQ-01、design/README、tokens.json、screens/index.html、concepts/index.html，共 10 份。

## 一、结论

发现 **11 处问题**：9 处已修复，2 处转为待办。另确认 **8 项通过**（见第三节）与 **6 项遗留待决**（见第四节）。

## 二、问题清单

| # | 文件 | 问题 | 严重度 | 处置 |
|---|---|---|---|---|
| F01 | README.md | 范围表仍是 v1.0 口径：质检员在列且含「检验值录入、出结论」；无取样归属说明 | 高 | 重写（18 权限点 / 3 角色 / 16 屏口径） |
| F02 | DECISIONS.md | v1.1 / v1.2 两次边界修订未入决策记录，「已定死不得静默改动」的纪律被自己打破 | 高 | 追加 D15–D19，待定区逐条更新 |
| F03 | MODULES.md | 页面编号错位 5 处：M07 写 S09–S12（应 S08–S11）、M09 写 S17（应 S15）、M10 写 S14（应 S12）、M11 写 S15/S16（应 S13/S14，正文两处）、M12 写 S18（应 S16） | 高 | 全部修正 |
| F04 | MODULES.md | 头部仍标「v1.0 · 待确认」「18 屏」 | 中 | 改 v1.2 / 16 屏 |
| F05 | MODULES.md | M06 判据「以 qc 账号调用时入口不可见」——qc 已不装 APP，此判据不可执行 | 中 | 改为「无 sample.take 权限的账号（收货员/管理层）入口不可见」 |
| F06 | MODULES.md | M13 真机全流程仍含「录值」（检测已移出） | 中 | 流程改为 收货→取样(生产)→建批→投料→成品打码→装车→出场→追溯 |
| F07 | MODULES.md | M02 组件 `SegmentedPass`（合格/不合格段选）随检测线移出失去用途，留着即死代码 | 低 | 换成 `NumPad`（过磅/取样量需要） |
| F08 | tokens.json | qc 域绿色标签写「质检 / 化验」——取样已归生产班组，标签与归属矛盾 | 低 | 改「取样 / 化验」；**色不变**：颜色跟业务性质，不跟执行人 |
| F09 | screens/index.html | 首页「最近作业」收货行右侧显示「合格」——收货动作没有合格概念，合格是检测结论 | 低 | 改「已入库」 |
| F10 | concepts/index.html | 历史比选稿未标注已被取代，稿内含已移出的检测线界面，存在被据此开发的风险 | 中 | 页首加历史稿横幅 + design/README 标注 |
| F11 | MODULES.md | M04 扫码未指明码解析接口与失败分类的来源 | 中（补强） | 写明 `GET /api/recv/scan?code=…`，并把后端「不存在三分」（400 码非法 / 404 对象不存在 / 409 业务拒绝）落为 APP 失败文案的对应要求 |

**根因**：F01–F07 全部是同一类错误——**边界两次修订时只改了 SCOPE 与 screens 清单，没有回扫其余文档**。这与后端侧「spec 八处同步」的教训同源：一次裁决落多处，必须有一份「改动波及面清单」并逐处核销。

**流程修正（即刻生效）**：此后每次边界修订，必须同步核对 README / DECISIONS / MODULES / design/README / tokens 五处的口径，并在复核报告里逐条勾销。

## 三、通过项（重新对账后确认无误）

| # | 核对项 | 结果 |
|---|---|---|
| P1 | SCOPE 权限分账：APP 18 + PC 33 = 51，与后端 `permission-points.json` 权限点总数一致 | ✓ |
| P2 | MODULES 引用的 API 前缀与后端一致：`/api/recv` `/api/sample` `/api/prod` `/api/ship` `/api/trace` `/api/rpt`（对照 `internal/httpapi/*.go` 的 Group 注册） | ✓ |
| P3 | M05/M06/M07/M10/M11 引用的具体路由（arrivals / weigh / bags / return / labels/reprint / take / batches / feeds / fg-lots / operations / shipments / depart / forward / backward / batch/:batchId / genealogy / rpt/:name）均在后端路由清单中存在 | ✓ |
| P4 | BACKEND-REQ-01：`seed.sample.take` 行号（第 107 行）、`INSERT IGNORE` 不覆盖已有行的陷阱、`PUT /api/admin/permission-matrix` 接口，均与后端代码事实一致 | ✓ |
| P5 | 包名 `cn.hunanjiangxi.jxlabtrace`、中文名「江熙追溯」在 README / DECISIONS / tokens 三处一致 | ✓ |
| P6 | screens 色值与 tokens.json 一致（#0B4F9E 等），无第二套色板 | ✓ |
| P7 | 手输规则与 `spec/code-rules.json#code_faces.manual_entry` 一致（先选父对象再输序号，无 27 位裸串）；人读行分组 `2-2-4-4-6-2-3-3-1` 一致 | ✓ |
| P8 | 断网、拦截、无入口三条铁律在 SCOPE / MODULES / screens 三处口径一致 | ✓ |

## 四、遗留待决（不阻塞设计，阻塞开工/联调）

| # | 事项 | 阻塞什么 | 找谁 |
|---|---|---|---|
| W1 | S01/S02 设计稿末尾 4 条交互确认（单按钮登录 / 断网可进取景 / 固定 4 色块 / 中央扫码键） | S03 起的逐屏设计基线 | 项目方 |
| W2 | MODULES §6 三件事（12 模块粒度 / M09 打印独立批次 / M12 贯穿式） | B1 任务包签发 | 项目方 |
| W3 | BACKEND-REQ-01（取样授权） | S07 真机联调（不阻塞开发） | 项目方转达后端会话 |
| W4 | KM-118BT 最大打印宽度 102 vs 80mm 冲突、指令集未定 | M09 打印实现路线 | 真机实测 |
| W5 | S04「该车牌最近 3 条预报」数据源：`GET /api/recv/notices` 是否支持按车牌过滤 | S04 拦截页实现细节 | 后端会话核对 |
| W6 | 磅秤/天平能否蓝牙或 HID 直读 | S05/S07「必填项 0–1」能否压到 0 | 现场设备确认 |
