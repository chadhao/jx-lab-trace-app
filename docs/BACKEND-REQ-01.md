# 后端需求 01 · 取样授权调整（供转达后端会话）

> 提出方：APP 设计侧（jx-lab-trace-app）｜日期：2026-10-19
> **本文只描述需求，不包含任何后端代码改动。** 由项目方转达后端会话实施。

## 一、为什么要改

业务事实更正：**取样由生产班组完成，样品再交给质检检测**——不是质检取样。
APP 端因此要把「取样」入口放在生产班组名下。

但后端授权现状与业务事实不一致，APP 改成生产班组后，调用会直接 403：

| 权限点 | 现状（`spec/permission-points.json#seed` 第 107 行） | 期望 |
|---|---|---|
| `sample.take` | `qc: ALL` · `production: NONE` | `qc: READ` · `production: ALL` |

## 二、需要改什么（两处，缺一不可）

### 2.1 真相源：`spec/permission-points.json`

第 107 行 `seed.sample.take`：

```json
"sample.take": { "receiver": "NONE", "qc": "READ", "production": "ALL", "management": "NONE", "sales": "NONE", "sysadmin": "NONE" },
```

改动仅两个值：`qc` 由 `ALL` → `READ`；`production` 由 `NONE` → `ALL`。

### 2.2 现有数据库：`s_role_permission`

**这一处最容易漏。** `internal/store/migrate.go` 的注释写得很清楚：

> ④ 授权种子只**补齐缺失行**（`INSERT IGNORE`）—— 后台已改过的 level 不被覆盖

也就是说：**只改 spec 不改库，现有环境不会生效**（行已存在，`INSERT IGNORE` 直接跳过）。两条途径任选：

- **推荐**：走后台接口 `PUT /api/admin/permission-matrix` 原子保存这两个格子（有 diff 审计、有防锁死校验）。
- 或直接 SQL：
  ```sql
  UPDATE s_role_permission SET level='ALL'  WHERE point_code='sample.take' AND role_code='production';
  UPDATE s_role_permission SET level='READ' WHERE point_code='sample.take' AND role_code='qc';
  ```

> 注意：只走后台改矩阵、不改 spec，会造成「spec 与运行时两份真相」——下次重建库或换环境时又变回去。所以 2.1 与 2.2 必须同时做。

## 三、影响面（供后端评估）

| 对象 | 影响 |
|---|---|
| 质检员 `qc` | 失去「扫码取样」写权限；保留读取样品列表的能力（`READ`） |
| 生产班组 `production` | 获得「扫码取样」写权限；`sampled_by` 将记录为生产班组的人 |
| 审计 | 取样操作人从质检变为生产——这正是本次要的职责分离效果 |
| 其它权限点 | **无改动**。`sample.retain.in` / `sample.retain.lend` 仍为 `qc: ALL`，APP 侧不实现这两个动作，后端无需调整 |

## 四、验收方法（后端自测用）

| # | 动作 | 期望 |
|---|---|---|
| 1 | 以 `production` 身份 `POST /api/sample/take` | **200**，`sampled_by` 为该生产账号 |
| 2 | 以 `qc` 身份 `POST /api/sample/take` | **403**（写权限已收回） |
| 3 | 以 `qc` 身份 `GET /api/sample/samples` | **200**（读权限保留） |
| 4 | 重启服务后 `GET /api/admin/permission-matrix` | `sample.take` 行中 `production=ALL`、`qc=READ` |
| 5 | 全新空库跑一次迁移 | 种子落库即为新值（证明 2.1 生效） |

## 五、不在本需求范围内

- 不新增「交接/送样」表或接口（已定：一期不做交接确认，靠留样袋流转 + `sampled_by`）。
- 不调整 `sample.retain.*` 的角色授权。
- 不改任何 APP 侧代码（APP 只是把取样入口从 qc 移到 production）。

## 六、APP 侧等待动作

本需求上线前，APP 的「取样」入口会因 403 不可用。因此：

- APP 设计与开发**不等**此需求（M06 取样模块可先按新角色实现）。
- **真机联调必须等**此需求完成，否则 S07 只能测到 403 分支。
