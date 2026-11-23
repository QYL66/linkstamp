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
import io.github.qyl66.linkstamp.project.dao.entity.LinkDeviceStatsDO;
import io.github.qyl66.linkstamp.project.dto.req.ShortLinkGroupStatsReqDTO;
import io.github.qyl66.linkstamp.project.dto.req.ShortLinkStatsReqDTO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 访问设备监控持久层
 */
public interface LinkDeviceStatsMapper extends BaseMapper<LinkDeviceStatsDO> {

    /**
     * 记录访问设备监控数据
     */
    @Insert("""
            <script>
            INSERT INTO t_link_device_stats (full_short_url, date, cnt, device, create_time, update_time, del_flag)
            VALUES
            <foreach collection="rows" item="row" separator=",">
              (#{row.fullShortUrl}, #{row.date}, #{row.cnt}, #{row.device}, NOW(), NOW(), 0)
            </foreach>
            ON DUPLICATE KEY UPDATE cnt = cnt + VALUES(cnt)
            </script>
            """)
    void upsertBatch(@Param("rows") List<LinkDeviceStatsDO> rows);

    /**
     * 根据短链接获取指定日期内访问设备监控数据
     */
    @Select("SELECT " +
            "    tlds.device, " +
            "    SUM(tlds.cnt) AS cnt " +
            "FROM " +
            "    t_link tl INNER JOIN " +
            "    t_link_device_stats tlds ON tl.full_short_url = tlds.full_short_url " +
            "WHERE " +
            "    tlds.full_short_url = #{param.fullShortUrl} " +
            "    AND tl.gid = #{param.gid} " +
            "    AND tl.del_flag = '0' " +
            "    AND tl.enable_status = #{param.enableStatus} " +
            "    AND tlds.date BETWEEN #{param.startDate} and #{param.endDate} " +
            "GROUP BY " +
            "    tlds.full_short_url, tl.gid, tlds.device;")
    List<LinkDeviceStatsDO> listDeviceStatsByShortLink(@Param("param") ShortLinkStatsReqDTO requestParam);

    /**
     * 根据分组获取指定日期内访问设备监控数据
     */
    @Select("SELECT " +
            "    tlds.device, " +
            "    SUM(tlds.cnt) AS cnt " +
            "FROM " +
            "    t_link tl INNER JOIN " +
            "    t_link_device_stats tlds ON tl.full_short_url = tlds.full_short_url " +
            "WHERE " +
            "    tl.gid = #{param.gid} " +
            "    AND tl.del_flag = '0' " +
            "    AND tl.enable_status = '0' " +
            "    AND tlds.date BETWEEN #{param.startDate} and #{param.endDate} " +
            "GROUP BY " +
            "    tl.gid, tlds.device;")
    List<LinkDeviceStatsDO> listDeviceStatsByGroup(@Param("param") ShortLinkGroupStatsReqDTO requestParam);
}
