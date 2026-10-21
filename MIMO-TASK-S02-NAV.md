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

## 追加任务回执（mimo · 2026-10-21）· 系统栏/刘海避让

### 交付（`MainActivity.kt` 单文件，39+/13-）
1. `MainActivity.onCreate`：`enableEdgeToEdge()`（androidx.activity）在 `setContent` 之前调用；
2. **宿主渲染容器根布局统一避让**（一次生效 HomeScreen 与全部推入屏）：内容区 `Box` 挂 `windowInsetsPadding(contentInsets)`——
   - TabBar 可见（Tab 宿主层）：`WindowInsets.safeDrawing.only(Top + Horizontal)`（顶部状态栏/刘海 + 水平；底部交给 TabBar 容器，避免双重垫高）；
   - 入口屏推入（无 TabBar）：全量 `WindowInsets.safeDrawing`（含底部导航条）；
3. **TabBar 背景延伸（可选优化，已做）**：TabBar 外包一层 `Box`——背景 `Surface.card` 铺满并延伸到底部导航条后面，`windowInsetsPadding(WindowInsets.navigationBars)` 仅推起内容；不动 `TabBar.kt` 组件本体，色值取 tokens。

### 判据
- **CI ✅** 内网 Gitea Actions **run34 = success**（head `b30777c`）：构建 + 194 单测 + 三门禁 + lint + **新 APK 发布**（workflow 每次 run 覆盖 `app-debug-latest.apk`）。
- **本地先证**：check_colors/check_scope OK；`testDebugUnitTest` 全绿 + `assembleDebug` 成功。
- **真机 ⏳**（项目方）：状态栏/刘海不遮挡身份栏、底部导航条不压 TabBar、推入屏同样正常。

### 事实与风险留痕
- 首编译失败一次：`WindowInsets.only` 为扩展函数需显式 import（`androidx.compose.foundation.layout.only`），已修；红不改判据不跳测。
- run32/33 为前序回执 push 的触发尾迹（cancelled，非本任务）；本任务以 run34 为准。
- 避让只在宿主层做，业务屏零改动；后续扫码全屏取景接入时同样落在该容器内自动生效。

### CI 回执
- push：`82edea0..b30777c → gitea`（显式路径 1 文件）；run34 success；提交 `b30777c mimo: S02-NAV 追加 系统栏/刘海避让 …`。

---

## 追加任务（2026-10-21 14:58）· workflow 自动发布 Release（免手动）

**需求**：`.gitea/workflows/android.yml` 构建成功后自动把 APK 发布到 Gitea Release（tag `apk-latest`，删旧重建，避免同名资产并存）：
1. 删旧：`GET /api/v1/repos/{owner}/{repo}/releases/tags/apk-latest` → `DELETE /releases/{id}`（tag 保留，GET 404 则跳过删除）；
2. 重建：`POST /releases`（tag_name=apk-latest，body 含构建 commit 短号）；
3. 上传：`POST /releases/{id}/assets?name=jx-lab-trace-debug.apk`（attachment=@ 服务器 APK 产物）；
4. 认证：优先 `secrets.GITHUB_TOKEN`（act_runner 注入）；权限不足（403/404）则回执注明并改用 runner 上配置的专用 token 方案（写 BLOCKED 等项目方）；
5. **失败容错**：发布步骤失败不阻塞 CI 绿红判定？——**不**，发布失败应标红（产物发布是交付的一部分）；只在「删旧 404」时视为正常首跑。
**判据**：本任务 push 触发的这轮 CI 自身就含发布步骤——run 绿且 Release apk-latest 更新为本轮产物（size 变化可查）。

## 追加任务回执（mimo · 2026-10-21）· Release 自动发布 + GitHub 停用 + HOME 骨架

### A. workflow 自动发布 Release（14:58 追加段，commit `913d160`）
- **交付**：`.gitea/workflows/android.yml` 构建后新增 `Release（apk-latest 删旧重建 + 上传 APK · Gitea API）` 步骤——GET by tag →（200 则 DELETE，404 视为首跑跳过）→ POST 重建（body 含 `commit 短号 · run 号`）→ multipart 上传 `jx-lab-trace-debug.apk`；认证用 `secrets.GITHUB_TOKEN`（`token` 头）；删旧非 404 的任何失败（含 401/403）与建/传非 201 一律 `exit 1` 标红（发布失败不放行）。
- **判据 ✅**：本任务 push（913d160）触发的 run37 被后到 push 顶掉为 `cancelled`（run36/37 两连取消 = 并发顶替链，非本步骤所致）；**run38 = 首个含发布步骤的完整绿 run**（body `commit 99762b0 · run 38`，资产 id 7→8 于 15:14 重建）；**run39 再绿并再次删旧重建**（body `commit 5170f67 · run 39`，资产 **id=9**，size **36,555,574 → 36,571,959** 变化可查，created 15:26:17）。Release `apk-latest` 现为本轮产物 ✅。
- **留痕**：token 权限足，未触发 BLOCKED；release 行 `created_at` 保留 10-20 旧值（Gitea 删建复用行为），以 body / 资产 id / size / 创建时间三项为准；「删旧 404 首跑」分支本轮未触发（既有 release 存在，走删旧路径）。

### B. ① GitHub Actions 停用（`5170f67`）
- `.github/workflows/android.yml` 的 `on:` 由 `push(main) + pull_request` 改为 **`workflow_dispatch` 仅手动**；文件保留（GitHub 备份通道），永不自动触发；`permissions/jobs/steps` 未动。
- 留痕：W-4 纪律「.github/workflows/android.yml byte-untouched」被本指令**显式取代**（回执存证）；变更随 main（gitea 权威）入库，GitHub 远端下次同步生效（GitHub push 按惯例延后，当前无自动触发源）。

### C. ② HOME 骨架 GET /api/app/home（`5170f67`）
- **交付**：`ui/home/HomeScreen.kt` 内新增提案模型（`HomeOverview{ongoing, todo_recv, todo_bag, todo_load, recent}`，字段语义 = `docs/BACKEND-REQ-06.md` 批次 A；字段级契约未定，`ignoreUnknownKeys` + 缺省值容忍，兼容裸对象与 `data` 包裹两形）+ `HomeRepository.overview()`（走 `executeRead` 统一读通道）+ `HomeOverviewState` 三态；`OverviewSection` 渲染：**Loading→「--」占位 / Unavailable（失败·404·解析错）→ EmptyState 占位不报错 / Ready→真实聚合值（无数据显「无」，不造假）**；`MainActivity` 宿主装载：首载与「回到现场 Tab」刷新（`LaunchedEffect(pushStack.isEmpty())`），`withSessionRetry` 包裹，任何失败不踢会话；今日待办角标仍 `badgeCount=null` + TODO（随接口就绪接计数）。
- **判据 ✅**：run39 = **success**（head `5170f67`）：构建 + 全部单测 + 三门禁 + lint + 新 APK + Release 发布；本地先证三门禁 OK + `testDebugUnitTest` 全绿 + `assembleDebug` 成功。
- **真机 ⏳**（项目方）：后端未实现期间首页应见数据区空态占位、入口功能不受影响。

### CI 回执（合并）
- push：`913d160`（Release 步骤）、`5170f67`（①+②）→ gitea（显式路径）；runs：37 cancelled（被 99762b0 顶替）→ **38 success（99762b0）** → **39 success（5170f67）**；Release `apk-latest` = run39 产物（asset id=9，size 36,571,959）。
- 无 BLOCKED；本地证据：三门禁 OK + 单测全绿 + assembleDebug 成功。
