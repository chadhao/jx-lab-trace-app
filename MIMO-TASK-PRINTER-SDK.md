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
