# MIMO-NEXT-BATCH-01 · 批次 B1（M01 工程骨架与 CI + M02 设计系统落地）

> 派发：WorkBuddy → mimo ｜ 日期：2026-10-19
> 交付仓库：`jx-lab-trace-app`（`main`，GitHub：`chadhao/jx-lab-trace-app`）
> 完成定义：本文件末尾「回执」段状态改为 **MIMO-DONE** + 全部判据证据在案 + CI 绿。

## 0. 动手前必读（按顺序）

1. `README.md` —— 项目定位与范围（18 权限点 / 16 屏 / 3 角色）
2. `docs/SCOPE-APP.md` —— **功能边界白名单**（不做的东西比做的更重要）
3. `docs/MODULES.md` —— 只读 **M01、M02** 两节与「全局机检判据」
4. `design/README.md` —— 设计原则与令牌纪律
5. `design/tokens.json` —— 唯一真相，你的 `Tokens.kt` 从这里生成
6. `design/screens/index.html` —— S01/S02 已出稿，**本批不做页面**，只看设计基调

**纪律**：设计文档与本任务包为只读。发现设计问题 ⇒ 在回执「设计疑问」段提出，**不擅自改**。

## 1. M01 工程骨架与 CI

### 1.1 工程要求

- 目录 `android/`，单模块 `:app`。
- 包名 `cn.hunanjiangxi.jxlabtrace`；`minSdk 26`，`compileSdk/targetSdk 35`；Kotlin 2.x + Compose BOM（Material 3）。
- `versionName` = 提交短号（CI 注入），`versionCode` = 递增整数。
- 应用名「江熙追溯」。

### 1.2 网络层（本批只要骨架，无业务请求）

- OkHttp + `CookieJar`：持久化并自动携带 `jx_sid`（EncryptedSharedPreferences 存储，进程重启不丢）。
- `ApiError` 封闭类五类：`Offline` / `Unauthorized` / `Forbidden` / `Server` / `Biz(code,message)` / `Parse`，全部带中文 `userMessage`。
- **提交前置探测**：任何写请求发出前先 `HEAD /healthz`（2s 超时）失败 ⇒ 直接返回 `Offline`，不进入真实请求。
- `NetworkMonitor`：基于 ConnectivityManager 的全局网络状态流（供 M12 顶部红条用，本批只暴露 StateFlow）。

### 1.3 日志

- 应用级环形缓冲 **500 条**（时间、级别、tag、消息），提供内存查询接口即可，「我的」页导出属后续批次。

### 1.4 CI（`.github/workflows/android.yml`）

- 触发：push 到 `main` + PR。
- 步骤：checkout → setup-java 17（temurin）→ `./gradlew assembleDebug testDebugUnitTest` → 上传 `app-debug.apk`（artifact，保留 90 天）→ 运行 `python scripts/check_tokens.py`。
- Runner 自带 Android SDK，**不要**额外下载 SDK 或装 Flutter/Node。

### 1.5 依赖白名单（新增任何白名单外依赖，须在回执说明理由）

Compose BOM · activity-compose · core-ktx · lifecycle-runtime · okhttp · kotlinx-serialization · datastore-preferences · security-crypto · junit · kotlinx-coroutines-test · compose ui-test-junit4。

## 2. M02 设计系统落地

### 2.1 令牌生成（单一真相）

- `scripts/gen_tokens.py`：读 `design/tokens.json` → 生成 `android/app/src/main/java/cn/hunanjiangxi/jxlabtrace/ui/theme/Tokens.kt`（颜色/字号/间距/圆角/尺寸，命名 snake→camel）。
- `scripts/check_tokens.py`：重新生成到临时文件并与仓内 `Tokens.kt` **逐字节比对**，不一致 ⇒ 退出码 1。CI 必跑。
- `Tokens.kt` 文件头必须注明「GENERATED — DO NOT EDIT」。

### 2.2 十二个基础组件（`ui/components/`，全部 Compose）

`DomainBlock`（色块入口，含角标）、`NowBar`（进行中条）、`StatusPill`、`FieldCard`、`BigButton`（高 60dp）、`NumPad`（大数字键盘，≥64dp 键位）、`EmptyState`、`InterceptPage`（原因+谁负责+拨号三要素）、`TopBar`、`TabBar`（平级 3/4 项，角色自适应，无扫码键）、`ScanHero`（追溯页首屏大扫码入口，高 ≥120dp）、`Skeleton`、`ToastHaptic`（Toast+振动反馈封装）。

### 2.3 图标

24 个业务矢量图标（收货/生产/取样/出货/追溯/异常等域色），`ui/icons/`，跟随令牌着色。

### 2.4 视觉基准

以 `design/screens/index.html` 中 S01/S02 的呈现为准（浅色主题、高对比、圆角 12–16dp、状态双编码）。

## 3. 明确不做（做了算越界）

任何业务页面（首页/登录/扫码/收货…）、导航图、扫码相机、蓝牙、标签渲染、翻译与深色主题。
组件允许有 `@Preview`，但不得组装成页面。

## 4. 验收判据（全部满足才算 DONE）

| # | 判据 | 证据要求 |
|---|---|---|
| J1 | CI 绿，Artifacts 有可安装 `app-debug.apk`，`versionName`=提交短号 | CI run 链接 + artifact 截图 |
| J2 | 断网（无网模拟）发起请求 ⇒ 返回 `Offline` 且日志可见前置探测拦截，**无 OkHttp 超时等待** | 单测 + 日志输出粘贴 |
| J3 | 401/403/500/Biz/Parse 五类均可解析为含 `userMessage` 的领域错误 | 5 个单测 |
| J4 | `grep -rn "#[0-9A-Fa-f]\{6\}" android/app/src` 命中 = 0（`Tokens.kt` 除外） | grep 输出粘贴 |
| J5 | `check_tokens.py` 在 CI 绿（生成物与 tokens.json 一致） | CI 截图 |
| J6 | 12 组件各有 `@Preview`（360/390/411 三档宽），无溢出 | Preview 截图拼图 |
| J7 | 所有组件最小触控 ≥56dp，`BigButton`=60dp、`NumPad` 键位 ≥64dp、`ScanHero` 高 ≥120dp | 尺寸断言单测 |

## 5. 提交纪律（沿用后端项目，违者打回）

- **只暂存显式路径**：禁止 `git add -A` / `git add .`；提交后 `git show --stat HEAD` 复核贴进回执。
- 分支 `main`；禁止 force push；禁止改 `design/`、`docs/` 下任何文件。
- 提交信息格式：`mimo: B1 M01 工程骨架与 CI —— 摘要`（一条一主题，M01/M02 分开提交）。
- **先提交、再 push、以 Actions 结果为准**：本地没有 Android SDK，无法编译自检，所以「push 后 CI 绿」是唯一事实——不要把「本地写完」当成「完成」。
- 门禁调管道必须 `set -o pipefail`。

## 6. 回执格式（本文件末尾追加，勿改上文）

```
## 回执 · BATCH-01
状态：MIMO-DONE / BLOCKED / PARTIAL
提交：<commit 短号 列表>
CI：<run 链接> · 结论：<绿/红>
判据：J1…J7 逐条 ✓/✗ + 证据
依赖变更：<无 / 白名单外清单+理由>
设计疑问：<无 / 列表>
遗留：<列表>
```
