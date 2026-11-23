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
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.qyl66.linkstamp.project.dao.entity.ShortLinkDO;
import io.github.qyl66.linkstamp.project.dto.req.ShortLinkPageReqDTO;
import io.github.qyl66.linkstamp.project.dto.req.ShortLinkRecycleBinPageReqDTO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 短链接持久层
 */
public interface ShortLinkMapper extends BaseMapper<ShortLinkDO> {

    @Select("""
            SELECT id FROM t_link WHERE gid = #{gid} AND full_short_url = #{url} AND del_time = 0 FOR UPDATE
            """)
    Long lockStatsRow(@Param("gid") String gid, @Param("url") String url);

    @Select("""
            SELECT id FROM t_link WHERE gid = #{gid} AND full_short_url = #{url}
            ORDER BY del_time DESC LIMIT 1 FOR UPDATE
            """)
    Long lockLastArchivedStatsRow(@Param("gid") String gid, @Param("url") String url);

    /**
     * 短链接访问统计自增
     */
    void incrementStats(@Param("gid") String gid,
                        @Param("id") Long id,
                        @Param("totalPv") Integer totalPv,
                        @Param("totalUv") Integer totalUv,
                        @Param("totalUip") Integer totalUip);

    /**
     * 分页统计短链接
     */
    IPage<ShortLinkDO> pageLink(ShortLinkPageReqDTO requestParam);

    /**
     * 分页统计回收站短链接
     */
    IPage<ShortLinkDO> pageRecycleBinLink(ShortLinkRecycleBinPageReqDTO requestParam);
}
