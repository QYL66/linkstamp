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

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import io.github.qyl66.linkstamp.project.dto.biz.ShortLinkStatsRecordDTO;
import io.github.qyl66.linkstamp.project.mq.consumer.VisitStatsBatchWriter.Visit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import static io.github.qyl66.linkstamp.project.common.constant.RedisKeyConstant.LOCK_GID_UPDATE_KEY;
import static io.github.qyl66.linkstamp.project.common.constant.RedisKeyConstant.SHORT_LINK_STATS_DEAD_LETTER_KEY;
import static io.github.qyl66.linkstamp.project.common.constant.RedisKeyConstant.SHORT_LINK_STATS_STREAM_GROUP_KEY;
import static io.github.qyl66.linkstamp.project.common.constant.RedisKeyConstant.SHORT_LINK_STATS_STREAM_TOPIC_KEY;
import static io.github.qyl66.linkstamp.project.common.constant.ShortLinkConstant.AMAP_REMOTE_URL;

@Slf4j
@Component
@DependsOn("shortLinkStatsStreamInitializeTask")
@RequiredArgsConstructor
public class ShortLinkStatsSaveConsumer {

    private static final int MAX_ATTEMPTS = 5;
    private static final DefaultRedisScript<Long> ACKNOWLEDGE = new DefaultRedisScript<>(
            """
            local acknowledged = {}
            for index = 2, #ARGV do
                local id = ARGV[index]
                if redis.call('XACK', KEYS[1], ARGV[1], id) == 1 then
                    acknowledged[#acknowledged + 1] = id
                end
            end
            if #acknowledged > 0 then
                redis.call('XDEL', KEYS[1], unpack(acknowledged))
            end
            return #acknowledged
            """, Long.class);
    private static final DefaultRedisScript<Long> DEAD_LETTER = new DefaultRedisScript<>(
            """
            if #redis.call('XPENDING', KEYS[1], ARGV[1], ARGV[2], ARGV[2], 1) == 0 then return 0 end
            redis.call('XADD', KEYS[2], '*', 'originalId', ARGV[2], 'reason', ARGV[3], 'statsRecord', ARGV[4])
            redis.call('XACK', KEYS[1], ARGV[1], ARGV[2])
            return redis.call('XDEL', KEYS[1], ARGV[2])
            """, Long.class);

    private final String consumerName = "stats-" + UUID.randomUUID();
    private final RedissonClient redissonClient;
    private final StringRedisTemplate redis;
    private final VisitStatsBatchWriter writer;

    @Value("${short-link.stats.retry-idle-ms:30000}")
    private long retryIdleMillis;

    @Value("${short-link.stats.batch-size:128}")
    private int batchSize;

    @Value("${short-link.stats.locale.amap-key}")
    private String statsLocaleAmapKey;

    @Scheduled(fixedDelayString = "${short-link.stats.retry-poll-ms:250}")
    public void poll() {
        var stream = redis.<String, String>opsForStream();
        long lastRetry = System.nanoTime();
        retryPending();
        while (!Thread.currentThread().isInterrupted()) {
            if (System.nanoTime() - lastRetry >= TimeUnit.SECONDS.toNanos(1)) {
                retryPending();
                lastRetry = System.nanoTime();
            }
            var records = stream.read(Consumer.from(SHORT_LINK_STATS_STREAM_GROUP_KEY, consumerName),
                    StreamReadOptions.empty().count(Math.max(1, Math.min(batchSize, 512))),
                    StreamOffset.create(SHORT_LINK_STATS_STREAM_TOPIC_KEY, ReadOffset.lastConsumed()));
            if (records == null || records.isEmpty()) {
                return;
            }
            process(records, Map.of());
        }
    }

    private void retryPending() {
        Duration idle = Duration.ofMillis(retryIdleMillis);
        var stream = redis.<String, String>opsForStream();
        var pending = stream.pending(SHORT_LINK_STATS_STREAM_TOPIC_KEY, SHORT_LINK_STATS_STREAM_GROUP_KEY,
                Range.unbounded(), 50, idle);
        if (pending == null || pending.isEmpty()) {
            return;
        }
        Map<RecordId, Long> attempts = new LinkedHashMap<>();
        pending.forEach(entry -> attempts.put(entry.getId(), entry.getTotalDeliveryCount() + 1));
        var claimed = stream.claim(SHORT_LINK_STATS_STREAM_TOPIC_KEY, SHORT_LINK_STATS_STREAM_GROUP_KEY,
                consumerName, idle, attempts.keySet().toArray(RecordId[]::new));
        if (claimed != null && !claimed.isEmpty()) {
            process(claimed, attempts);
        }
    }

