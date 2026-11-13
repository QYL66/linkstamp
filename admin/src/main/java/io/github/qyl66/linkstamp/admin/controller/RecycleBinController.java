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

package io.github.qyl66.linkstamp.admin.controller;

/* LinkStamp changes: renamed the Java package and removed promotional references where present. */

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.qyl66.linkstamp.admin.common.convention.result.Result;
import io.github.qyl66.linkstamp.admin.common.convention.result.Results;
import io.github.qyl66.linkstamp.admin.dto.req.RecycleBinRecoverReqDTO;
import io.github.qyl66.linkstamp.admin.dto.req.RecycleBinRemoveReqDTO;
import io.github.qyl66.linkstamp.admin.dto.req.RecycleBinSaveReqDTO;
import io.github.qyl66.linkstamp.admin.remote.ShortLinkActualRemoteService;
import io.github.qyl66.linkstamp.admin.remote.dto.req.ShortLinkRecycleBinPageReqDTO;
import io.github.qyl66.linkstamp.admin.remote.dto.resp.ShortLinkPageRespDTO;
import io.github.qyl66.linkstamp.admin.service.RecycleBinService;
import io.github.qyl66.linkstamp.admin.service.GroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 回收站管理控制层
 */
@RestController(value = "recycleBinControllerByAdmin")
@RequiredArgsConstructor
public class RecycleBinController {

    private final RecycleBinService recycleBinService;
    private final GroupService groupService;
    private final ShortLinkActualRemoteService shortLinkActualRemoteService;

    /**
     * 保存回收站
     */
    @PostMapping("/api/short-link/admin/v1/recycle-bin/save")
    public Result<Void> saveRecycleBin(@RequestBody RecycleBinSaveReqDTO requestParam) {
        groupService.requireOwnedGroup(requestParam.getGid());
        shortLinkActualRemoteService.saveRecycleBin(requestParam);
        return Results.success();
    }

    /**
     * 分页查询回收站短链接
     */
    @GetMapping("/api/short-link/admin/v1/recycle-bin/page")
    public Result<Page<ShortLinkPageRespDTO>> pageShortLink(ShortLinkRecycleBinPageReqDTO requestParam) {
        return recycleBinService.pageRecycleBinShortLink(requestParam);
    }

    /**
     * 恢复短链接
     */
    @PostMapping("/api/short-link/admin/v1/recycle-bin/recover")
    public Result<Void> recoverRecycleBin(@RequestBody RecycleBinRecoverReqDTO requestParam) {
        groupService.requireOwnedGroup(requestParam.getGid());
        shortLinkActualRemoteService.recoverRecycleBin(requestParam);
        return Results.success();
    }

    /**
     * 移除短链接
     */
    @PostMapping("/api/short-link/admin/v1/recycle-bin/remove")
    public Result<Void> removeRecycleBin(@RequestBody RecycleBinRemoveReqDTO requestParam) {
        groupService.requireOwnedGroup(requestParam.getGid());
        shortLinkActualRemoteService.removeRecycleBin(requestParam);
        return Results.success();
    }
}
