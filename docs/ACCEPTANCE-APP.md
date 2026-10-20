# APP 端验收报告（骨架）· jx-lab-trace-app

> 状态：**骨架**（B5 / M13-1 交付）。判据索引已列全；录屏/截图占位待真机验收方填写。
> 验收口径：`docs/SCOPE-APP.md` §八 + 各模块判据（`docs/MODULES.md` §2）。

## 0. 门禁（机检 · 每次提交必过）

| 项 | 命令 | 状态 |
|---|---|---|
| tokens 一致性 | `python3 scripts/check_tokens.py` | ✅ OK（验收方本机复核 2026-10-20） |
| 裸色值 = 0 | `python3 scripts/check_colors.py` | ✅ OK |
| 白名单零命中 | `python3 scripts/check_scope.py` | ✅ OK（终扫 2026-10-20） |
| lint + 单测 + APK | `scripts/check_app.sh`（CI 内跑） | ✅ 168 单测 + assembleDebug（run 39353825386 success） |
| APK artifact | CI `app-debug-apk`（90 天） | ✅ 23,996,016 bytes |

## 1. B1（M01 工程骨架 + M02 设计系统）

| 判据 | 证据 | 状态 |
|---|---|---|
| J1 CI 绿 + 可装 APK + versionName=提交号 | Actions run（见 COLLAB §1） | ✅ 已验收 |
| J2 断网前置探测不进真实请求 | 单测 OfflineGateTest / PreflightOfflineTest | ✅ |
| J3 五类错误映射 | ApiErrorMappingTest | ✅ |
| J4 tokens 一致 + 零裸色值 + 触控 ≥56dp | check_tokens / check_colors / ComponentSizeTest | ✅ |

## 2. B2（M03 会话 + M04 扫码 + M12 基线）

| 判据 | 证据 | 状态 |
|---|---|---|
| M3-1 三失败分因文案 | LoginFailureTest | ✅ 已验收 |
| M3-3 403 不白屏（18 权限点桩） | SessionRetryTest | ✅ |
| M4-1 校验位 = spec sample_vectors | CodeParserTest | ✅ |
| M4-2 五类扫码失败不合并 | ScanFailureMappingTest | ✅ |
| M4-4 无无归属扫码 | 构造必传 confirmLabel+onConfirm | ✅ |

## 3. B3（M05 收货 + M06 取样 + M09 打印）

| 判据 | 证据 | 状态 |
|---|---|---|
| M5-1 无预报零写请求 + 可拨号 | NoNoticeInterceptTest | ✅ 已验收 |
| M5-2 建袋幂等 | BagIdempotencyTest | ✅ |
| M5-3 补打必填原因 | ReprintAndReturnTest | ✅ |
| M6-2 take 请求体形状 | SampleFormTest | ✅ |
| M9-1 标签 800×800 / QR ≥6dp | LabelRendererTest | ✅ |
| B3-b 蓝牙直打自检 | ⏳ 待真机（自检页已就绪） | ⏳ |

## 4. B4（M07 生产线）

| 判据 | 证据 | 状态 |
|---|---|---|
| M7-1 建批零输入（三默认） | ProdBatchCreateTest（6 条） | ✅ 已验收 |
| M7-2 投料进度本地即时 +1 | FeedProgressViewModelTest | ✅ |
| M7-3 班次判定用服务端时间 | FeedShiftAndCorrectTest（8 条） | ✅ |
| M7-4 更正请求体 v2 | FeedShiftAndCorrectTest | ✅ |
| M7-5 作业段结束幂等 | OperationIdempotencyTest（9 条） | ✅ |
| M7-6 复用 M09 打印队列 | grep TsplPrinter 单定义 | ✅ |
| M7-7 无 rework / insp.* | git grep 零命中 | ✅ |

## 5. B5（M10 出货 + M11 追溯报表 + M12 完整 + M13 验收）

| 判据 | 证据 | 状态 |
|---|---|---|
| M10-1 重复袋本地拦截（服务端计数仍 1） | ShipLoadTest | ☐ |
| M10-2 汇总袋数/净重本地即增 | ShipLoadTest | ☐ |
| M11-1 谱系四接口适配 | TraceAdapterTest | ☐ |
| M11-2 报表只读（无导出/写入口） | ReportReadonlyTest + grep `rpt/export`=0 | ☐ |
| M12 四类拦截齐全 + 409 文案表 | ErrorMessagesTest + ArrivalInterceptKind | ☐ |
| M13-1 check_app.sh 全绿 + check_scope 通过 | CI | ☐ |
| M13-2 全流程冒烟 ≥11 步链绿 | FullFlowSmokeTest | ☐ |
| M13-3 APK artifact 产出 | CI app-debug-apk | ☐ |

## 6. 真机验收（待验收方填写）

### 6.1 全流程录屏

| 步骤 | 录屏路径 | 日期 | 结论 |
|---|---|---|---|
| 登录（飞书授权 + 会话过期重授权） | _占位_ | | |
| 收货（到货 → 过磅 → 建袋打码） | _占位_ | | |
| 取样（生产班组） | _占位_ | | |
| 生产（建批 → 投料 → 作业段 → 成品打码） | _占位_ | | |
| 出货（装车归集 → 重复袋拦截 → 出场） | _占位_ | | |
| 追溯（正/反向时间线 + 批档案） | _占位_ | | |

### 6.2 四类拦截页截图

| 拦截类型 | 截图路径 | 三要素齐备（原因+负责人+拨号） | 结论 |
|---|---|---|---|
| 码无效 | _占位_ | ☐ | |
| 无权限 | _占位_ | ☐ | |
| 无预报 | _占位_ | ☐ | |
| 无车辆档案 | _占位_ | ☐ | |

### 6.3 弱网 / 断网

| 场景 | 录屏/截图 | 表单保留 | 恢复后一次成功 | 结论 |
|---|---|---|---|---|
| 断网提交（作业表单） | _占位_ | ☐ | ☐ | |
| 断网扫码 | _占位_ | — | ☐ | |

### 6.4 打印（B3-b 遗留）

| 项 | 证据 | 结论 |
|---|---|---|
| KM-118BT 蓝牙直打自检（100mm 标尺） | _占位_ | |
| 袋码标签实贴可扫 | _占位_ | |

## 7. 验收签字

| 角色 | 姓名 | 日期 | 结论 |
|---|---|---|---|
| 验收方（WorkBuddy/项目方） | | | |
| 实现方（mimo） | | B5 回执 MIMO-DONE | |

## 变更记录

| 版本 | 日期 | 变更 | 原因 |
|---|---|---|---|
| 骨架 v1 | 2026-10-20 | B1–B5 判据索引 + 录屏/截图占位 | B5 / M13-1 交付 |