    private void process(List<MapRecord<String, String, String>> messages, Map<RecordId, Long> attempts) {
        List<Visit> batch = new ArrayList<>(messages.size());
        Map<String, Optional<JSONObject>> locations = new HashMap<>();
        for (var message : messages) {
            String payload = message.getValue().get("statsRecord");
            ShortLinkStatsRecordDTO record = parse(payload);
            if (record == null) {
                deadLetter(message.getId().getValue(), "invalid-payload", payload);
            } else {
                JSONObject locale = locations.computeIfAbsent(record.getRemoteAddr(),
                        ip -> Optional.ofNullable(lookupLocale(ip))).orElse(null);
                batch.add(new Visit(message.getId().getValue(), record, locale));
            }
        }
        if (batch.isEmpty()) {
            return;
        }

        try {
            persistUnderReadLocks(batch);
        } catch (CompletionException ex) {
            log.warn("Could not acquire route read locks for the statistics batch", ex);
            return;
        } catch (RuntimeException ex) {
            log.warn("Statistics transaction failed for {} entries starting at {}", batch.size(), batch.get(0).id(), ex);
            if (!attempts.isEmpty()) {
                // The first failure rolls the batch back. On redelivery, isolate poison records.
                recoverIndividually(messages, attempts, batch);
            }
            return;
        }
        acknowledge(batch);
    }

    private void recoverIndividually(List<MapRecord<String, String, String>> messages,
                                     Map<RecordId, Long> attempts, List<Visit> batch) {
        Map<String, MapRecord<String, String, String>> byId = new HashMap<>();
        messages.forEach(message -> byId.put(message.getId().getValue(), message));
        List<Visit> committed = new ArrayList<>();
        for (Visit visit : batch) {
            try {
                persistUnderReadLocks(List.of(visit));
                committed.add(visit);
            } catch (CompletionException ex) {
                log.warn("Could not acquire the route read lock for Stream entry {}", visit.id(), ex);
            } catch (RuntimeException ex) {
                var message = byId.get(visit.id());
                long attempt = attempts.get(message.getId());
                log.warn("Visit statistics failed for Stream entry {} on attempt {}", visit.id(), attempt, ex);
                if (attempt >= MAX_ATTEMPTS) {
                    deadLetter(visit.id(), ex.getClass().getSimpleName(), message.getValue().get("statsRecord"));
                }
            }
        }
        if (!committed.isEmpty()) {
            acknowledge(committed);
        }
    }

    private void persistUnderReadLocks(List<Visit> visits) {
        long owner = Thread.currentThread().getId();
        List<ReadLock> locks = new ArrayList<>();
        try {
            for (String url : visits.stream().map(Visit::url).distinct().sorted().toList()) {
                RLock lock = redissonClient.getReadWriteLock(String.format(LOCK_GID_UPDATE_KEY, url)).readLock();
                locks.add(new ReadLock(lock, lock.lockAsync(owner).toCompletableFuture()));
            }
            CompletableFuture.allOf(locks.stream().map(ReadLock::acquired)
                    .toArray(CompletableFuture[]::new)).join();
            writer.persist(visits);
        } finally {
            CompletableFuture.allOf(locks.stream().map(held -> held.acquired().handle((ignored, error) -> error == null)
                    .thenCompose(acquired -> acquired ? held.lock().unlockAsync(owner) : CompletableFuture.completedFuture(null))
                    .exceptionally(error -> {
                        log.warn("Could not release statistics read lock {}", held.lock().getName(), error);
                        return null;
                    })).toArray(CompletableFuture[]::new)).join();
        }
    }

    private void acknowledge(List<Visit> visits) {
        List<String> args = new ArrayList<>(visits.size() + 1);
        args.add(SHORT_LINK_STATS_STREAM_GROUP_KEY);
        visits.forEach(visit -> args.add(visit.id()));
        redis.execute(ACKNOWLEDGE, List.of(SHORT_LINK_STATS_STREAM_TOPIC_KEY), args.toArray(String[]::new));
    }

    private void deadLetter(String id, String reason, String payload) {
        redis.execute(DEAD_LETTER, List.of(SHORT_LINK_STATS_STREAM_TOPIC_KEY, SHORT_LINK_STATS_DEAD_LETTER_KEY),
                SHORT_LINK_STATS_STREAM_GROUP_KEY, id, reason, payload == null ? "" : payload);
        log.warn("Moved statistics Stream entry {} to dead letter storage ({})", id, reason);
    }

    private static ShortLinkStatsRecordDTO parse(String payload) {
        try {
            ShortLinkStatsRecordDTO record = payload == null ? null : JSON.parseObject(payload, ShortLinkStatsRecordDTO.class);
            if (record != null && record.getCurrentDate() != null && StrUtil.isNotBlank(record.getUv())
                    && StrUtil.isNotBlank(record.getFullShortUrl()) && StrUtil.isNotBlank(record.getRemoteAddr())) {
                return record;
            }
        } catch (JSONException ignored) {
            // Malformed entries follow the same dead-letter path as incomplete entries.
        }
        return null;
    }

    private JSONObject lookupLocale(String ip) {
        if (StrUtil.isBlank(statsLocaleAmapKey)) {
            return null;
        }
        try {
            return JSON.parseObject(HttpUtil.get(AMAP_REMOTE_URL, Map.of("key", statsLocaleAmapKey, "ip", ip), 2000));
        } catch (RuntimeException ex) {
            log.debug("IP location lookup unavailable");
            return null;
        }
    }

    private record ReadLock(RLock lock, CompletableFuture<Void> acquired) {
    }
}
