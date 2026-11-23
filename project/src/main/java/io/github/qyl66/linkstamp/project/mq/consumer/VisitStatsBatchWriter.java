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
import com.alibaba.fastjson2.JSONObject;
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
import io.github.qyl66.linkstamp.project.dao.mapper.LinkStatsEventMapper;
import io.github.qyl66.linkstamp.project.dao.mapper.LinkStatsTodayMapper;
import io.github.qyl66.linkstamp.project.dao.mapper.ShortLinkGotoMapper;
import io.github.qyl66.linkstamp.project.dao.mapper.ShortLinkMapper;
import io.github.qyl66.linkstamp.project.dto.biz.ShortLinkStatsRecordDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Publishes all database effects of a batch atomically while the caller holds the route read locks.
 */
@Component
@RequiredArgsConstructor
class VisitStatsBatchWriter {
    private static final DateTimeFormatter SQL_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");
    private static final ZoneOffset STATS_OFFSET = ZoneOffset.ofHours(8);
    private static final Pattern STANDARD_UV = Pattern.compile("[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}");
    private static final Pattern STANDARD_IP = Pattern.compile("[a-f0-9:.]+");

    private final ShortLinkMapper shortLinkMapper;
    private final ShortLinkGotoMapper shortLinkGotoMapper;
    private final LinkAccessLogsMapper logsMapper;
    private final LinkStatsEventMapper eventMapper;
    private final LinkAccessStatsMapper hourMapper;
    private final LinkStatsTodayMapper dailyMapper;
    private final LinkOsStatsMapper osMapper;
    private final LinkBrowserStatsMapper browserMapper;
    private final LinkDeviceStatsMapper deviceMapper;
    private final LinkNetworkStatsMapper networkMapper;
    private final LinkLocaleStatsMapper localeMapper;
    private final TransactionTemplate transactionTemplate;

    record Visit(String id, ShortLinkStatsRecordDTO record, JSONObject locale) {
        String url() {
            return record.getFullShortUrl();
        }
    }

    void persist(List<Visit> batch) {
        Map<String, List<Visit>> byUrl = new TreeMap<>();
        for (Visit visit : batch) {
            byUrl.computeIfAbsent(visit.url(), ignored -> new ArrayList<>()).add(visit);
        }
        Map<String, String> routes = loadRoutes(byUrl.keySet());
        transactionTemplate.executeWithoutResult(status -> writeBatch(batch, byUrl, routes));
    }

    private Map<String, String> loadRoutes(Collection<String> urls) {
        Map<String, String> routes = new HashMap<>();
        for (ShortLinkGotoDO route : shortLinkGotoMapper.selectList(Wrappers.<ShortLinkGotoDO>lambdaQuery()
                .in(ShortLinkGotoDO::getFullShortUrl, urls))) {
            routes.put(route.getFullShortUrl(), route.getGid());
        }
        // Existing URLs may compare equal under the database collation without being equal Java strings.
        for (String url : urls) {
            if (!routes.containsKey(url)) {
                ShortLinkGotoDO route = shortLinkGotoMapper.selectOne(Wrappers.<ShortLinkGotoDO>lambdaQuery()
                        .eq(ShortLinkGotoDO::getFullShortUrl, url));
                if (route != null) {
                    routes.put(url, route.getGid());
                }
            }
        }
        return routes;
    }

    private void writeBatch(List<Visit> batch, Map<String, List<Visit>> byUrl, Map<String, String> routes) {
        Map<String, Long> rowIds = new HashMap<>();
        // Take current DB locks in URL order before any consistent read establishes a MySQL snapshot.
        // This serializes same-link UV/UIP calculation across consumers sharing a route read lock.
        for (String url : byUrl.keySet()) {
            String gid = routes.get(url);
            if (gid != null) {
                Long id = shortLinkMapper.lockStatsRow(gid, url);
                if (id == null) {
                    id = shortLinkMapper.lockLastArchivedStatsRow(gid, url);
                }
                rowIds.put(url, id);
            }
        }
        Set<String> committed = new HashSet<>(eventMapper.findCommitted(batch.stream().map(Visit::id).toList()));
        Set<String> fresh = new HashSet<>();
        for (Visit visit : batch) {
            if (!committed.contains(visit.id())) {
                if (routes.get(visit.url()) == null || rowIds.get(visit.url()) == null) {
                    throw new IllegalStateException("No target row for statistics event " + visit.id());
                }
                fresh.add(visit.id());
            }
        }
        if (fresh.isEmpty()) {
            return;
        }

        eventMapper.insertEvents(fresh.stream().sorted().toList());
        Aggregates aggregates = new Aggregates();
        for (var group : byUrl.entrySet()) {
            List<Visit> visits = group.getValue().stream().filter(visit -> fresh.remove(visit.id())).toList();
            if (visits.isEmpty()) {
                continue;
            }
            Counts counts = aggregateLink(visits, aggregates);
            String url = group.getKey();
            shortLinkMapper.incrementStats(routes.get(url), rowIds.get(url), counts.pv, counts.uv, counts.uip);
        }
        flush(aggregates);
    }

