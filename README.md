# redis-lock
基于 Redis 的分布式锁实现

## 使用方式
- 引入 Maven 依赖
```xml
<dependency>
    <groupId>zhong</groupId>
    <artifactId>redis-lock-spring-boot-starter</artifactId>
    <version>${revision}</version>
</dependency>
```
- 配置 application.yml
```yaml
zhong:
  redis:
    lock:
      enable: true  # 配置是否启用，默认 false
      keep-alive:   # 定时任务执行周期，默认 60000，最小值 5000，单位：ms
      factor:       # keep-alive 系数，默认 1.5，最小值 1.1，即使用 RedisLock#lockAndKeepAlive(...) api 时 timeout 时长必须大于等于 keep-alive * factor
```
