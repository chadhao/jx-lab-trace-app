# APP 端功能边界（定稿 v1.1）

> v1.0 2026-10-19 定稿；**v1.1 2026-10-19 修订：检测线整体移出**（见文末变更记录）。
> 依据：对后端全部 **51 个权限点**逐项分类 + 用户裁决。
> 本文件是 APP 的**功能白名单**：不在此列的权限点，APP 不提供任何入口。

## 一、判据（三条全中才进 APP）

1. **地点**：动作发生在车间 / 磅房 / 装车区 / 现场取样点。
2. **时效**：等不起——车在等、料在等、人在等。
3. **设备**：当时手边没有电脑。

审批类**默认不进**（远程审批走 PC 或飞书待办），主数据**一律不进**。

## 二、APP 白名单：20 个权限点

| 模块 | 权限点 | APP 里做什么 | 所在屏 |
|---|---|---|---|
| 收货 | `recv.arrive.confirm` | 选已有预报 → 到货确认，生成车次与车码 | S04 |
| 收货 | `recv.weigh` | 录毛重/皮重/净重 | S05 |
| 收货 | `recv.return` | 退车登记 | S04 |
| 收货 | `recv.bag.gen` | 录袋数 → 批量生成袋码 | S06 |
| 收货 | `recv.label.print` | 连续打印袋码标签（蓝牙 KM-118BT） | S06 |
| 收货 | `recv.label.reprint` | 补打（须填原因） | S06 |
| 取样 | `sample.take` | 扫码取样（份样/大样/保留样） | S07 |
| 取样 | `sample.retain.in` | 留样入库（位置/期限） | S07 |
| 取样 | `sample.retain.lend` | 留样借出/归还登记 | S08 |
| 生产 | `prod.batch.create` | 建生产批（沿用上批配方与设备） | S09 |
| 生产 | `prod.feed.scan` | 投料扫码 | S10 |
| 生产 | `prod.feed.correct` | 投料记录更正（**限本班次内**，超时转 PC） | S10 |
| 生产 | `prod.op.log` | 作业段开始/结束打点 | S11 |
| 生产 | `prod.fg.gen` | 成品批/成品袋生成与打码 | S12 |
| 出货 | `ship.load.scan` | 装车逐袋扫码归集 | S13 |
| 出货 | `ship.out.register` | 出场登记 | S13 |
| 追溯 | `trace.forward` | 正向：车 → 成品 | S14 |
| 追溯 | `trace.backward` | 反向：成品 → 车 | S14 |
| 追溯 | `trace.batch.view` | 批次档案查看 | S14 |
| 报表 | `rpt.view` | 基础报表只读 | S15 |

**合计 20 / 51。**

## 三、PC 保留：31 个权限点

| 类别 | 权限点 | 备注 |
|---|---|---|
| 主数据（5） | `md.customer` `md.material` `md.test_item` `md.vehicle` `md.team` | APP 一律不可建、不可改（含新车牌） |
| 车次预报（2） | `recv.notice.create` `recv.notice.edit` | APP 只能**选用**已有预报 |
| 留样销毁（2） | `sample.retain.destroy.init` `.approve` | 事后纠错 + 审批链 |
| 返工（1） | `prod.rework` | 新批号 + 关联原批 |
| **检测线（11）** | `insp.task.view` `insp.scope.edit` `insp.result.entry` `insp.result.correct` `insp.file.upload` `insp.conclusion` `insp.disposition` `insp.concession.qc_sign` `insp.concession.dept_sign` `insp.urgent.release.init` `insp.urgent.release.approve` | **全部在实验室电脑完成**：录值、附件、任务、结论、处置、签署、紧急放行 |
| 出货撤销（2） | `ship.void.init` `.approve` | 事后纠错 + 审批链 |
| 报告（2） | `report.generate` `report.share.manage` | 对外产出 |
| 报表导出（1） | `rpt.export` | 桌面活 |
| 系统（5） | `sys.user.manage` `sys.perm.edit` `sys.audit.view` `sys.code.rule` `sys.param.edit` | 全部后台 |

## 四、被砍掉的动作，现场怎么走

