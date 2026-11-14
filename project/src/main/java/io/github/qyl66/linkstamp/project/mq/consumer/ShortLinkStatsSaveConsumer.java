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

package io.github.qyl66.linkstamp.project.mq.consumer;

/* LinkStamp changes: persist visit statistics transactionally and recover unconfirmed Stream messages. */

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.qyl66.linkstamp.project.dao.entity.LinkAccessLogsDO;
import io.github.qyl66.linkstamp.project.dao.entity.LinkAccessStatsDO;
import io.github.qyl66.linkstamp.project.dao.entity.LinkBrowserStatsDO;
import io.github.qyl66.linkstamp.project.dao.entity.LinkDeviceStatsDO;
import io.github.qyl66.linkstamp.project.dao.entity.LinkLocaleStatsDO;
import io.github.qyl66.linkstamp.project.dao.entity.LinkNetworkStatsDO;
import io.github.qyl66.linkstamp.project.dao.entity.LinkOsStatsDO;
import io.github.qyl66.linkstamp.project.dao.entity.LinkStatsTodayDO;
import io.github.qyl66.linkstamp.project.dao.entity.ShortLinkGotoDO;
import io.github.qyl66.linkstamp.project.dao.mapper.LinkAccessLogsMapper;
import io.github.qyl66.linkstamp.project.dao.mapper.LinkAccessStatsMapper;
import io.github.qyl66.linkstamp.project.dao.mapper.LinkBrowserStatsMapper;
import io.github.qyl66.linkstamp.project.dao.mapper.LinkDeviceStatsMapper;
import io.github.qyl66.linkstamp.project.dao.mapper.LinkLocaleStatsMapper;
import io.github.qyl66.linkstamp.project.dao.mapper.LinkNetworkStatsMapper;
import io.github.qyl66.linkstamp.project.dao.mapper.LinkOsStatsMapper;
import io.github.qyl66.linkstamp.project.dao.mapper.LinkStatsTodayMapper;
import io.github.qyl66.linkstamp.project.dao.mapper.LinkStatsEventMapper;
import io.github.qyl66.linkstamp.project.dao.mapper.ShortLinkGotoMapper;
import io.github.qyl66.linkstamp.project.dao.mapper.ShortLinkMapper;
import io.github.qyl66.linkstamp.project.dto.biz.ShortLinkStatsRecordDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.domain.Range;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.Map;

import static io.github.qyl66.linkstamp.project.common.constant.RedisKeyConstant.LOCK_GID_UPDATE_KEY;
import static io.github.qyl66.linkstamp.project.common.constant.ShortLinkConstant.AMAP_REMOTE_URL;
import static io.github.qyl66.linkstamp.project.common.constant.RedisKeyConstant.SHORT_LINK_STATS_STREAM_TOPIC_KEY;
import static io.github.qyl66.linkstamp.project.common.constant.RedisKeyConstant.SHORT_LINK_STATS_STREAM_GROUP_KEY;
import static io.github.qyl66.linkstamp.project.common.constant.RedisKeyConstant.SHORT_LINK_STATS_DEAD_LETTER_KEY;

/**
 * 短链接监控状态保存消息队列消费者
 */
@Slf4j
@Component
@DependsOn("shortLinkStatsStreamInitializeTask")
@RequiredArgsConstructor
public class ShortLinkStatsSaveConsumer {

    private static final int MAX_ATTEMPTS = 5;
    private static final DateTimeFormatter SQL_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");
    private static final ZoneOffset STATS_OFFSET = ZoneOffset.ofHours(8);
    private static final DefaultRedisScript<Long> ACKNOWLEDGE = new DefaultRedisScript<>(
            """
            if redis.call('XACK', KEYS[1], ARGV[1], ARGV[2]) == 1 then
                return redis.call('XDEL', KEYS[1], ARGV[2])
            end
            return 0
            """, Long.class);
    private static final DefaultRedisScript<Long> DEAD_LETTER = new DefaultRedisScript<>(
            """
            if #redis.call('XPENDING', KEYS[1], ARGV[1], ARGV[2], ARGV[2], 1) == 0 then return 0 end
            redis.call('XADD', KEYS[2], '*', 'originalId', ARGV[2], 'reason', ARGV[3], 'statsRecord', ARGV[4])
            redis.call('XACK', KEYS[1], ARGV[1], ARGV[2])
            return redis.call('XDEL', KEYS[1], ARGV[2])
            """, Long.class);

    private final String consumerName = "stats-" + UUID.randomUUID();
    private final ShortLinkMapper shortLinkMapper;
    private final ShortLinkGotoMapper shortLinkGotoMapper;
    private final RedissonClient redissonClient;
    private final LinkAccessStatsMapper linkAccessStatsMapper;
    private final LinkLocaleStatsMapper linkLocaleStatsMapper;
    private final LinkOsStatsMapper linkOsStatsMapper;
    private final LinkBrowserStatsMapper linkBrowserStatsMapper;
    private final LinkAccessLogsMapper linkAccessLogsMapper;
    private final LinkDeviceStatsMapper linkDeviceStatsMapper;
    private final LinkNetworkStatsMapper linkNetworkStatsMapper;
    private final LinkStatsTodayMapper linkStatsTodayMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final LinkStatsEventMapper linkStatsEventMapper;
    private final TransactionTemplate transactionTemplate;

