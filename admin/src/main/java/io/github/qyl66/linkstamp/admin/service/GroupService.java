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

package io.github.qyl66.linkstamp.admin.service;

/* LinkStamp changes: expose a group ownership check for management operations. */

import com.baomidou.mybatisplus.spring.service.IService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.qyl66.linkstamp.admin.common.biz.user.UserContext;
import io.github.qyl66.linkstamp.admin.common.convention.exception.ClientException;
import io.github.qyl66.linkstamp.admin.dao.entity.GroupDO;
import io.github.qyl66.linkstamp.admin.dto.req.ShortLinkGroupSortReqDTO;
import io.github.qyl66.linkstamp.admin.dto.req.ShortLinkGroupUpdateReqDTO;
import io.github.qyl66.linkstamp.admin.dto.resp.ShortLinkGroupRespDTO;

import java.util.List;

/**
 * 短链接分组接口层
 */
public interface GroupService extends IService<GroupDO> {

    default void requireOwnedGroup(String gid) {
        String username = UserContext.getUsername();
        if (username == null || gid == null || count(Wrappers.lambdaQuery(GroupDO.class)
                .eq(GroupDO::getUsername, username)
                .eq(GroupDO::getGid, gid)
                .eq(GroupDO::getDelFlag, 0)) == 0) {
            throw new ClientException("分组不存在或无权使用");
        }
    }

    /**
     * 新增短链接分组
     *
     * @param groupName 短链接分组名
     */
    void saveGroup(String groupName);

    /**
     * 新增短链接分组
     *
     * @param username  用户名
     * @param groupName 短链接分组名
     */
    void saveGroup(String username, String groupName);

    /**
     * 查询用户短链接分组集合
     *
     * @return 用户短链接分组集合
     */
    List<ShortLinkGroupRespDTO> listGroup();

    /**
     * 修改短链接分组
     *
     * @param requestParam 修改链接分组参数
     */
    void updateGroup(ShortLinkGroupUpdateReqDTO requestParam);

    /**
     * 删除短链接分组
     *
     * @param gid 短链接分组标识
     */
    void deleteGroup(String gid);

    /**
     * 短链接分组排序
     *
     * @param requestParam 短链接分组排序参数
     */
    void sortGroup(List<ShortLinkGroupSortReqDTO> requestParam);
}
