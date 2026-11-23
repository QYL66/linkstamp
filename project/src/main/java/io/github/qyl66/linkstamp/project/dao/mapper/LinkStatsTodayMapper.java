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

package io.github.qyl66.linkstamp.project.dao.mapper;

/* LinkStamp changes: moved the Java package to io.github.qyl66.linkstamp. */

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.qyl66.linkstamp.project.dao.entity.LinkStatsTodayDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 短链接今日统计持久层
 */
public interface LinkStatsTodayMapper extends BaseMapper<LinkStatsTodayDO> {

    /**
     * 记录今日统计监控数据
     */
    @Insert("""
            <script>
            INSERT INTO t_link_stats_today (full_short_url, date, today_uv, today_pv, today_uip, create_time, update_time, del_flag)
            VALUES
            <foreach collection="rows" item="row" separator=",">
              (#{row.fullShortUrl}, #{row.date}, #{row.todayUv}, #{row.todayPv}, #{row.todayUip}, NOW(), NOW(), 0)
            </foreach>
            ON DUPLICATE KEY UPDATE today_uv = today_uv + VALUES(today_uv), today_pv = today_pv + VALUES(today_pv), today_uip = today_uip + VALUES(today_uip)
            </script>
            """)
    void upsertBatch(@Param("rows") List<LinkStatsTodayDO> rows);
}