    @Value("${short-link.stats.retry-idle-ms:30000}")
    private long retryIdleMillis;

    @Value("${short-link.stats.locale.amap-key}")
    private String statsLocaleAmapKey;

    private JSONObject lookupLocale(String ip) {
        if (StrUtil.isBlank(statsLocaleAmapKey)) {
            return null;
        }
        try {
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("key", statsLocaleAmapKey);
            parameters.put("ip", ip);
            return JSON.parseObject(HttpUtil.get(AMAP_REMOTE_URL, parameters, 2000));
        } catch (RuntimeException ex) {
            log.debug("IP location lookup unavailable");
            return null;
        }
    }

    @Scheduled(fixedDelayString = "${short-link.stats.retry-poll-ms:1000}")
    public void poll() {
        var stream = stringRedisTemplate.<String, String>opsForStream();
        var records = stream.read(Consumer.from(SHORT_LINK_STATS_STREAM_GROUP_KEY, consumerName),
                StreamReadOptions.empty().count(25),
                StreamOffset.create(SHORT_LINK_STATS_STREAM_TOPIC_KEY, ReadOffset.lastConsumed()));
        if (records != null) {
            records.forEach(record -> process(record, 1));
        }
        retryPending();
    }

    private void retryPending() {
        Duration idle = Duration.ofMillis(retryIdleMillis);
        var stream = stringRedisTemplate.<String, String>opsForStream();
        var pending = stream.pending(SHORT_LINK_STATS_STREAM_TOPIC_KEY, SHORT_LINK_STATS_STREAM_GROUP_KEY,
                Range.unbounded(), 50, idle);
        if (pending == null) {
            return;
        }
        for (var entry : pending) {
            var claimed = stream.claim(SHORT_LINK_STATS_STREAM_TOPIC_KEY, SHORT_LINK_STATS_STREAM_GROUP_KEY,
                    consumerName, idle, entry.getId());
            if (claimed != null) {
                claimed.forEach(record -> process(record, entry.getTotalDeliveryCount() + 1));
            }
        }
    }

