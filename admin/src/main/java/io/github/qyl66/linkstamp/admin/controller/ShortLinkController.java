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
import io.github.qyl66.linkstamp.admin.remote.ShortLinkActualRemoteService;
import io.github.qyl66.linkstamp.admin.remote.dto.req.ShortLinkBatchCreateReqDTO;
import io.github.qyl66.linkstamp.admin.remote.dto.req.ShortLinkCreateReqDTO;
import io.github.qyl66.linkstamp.admin.remote.dto.req.ShortLinkPageReqDTO;
import io.github.qyl66.linkstamp.admin.remote.dto.req.ShortLinkUpdateReqDTO;
import io.github.qyl66.linkstamp.admin.remote.dto.resp.ShortLinkBaseInfoRespDTO;
import io.github.qyl66.linkstamp.admin.remote.dto.resp.ShortLinkBatchCreateRespDTO;
import io.github.qyl66.linkstamp.admin.remote.dto.resp.ShortLinkCreateRespDTO;
import io.github.qyl66.linkstamp.admin.remote.dto.resp.ShortLinkPageRespDTO;
import io.github.qyl66.linkstamp.admin.toolkit.EasyExcelWebUtil;
import io.github.qyl66.linkstamp.admin.service.GroupService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 短链接后管控制层
 */
@RestController(value = "shortLinkControllerByAdmin")
@RequiredArgsConstructor
public class ShortLinkController {

    private final ShortLinkActualRemoteService shortLinkActualRemoteService;
    private final GroupService groupService;

    /**
     * 创建短链接
     */
    @PostMapping("/api/short-link/admin/v1/create")
    public Result<ShortLinkCreateRespDTO> createShortLink(@RequestBody ShortLinkCreateReqDTO requestParam) {
        groupService.requireOwnedGroup(requestParam.getGid());
        return shortLinkActualRemoteService.createShortLink(requestParam);
    }

    /**
     * 批量创建短链接
     */
    @SneakyThrows
    @PostMapping("/api/short-link/admin/v1/create/batch")
    public void batchCreateShortLink(@RequestBody ShortLinkBatchCreateReqDTO requestParam, HttpServletResponse response) {
        groupService.requireOwnedGroup(requestParam.getGid());
        Result<ShortLinkBatchCreateRespDTO> shortLinkBatchCreateRespDTOResult = shortLinkActualRemoteService.batchCreateShortLink(requestParam);
        if (shortLinkBatchCreateRespDTOResult.isSuccess()) {
            List<ShortLinkBaseInfoRespDTO> baseLinkInfos = shortLinkBatchCreateRespDTOResult.getData().getBaseLinkInfos();
            EasyExcelWebUtil.write(response, "链签-批量创建短链接", ShortLinkBaseInfoRespDTO.class, baseLinkInfos);
        }
    }

    /**
     * 修改短链接
     */
    @PostMapping("/api/short-link/admin/v1/update")
    public Result<Void> updateShortLink(@RequestBody ShortLinkUpdateReqDTO requestParam) {
        groupService.requireOwnedGroup(requestParam.getOriginGid());
        if (!requestParam.getOriginGid().equals(requestParam.getGid())) {
            groupService.requireOwnedGroup(requestParam.getGid());
        }
        shortLinkActualRemoteService.updateShortLink(requestParam);
        return Results.success();
    }

    /**
     * 分页查询短链接
     */
    @GetMapping("/api/short-link/admin/v1/page")
    public Result<Page<ShortLinkPageRespDTO>> pageShortLink(ShortLinkPageReqDTO requestParam) {
        groupService.requireOwnedGroup(requestParam.getGid());
        return shortLinkActualRemoteService.pageShortLink(requestParam.getGid(), requestParam.getOrderTag(), requestParam.getCurrent(), requestParam.getSize());
    }
}
