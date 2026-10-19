# MIMO-NEXT-BATCH-02 · 批次 B2（M03 会话与权限 + M04 扫码内核 + M12 基线）

> 派发：WorkBuddy → mimo ｜ 日期：2026-10-20
> 前置：B1 已验收（CI success，`Tokens.kt`/组件/网络层就绪）。
> 设计依据：`design/screens/index.html` 的 **S01（登录）、S03（扫码）** 两节——本批的 UI 按它做，含全部状态。
> 完成定义：末尾回执 `MIMO-DONE` + J 判据逐条证据 + CI success。

## 0. 动手前必读

1. `COLLAB.md`（铁律：显式路径提交、禁改 design/docs、测试红不许跳过）
2. `MIMO-NEXT-BATCH-01.md` 的回执——B1 交付了什么（网络层/组件清单）
3. `docs/SCOPE-APP.md` —— 18 权限点白名单（本批涉及 `insp.*` 之外的新边界：**S03 手输不得实现 27 位裸串输入**）
4. `design/screens/index.html#s01`、`#s03` —— 本批 UI 的事实
5. 后端事实：登录 `/api/auth/feishu/start`（GET，302 到飞书）· 会话 Cookie 名 **`jx_sid`**（HttpOnly，12h 滑动）· 当前用户 `GET /api/me` · 码解析 `GET /api/recv/scan?code=…`（不存在三分：400 码非法 / 404 对象不存在 / 409 业务拒绝）

## 1. M03 会话与权限（S01）

### 做什么
- **WebView 登录流**：`LoginWebViewActivity` 加载 `/api/auth/feishu/start`；`CookieManager.getInstance()` 读取 `jx_sid` 写入 B1 的 `SidStore`（新增 `FileSidStore`：EncryptedSharedPreferences 持久化，替换测试用的 InMemory）。
- 登录成功判定：回调 URL 回到站内（含 `/api/me` 200）即成功；取 `GET /api/me` 得 `{name, roles[], permissions[]}` → `MeRepository`（DataStore 缓存）。
- **S01 四态**照设计稿：初始 / 授权中（5s 超时给「手动打开」）/ 失败卡（三类分因）/ 成功过渡（1.2s 自动进占位主页）。
  - 失败三类：①网络不可达（`Offline`）；②授权被拒/回调缺失；③`/api/me` 200 但 `permissions` 为空 ⇒ **未分配角色**（文案「您的账号尚未分配角色，请联系综合运营部」，**不出现重试按钮**）。
- **会话过期原位重授权**：所有请求遇 401 ⇒ 发 `SessionExpired` 事件 ⇒ 全屏 WebView 重授权 ⇒ 成功后重放原请求。本批实现为可复用挂起函数 `withSessionRetry { … }`。

### 验收判据（M03）
- M3-1 三类失败文案不同；③类无重试按钮（纯函数 `loginFailureOf(...)` 单测）。
- M3-2 `FileSidStore` 读写往返单测（不依赖 Android 框架的加密抽象可先接口化：`SidStore` 接口 + Robolectric 后补，本批 InMemory + File 两实现，File 的加密封装用接口注入以便单测）。
- M3-3 `withSessionRetry`：首次 401 → 重授权成功 → 重放成功（MockWebServer 单测：401 后 200）。
- M3-4 403 → `ApiError.Forbidden` → 话术「你的角色无此权限，请联系综合运营部」（复用 B1 错误模型，单测断言 userMessage）。

## 2. M04 扫码内核（S03）

### 做什么
- **CodeParser（纯 Kotlin，零 Android 依赖）**：解析 27 位码（版本 2 位/类型 1 位/日期 6 位/链嵌套段/校验 1 位），人读行分组 `2-2-4-4-6-2-3-3-1` 与裸串互转；校验算法与 `spec/code-rules.json` 一致。
  - **单测**：把 spec 的 `sample_vectors` 全部向量硬编码进测试逐条断言（解析+校验+人读行双向转换）。
- **ScanViewModel**：状态机 `Idle → Scanning → Confirm(info) → Submitting → Done/Failure(reason)`；`Failure(reason)` 五类：`NotOurs`(400) / `UnknownCode`(404) / `Rejected(code,msg)`(409) / `NoPermission`(403) / `Voided`。
- **相机层**：CameraX Preview + ML Kit `barcode-scanning`（Code128/QR）；识别成功 ⇒ 震动+蜂鸣+绿边+尾 6 位大字回显（S03 ①）。
- **解析走后端**：识别成功后 `GET /api/recv/scan?code=…`（走 executeWrite 同款前置探测？——**不**：解析是读操作，不走前置探测，直接 GET；HTTP 状态映射到五类 Failure）。
- **确认卡**（S03 ②）：对象类型 + 人读码 + 四字段 + 主按钮文案随宿主业务变（构造参数 `confirmLabel`）。
- **手输 sheet**（S03 ④）：父对象选择 + 序号输入 + 校验位本地计算回显全码；**不得提供 27 位裸串输入框**。
- **HID 通道**：监听 KeyEvent 流（扫码枪=键盘），缓冲到 Enter；长度≠27 或校验失败 ⇒ 红边+原因，**不静默丢弃**。

