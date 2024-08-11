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

package zhong.redis.lock.starter.test;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import zhong.redis.lock.starter.core.RedisLock;

import java.util.concurrent.TimeUnit;

/**
 * @author Zhong
 * @since 0.0.1
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = {Application.class})
public class RedisLockTest {
    private static final Logger log = LoggerFactory.getLogger(RedisLockTest.class);

    @Autowired
    private RedisLock redisLock;
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Test
    public void test_bean() {
        Assert.assertNotNull(redisLock);
        Assert.assertNotNull(redisTemplate);
    }

    @Test
    public void test_lock() {
        final String k = "hello";
        final String v = "world";
        final long timeout = 10_000;
        final TimeUnit timeUnit = TimeUnit.MILLISECONDS;

        /*
         * 清理数据
         */
        redisTemplate.delete(k);

        /*
         * lock，期望：true
         */
        boolean success = redisLock.lock(k, v, timeout, timeUnit);
        Assert.assertTrue(success);
        // 获取值，期望：等于 v
        String s = redisTemplate.opsForValue().get(k);
        Assert.assertEquals(s, v);
        // 获取过期时间，期望：大于 0 且小于等于 timeout
        Long expire = redisTemplate.getExpire(k, TimeUnit.MILLISECONDS);
        Assert.assertNotNull(expire);
        Assert.assertTrue(expire > 0);
        Assert.assertTrue(expire <= timeout);

        /*
         * lock 已存在的数据，期望：false
         */
        success = redisLock.lock(k, v, timeout, timeUnit);
        Assert.assertFalse(success);
    }

    @Test
    public void test_unlock() {
        final String k = "hello";
        final String v = "world";
        final long timeout = 10_000;
        final TimeUnit timeUnit = TimeUnit.MILLISECONDS;

        /*
         * 清理数据
         */
        redisTemplate.delete(k);

        /*
         * unlock 不存在的数据，期望：false
         */
        boolean success = redisLock.unlock(k, v);
        Assert.assertFalse(success);

        /*
         * lock 不存在的数据，期望：true
         */
        success = redisLock.lock(k, v, timeout, timeUnit);
        Assert.assertTrue(success);

        /*
         * unlock 存在的数据，但值不一样，期望：false
         */
        success = redisLock.unlock(k, v + v);
        Assert.assertFalse(success);

        /*
         * unlock 存在的数据，值一样，期望：true
         */
        success = redisLock.unlock(k, v);
        Assert.assertTrue(success);
    }

    @Test
    public void test_expire() {
        final String k = "hello";
        final String v = "world";
        final long timeout = 10_000;
        final long updateTimeout = timeout * 2;
        final TimeUnit timeUnit = TimeUnit.MILLISECONDS;

        /*
         * 清理数据
         */
        redisTemplate.delete(k);

        /*
         * expire 不存在的数据，期望：true
         */
        boolean success = redisLock.expire(k, v, updateTimeout, timeUnit);
        Assert.assertTrue(success);
        Long expire = redisTemplate.getExpire(k, timeUnit);
        Assert.assertNotNull(expire);
        Assert.assertTrue(expire > timeout);
        Assert.assertTrue(expire <= updateTimeout);

        /*
         * 清理数据
         */
        redisTemplate.delete(k);

        /*
         * lock 不存在的数据，期望：true
         */
        success = redisLock.lock(k, v, timeout, timeUnit);
        Assert.assertTrue(success);

        /*
         * expire 存在的数据，但值不一样，期望：false
         */
        success = redisLock.expire(k, v + v, updateTimeout, timeUnit);
        Assert.assertFalse(success);

        /*
         * expire 存在的数据，值一样，期望：true，过期时间更新
         */
        success = redisLock.expire(k, v, updateTimeout, timeUnit);
        Assert.assertTrue(success);
        expire = redisTemplate.getExpire(k, timeUnit);
        Assert.assertNotNull(expire);
        Assert.assertTrue(expire > timeout);
        Assert.assertTrue(expire <= updateTimeout);
    }
}
