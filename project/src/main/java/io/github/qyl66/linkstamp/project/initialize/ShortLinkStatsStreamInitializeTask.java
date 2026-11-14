/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.qyl66.linkstamp.project.initialize;

/* LinkStamp changes: ensure the statistics consumer group exists without resetting its position. */

import io.github.qyl66.linkstamp.project.dao.mapper.LinkStatsEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import static io.github.qyl66.linkstamp.project.common.constant.RedisKeyConstant.SHORT_LINK_STATS_STREAM_GROUP_KEY;
import static io.github.qyl66.linkstamp.project.common.constant.RedisKeyConstant.SHORT_LINK_STATS_STREAM_TOPIC_KEY;

/**
 * 初始化短链接监控消息队列消费者组
 */
@Component
@RequiredArgsConstructor
public class ShortLinkStatsStreamInitializeTask implements InitializingBean {

    private final StringRedisTemplate stringRedisTemplate;
    private final LinkStatsEventMapper linkStatsEventMapper;

    @Override
    public void afterPropertiesSet() {
        try {
            linkStatsEventMapper.checkTable();
        } catch (DataAccessException ex) {
            throw new IllegalStateException("Verify the database connection and apply resources/database/migrate-visit-stats.sql before starting LinkStamp.", ex);
        }
        try {
            stringRedisTemplate.opsForStream().createGroup(
                    SHORT_LINK_STATS_STREAM_TOPIC_KEY, ReadOffset.from("0-0"), SHORT_LINK_STATS_STREAM_GROUP_KEY);
        } catch (DataAccessException ex) {
            Throwable cause = ex.getMostSpecificCause();
            if (cause.getMessage() == null || !cause.getMessage().contains("BUSYGROUP")) {
                throw ex;
            }
        }
    }
}