### 依赖白名单新增（本批允许）
`androidx.camera:camera-camera2 / camera-lifecycle / camera-view` · `com.google.mlkit:barcode-scanning`。其余沿用 B1 白名单；再新增需回执说明。

### 验收判据（M04）
- M4-1 CodeParser 单测：`sample_vectors` 逐条通过；非法校验位全部拒绝；人读行⇄裸串互转无损。
- M4-2 失败映射单测：MockWebServer 桩 `/api/recv/scan` 返回 400/404/409/403 各一 ⇒ `Failure(reason)` 五类中对应类正确。
- M4-3 HID 拼接器单测：模拟按键流（含分片/超时清空/非数字字符过滤）。
- M4-4 全仓 grep：不存在 27 位裸串输入框；不存在无归属扫码入口（ScanViewModel 必须由宿主业务构造传入 `confirmLabel` 与 `onConfirm`）。
- M4-5 相机权限拒绝 ⇒ 取景层显示「去设置」而非白屏（逻辑单测+Preview）。

## 3. M12 状态族与弱网（基线）

- `GlobalOfflineBar`：订阅 M01 `NetworkMonitor` StateFlow，红条「网络中断 · 作业提交已暂停」+ 重试按钮。
- `ErrorMessages`：`ApiError → 中文话术` 映射函数（Offline/Unauthorized/Forbidden/Server/Biz/Parse），**单测覆盖五类 + 三类 409 业务码**。
- 四类拦截页中本批落 **2 类实体**：`码无效`（NotOurs/UnknownCode）与 `无权限`（Forbidden，含当前角色显示）；`无预报`、`无车辆档案` 随 B3。
- 提交前探测已在 B1（M01 preflight）——本批补单测：`executeWrite` 在 NetworkMonitor=false 时**不发任何请求**。

## 4. 明确不做

导航图（MainActivity 占位即可）、业务页面（收货/生产等）、标签打印、离线队列、深色主题、`insp.*` 任何调用。

## 5. 提交与回执纪律

与 BATCH-01 相同（显式路径、`mimo: B2 …` 前缀、push 后轮询 Actions API、`MIMO-DONE` 回执附证据）。**若被内容过滤拦断：不要重试读大日志——在回执写 BLOCKED 与卡点位置，验收方会出精确修复单。**

CI 门禁本起新增：**裸色值扫描并入 CI**（`scripts/check_colors.py`：`grep -rn "#[0-9A-Fa-f]\{6\}" android/app/src --include=*.kt | grep -v Tokens.kt` 非空即失败）——由验收方先行写好并合入，mimo 只需保证绿。

---

## 回执 · BATCH-02

状态：**MIMO-DONE**

提交：168ff2e（M03 登录会话与权限）· 010dfb3（M04 扫码内核）· 3c7612f（M12 状态族基线）· bb21b04（首轮 CI 编译修复 ×6）· f7415cb（测试误导入修复）· ca9a82b（校验位下标 + 409 断言）· d692ceb（CodeParser 段切分 cursor）· 1c67a61（check_colors.py 语法修复）

CI：https://github.com/chadhao/jx-lab-trace-app/actions/runs/39297820205 · 结论 **success**（head 1c67a61：assembleDebug + 73 条单测全绿 + check_tokens + check_colors 均过）

### 判据

