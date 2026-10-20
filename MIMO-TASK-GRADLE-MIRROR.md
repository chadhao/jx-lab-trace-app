# MIMO-TASK-GRADLE-MIRROR · 构建依赖国内镜像化（内网 CI 前置）

> 派发：WorkBuddy → mimo ｜ 日期：2026-10-20 ｜ 优先级：高（内网 CI 切换的前置条件）
> 背景：CI 从 GitHub Actions 迁到内网 Gitea Actions（服务器 `192.168.10.50`）。该服务器外网环境：`services.gradle.org` ✗、`maven.google.com` ✗、`github.com` ✗；但 `mirrors.cloud.tencent.com` ✅、`maven.aliyun.com` ✅、`dl.google.com` ✅。当前构建依赖源在服务器上不可达，必须镜像化。

## 需求（3 项，全部最小改动）

1. **Gradle 发行版走腾讯镜像**：`android/gradle/wrapper/gradle-wrapper.properties`
   `distributionUrl=https\://services.gradle.org/distributions/gradle-8.10.2-bin.zip`
   → `distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-8.10.2-bin.zip`
   （版本号 8.10.2 不变，仅换 host；加行注释说明「内网 CI 服务器外网受限，走腾讯镜像」）。
2. **Maven 仓库加阿里云镜像且置于 google()/mavenCentral() 之前**：
   `android/settings.gradle.kts` 两处 `repositories` 块（pluginManagement 与 dependencyResolutionManagement），各加：
   ```kotlin
   maven("https://maven.aliyun.com/repository/google")
   maven("https://maven.aliyun.com/repository/public")
   ```
   位置在 `google()` 与 `mavenCentral()` **之前**（命中即返回，未命中自动回落官方源——本地开发不受影响）。加注释说明同上。
3. **`gradle/wrapper/gradle-wrapper.properties` 注释同步**，不改其它。

## 判据

- G-1 `grep -c "maven.aliyun.com" android/settings.gradle.kts` = 2；
- G-2 `distributionUrl` host = `mirrors.cloud.tencent.com` 且版本号 8.10.2 不变；
- G-3 CI（GitHub Actions，当前仍生效）全绿——证明镜像配置不破坏本地/CI 构建（GitHub runner 走官方源优先即可，阿里云镜像放前面只是多一个首选源，不影响）。
- G-4 全仓 grep 无 `maven.aliyun.com/repository/jcenter`（jcenter 已废弃，不许出现）。

## 纪律

显式路径提交（`mimo: gradle 镜像化`）、push 后 token 轮询 CI 到 success、回执写本文件末尾。若内容过滤拦断写 BLOCKED。
