# MIMO-TASK-PRINTER-SDK · 打印机治本：接入 Android-BluetoothPrinter 驱动库

> 派发：WorkBuddy → mimo ｜ 2026-10-22 ｜ 优先级：高（S15 打印连接真机失败，SPP 自研连接两轮失败）
> **根因（已定位）**：快麦 KM-118BT 的蓝牙打印通道与自研 SPP RFCOMM 契约不匹配（Socket failure）。**治本：换开源驱动库**——`vinint/Android-BluetoothPrinter` 原生封装快麦驱动（`KuaiMaiBluetoothPrinterFactory`）。

## 需求

1. **加依赖**（android/app/build.gradle.kts）：
   ```kotlin
   implementation("io.vin.android:bluetoothprinterprotocol:1.0.2")
   implementation("io.vin.android:bluetoothprinter:1.0.18")
   ```
   **注意**：该库在 **jcenter**（已只读）——先试 `maven { url = uri("https://jitpack.io") }` 与阿里云镜像能否解析；都不行则**从 GitHub 下载 aar/源码入库**（vinint/Android-BluetoothPrinter 的 bluetoothprinter 模块），回执注明取用方式。
2. **PrinterScreen 改造**（按官方示例）：
   - 初始化：`BluetoothPrinterManager.getInstance().registerPrinter("KM-118BT", KuaiMaiBluetoothPrinterFactory("KM-118BT"), 1)`（KM-118BT 与库支持型号 KM-118B 同族，型号串按实际设备名前缀匹配）；
   - 连接：`BluetoothPrinterManager.getInstance().connect(printerName, printerAddr)`；
   - 状态：`printerStatus()`（STATUS_DISCONNECT 判断）；
   - 现有 SPP 自研连接（`AndroidBluetoothSpp`）**保留代码但默认不启用**（打印走新库；自研路径留作回退开关）；
   - UI 状态机沿用现有（connecting/connected/error 三态 + 友好错误文案）。
3. **诊断导出入口补缺**：「我的」屏（PrinterScreen 顶部或操作区）加「**导出诊断日志**」按钮——调用现有 LoginDiag 导出（含 crash-log.txt）——之前入口只在登录失败卡有，真机排障找不到。
4. **打印功能本批不动**：本批只做**连接打通**（连接成功状态显示 + 打印机状态读取）；标签绘制（TSPL drawText/drawQRCode）下一批。

## 判据
- P-1 内网 CI 全绿（含新依赖解析成功）+ 新 APK；
- P-2 「我的」屏有「导出诊断日志」入口；
- P-3 真机（项目方）：连接快麦 KM-118BT 显示已连接 + 打印机状态可读。

## 纪律
显式路径提交、push gitea、回执本文件末尾、拦断写 BLOCKED。**工程规则**：新库先读其 README/接口定义（io.vin.android 的 IBluetoothPrinterProtocol）再写代码。

---

## 回执（mimo · 2026-10-22）

**交付**：提交 `5239dca`（4 文件，+289/−36）——快麦 KM-118BT 官方驱动库接入（连接打通）+「我的」屏诊断导出入口 + 上轮崩溃留栈收尾（CrashLogWriterTest 一并入库）。

### 依赖取用（①）

- **阿里云 public 镜像实测可解析**，无需 GitHub 下载 aar 入库：
  - `io.vin.android:bluetoothprinterprotocol:1.0.2`（POM+AAR HTTP 200，AAR 7,247 B）
  - `io.vin.android:bluetoothprinter:1.0.18`（POM+AAR HTTP 200，AAR 166,263 B）
- jcenter 已死（预期）；jitpack 404；mavenCentral 404。`settings.gradle.kts` 已有阿里云 public 仓库（`maven.aliyun.com/repository/public`），直接加 `implementation` 即可，CI run52 解析成功（P-1）。
- 库 README/接口实读：`BluetoothPrinterManager` 在 **protocol 包**（`io.vin.android.bluetoothprinterprotocol`），`KuaiMaiBluetoothPrinterFactory` 在 `io.vin.android.bluetoothprinter.kuaimai`。关键源码口径：`registerPrinter` 按型号**前缀**匹配；`connect(name, addr)` 为**同步阻塞**（内部 socket.connect）；`printerStatus()` 返回 `IBluetoothPrinterProtocol.STATUS_*`（OK=0, DISCONNECT=32 等）。

### PrinterScreen 改造（②）

- 新增 `print/KmPrinterBridge.kt`：薄封装 `BluetoothPrinterManager`——幂等 `ensureRegistered()`（注册 KM-118BT + KuaiMai 工厂，type=1）、`connect/status/disconnect`，SDK 异常一律吞掉返回 false/DISCONNECT（不闪退）。
- `PrinterScreen` 默认路径改为 SDK：读已配对列表（复用 `AndroidBluetoothSpp.bondedDevices()` 仅作枚举）→ 记忆地址优先 / 名字前缀 KM-118BT 匹配 → `KmPrinterBridge.connect`（**Dispatchers.IO**，库 connect 阻塞）→ 成功 `onConnected`，失败友好错误。
- 自研 SPP（`AndroidBluetoothSpp`/`PrintSession`/TSPL）**代码保留、默认不启用**——`useLegacySpp` 开关（当前恒 false），回退路径 `connectViaLegacySpp` 完整保留。
- UI 状态机沿用 `PrinterStatusModel` 三态；本批自检按钮提示「标签打印下批接入」（打印绘制 drawText/drawQRCode 按任务包不动）。

### 诊断导出入口（③ / P-2）

- 「我的 · 打印机」屏底部新增「**导出诊断日志**」BigButton：`LoginDiag.exportTextWithCrash(AppLogger.snapshot(), crash-log.txt)` → 系统分享面板（text/plain）。与登录失败卡同一导出口径（含全局崩溃留栈）。

### 崩溃留栈收尾（上轮遗留）

- `CrashLogWriterTest.kt`（6 条）入库：formatCrash 纯函数 / append+readAll 追加往返 / install 捕获交还 handler / exportTextWithCrash 两段格式。修一处断言（`[Printer]` 前缀口径）。

### 判据

- **P-1 ✅** 本地 `testDebugUnitTest` 225 全绿 + `lintDebug` + `assembleDebug`；内网 Gitea **run52（head `5239dca`）status=success**；APK `/srv/jx-lab-trace/ci/apk/app-debug-latest.apk` = 36,719,473 B（mtime 21:43）。D8 对库旧字节码有 stack-map 警告（无害，不阻构建）。
- **P-2 ✅** 「我的」屏有「导出诊断日志」按钮（PrinterScreen 底部，Quiet 风格）。
- **P-3 ⏳ 真机（项目方）**：连接快麦 KM-118BT 显示已连接 + 状态可读——待真机验收；若 SDK 仍失败，导出诊断日志发回（含 crash-log）定位。

### 如实声明

- 库 `connect()` 内部回调时序：`ConnectCallback.onConnectSuccess` 在协议层同步路径中回调（源码实读），Manager 在返回前读 `connectState`——与 README 示例一致；真机若见连接成功但 status=DISCONNECT，以 `printerStatus()` 为准二次判定（Bridge 已包）。
- 打印绘制（drawText/drawQRCode/printAndFeed）按任务包本批不做，自检按钮暂以提示占位。
