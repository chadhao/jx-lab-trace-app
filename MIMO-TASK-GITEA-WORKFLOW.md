# MIMO-TASK-GITEA-WORKFLOW · 内网 Gitea Actions 工作流迁移

> 派发：WorkBuddy → mimo ｜ 日期：2026-10-20 ｜ 优先级：高（CI 切换的核心任务）
> 背景：CI 从 GitHub Actions 迁至**内网 Gitea**（`http://192.168.10.50:3010`，仓库 `chadhao/jx-lab-trace-app` 已建好并完成首次推送；act_runner `srv-runner` 已注册，**labels: `ubuntu-latest`（宿主执行模式，非容器）**）。

## 服务器环境事实（runner 宿主 = 192.168.10.50，全部已就绪）

| 项 | 路径/值 |
|---|---|
| JDK 17 | `/home/chadhao/ci/jdk17`（Temurin 17.0.2） |
| Android SDK | `/home/chadhao/ci/android-sdk`（cmdline-tools/latest + platform-tools + platforms;android-34 + build-tools;34.0.0） |
| 门禁脚本 | 仓库内 `scripts/check_tokens.py` `check_colors.py` `check_scope.py`（python3 可用） |
| APK 产出目录 | `/srv/jx-lab-trace/ci/apk/`（已存在；发布用 Gitea Release 亦可） |
| runner labels | `ubuntu-latest`（宿主执行，**非容器**——workflow 不能用 `container:`） |
| clone 认证 | Gitea Actions 自动注入 `secrets.GITHUB_TOKEN`（等于内部 token），可用其 clone 本仓库 |

## 需求

1. **新建 `.gitea/workflows/android.yml`**（注意目录是 `.gitea/`，不是 `.github/`），逻辑与现有 `.github/workflows/android.yml` 等价：
   - `runs-on: ubuntu-latest`；**禁止 `container:`**（宿主执行模式）；
   - `env`: `JAVA_HOME=/home/chadhao/ci/jdk17`、`ANDROID_HOME=/home/chadhao/ci/android-sdk`；
   - Checkout：**不用 actions/checkout**（内网拉不到）——用 run 步骤：
     `git clone --depth 1 --branch "$GITEA_REF_NAME" "$GITEA_SERVER_URL/$GITEA_REPOSITORY.git" repo`（认证用 `http header` 或把 token 嵌 URL：`http://x-access-token:${{ secrets.GITHUB_TOKEN }}@192.168.10.50:3010/...`——mimo 可按 act_runner 宿主模式实测选一种），然后 `git checkout "$GITEA_SHA"`；
   - Build：`export PATH=$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH` 后 `./gradlew --no-daemon assembleDebug testDebugUnitTest lintDebug`；
   - Gates：`python3 scripts/check_tokens.py && python3 scripts/check_colors.py && python3 scripts/check_scope.py`；
   - Publish：把 `android/app/build/outputs/apk/debug/app-debug.apk` 复制到 `/srv/jx-lab-trace/ci/apk/app-debug-latest.apk`，并 `echo` 出内网下载 URL `http://192.168.10.50:5502/ci/...` **不需要**——直接 echo 服务器路径与 Gitea 页面地址即可；
   - Gitea 上下文变量参考：`$GITEA_SERVER_URL` `$GITEA_REPOSITORY` `$GITEA_REF_NAME` `$GITEA_SHA`（act_runner 宿主模式以环境变量注入；若实测变量名不同，以 runner 日志为准调整并在回执注明）。
2. **保留** `.github/workflows/android.yml` 不删（GitHub 侧继续跑，双通道过渡期）。
3. README 或注释注明：内网 CI 入口 `http://192.168.10.50:3010`。

## 判据

- W-1 push 后 Gitea Actions 自动触发（Actions 页签出现 run）；
- W-2 首跑全绿（含 166 单测 + 三门禁 + lint）；
- W-3 `/srv/jx-lab-trace/ci/apk/app-debug-latest.apk` 产出且 size > 10MB；
- W-4 `.github/workflows/android.yml` 未被改动（GitHub 双通道保留）。

## 纪律

显式路径提交、push 到 **gitea remote**（`git push gitea main`）触发内网 CI；GitHub 同步推送可延后（网络好时再推）。回执写本文件末尾。若 runner 报错，日志在服务器 `/srv/jx-lab-trace/ci/runner.log`（可 SSH 查看，SSH 权限已由项目方授权）。

---

## MIMO-DONE（2026-10-20 · mimo）

**交付**：新建 `.gitea/workflows/android.yml`（提交链 `6aaf3fa` → `5b6de3b` → `c79942f`）——`runs-on: ubuntu-latest`（宿主 labels 匹配）、**无 `container:`**、**零 `actions/*`**、全 `run:` 步骤；job env `JAVA_HOME=/home/chadhao/ci/jdk17`、`ANDROID_HOME=/home/chadhao/ci/android-sdk`；步骤=git clone（token 嵌 URL）+ checkout SHA → SDK 预检 → `./gradlew --no-daemon assembleDebug testDebugUnitTest lintDebug` → 三门禁 → 复制 APK 到 `/srv/jx-lab-trace/ci/apk/app-debug-latest.apk` 并 echo 服务器路径/Gitea 页面地址；文件头注释注明内网 CI 入口 `http://192.168.10.50:3010`。`.github/workflows/android.yml` 未动。

**判据证据**：

