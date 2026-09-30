# 本机验证栈

远端容器栈（`deploy/docker-compose.yml`，部署在 192.168.0.183）一旦不可达
（换网段、VPN 抢了默认路由……），接口回归与浏览器检查就全都没法跑。
这个目录用**同一份 SQL** 在本机起一套等价的 MySQL + Redis，端口也保持一致：

| 服务 | 地址 | 账号 |
|---|---|---|
| MySQL | `127.0.0.1:3307` | `root` / `<口令见 deploy/_secrets.sh>`（库 `ruoyi-vue-pro`）|
| Redis | `127.0.0.1:6380` | 密码 `<口令见 deploy/_secrets.sh>` |

```bash
cd deploy/local-stack
docker compose up -d
docker compose logs -f mysql     # 首次初始化会依次执行 ../sql/01~36
```

## 两个踩过的坑（容器引擎不稳时）

1. **`docker ps` 挂住 / 端口"开着"但后端已经死了**
   OrbStack 的 VM 出问题时，会留下**半开的端口转发**：`lsof` 显示 6380/3307 在 LISTEN、
   连 TCP 也能连上，但后面的容器其实已经没了 —— 表现为 MySQL 不回握手包、Redis 不回 PING。
   判断方法（比 `docker ps` 靠谱，后者本身可能挂住）：

   ```bash
   # MySQL 连上后应当立刻收到 greeting；收不到就是后端死了
   python3 -c "import socket;s=socket.socket();s.settimeout(5);s.connect(('127.0.0.1',3307));print(len(s.recv(80)))"
   # Redis 应当回 +PONG
   python3 -c "import socket;s=socket.socket();s.settimeout(5);s.connect(('127.0.0.1',6380));s.sendall(b'*2\\r\\n\$4\\r\\nAUTH\\r\\n\$10\\r\\n<口令见 deploy/_secrets.sh>\\r\\n*1\\r\\n\$4\\r\\nPING\\r\\n');print(s.recv(60))"
   ```

   救火顺序：`orbctl restart -a` → 还不行就 `orbctl stop -a && orbctl start -a` → 仍不行就**别在这上面耗**
   （本次两轮都没救回来，只能等引擎自己恢复）。

2. **容器里的 Redis 起不来时，用本机 redis 顶上**
   只有 Redis 不可用时（MySQL 还活着），不必等容器：

   ```bash
   redis-server --port 6381 --requirepass '<口令见 deploy/_secrets.sh>' --appendonly no --save '' &
   # 后端启动参数里换成 6381
   java -jar ... --spring.data.redis.host=127.0.0.1 --spring.data.redis.port=6381
   ```

   停掉：`redis-cli -p 6381 -a '<口令见 deploy/_secrets.sh>' shutdown`。

3. **客户端字符集必须是 utf8mb4**：`conf/charset.cnf` 已经挂进容器；手工灌 SQL 时也要带
   `--default-character-set=utf8mb4`，否则中文会双重编码（变成「ç¦…」那种）。

后端启动时把地址覆盖掉即可（**不用改配置文件**，远端那套配置原样留着）：

```bash
java -jar yudao-server/target/yudao-server.jar --spring.profiles.active=local \
  --spring.datasource.dynamic.datasource.master.url='jdbc:mysql://127.0.0.1:3307/ruoyi-vue-pro?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true&nullCatalogMeansCurrent=true' \
  --spring.datasource.dynamic.datasource.slave.url='jdbc:mysql://127.0.0.1:3307/ruoyi-vue-pro?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true&nullCatalogMeansCurrent=true' \
  --spring.data.redis.host=127.0.0.1 --spring.data.redis.port=6380
```

> 顺带一个副作用是好的：本机栈会**从零**执行 `../sql/01~35`，
> 等于把「这套迁移脚本能不能从空库建出完整环境」也验证了一遍
> （之前它们都是分批灌到远端已有库上的）。
>
> 顺带一提：第一次从空库初始化就暴露了一个真实的缺口 —— `01~35` 只建表与"部分"演示数据，
> 而项目/需求/任务/缺陷这几条演示数据当年是手工造在远端库里的、从来没进过脚本。
> `36-zt_demo_seed.sql` 就是补这个缺口的（详见那个文件的注释）。