    private Counts aggregateLink(List<Visit> visits, Aggregates aggregates) {
        Counts totals = new Counts();
        // For arbitrary legacy cookie or IP strings, let the DB apply its collation after each insert.
        // Normal UUID/IP values can be tracked in memory while their access logs are buffered.
        boolean bufferLogs = visits.stream().allMatch(visit ->
                STANDARD_UV.matcher(visit.record().getUv()).matches()
                        && STANDARD_IP.matcher(visit.record().getRemoteAddr()).matches());
        Set<String> seenVisitors = new HashSet<>();
        Set<String> seenIps = new HashSet<>();
        Set<IdentityDay> seenDayVisitors = new HashSet<>();
        Set<IdentityDay> seenDayIps = new HashSet<>();

        for (Visit visit : visits) {
            ShortLinkStatsRecordDTO record = visit.record();
            LocalDateTime time = LocalDateTime.ofInstant(record.getCurrentDate().toInstant(), STATS_OFFSET);
            LocalDate day = time.toLocalDate();
            String start = day.atStartOfDay().format(SQL_TIME);
            String end = day.plusDays(1).atStartOfDay().format(SQL_TIME);
            Presence visitor = presence(record.getUv(), day, bufferLogs, seenVisitors, seenDayVisitors,
                    () -> logsMapper.findVisitorPresence(visit.url(), record.getUv(), start, end));
            Presence ip = presence(record.getRemoteAddr(), day, bufferLogs, seenIps, seenDayIps,
                    () -> logsMapper.findIpPresence(visit.url(), record.getRemoteAddr(), start, end));
            totals.add(visitor.total(), ip.total());

            Day bucket = new Day(visit.url(), Date.valueOf(day));
            aggregates.daily.computeIfAbsent(bucket, ignored -> new Counts()).add(visitor.daily(), ip.daily());
            Hour hour = new Hour(bucket, time.getHour(), time.getDayOfWeek().getValue());
            aggregates.hourly.computeIfAbsent(hour, ignored -> new Counts()).add(visitor.daily(), ip.daily());

            LocaleValue locale = LocaleValue.from(visit.locale());
            if (locale != null) {
                String nullableRowId = locale.adcode() == null || locale.province() == null ? visit.id() : null;
                aggregates.locales.merge(new Dimension<>(bucket, locale, nullableRowId), 1, Integer::sum);
            }
            count(aggregates.os, bucket, record.getOs(), visit.id());
            count(aggregates.browsers, bucket, record.getBrowser(), visit.id());
            count(aggregates.devices, bucket, record.getDevice(), visit.id());
            count(aggregates.networks, bucket, record.getNetwork(), visit.id());

            LinkAccessLogsDO log = LinkAccessLogsDO.builder()
                    .fullShortUrl(visit.url()).user(record.getUv()).ip(record.getRemoteAddr())
                    .os(record.getOs()).browser(record.getBrowser()).device(record.getDevice()).network(record.getNetwork())
                    .locale(locale == null ? "中国-未知-未知" : StrUtil.join("-", "中国", locale.province(), locale.city()))
                    .build();
            String visitedAt = time.format(SQL_TIME);
            if (bufferLogs) {
                aggregates.logs.add(new LinkAccessLogsMapper.VisitRow(log, visitedAt));
            } else {
                logsMapper.insertVisit(log, visitedAt);
            }
        }
        return totals;
    }

    private static Presence presence(String value, LocalDate day, boolean buffered, Set<String> seenAll,
                                     Set<IdentityDay> seenDay, java.util.function.Supplier<LinkAccessStatsDO> lookup) {
        if (!buffered) {
            return Presence.from(lookup.get());
        }
        boolean firstOverall = seenAll.add(value);
        if (!seenDay.add(new IdentityDay(value, day))) {
            return new Presence(0, 0);
        }
        Presence stored = Presence.from(lookup.get());
        return new Presence(firstOverall ? stored.total() : 0, stored.daily());
    }

