# 内网 Gitea 迁移指南（APP + 后端仓库）· 2026-10-20

> 面向：项目方 / 后端开发 ｜ 作者：WorkBuddy（APP 仓库会话）
> 结论：内网 CI 已上线并完成 APP 仓库迁移；后端仓库迁移按本文 §3 执行（约 10 分钟）。

## 1. 内网 CI 入口与账号

| 项 | 值 |
|---|---|
| Gitea 地址 | `http://192.168.10.50:3010`（仅内网） |
| 管理员账号 | `chadhao` |
| 初始密码 | `e82c464a1934d862`（★首次登录后请修改） |
| Runner | `srv-runner`（act_runner v5.1.1，宿主执行模式，daemon 常驻） |
| 构建环境 | JDK17 `/home/chadhao/ci/jdk17`、Android SDK `/home/chadhao/ci/android-sdk` |
| APK 产出 | `/srv/jx-lab-trace/ci/apk/app-debug-latest.apk` |
| 部署位置 | 二进制 `~/ci/bin/`、配置 `/srv/jx-lab-trace/ci/gitea-conf/app.ini`、日志 `/srv/jx-lab-trace/ci/*.log` |
| 进程管理 | 当前 nohup 常驻（与 jxlabtrace 同模式）；重启：`pkill -f 'gitea web'` 后重跑启动命令 |

## 2. APP 仓库（jx-lab-trace-app）—— 已迁移 ✅

- Gitea 仓库：`http://192.168.10.50:3010/chadhao/jx-lab-trace-app`（私有）
- 双 remote 现状：`gitea`（主，日常 push/CI）+ `origin`（GitHub，只读备份）
- 内网 workflow：`.gitea/workflows/android.yml`（全 `run:` 步骤，零官方 action 依赖）
- GitHub workflow 保留（双通道过渡期，网络好时仍会跑）
- gradle 已镜像化：wrapper→腾讯镜像、maven→阿里云（内网服务器外网受限的前提）

## 3. 后端仓库（jx-lab-trace）迁移步骤（由后端执行，约 10 分钟）

```bash
# ① 在 Gitea 建仓（浏览器：http://192.168.10.50:3010 → 右上 + → 新建仓库）
#    名称 jx-lab-trace，私有，不初始化
# ② 在后端开发机（仓库所在处）添加 remote 并推送：
cd /path/to/jx-lab-trace
git remote add gitea http://192.168.10.50:3010/chadhao/jx-lab-trace.git
git push gitea --all
git push gitea --tags
# ③（可选）后端 CI：仿照 APP 的 .gitea/workflows/ 写 go 构建 workflow
#    （runs-on: ubuntu-latest 宿主执行；GO 构建不需要 SDK，只有 JDK 已装——
#    Go 工具链需在 workflow 里安装或放 ~/ci/go；首版建议仍用现有
#    deploy-test-server.sh 本机编译+部署模式，CI 化后置）
# ④ 迁移验证：Gitea 页面能浏览代码/提交历史即完成
```

## 4. 本机系统级改动清单（为打通 GitHub 通路，均已披露）

| 文件 | 改动 | 回滚方法 |
|---|---|---|
| `C:\Windows\System32\drivers\etc\hosts` | 318-320 行：GitHub 指向 140.82.121.3（mimo 标记块） | 删除标记块 3 行 |
| `~/.ssh/config` | 移除 `Host github.com → ssh.github.com:443` 回落块 | 备份 `~/.ssh/config.bak-github443` |
| `~/.ssh/known_hosts` | 追加 `github.com` 主机键 | 删除追加行 |

## 5. 回退方案

- 内网 CI 出问题：APP 仓库 `git push origin main` 回 GitHub（workflow 仍保留，GitHub Actions 照常构建）——双通道天然互为回退。
- Gitea 数据备份：打包 `/srv/jx-lab-trace/ci/` 即全量（数据+配置+仓库）。

## 6. 后续排期（已入 COLLAB）

- 探针假阳性修复（/healthz 校验 version 特征）
- Applink 二期（拉起飞书 App 授权，依赖 BACKEND-REQ-03 的 exchange 端点上线）
- 后端 REQ-02 可选增强（B-req-2/3 建议随 REQ-03 同批）