| 现场会遇到 | APP 现在怎么表现 | 人该怎么办 |
|---|---|---|
| 车到了但**没预报** | 到货确认页拦截：「该车牌今日无预报」+ 列出该车牌最近 3 条预报 + **一键拨号**给综合运营部 | 电话报车号，PC 补预报，APP 下拉刷新后重试 |
| **新车牌**不在档案 | 同上拦截：「车辆档案不存在」+ 一键拨号 | PC 建档后再收 |
| 想知道**这个样品测出来多少** | 不显示录值界面，走 **S14 追溯**看批次档案里的检测结论 | 结论在 PC 出；APP 只读展示 |
| 现场**想拍照留证**（袋破损、异物） | APP 无上传入口 | 拍完回实验室随检测附件一起传 |
| 投料投错了且**已过本班次** | 更正入口置灰，注明「已超过本班次，请到 PC 端更正」 | 电话告知生产主管，PC 改 |
| 需要**紧急放行**（车等卸） | APP 无入口；「我的 → 帮助」写明「联系主管在 PC 审批」+ 一键拨号 | 电话主管 → PC 审批 → APP 侧状态自动变为可收货 |

## 五、三条设计铁律

1. **无入口即无入口。** 页面里不出现任何 PC 专属动作的按钮——灰掉也不行。灰按钮会变成「为什么点不了」的持续追问。
2. **越权一律给话术，不给白屏。** 后端 403 时展示「你的角色无此权限，请联系综合运营部」，并显示当前角色；不显示技术错误码。
3. **拦截必须给下一步。** 任何「不能做」的界面，必须同时给出：原因 + 谁负责 + 怎么联系。只说不行的界面，工人会绕过去。

## 六、终版页面清单（17 屏）

| 编号 | 页面 | 对应权限点 | 角色 |
|---|---|---|---|
| S01 | 登录 · 飞书授权 | — | 全部 |
| S02 | 现场首页（4 变体） | — | 全部 |
| S03 | 扫码（取景 / 确认卡 / 手输 / 异常） | — | 现场三岗 |
| S04 | 收货 · 到货确认（选预报 · 含退车） | arrive.confirm / return | 收货员 |
| S05 | 收货 · 过磅 | weigh | 收货员 |
| S06 | 收货 · 建袋打码（含补打） | bag.gen / label.print / label.reprint | 收货员 |
| S07 | 取样 · 取样与留样入库 | sample.take / retain.in | 质检员 |
| S08 | 取样 · 留样借还 | retain.lend | 质检员 |
| S09 | 生产 · 建批 | batch.create | 生产班组 |
| S10 | 生产 · 投料（含更正） | feed.scan / feed.correct | 生产班组 |
| S11 | 生产 · 作业段 | op.log | 生产班组 |
| S12 | 生产 · 成品袋打码 | fg.gen | 生产班组 |
| S13 | 出货 · 装车归集与出场 | load.scan / out.register | 收货员 · 销售 |
| S14 | 追溯（正/反向 · 批次档案，含检测结论只读） | trace.* | 全部 |
| S15 | 报表（只读） | rpt.view | 管理层 · 销售 |
| S16 | 我的 · 打印机 | — | 全部 |
| S17 | 全局状态族 | — | 全部 |

## 七、验收口径（可机检）

- APP 源码中出现以下任一 ⇒ **判失败**：`notice.create` / `md.*` / `void` / `destroy` / `concession` / `urgent` / `conclusion` / `rework` / `export` / **`insp.*` 全部**。
- 上述 17 屏之外新增页面 ⇒ 先改本文件再动代码。
- 每个页面必须有无权限、拦截、断网三态设计稿，缺一态不许进开发。

## 变更记录

| 版本 | 日期 | 变更 | 原因 |
|---|---|---|---|
| v1.0 | 2026-10-19 | 23 / 28 | 首版定稿 |
| v1.1 | 2026-10-19 | **移出检测线 3 项（task.view / result.entry / file.upload）⇒ 20 / 31；页面 18 → 17** | 检测全部在实验室电脑完成，数据由电脑上传，移动端不录检测数据 |