    private void process(MapRecord<String, String, String> message, long attempt) {
        RecordId id = message.getId();
        String payload = message.getValue().get("statsRecord");
        ShortLinkStatsRecordDTO record;
        try {
            record = payload == null ? null : JSON.parseObject(payload, ShortLinkStatsRecordDTO.class);
        } catch (com.alibaba.fastjson2.JSONException ex) {
            record = null;
        }
        if (record == null || record.getCurrentDate() == null || StrUtil.isBlank(record.getUv())
                || StrUtil.isBlank(record.getFullShortUrl()) || StrUtil.isBlank(record.getRemoteAddr())) {
            deadLetter(id, "invalid-payload", payload);
            return;
        }

        ShortLinkStatsRecordDTO visit = record;
        JSONObject location = lookupLocale(visit.getRemoteAddr());
        RLock lock = redissonClient.getReadWriteLock(String.format(LOCK_GID_UPDATE_KEY, visit.getFullShortUrl())).writeLock();
        lock.lock();
        try {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    if (linkStatsEventMapper.claimEvent(id.getValue()) != 0) {
                        saveVisit(visit, location);
                    }
                });
            } catch (RuntimeException ex) {
                log.warn("Visit statistics failed for Stream entry {} on attempt {}", id, attempt, ex);
                if (attempt >= MAX_ATTEMPTS) {
                    deadLetter(id, ex.getClass().getSimpleName(), payload);
                }
                return;
            }
            stringRedisTemplate.execute(ACKNOWLEDGE, List.of(SHORT_LINK_STATS_STREAM_TOPIC_KEY),
                    SHORT_LINK_STATS_STREAM_GROUP_KEY, id.getValue());
        } finally {
            lock.unlock();
        }
    }

    private void deadLetter(RecordId id, String reason, String payload) {
        stringRedisTemplate.execute(DEAD_LETTER,
                List.of(SHORT_LINK_STATS_STREAM_TOPIC_KEY, SHORT_LINK_STATS_DEAD_LETTER_KEY),
                SHORT_LINK_STATS_STREAM_GROUP_KEY, id.getValue(), reason, payload == null ? "" : payload);
        log.warn("Moved statistics Stream entry {} to dead letter storage ({})", id, reason);
    }

    private void saveVisit(ShortLinkStatsRecordDTO statsRecord, JSONObject localeResultObj) {
        String fullShortUrl = statsRecord.getFullShortUrl();
        ShortLinkGotoDO route = shortLinkGotoMapper.selectOne(Wrappers.<ShortLinkGotoDO>lambdaQuery()
                .eq(ShortLinkGotoDO::getFullShortUrl, fullShortUrl));
        if (route == null) {
            throw new IllegalStateException("No route for statistics event");
        }
        String gid = route.getGid();
        LocalDateTime visitedAt = LocalDateTime.ofInstant(statsRecord.getCurrentDate().toInstant(), STATS_OFFSET);
        var day = visitedAt.toLocalDate();
        var sqlDay = java.sql.Date.valueOf(day);
        String dayStart = day.atStartOfDay().format(SQL_TIME);
        String dayEnd = day.plusDays(1).atStartOfDay().format(SQL_TIME);
        LinkAccessStatsDO visitorCounts = linkAccessLogsMapper.countVisitor(fullShortUrl, statsRecord.getUv(), dayStart, dayEnd);
        LinkAccessStatsDO ipCounts = linkAccessLogsMapper.countIp(fullShortUrl, statsRecord.getRemoteAddr(), dayStart, dayEnd);
        int dailyUv = visitorCounts.getUv() == 0 ? 1 : 0;
        int dailyUip = ipCounts.getUv() == 0 ? 1 : 0;
        LinkAccessStatsDO linkAccessStatsDO = LinkAccessStatsDO.builder()
                .pv(1)
                .uv(dailyUv)
                .uip(dailyUip)
                .hour(visitedAt.getHour())
                .weekday(visitedAt.getDayOfWeek().getValue())
                .fullShortUrl(fullShortUrl)
                .date(sqlDay)
                .build();
        linkAccessStatsMapper.shortLinkStats(linkAccessStatsDO);
        String actualProvince = "未知";
        String actualCity = "未知";
        if (localeResultObj != null && "10000".equals(localeResultObj.getString("infocode"))) {
            String province = localeResultObj.getString("province");
            boolean unknownFlag = StrUtil.equals(province, "[]");
            LinkLocaleStatsDO linkLocaleStatsDO = LinkLocaleStatsDO.builder()
                    .province(actualProvince = unknownFlag ? actualProvince : province)
                    .city(actualCity = unknownFlag ? actualCity : localeResultObj.getString("city"))
                    .adcode(unknownFlag ? "未知" : localeResultObj.getString("adcode"))
                    .cnt(1)
                    .fullShortUrl(fullShortUrl)
                    .country("中国")
                    .date(sqlDay)
                    .build();
            linkLocaleStatsMapper.shortLinkLocaleState(linkLocaleStatsDO);
        }
        LinkOsStatsDO linkOsStatsDO = LinkOsStatsDO.builder()
                .os(statsRecord.getOs())
                .cnt(1)
                .fullShortUrl(fullShortUrl)
                .date(sqlDay)
                .build();
        linkOsStatsMapper.shortLinkOsState(linkOsStatsDO);
        LinkBrowserStatsDO linkBrowserStatsDO = LinkBrowserStatsDO.builder()
                .browser(statsRecord.getBrowser())
                .cnt(1)
                .fullShortUrl(fullShortUrl)
                .date(sqlDay)
                .build();
        linkBrowserStatsMapper.shortLinkBrowserState(linkBrowserStatsDO);
        LinkDeviceStatsDO linkDeviceStatsDO = LinkDeviceStatsDO.builder()
                .device(statsRecord.getDevice())
                .cnt(1)
                .fullShortUrl(fullShortUrl)
                .date(sqlDay)
                .build();
        linkDeviceStatsMapper.shortLinkDeviceState(linkDeviceStatsDO);
        LinkNetworkStatsDO linkNetworkStatsDO = LinkNetworkStatsDO.builder()
                .network(statsRecord.getNetwork())
                .cnt(1)
                .fullShortUrl(fullShortUrl)
                .date(sqlDay)
                .build();
        linkNetworkStatsMapper.shortLinkNetworkState(linkNetworkStatsDO);
        LinkAccessLogsDO linkAccessLogsDO = LinkAccessLogsDO.builder()
                .user(statsRecord.getUv())
                .ip(statsRecord.getRemoteAddr())
                .browser(statsRecord.getBrowser())
                .os(statsRecord.getOs())
                .network(statsRecord.getNetwork())
                .device(statsRecord.getDevice())
                .locale(StrUtil.join("-", "中国", actualProvince, actualCity))
                .fullShortUrl(fullShortUrl)
                .build();
        linkAccessLogsMapper.insertVisit(linkAccessLogsDO, visitedAt.format(SQL_TIME));
        shortLinkMapper.incrementStats(gid, fullShortUrl, 1,
                visitorCounts.getPv() == 0 ? 1 : 0, ipCounts.getPv() == 0 ? 1 : 0);
        LinkStatsTodayDO linkStatsTodayDO = LinkStatsTodayDO.builder()
                .todayPv(1)
                .todayUv(dailyUv)
                .todayUip(dailyUip)
                .fullShortUrl(fullShortUrl)
                .date(sqlDay)
                .build();
        linkStatsTodayMapper.shortLinkTodayState(linkStatsTodayDO);
    }
}
