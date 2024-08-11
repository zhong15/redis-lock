/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zhong.redis.lock.starter.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import zhong.redis.lock.starter.RedisLockProps;
import zhong.redis.lock.starter.utils.NamedThreadFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author Zhong
 * @since 0.0.1
 */
@Component
public class DefaultRedisLock implements RedisLock {
    private static final Logger log = LoggerFactory.getLogger(DefaultRedisLock.class);
    private static final String SERVER_UUID;

    static {
        SERVER_UUID = UUID.randomUUID().toString();
    }

    private ScheduledExecutorService threadPool;
    private volatile boolean isTaskRunning;
    private Map<String, LockCache> lockMap;
    private int keepAlive;
    private double factor;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    @Autowired
    private RedisLockProps redisLockProps;

    private static String getLockMapKey(Thread thread, String k) {
        return thread.getId() + "-" + k;
    }

    @PostConstruct
    public void init() {
        log.info("init");
        initProps();
        initThreadVar();
        startKeepAliveTask();
    }

    @PreDestroy
    public void destroy() {
        log.info("destroy");
        log.info("开始 threadPool shutdown");
        if (threadPool == null) {
            log.info("已跳过，原因：threadPool null");
            return;
        }
        if (threadPool.isShutdown()) {
            log.info("已跳过，原因：threadPool 已经 shutdown");
            return;
        }
        threadPool.shutdown();
        log.info("结束 threadPool shutdown");
    }

    private void initProps() {
        log.info("init properties");

        keepAlive = redisLockProps.getKeepAlive();
        log.info("keepAlive: {}", keepAlive);

        factor = redisLockProps.getFactor();
        log.info("factor: {}", factor);
    }

    private void initThreadVar() {
        log.info("init threadPool and lockMap");
        threadPool = Executors.newScheduledThreadPool(1, new NamedThreadFactory("Redis锁定时任务"));
        lockMap = new ConcurrentHashMap<>();
    }

    private void startKeepAliveTask() {
        log.info("startKeepAliveTask");
        threadPool.scheduleAtFixedRate(() -> {
            log.info("开始延长 Reids 锁时间定时任务");
            if (isTaskRunning) {
                log.info("已跳过，原因：上一个任务未结束");
                return;
            }
            try {
                isTaskRunning = true;
                if (lockMap.isEmpty()) {
                    log.info("已跳过，原因：暂时没有需要延长的锁");
                    return;
                }
                List<String> stopThreadKeyList = new ArrayList<>();
                lockMap.forEach((k, v) -> {
                    log.info("线程 id={}，k={}，是否存活={}", v.thread.getId(), v.k, v.thread.isAlive());
                    if (v.thread.isAlive()) {
                        boolean expireSuccess = expire(v.k, getThreadLockValue(v.thread), v.timeout, v.timeUnit);
                        log.info("延长时长，线程 id={}，k={}，成功={}", v.thread.getId(), v.k, expireSuccess);
                    } else {
                        boolean unlockSuccess = unlock(v.k, getThreadLockValue(v.thread));
                        log.info("释放 Redis 锁 k={}，成功={}", v.k, unlockSuccess);
                        stopThreadKeyList.add(k);
                    }
                });
                stopThreadKeyList.forEach(e -> {
                    lockMap.remove(e);
                });
            } catch (Exception e) {
                log.error("定时任务错误", e);
            } finally {
                isTaskRunning = false;
                log.info("已结束定时任务");
            }
        }, keepAlive, keepAlive, TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean lock(String k, String v, long timeout, TimeUnit timeUnit) {
        return lock(k, v, timeout, timeUnit, false);
    }

    @Override

    public boolean lockAndKeepAlive(String k, long timeout, TimeUnit timeUnit) {
        String v = String.valueOf(Thread.currentThread().getId());
        return lock(k, v, timeout, timeUnit, true);
    }

    private boolean lock(String k, String v, long timeout, TimeUnit timeUnit, boolean isKeepAlive) {
        Assert.hasLength(k, "k 必填");
        Assert.hasLength(v, "v 必填");
        Assert.isTrue(timeout > 0, "timeout 必须大于 0");
        Assert.notNull(timeUnit, "timeUnit 必填");

        if (isKeepAlive) {
            if (timeUnit.toMillis(timeout) < keepAlive * factor) {
                throw new IllegalArgumentException("timeout 必须大于 " + (keepAlive * factor) + " 毫秒");
            }
        }
        Boolean result = redisTemplate.opsForValue().setIfAbsent(k, v, timeout, timeUnit);
        if (result != null && result) {
            if (isKeepAlive) {
                Thread thread = Thread.currentThread();
                lockMap.put(getLockMapKey(thread, k), new LockCache(thread, k, timeout, timeUnit));
            }
        }
        return result != null && result;
    }

    @Override
    public boolean unlock(String k, String v) {
        Assert.hasLength(k, "k 必填");
        Assert.hasLength(v, "v 必填");

        // redis 是单线程，且是del操作，所以不用先 expire 这一步
        String s = " if redis.call('GET', KEYS[1]) == ARGV[1]" +
                " then redis.call('DEL', KEYS[1])" +
                " return 'OK'" +
                " end" +
                " return nil";
        RedisScript<String> script = new DefaultRedisScript<>(s, String.class);
        String success = redisTemplate.execute(script, Collections.singletonList(k), v);
        lockMap.remove(getLockMapKey(Thread.currentThread(), k));
        return Objects.equals(success, "OK");
    }

    @Override
    public boolean expire(String k, String v, long timeout, TimeUnit timeUnit) {
        Assert.hasLength(k, "k 必填");
        Assert.hasLength(v, "v 必填");
        Assert.isTrue(timeout > 0, "timeout 必须大于 0");
        Assert.notNull(timeUnit, "timeUnit 必填");

        String s = "if redis.call('EXPIRE', KEYS[1], ARGV[2]) == 0" +
                " then" +
                " return redis.call('SET', KEYS[1], ARGV[1], 'NX', 'EX', ARGV[2])" +
                " end" +
                " if redis.call('GET', KEYS[1]) == ARGV[1]" +
                " then return 'OK'" +
                " else return nil" +
                " end";
        RedisScript<String> script = new DefaultRedisScript<>(s, String.class);
        String success = redisTemplate.execute(script, Collections.singletonList(k), v, String.valueOf(timeUnit.toSeconds(timeout)));
        return Objects.equals(success, "OK");
    }

    private static String getThreadLockValue(Thread thread) {
        return SERVER_UUID + thread.getId();
    }

    private static final class LockCache {
        private final Thread thread;
        private final String k;
        private final Long timeout;
        private final TimeUnit timeUnit;

        private LockCache(Thread thread, String k, Long timeout, TimeUnit timeUnit) {
            this.thread = thread;
            this.k = k;
            this.timeout = timeout;
            this.timeUnit = timeUnit;
        }
    }
}
