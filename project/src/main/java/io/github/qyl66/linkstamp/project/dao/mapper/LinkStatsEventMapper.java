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

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Records the Stream entry ID in the same transaction as its statistics. */
public interface LinkStatsEventMapper {

    @Select("SELECT 1 FROM t_link_stats_event LIMIT 1")
    Integer checkTable();

    @Insert("INSERT IGNORE INTO t_link_stats_event (stream_id) VALUES (#{id})")
    int claimEvent(@Param("id") String id);
}
