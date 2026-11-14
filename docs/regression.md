# 本机回归验收

回归脚本会写入演示账号、短链接和访问数据，并临时创建 MySQL 触发器来模拟故障。请使用单独的 MySQL 库和单独的 Redis 实例。测试库名以 `_regression` 结尾。

## 配置测试环境

1. 将 [link.sql](../resources/database/link.sql) 导入空测试库，启动专用 Redis。脚本默认使用 MySQL `23307`、Redis `26380`，需要能调用 `mysql` 与 `redis-cli`。MySQL 回归用户为本地测试实例的 `root`，需要有建触发器权限。
2. 按[配置样例](../resources/sharding-local.example.yaml)生成单独的分片配置。为聚合服务设置 `SHORTLINK_SHARDING_FILE` 指向该文件、`SHORTLINK_DOMAIN=127.0.0.1:28003` 及 `SHORTLINK_REDIS_PORT=26380`。用 `--server.port=28003 --short-link.stats.retry-idle-ms=3000` 启动聚合服务。仅在回归环境缩短重试等待时间。
3. 设置网关的 `SHORTLINK_REDIS_PORT=26380` 与 `SHORTLINK_AGGREGATION_URL=http://127.0.0.1:28003`，用 `--server.port=28000` 启动网关。
4. 从仓库根目录运行。

```powershell
python scripts/verify-core.py --database linkstamp_regression
```

脚本依次验证注册和分组权限、并发短码、自定义与自动短码跳转、批量写入回滚、跨日 UV/IP、统计失败重试、毒消息隔离、回收站恢复和永久保留短码。参数说明见 `python scripts/verify-core.py --help`。

## 已执行验证

- 后端使用 JDK 17 和 Maven 打包，生成 `linkstamp-aggregation.jar`、`linkstamp-gateway.jar`。
- 前端使用 Node.js 24 和现有依赖执行 `npm.cmd run build`。构建完成，Vite 报告大于 500 kB 的打包分块提醒。
- 使用 MySQL 8.0.46 与 Redis 8.4.0，在隔离实例中验证短码争抢、分组权限、同批中一条回滚另一条成功、UTC+8 跨日访问、统计重试与重复处理、回收站恢复。格式错误的消息和连续处理失败五次的消息均进入死信 Stream。
- 按上一版 SQL 建立独立旧库，预置短链路由和访问明细，连续执行两次统计迁移，均保留了原有记录。
