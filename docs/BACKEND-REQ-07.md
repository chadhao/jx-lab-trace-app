# 后端需求 07 · `/api/me` 增加 `permissions` 聚合字段（APP 权限矩阵驱动）

> 提出方：APP 侧 ｜ 2026-10-23 ｜ 优先级：高（APP 首页入口的权限判定依赖此字段）
> **背景（项目方裁决）**：前端权限判定必须按权限矩阵——用户首页入口的可见性 = 其全部角色（业务+系统）在矩阵中**聚合出的权限点**，不做任何角色名特判。

## 需求

`GET /api/me` 响应**增加一个字段** `permissions: string[]`：

```json
{
  "open_id": "ou_b490a605...",
  "name": "郝端",
  "roles": ["production"],
  "sys_roles": ["sysadmin"],
  "permissions": ["recv.arrive.confirm", "recv.weigh", "prod.feed.scan", "..."]
}
```

## 聚合口径（SQL 参考）

该用户全部角色的权限点**并集**（去重）：

```sql
SELECT DISTINCT rp.perm_code
  FROM s_user_role ur
  JOIN s_role_permission rp ON rp.role_code = ur.role_code
 WHERE ur.open_id = ?
```

- 注意：**不要**过滤 `s_role.status`（与 `RolesOf` 的口径差异需后端自裁——若按「启用角色才聚合」请在回执注明口径）；
- 权限点 `code` 与 `s_permission_point.code` 同源（51 点字典）。

## 兼容性

- 仅**新增字段**，`roles`/`sys_roles`/`open_id`/`name` 原样不动；
- Web 端同一 `/api/me`：多出的字段对 Web 无影响（前端忽略未知字段）。

## APP 侧消费方式（同步开发中）

- 首页入口可见性：`入口所需权限点 ∈ me.permissions`（映射表按 SCOPE-APP：收货=`recv.arrive.confirm`、建袋=`recv.bag.gen`、装车=`ship.load.scan`+`out.register`、取样=`sample.take`、建批=`prod.batch.create`、投料=`prod.feed.scan`、作业段=`prod.op.log`、成品打码=`prod.fg.gen`、追溯=`trace.forward|backward|batch.view` 任一、报表=`rpt.view`）；
- 删除 APP 现存的 sysadmin 特判（过渡代码，字段上线即切）。

## 验证

1. 部署后 `GET /api/me`（sysadmin 会话）应返回 `permissions` 含全部 51 点；
2. 某仅 production 角色用户返回其生产域权限点子集；
3. APP 首页入口随 `permissions` 变化（矩阵调整后重新登录即生效）。
