# 后端需求 06 · APP 业务 API 总清单（16 屏 / 18 现场动作）

> 提出方：APP 侧 ｜ 2026-10-21 ｜ 优先级：**P0 路线图**（APP 后续功能的关键路径）
> 现状：后端已有 auth//api/me//api/admin//api/md//api/limits/resolve；**16 屏对应的业务 API 全部缺位**。APP 前端 14 屏 UI 已就绪，等本清单的 API 逐一接入。

## 清单（按 SCOPE-APP 的 18 个现场权限点组织，建议后端按批次交付）

### 批次 A · S02 首页聚合（最高优先——首页三个数据位当前占位）
| API | 方法/路径（建议） | 说明 |
|---|---|---|
| 首页聚合 | `GET /api/app/home` | 按登录人角色返回：进行中作业（各域进行中的单据摘要）/ 今日待办计数（待收车数、待打码数、待装车数）/ 最近作业列表（本人最近 N 条） |

### 批次 B · 收货域（receiver：S04-S06）
| 动作（权限点） | API（建议） | 说明 |
|---|---|---|
| 车次预报列表 | `GET /api/app/recv/notices` | 今日/近期预报（到货确认的选择源） |
| 到货确认（recv.arrive.confirm） | `POST /api/app/recv/arrive-confirm` | 车次 → 到货确认 |
| 退车（recv.return） | `POST /api/app/recv/return` | 含原因 |
| 过磅（recv.weigh） | `POST /api/app/recv/weigh` | 毛重/皮重（皮重取历史） |
| 建袋打码（recv.bag.gen） | `POST /api/app/recv/bag-gen` | 袋数/序号自动分配 |
| 补打（label.reprint） | `POST /api/app/label/reprint` | 按袋号重打 |

### 批次 C · 生产域（production：S07-S11）
| 动作 | API（建议） | 说明 |
|---|---|---|
| 取样（sample.take） | `POST /api/app/prod/sample-take` | 默认 500g/取样人=会话 |
| 建批（prod.batch.create） | `POST /api/app/prod/batch-create` | 沿用上批配方/设备，批号自动 |
| 投料（prod.feed.scan） | `POST /api/app/prod/feed` | 扫码即投，重量取袋记录 |
| 投料更正（prod.feed.correct） | `POST /api/app/prod/feed-correct` | |
| 作业段（prod.op.log） | `POST /api/app/prod/op-start` / `op-end` | 两点式，时长自动 |
| 成品打码（prod.fg.gen） | `POST /api/app/prod/fg-gen` | 扫成品码自动建袋 |

### 批次 D · 出货/追溯/报表（S12-S14）
| 动作 | API（建议） | 说明 |
|---|---|---|
| 装车归集（ship.load.scan） | `POST /api/app/ship/load-scan` | 逐袋扫码归集 |
| 出场登记（out.register） | `POST /api/app/ship/out-register` | |
| 追溯（trace.*） | `GET /api/app/trace?code=&dir=forward\|backward` | 批次档案含检测结论只读 |
| 报表（rpt.view） | `GET /api/app/report/...` | 只读 |

## 通用约定
- 鉴权：全部走 `jx_sid` 会话（Cookie），后端 `RequirePerm(权限点)` 拦截——与现有 51 权限点一一对应；
- 错误结构：沿用现有 `{"error":"..."}`；无预报/无车辆档案/无权限三类拦截需要**结构化原因 + 责任人**字段（APP 拦截页「原因+谁负责+怎么联系」）；
- 序号自动分配（袋序/批序/车序）由后端负责（避免并发跳号）。

## 优先级建议（待项目方/后端确认）
**批次 A + B 先行**（首页+收货域——现场最先用到的域），C/D 随后。细化契约（字段级）随每个批次开工时出。
