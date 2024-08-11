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

package zhong.redis.lock.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;

/**
 * @author Zhong
 * @since 0.0.1
 */
@Validated
@ConfigurationProperties(prefix = RedisLockProps.PROPERTIES_PREFIX)
public class RedisLockProps {
    static final String PROPERTIES_PREFIX = "zhong.redis-lock";

    private static final int MIN_KEEP_ALIVE = 5_000;
    private static final int DEFAULT_KEEP_ALIVE = 60_000;
    private static final String MIN_FACTOR = "1.1";
    private static final double DEFAULT_FACTOR = 1.5;

    @Min(value = MIN_KEEP_ALIVE)
    private Integer keepAlive = DEFAULT_KEEP_ALIVE;

    @DecimalMin(value = MIN_FACTOR)
    private Double factor = DEFAULT_FACTOR;

    public Integer getKeepAlive() {
        return keepAlive;
    }

    public void setKeepAlive(Integer keepAlive) {
        this.keepAlive = keepAlive;
    }

    public Double getFactor() {
        return factor;
    }

    public void setFactor(Double factor) {
        this.factor = factor;
    }
}
