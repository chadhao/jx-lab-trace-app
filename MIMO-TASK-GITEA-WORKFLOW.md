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
