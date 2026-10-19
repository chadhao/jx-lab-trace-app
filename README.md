# 江熙追溯 · jx-lab-trace-app

湖南江熙新材料科技 · 岳阳城陵矶磨粉项目 —— **实验检测数据追踪系统的安卓手持端**。

后端为 [`chadhao/jx-lab-trace`](https://github.com/chadhao/jx-lab-trace)（Go + Echo + MySQL），本仓库**只做客户端**，不改后端代码。

## 一句话定位

车间现场工人戴着手套、站在投料口，用手机扫一下袋子上的码，三秒内把事情记对——数据进库，谱系不断。

## 范围（一期）

| 角色 | 手机端做什么 |
|---|---|
| 收货员 `receiver` | 车次预报、建袋贴码、过磅录入、出场登记 |
| 生产班组 `production` | 建批、投料扫码、作业段、成品袋入账 |
| 质检员 `qc` | 扫码取样、留样、检验值录入、出结论 |
| 管理层 `management` / 销售 `sales` | 只读：看板、追溯谱系、报告（不含任何后台配置） |

**不做**：主数据维护、权限矩阵、系统管理——这些留在 PC 端。

## 关键约束

- **登录**：统一飞书登录，账号即操作人；一人一机，权限由后台角色决定。APP 内 WebView 完成 OAuth，复用 HttpOnly 会话 Cookie，**零后端改动**。
- **断网即报错**：不做离线写入、不做本地队列。提交前探测联通性；断网时全屏拦截、**表单内容不丢**，恢复后原样重试（不重扫）。
- **扫码**：摄像头为主（CameraX + ML Kit），扫码枪 HID 输入兜底，手输走「先选父对象再输序号」（与 `spec/code-rules.json` 一致）。
- **打印**：个人手机通过蓝牙连接车间公用标签机，标签规格 **100×100 mm**。
- **构建**：全 GitHub Actions，本地不装 Android 工具链。

## 目录

```
design/            设计侧（唯一真相）
  tokens.json      设计令牌：颜色/字号/间距/组件尺寸
  concepts/        方案比选原型（A/B/C）
android/           安卓工程（Kotlin + Jetpack Compose）
docs/              决策记录、后端需求清单
```

## 状态

`v0.1` 设计比选中 —— 等待方案定档后进入逐屏规格与开发。

## 技术栈

Kotlin · Jetpack Compose (Material 3) · CameraX + ML Kit · OkHttp · DataStore
