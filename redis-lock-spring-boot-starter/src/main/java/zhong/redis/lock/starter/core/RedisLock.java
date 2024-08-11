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

import java.util.concurrent.TimeUnit;

/**
 * @author Zhong
 * @since 0.0.1
 */
public interface RedisLock {
    /**
     * 锁 key 并设置过期时间
     *
     * @param k        key
     * @param v        value
     * @param timeout  过期时间
     * @param timeUnit 单位
     * @return true 如果设置成功
     */
    boolean lock(String k, String v, long timeout, TimeUnit timeUnit);

    /**
     * 锁 key 并设置过期时间，如果线程存活，会定时延长过期时间
     *
     * @param k        key
     * @param timeout  过期时间
     * @param timeUnit 单位
     * @return true 如果设置成功
     */
    boolean lockAndKeepAlive(String k, long timeout, TimeUnit timeUnit);

    /**
     * 解锁，当且仅当 k 的值为 v
     *
     * @param k key
     * @param v value
     * @return true 如果解锁成功
     */
    boolean unlock(String k, String v);

    /**
     * 设置过期时间，当 k 的值为 v 或 k 不存在
     *
     * @param k        key
     * @param v        value
     * @param timeout  过期时间
     * @param timeUnit 单位
     * @return true 如果设置成功
     */
    boolean expire(String k, String v, long timeout, TimeUnit timeUnit);
}
