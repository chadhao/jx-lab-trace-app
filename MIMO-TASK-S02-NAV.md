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

## 回执（mimo · 2026-10-21）

### 交付
- **`ui/home/HomeScreen.kt`（新增）**：身份栏（姓名首字圆形头像 + `me.name` + 角色中文 `homeRoleLabel`——业务角色复用 S01 `roleLabel` 口径，`sysadmin` ⇒ 系统管理员，多角色「·」分隔）；入口区纯函数 `homeEntryIds(me.allRoles)` 按角色变体出集合（receiver→收货/建袋打码/装车出场/追溯；production→取样/建批/投料/作业段/成品打码/追溯；management|sales→追溯/报表；多角色并集；未知/空角色仅剩角色无关的追溯），入口卡复用 `DomainBlock`（域色取 tokens，无手写 hex），点击经 `onEntry(id)` 交宿主导航；**数据位占位**：进行中作业/今日待办/最近作业 = `FieldCard` 值「--」+ TODO 注释，入口待办角标 `badgeCount=null` + TODO（不造假数据）。
- **`MainActivity.kt`（导航宿主改造）**：`shouldRouteToLogin` 登录路由原样保留；sid 有 ⇒ `HomeScreen` + `TabBar` 三 Tab（**现场**=HomeScreen / **追溯**=TraceScreen / **我的**=PrinterScreen，`HOME_TABS` 常量与角色无关）；**简化路由**：单 Activity + Compose 状态（`pushStack`），未引 Navigation 库；入口屏全屏推入、返回键逐级回 Home（非现场 Tab 时返回键回现场）；**TabBar 在入口屏推入期间隐藏**。入口接线：收货 S04→S05→S06 全链（到货确认车次落 `home_flow` 偏好）、装车 S12、取样 S07、建批 S08（创建后直推投料）、投料/作业段/成品打码 S09/S10/S11（进行中批解析门：加载中 / 暂无批→去建批 / 失败→重试，无批不造假）、报表 S14；打印桥 `printOne`（裸码直渲 `LabelRenderer` → 蓝牙 TSPL，地址取 S15 `printer_memory/address`，未连接给话术）；**`/api/me` 经原位重授权后仍 401 ⇒ `expireSession()` 清 sid + 过期 Cookie 回 S01**（与登录屏「我知道了」清理口径同源）。
- **`HomeNavTest.kt`（新增，8 测试）**：见判据。

### 判据
- **N-1 ✅** 内网 CI **run30 success**（head `64c122f`）：构建 + 全部单测 + 三门禁 + lint + APK 发布全绿。本地同套件先证：`check_colors/check_scope/check_tokens` OK，`testDebugUnitTest` **194/194** 通过，`assembleDebug` 成功。
- **N-2 ✅** `HomeNavTest`：给定 roles ⇒ 期望入口 id 集合 —— receiver / production / management / sales 四变体 + 多角色并集（receiver+production、sales+receiver）+ 空角色、qc、sysadmin 仅追溯回退，逐条断言。
- **N-3 ✅** `HOME_TABS` 恒为 `home/trace/me` = 现场/追溯/我的（三项、顺序、文案与 roles 解耦；四角色遍历断言 + 追溯入口恒在）。
- **N-4 ⏳** 真机验收（项目方）：登录 → 首页入口可见 → 点入各屏 → TabBar 切换正常。

### 事实与风险留痕（简化与延后，按裁剪口径）
1. **路由口径**：「TabBar 仅 Home 显示」实现为 = Tab 宿主层（现场/追溯/我的三 Tab）显示、入口屏推入期间隐藏；入口屏返回键回现场 Tab。此口径下 N-4「TabBar 切换正常」在任意 Tab 可达。
2. **扫码延后（本轮裁剪：本任务不含扫码，未读 scan/ 任何文件）**：追溯/投料/装车的 `onStartScan` 与 S07「扫码定位对象」= Toast「扫码功能随后续任务接通」+ TODO；S07 未定位时「确认取样」保持禁用（无假数据）。S03 仍为独立屏任务。
3. **打印桥 v1**：人读行走裸串（非 2-2-4-… 分组形）、对象类型按码位推断（D/E→FG、B→BAG），S15 连接打印机后即可连打；后续可升级为分组人读行。
4. **S06 直入**：车次上下文取收货链确认时落盘的 `lastTruckId`；无上下文 ⇒ 结构空态引导「去收货确认」。
5. **批次门**：S09/S10/S11 进入时解析 `status=进行中` 的首个批（多批取首）；S08 创建成功后直推投料并直传 batchId。
6. **401 回 S01 观察点**：首页 `/api/me` refresh 的重授权失败分支（首 401 被原位重授权吸收，不误踢）；业务屏 401 仍走原位重授权。
7. 遗留接通项：扫码挂接（S03 独立任务）、S07 目标定位、待办角标/进行中/最近作业数据（后续数据批次）。

### CI 回执
- push：`93235bb..64c122f → gitea`（显式路径 3 文件，无 `git add -A`）；Gitea Actions **run30 = success**（head `64c122f`）。
- 提交：`64c122f mimo: S02-NAV 现场首页与导航宿主 —— 身份栏/角色入口变体/TabBar 三 Tab 路由 + 入口集合与 Tab 单测`（`MainActivity.kt` / `ui/home/HomeScreen.kt` / `HomeNavTest.kt`）。
- 无 BLOCKED；本地证据：三门禁 OK + 194/194 单测 + assembleDebug 成功。

---

## 追加任务（2026-10-21 14:30）· 系统栏/刘海避让（真机实锤：状态栏压身份栏、导航条压 TabBar）

**根因**：targetSdk=35 强制 edge-to-edge（内容延伸到系统栏后），而全仓无任何 WindowInsets 处理（grep 实证 0 处）。

**修复（统一在宿主级处理，一次生效全部屏）**：
1. `MainActivity.onCreate`：`enableEdgeToEdge()`（androidx.activity）；
2. **宿主渲染容器根布局**（含 HomeScreen 与 pushStack 推入屏的统一容器）：`Modifier.windowInsetsPadding(WindowInsets.safeDrawing)`——一次性避开顶部状态栏+刘海（displayCutout）与底部手势导航条；
3. **可选优化（做了更好，不做不阻塞）**：TabBar 的背景延伸到底部导航条后面（TabBar 自身加 `windowInsetsPadding(WindowInsets.navigationBars)` 仅推内容、背景铺满）——mimo 视实现便利取舍，回执注明。

**判据**：内网 CI 全绿 + 新 APK；真机（项目方）：状态栏/刘海不遮挡身份栏、底部导航条不压 TabBar、推入屏同样正常。
