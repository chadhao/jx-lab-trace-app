# MIMO-TASK-S02-NAV · S02 现场首页 + 底部导航接通（登录闭环的最后一块）

> 派发：WorkBuddy → mimo ｜ 2026-10-21 ｜ 优先级：高（真机登录已闭环，缺 S02 与导航）
> 现状：LarkSSO 登录真机闭环 ✅（sysadmin 进 APP）；14 个 Screen Composable 已实现；`TabBar`/`BigButton`/`TopBar`/`InterceptPage` 组件就绪；**缺 S02 首页与导航宿主**（登录成功落 B1 占位页）。

## 设计来源（严格按稿）
- `design/screens/index.html` 的 **S02 现场首页**（3 变体：收货员/生产/管理层·销售）；
- `docs/SCOPE-APP.md` 的角色×屏幕矩阵（46-48 行）；
- 组件直接复用：`TabBar(items,selectedId,onSelect)`（**无扫码键**，D25）、`BigButton`、`TopBar`。

## 需求

1. **S02 HomeScreen**（`ui/home/HomeScreen.kt`）：
   - **身份栏**：头像（姓名首字）+ `me.name` + 角色文案（`me.roles`/`sys_roles` 中文映射，后端 kind=system 进「系统管理员」）；
   - **入口区（按角色变体）**：
     - receiver：**收货**（预报·过磅·打码 → S04）、**建袋打码**（→ S06）、**装车出场**（→ S12）、**追溯**（→ S13）；
     - production：**取样**（→ S07）、**建批**（→ S08）、**投料**（→ S09）、**作业段/成品打码**（→ S10/S11）、**追溯**；
     - management/sales：**追溯**（→ S13）、**报表**（→ S14，只读）；
     - 多角色 ⇒ 入口取并集；角色无关入口（追溯/我的）全角色可见；
   - **本期数据位**：「进行中作业/今日待办角标/最近作业」**留结构占位**（EmptyState 或「--」），数据接口后续批次（标注 TODO 注释，不造假数据）；
   - 入口点击 → 导航到对应 Screen。
2. **导航宿主**（MainActivity 侧）：
   - 登录成功（sid 存在）⇒ 显示 `HomeScreen` + `TabBar`（底部）；
   - TabBar 三项：**现场**（=HomeScreen）/ **追溯**（=TraceScreen）/ **我的**（=PrinterScreen，S15 我的·打印机）——全部角色一致（SCOPE 矩阵：三角色都含 S02/S13/S15）；
   - 入口屏为全屏推入（返回键回 Home），TabBar 仅在 Home 显示（**简化路由**：单 Activity + Compose 状态路由即可，不引 Navigation 库；mimo 定实现并回执注明）；
   - 会话失效（/api/me 401）⇒ 回 S01 登录。
3. **权限语义**（项目方已裁决）：矩阵是唯一真相——入口按 `me.allRoles` 对应 SCOPE 矩阵显示；后端 `RequirePerm` 403 ⇒ 走现成 `InterceptPage`（无权限拦截）。
4. **S03 扫码**为独立屏（取景），首页不放扫码键（D25）；各业务页内嵌唤起。

## 判据
- N-1 内网 CI 全绿（构建+全部单测+三门禁）+ 新 APK 产出；
- N-2 HomeScreen 三变体的入口集合与 SCOPE 矩阵一致（单测断言：给定 roles ⇒ 期望入口 id 集合）；
- N-3 TabBar 三项在全部角色下均为「现场/追溯/我的」；
- N-4 真机（项目方）：登录 → 首页四入口可见 → 点入各屏 → TabBar 切换正常。

## 纪律
显式路径提交、push gitea 触发内网 CI、回执写本文件末尾、拦断写 BLOCKED。**工程规则**：外部组件/契约先读文档（§0 全局规则）。
