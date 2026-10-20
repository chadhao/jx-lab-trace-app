# 江熙追溯 · jx-lab-trace-app

湖南江熙新材料科技 · 岳阳城陵矶磨粉项目 —— **实验检测数据追踪系统的安卓手持端**。

后端为 [`chadhao/jx-lab-trace`](https://github.com/chadhao/jx-lab-trace)（Go + Echo + MySQL），本仓库**只做客户端**，不改后端代码。

## 一句话定位

车间现场工人戴着手套、站在磅房和投料口，用手机扫一下码，三秒内把事情记对——数据进库，谱系不断。

## 范围（一期 · v1.2 定稿）

后端 51 个权限点，APP 只做 **18 个现场动作**，其余 33 项留 PC（见 [`docs/SCOPE-APP.md`](docs/SCOPE-APP.md)）。

| APP 使用者 | 手机端做什么 |
|---|---|
| 收货员 `receiver` | 到货确认（选预报）、过磅、建袋打码、装车归集、出场登记、退车 |
| 生产班组 `production` | **取样**、建批、投料（含班次内更正）、作业段、成品袋打码 |
| 管理层 `management` / 销售 `sales` | 只读：追溯谱系、批次档案、报表 |

**质检员不装 APP**：检测、留样入库、留样借还全部在化验室电脑完成。

**不做**：主数据维护、车次预报、检测全流程、留样管理、撤销/销毁/审批类、报告、导出、系统管理。

## 关键约束

- **登录**：统一飞书登录，账号即操作人；一人一机，权限由后台角色决定。APP 内 WebView 完成 OAuth，复用 HttpOnly 会话 Cookie。
- **断网即报错**：不做离线写入、不做本地队列。提交前探测联通性；断网时拦截在提交动作、**表单内容不丢**，恢复后原样重试。
- **扫码**：摄像头为主（CameraX + ML Kit），扫码枪 HID 兜底，手输走「先选父对象再输序号」（与 `spec/code-rules.json` 一致）；码解析走 `GET /api/recv/scan`。
- **打印**：个人手机蓝牙连接车间公用标签机（快麦 KM-118BT，203dpi），标签 **100×100 mm**，APP 内渲染 800×800 位图直发，系统打印兜底（见 [`docs/PRINTER-KM118BT.md`](docs/PRINTER-KM118BT.md)）。
- **构建**：全 GitHub Actions，本地不装 Android 工具链。
- **前置后端需求**：`sample.take` 授权需调整（取样归生产班组），见 [`docs/BACKEND-REQ-01.md`](docs/BACKEND-REQ-01.md)。

## 目录

```
design/                  设计侧
  tokens.json            设计令牌（唯一真相，Compose 主题由此生成）
  screens/index.html     逐屏规格与页面清单（S01–S16）
  concepts/              方案比选历史稿（已被方案 A 取代，仅回溯）
android/                 安卓工程（Kotlin + Jetpack Compose，随 B1 建立）
docs/                    决策与需求
  SCOPE-APP.md           功能边界（18/33 权限点白名单）★ 先读这个
  MODULES.md             开发模块划分（12 模块 / 5 批次）
  DECISIONS.md           决策记录 D01–D19
  PRINTER-KM118BT.md     打印机能力核查
  BACKEND-REQ-01.md      后端需求：取样授权调整（已转达）
  PRINTER-SELFTEST.md    打印机自测指引（宽度/指令集先行验证）
  MIMO-NEXT-BATCH-01.md  任务包 B1（已发 mimo）
  DESIGN-REVIEW-01.md    设计全量复核报告
```

## 状态

**B1–B5 全部开发完成并通过验收**（2026-10-20）：168 单测全绿、CI 门禁全过、13 步全流程冒烟链贯通。待真机验收（见 `docs/ACCEPTANCE-APP.md`）。

## 技术栈

Kotlin · Jetpack Compose (Material 3) · CameraX + ML Kit · OkHttp · DataStore