- **M3-1** ✓ `LoginFailureTest` 6 条绿：网络/授权被拒/未分配角色三类文案与出路互不相同；③ NoRole `canRetry=false`（界面无重试按钮，只有「我知道了」）；me 401 归入授权被拒类。
- **M3-2** ✓ `FileSidStoreTest` 5 条绿：写读往返、跨实例（进程重启语义）、落盘字节确经加密（非明文）、null/空串删除文件、文件缺失读 null。加密封装 `SidCipher` 接口注入 ⇒ 纯 JVM 单测，无 Android 框架依赖。
- **M3-3** ✓ `SessionRetryTest`：首次 401 → 重授权成功 → 重放 200（恰好 2 个请求、重授权触发 1 次、返回 Success）；重授权失败原样返回 Unauthorized 不重放；非 401（500）不触发重授权。
- **M3-4** ✓ `SessionRetryTest`：403 → `ApiError.Forbidden`，`userMessage` 含「你的角色无此权限，请联系综合运营部」，且 `ErrorMessages.of` 同源一致；403 不触发会话重授权。
- **M4-1** ✓ `CodeParserTest` 绿：spec `sample_vectors` 6 向量（A–F）校验字符/加权和余数逐条一致；解析逐条通过；人读行⇄裸串双向无损（含空格分隔容错）；非法校验位 6 向量全部拒绝；分组 `2-2-4-4-6-2-3-3-1` 与 spec 一致；手输组装（父 A+袋序7）产出与 spec B 向量逐字节一致。
- **M4-2** ✓ `ScanFailureMappingTest` 10 条绿（MockWebServer 桩 `GET /api/recv/scan`）：400→`NotOurs`、404→`UnknownCode`、409→`Rejected`（code=ALREADY_FED 透传）、403→`NoPermission`、409 含「作废」→`Voided`、200+状态作废→`Voided`、200 正常→`Found`（四字段）、五类展示文案互不相同、解析路径只发 GET 无前置探测、5xx→`Server` 归网络层。
- **M4-3** ✓ `HidCodeAssemblerTest` 8 条绿：分片拼接（3 片→Enter 成功）、超时清空（>500ms 旧缓冲丢弃）、超时空缓冲拒绝且原因非空、非字母表字符过滤+小写归一（嘈杂按键流）、长度≠27 说明位数、校验失败提示重扫、空提交拒绝、clear 生效。
- **M4-4** ✓ 全仓 grep 证据：①`git grep -E "BasicTextField|EditText" -- android/app/src` = **0 命中**（无 27 位裸串输入框；手输 sheet 仅 3 位序号 NumPad）；②`git grep "ScanViewModel("` 与 `"ScanScreen("` 全仓各仅 1 处命中（均为定义本身），无宿主/路由引用 ⇒ 无无归属扫码入口；`ScanViewModel` 构造必传 `confirmLabel` + `onConfirm`（无默认值，scan/ScanViewModel.kt:36-40）。
- **M4-5** ✓ `CameraGateTest` 2 条绿：`cameraGateOf(false)=Denied`（渲染 `CameraDeniedPanel`「去设置」）、`=true` 取景；面板有 360/390/411 三档 Preview（`CameraDeniedPreview`）。
- **M12** ✓ `ErrorMessagesTest` 4 条绿（6 类 ApiError 话术互异 + Forbidden 规定原文 + **三类 409 业务码** ALREADY_FED/ALREADY_VOID/NO_RETURN_DECISION 专属话术互异 + 未知码透传/空 message 兜底）；`OfflineGateTest` 4 条绿（`online={false}` 时 `executeWrite`/`executeRead` 返回 Offline 且 **零请求到达**（连 /healthz 都不发）+ 在线对照组）；`GlobalOfflineBar`（订阅 NetworkMonitor StateFlow，红条「网络中断 · 作业提交已暂停」+ 重试）与两类拦截实体 `CodeInvalidIntercept`（码无效，含返回重扫出路）/ `ForbiddenIntercept`（无权限，含当前角色显示）组件 + 三档 Preview。

### 依赖变更

任务包 §2 白名单内新增：`androidx.camera:camera-camera2` / `camera-lifecycle` / `camera-view`（1.3.4）、`com.google.mlkit:barcode-scanning`（17.3.0）。无其他新增。

### 设计疑问

1. 综合运营部电话仍为设计稿占位 `0730-xxxxxxx`（`OPERATIONS_PHONE` 常量），定稿后全局替换即可。
2. 后端 `GET /api/me` 当前返回 `open_id/name/roles/sys_roles`，**无 `permissions[]`**；「未分配角色」按 `roles∪sysRoles` 皆空判定（文案不变），`Me.permissions` 字段已预留。
3. 后端 409 错误体仅 `{"error": text}` 无机器码；`Rejected.code` 取自 body 的 `code` 字段（可空），有码走 ErrorMessages 专属话术、无码透传后端中文（S03 ③「HTTP 409 · ALREADY_FED」样式在有码时完整呈现）。
4. S01 设计稿写「Cookie 存 EncryptedSharedPreferences」；按 M3-2「加密封装接口注入」实现为 `FileSidStore + SidCipher`，生产密钥走 Android Keystore AES-256-GCM（等价强度，且可 JVM 单测）。B1 的 `EncryptedSidStore` 保留未删。

### 遗留

- 真机验证项（弱光/污损识别率 ≥18/20、登录与重授权真机录屏、断网提交录屏）留待 M13/B3 真机阶段；本批完成逻辑、单测与 Preview 层。
- `scripts/check_colors.py`（验收方代写）存在换行损坏（第 12 行字符串字面量断裂导致 SyntaxError），已由 mimo 修复（`scripts/` 属 mimo 责任域），本地与 CI 均验证通过。
- `BuildConfig.BASE_URL` 默认 `http://10.0.0.12`（设计稿内网示例），真机联调以环境变量 `JX_BASE_URL` 覆盖；manifest 暂开 `usesCleartextTraffic`（内网 http 需要），若生产为 https 可收紧。
- S01 成功页只显示角色行（后端无部门字段，未杜撰「综合运营部」）。
