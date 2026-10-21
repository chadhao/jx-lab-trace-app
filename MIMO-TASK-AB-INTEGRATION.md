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