- **W-1 ✅** `git push gitea main` 后 Gitea Actions 自动触发（run 系列 3→6 均由 push 产生；run6=本工作流，Actions 页 `…/actions/runs/6`）。
- **W-2 ✅** **run6（head `c79942f`）status=success**：clone、SDK 预检、构建+166 单测+lint、三门禁、APK 发布全部通过。**非首跑绿——红跑根因与修复留痕**：run3/4（首跑类）败于服务器 SDK 缺 `platforms;android-35`（工程 compileSdk=35，任务包环境表只列 android-34）且 licenses 未接受 ⇒ 增加「SDK 预检」步骤（`sdkmanager --licenses` + 补装 android-35/build-tools;34.0.0，走 dl.google.com）；run5 败于 cmdline-tools 丢失执行位 ⇒ 预检步补 `chmod +x`；run6 全绿。若服务器补装好 android-35 与许可，预检步可整段删除。
- **W-3 ✅** SSH 实测 `/srv/jx-lab-trace/ci/apk/app-debug-latest.apk` = **36,420,556 bytes（34.7MB > 10MB）**，mtime 20:41（run6 产出）。
- **W-4 ✅** `git diff 740acdd..HEAD -- .github/` 为空（`.github/` 零改动，双通道保留）。

**实测备注（任务包第 27 条）**：act_runner v5.1.1 实际注入的是 **GITHUB_\* 兼容变量**（`GITHUB_SERVER_URL=http://127.0.0.1:3010` 等，`GITEA_*` 未出现）——工作流里 `GITEA_* :- GITHUB_*` 回退链按预期落到 GITHUB_*，`secrets.GITHUB_TOKEN` 正常供 clone。runner 日志除 `/srv/jx-lab-trace/ci/runner.log` 外，逐步日志存 Gitea 库 `data/actions_log/…/*.log.zst`（zstd 解码可读，红跑定位用）。

设计疑问：无。

---

## MIMO-DONE（2026-10-20 · mimo · GITEA-WORKFLOW）

交付：`.gitea/workflows/android.yml`（新建，59 行）——`runs-on: ubuntu-latest`、无 `container:`、无 `actions/*`、全 `run:` 步骤；job env `JAVA_HOME=/home/chadhao/ci/jdk17`、`ANDROID_HOME=/home/chadhao/ci/android-sdk`；Checkout 用 `git clone --depth 1` + `x-access-token:${{ secrets.GITHUB_TOKEN }}` 嵌 URL + `git checkout $SHA`；Build `./gradlew --no-daemon assembleDebug testDebugUnitTest lintDebug`；Gates 三脚本；Publish `cp → /srv/jx-lab-trace/ci/apk/app-debug-latest.apk` 并 echo 服务器路径与 Gitea 页地址；文件头注释注明内网 CI 入口 `http://192.168.10.50:3010`。`.github/workflows/android.yml` 未动。

判据证据：

- **W-1 ✅** `git push gitea main` 后 Gitea Actions 自动触发（run 1–6 共 6 次 run 记录，Actions 页签可见；run3 即首跑触发）。
- **W-2 ✅** 终跑 **run6（head `c79942f`）status=success**——含单测（166，本地同套全绿）、三门禁、lint 全部通过。首跑（run3）失败两轮修复实录：① 服务器 SDK 缺 `platforms;android-35`（环境表仅列 android-34）且 licenses 未接受 ⇒ 增加「SDK 预检」步（`yes | sdkmanager --licenses` + 装 android-35/build-tools;34.0.0，dl.google.com 可达）；② cmdline-tools 丢失执行位（run5 `Permission denied` exit126）⇒ 预检步内 `chmod +x` 修复。runner 日志取自服务器 `/srv/jx-lab-trace/ci/runner.log` 与 `gitea-data/data/actions_log/**/N.log.zst`（SSH 解压读取）。
- **W-3 ✅** `/srv/jx-lab-trace/ci/apk/app-debug-latest.apk` = **36,420,556 bytes（≈35MB >10MB）**，20:41 产出（run6 Publish 步，`ls -l` 实证）。
- **W-4 ✅** `git diff 740acdd..HEAD -- .github/` 为空——`.github/workflows/android.yml` 零改动，GitHub 双通道保留。

环境事实修正与实测注明：

1. 上下文变量实测：act_runner v5.1.1 注入的是 **GITHUB_\\*** 兼容集（`GITHUB_SERVER_URL=http://127.0.0.1:3010`、`GITHUB_REPOSITORY`、`GITHUB_REF_NAME`、`GITHUB_SHA`），`GITEA_*` 未注入——工作流用 `GITEA_*:-GITHUB_*` 兜底链，回执以 GITHUB_* 为准。
2. 任务包环境表「platforms;android-34 + build-tools;34.0.0」与工程 `compileSdk = 35` 不符、且 SDK licenses 未接受——已由工作流自愈（若服务器侧预装 android-35 + 许可，预检步可保留为幂等兜底）。
3. `.gitea/workflows/android.yml` 与 `.github/workflows/android.yml` 并存时，Gitea 只执行 `.gitea/`（run3/6 日志逐步名实证），GitHub Actions 只读 `.github/`——双通道互不串扰。
4. gitea remote 推送凭据：Gitea `DISABLE_SSH=true`，走 HTTP + 本机凭据管理器（`chadhao@http://192.168.10.50:3010`）——`git push gitea main` 可直接用。

设计疑问：无。（注：`git push gitea` 首次在无凭据环境下会要求 HTTP 认证，凭据已存于本机凭据管理器。）
