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

package io.github.qyl66.linkstamp.project.config;

/* LinkStamp changes: make the single-link creation limit configurable and validate its value. */

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 初始化限流配置
 */
@Component
public class SentinelRuleConfig implements InitializingBean {

    @Value("${short-link.creation-qps:20}")
    private int creationQps;

    @Override
    public void afterPropertiesSet() {
        if (creationQps < 1) {
            throw new IllegalArgumentException("short-link.creation-qps must be positive");
        }
        FlowRule createRule = new FlowRule("create_short-link");
        createRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        createRule.setCount(creationQps);
        FlowRuleManager.loadRules(List.of(createRule));
    }
}
