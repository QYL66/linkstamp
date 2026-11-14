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

package io.github.qyl66.linkstamp.project.service.impl;

/* LinkStamp changes: derive a description from the URL host without requesting the target page. */

import io.github.qyl66.linkstamp.project.service.UrlTitleService;
import io.github.qyl66.linkstamp.project.toolkit.LinkUtil;
import org.springframework.stereotype.Service;

/**
 * URL 标题接口实现层
 */
@Service
public class UrlTitleServiceImpl implements UrlTitleService {

    @Override
    public String getTitleByUrl(String url) {
        return LinkUtil.requireHttpHost(url);
    }
}
