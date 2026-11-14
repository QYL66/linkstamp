<!-- LinkStamp changes: project documentation and local setup. -->
# 链签 LinkStamp｜短链接管理与访问统计

链签用于把长网址生成可分享的短链接。登录后可以按分组管理链接、设置有效期、下载二维码、查看访问数据，也可以为单条短链接指定易读的短码。

![链签控制台及访问统计](docs/linkstamp-console.png)

[登录界面](docs/linkstamp-login.png)

## 使用流程

1. 注册并登录。系统会创建默认分组。
2. 点击「创建短链」，输入跳转网址和描述。自定义短码支持 3–8 位小写字母或数字，首字符为字母。留空时自动生成。短码在同一短链域名下唯一。
3. 点击列表里的短链接，浏览器会跳到原始网址。回到列表可查看访问次数、访客数和 IP 数。移入回收站的链接可以恢复。

![创建短链时预览自定义短码](docs/linkstamp-custom-code.png)

## 本机启动

本项目的默认运行配置用于本机学习和演示。前端、网关和聚合服务默认监听 `127.0.0.1`。

后端使用 Spring Boot 4、Spring Cloud Gateway 5 和 ShardingSphere 5.5。若已有 ShardingSphere 5.3 的本地配置，先备份 `.runtime/sharding.yaml`，再按新版模板迁移规则，并沿用已有的数据库连接信息和加密密钥。

以下示例使用 JDK 17、Maven、Node.js 24、MySQL 8.0 和 Redis。Vite 在 5173，网关在 8000，聚合服务在 8003。示例 MySQL 端口为 13307，Redis 为 16380。

1. 启动本机 MySQL 和 Redis。在 MySQL 新建空数据库 `linkstamp`，使用 UTF-8（utf8mb4）。创建一个有该数据库读写权限的项目用户，并在该数据库执行 [resources/database/link.sql](resources/database/link.sql)。本机首次访问时通过注册创建账号。以下导入命令从项目根目录执行，使用有建表权限的账号。

   ~~~powershell
   mysql.exe -h 127.0.0.1 -P 13307 -u root -p --default-character-set=utf8mb4 linkstamp -e "source resources/database/link.sql"
   ~~~
2. 在项目根目录用 PowerShell 生成本地数据库配置。输入项目数据库用户的密码。脚本把分片配置写到 .runtime/sharding.yaml 并生成数据加密密钥。换数据库连接时要保留原密钥，以便继续读取已有数据。

   ~~~powershell
   .\scripts\New-LocalShardingConfig.ps1 -DbPassword (Read-Host -AsSecureString 'MySQL password')
   ~~~

   默认用户名为 `linkstamp`。如使用其他实例，通过 `-DbUrl` 和 `-DbUser` 传入实际连接信息。需要自定义 Redis 时，在 IDEA 的两个运行配置中设置 `SHORTLINK_REDIS_HOST` 和 `SHORTLINK_REDIS_PORT`。如果 Redis 要求密码，再设置 `SPRING_DATA_REDIS_PASSWORD`。
3. 用 IDEA 打开根目录并导入 Maven 项目，选择 JDK 17。运行 AggregationServiceApplication，等待 8003 就绪，再运行 GatewayServiceApplication。两个运行配置的工作目录均设为项目根目录。网关使用 aggregation 配置，直连本机聚合服务。

   Windows 下项目路径含中文时，在两个运行配置的「缩短命令行」中选择「JAR 清单」。本项目的 JDK 17 在用 `@argfile` 读取含中文的类路径时可能报主类找不到。

4. 在 IDEA 的 PowerShell 终端执行前端命令，然后打开 [本地登录页](http://127.0.0.1:5173/login)。

   ~~~powershell
   cd .\console-vue
   npm.cmd ci
   npm.cmd run dev -- --host 127.0.0.1 --port 5173 --strictPort
   ~~~

如需修改短链域名，将聚合服务的 `SHORTLINK_DOMAIN` 和前端 `console-vue/.env.local` 的 `VITE_SHORTLINK_DOMAIN` 设为同一个主机和端口。启动时请使用能访问该主机的本地网络配置。

后端也可以在根目录使用 Maven 打包，然后在两个终端分别启动聚合服务和网关。前端可以在 `console-vue` 下用 `npm run build` 构建。

~~~powershell
mvn.cmd -pl aggregation,gateway -am clean package
java -jar .\aggregation\target\shortlink-aggregation.jar
java -jar .\gateway\target\shortlink-gateway.jar
~~~

## 请求如何流转

- **控制台**。Vite 将 /api 请求转发到网关。网关校验需要登录的请求，再把它交给聚合服务，后者负责查询和写入 MySQL、Redis。
- **访问短链**。访客直接请求聚合服务的短链路径。服务先查 Redis，缓存未命中时先通过路由表找到分组，再查分片后的短链表。服务返回 HTTP 302，同时将访问事件交给 Redis Stream，消费者异步更新统计。
- **短码唯一性**。创建时在同一事务中写入短链和路由记录，由路由表的唯一索引决定冲突请求的结果。自定义短码沿用跳转锁协调首次回填。回收站删除后仍保留路由占用。
- **账号与链接**。密码使用带随机盐的 PBKDF2 存储，Redis 会话只保存网关需要的用户标识和显示名。系统根据目标网址的域名建议描述，创建和编辑过程仅校验网址格式。
- **创建限流**。聚合服务用 Sentinel 控制单条短链创建速率，本机配置默认每秒 20 次，可通过 `SHORTLINK_CREATE_QPS` 调整。

## 运行数据

初始化 SQL 只建表。公开仓库的截图使用演示账号和 `example.com`，实际账号、运行日志和数据库文件留在本机。登录状态通过请求头传递，异常日志只记录请求路径，不拼接查询参数。注册会写入账号资料，访问统计会记录访客 IP、浏览器、设备类型和访问时间，并用访客 Cookie 统计 UV。地区查询默认关闭。显式设置 `SHORTLINK_AMAP_KEY` 后，聚合服务会将访客 IP 发送给高德 IP 定位服务。

## 实测结果

使用独立空库初始化，构建并启动聚合服务、网关和 Vite 后，完成以下验收。

| 场景 | 实测结果 |
| --- | --- |
| 账号与分组 | 注册、登录后出现默认分组。另一账号提交该分组 ID 时，创建被拒绝。 |
| 自动与自定义短码 | 两种短码都能返回 302；非法字符、长度和保留字被拒绝。 |
| 重名与并发 | 两个账号同时提交同一个短码，只有一个成功。核对 16 张短链分表和 16 张路由分表，两类记录各只有一条。 |
| 异步统计 | 访问后 PV 和 UV 写入成功，可从列表查询。 |
| 回收站 | 移入回收站后停止跳转，恢复后重新跳转，彻底删除后短码仍被保留。 |
| 框架升级 | 验证了旧账号、加密的手机号和邮箱、已有短链，以及批量创建和 Excel 导出。浏览器中确认了新版统计图与短码预览。 |
| 控制台界面 | 实测登录失败提示、创建与编辑短链、二维码、Excel 下载、访问统计、回收站恢复和退出。用 390 像素宽的视口验证短链工作台和账号设置。 |

## 开源许可

[Apache License 2.0](LICENSE) · [NOTICE](NOTICE)