    private static <T> void count(Map<Dimension<T>, Integer> totals, Day day, T value, String id) {
        // A NULL dimension never collides in the corresponding MySQL unique index.
        totals.merge(new Dimension<>(day, value, value == null ? id : null), 1, Integer::sum);
    }

    private void flush(Aggregates a) {
        if (!a.logs.isEmpty()) {
            logsMapper.insertVisits(a.logs);
        }
        hourMapper.upsertBatch(a.hourly.entrySet().stream().map(entry -> {
            Hour key = entry.getKey();
            Counts hits = entry.getValue();
            return LinkAccessStatsDO.builder().fullShortUrl(key.day().url()).date(key.day().date())
                    .hour(key.hour()).weekday(key.weekday()).pv(hits.pv).uv(hits.uv).uip(hits.uip).build();
        }).toList());
        dailyMapper.upsertBatch(a.daily.entrySet().stream().map(entry -> {
            Day key = entry.getKey();
            Counts hits = entry.getValue();
            return LinkStatsTodayDO.builder().fullShortUrl(key.url()).date(key.date())
                    .todayPv(hits.pv).todayUv(hits.uv).todayUip(hits.uip).build();
        }).toList());
        osMapper.upsertBatch(a.os.entrySet().stream().map(entry -> LinkOsStatsDO.builder()
                .fullShortUrl(entry.getKey().day().url()).date(entry.getKey().day().date())
                .os(entry.getKey().value()).cnt(entry.getValue()).build()).toList());
        browserMapper.upsertBatch(a.browsers.entrySet().stream().map(entry -> LinkBrowserStatsDO.builder()
                .fullShortUrl(entry.getKey().day().url()).date(entry.getKey().day().date())
                .browser(entry.getKey().value()).cnt(entry.getValue()).build()).toList());
        deviceMapper.upsertBatch(a.devices.entrySet().stream().map(entry -> LinkDeviceStatsDO.builder()
                .fullShortUrl(entry.getKey().day().url()).date(entry.getKey().day().date())
                .device(entry.getKey().value()).cnt(entry.getValue()).build()).toList());
        networkMapper.upsertBatch(a.networks.entrySet().stream().map(entry -> LinkNetworkStatsDO.builder()
                .fullShortUrl(entry.getKey().day().url()).date(entry.getKey().day().date())
                .network(entry.getKey().value()).cnt(entry.getValue()).build()).toList());
        if (!a.locales.isEmpty()) {
            localeMapper.upsertBatch(a.locales.entrySet().stream().map(entry -> {
                Dimension<LocaleValue> key = entry.getKey();
                LocaleValue locale = key.value();
                return LinkLocaleStatsDO.builder().fullShortUrl(key.day().url()).date(key.day().date())
                        .country("中国").province(locale.province()).city(locale.city()).adcode(locale.adcode())
                        .cnt(entry.getValue()).build();
            }).toList());
        }
    }

    private record Day(String url, Date date) {
    }

    private record Hour(Day day, int hour, int weekday) {
    }

    private record IdentityDay(String value, LocalDate day) {
    }

    private record Dimension<T>(Day day, T value, String nullableRowId) {
    }

    private record Presence(int total, int daily) {
        static Presence from(LinkAccessStatsDO row) {
            return new Presence(row.getPv() == 0 ? 1 : 0, row.getUv() == 0 ? 1 : 0);
        }
    }

    private record LocaleValue(String province, String city, String adcode) {
        static LocaleValue from(JSONObject json) {
            if (json == null || !"10000".equals(json.getString("infocode"))) {
                return null;
            }
            String province = json.getString("province");
            boolean unknown = "[]".equals(province);
            return new LocaleValue(unknown ? "未知" : province, unknown ? "未知" : json.getString("city"),
                    unknown ? "未知" : json.getString("adcode"));
        }
    }

    private static final class Counts {
        int pv;
        int uv;
        int uip;

        void add(int uv, int uip) {
            pv++;
            this.uv += uv;
            this.uip += uip;
        }
    }

    private static final class Aggregates {
        final List<LinkAccessLogsMapper.VisitRow> logs = new ArrayList<>();
        final Map<Day, Counts> daily = new LinkedHashMap<>();
        final Map<Hour, Counts> hourly = new LinkedHashMap<>();
        final Map<Dimension<String>, Integer> os = new LinkedHashMap<>();
        final Map<Dimension<String>, Integer> browsers = new LinkedHashMap<>();
        final Map<Dimension<String>, Integer> devices = new LinkedHashMap<>();
        final Map<Dimension<String>, Integer> networks = new LinkedHashMap<>();
        final Map<Dimension<LocaleValue>, Integer> locales = new LinkedHashMap<>();
    }
}
