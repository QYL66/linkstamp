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

package io.github.qyl66.linkstamp.admin.service.impl;

/* LinkStamp changes: hash passwords and store the fields needed by the gateway in each Redis session. */

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.UUID;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import io.github.qyl66.linkstamp.admin.common.biz.user.UserContext;
import io.github.qyl66.linkstamp.admin.common.convention.exception.ClientException;
import io.github.qyl66.linkstamp.admin.common.convention.exception.ServiceException;
import io.github.qyl66.linkstamp.admin.common.enums.UserErrorCodeEnum;
import io.github.qyl66.linkstamp.admin.dao.entity.UserDO;
import io.github.qyl66.linkstamp.admin.dao.mapper.UserMapper;
import io.github.qyl66.linkstamp.admin.dto.req.UserLoginReqDTO;
import io.github.qyl66.linkstamp.admin.dto.req.UserRegisterReqDTO;
import io.github.qyl66.linkstamp.admin.dto.req.UserUpdateReqDTO;
import io.github.qyl66.linkstamp.admin.dto.resp.UserLoginRespDTO;
import io.github.qyl66.linkstamp.admin.dto.resp.UserRespDTO;
import io.github.qyl66.linkstamp.admin.service.GroupService;
import io.github.qyl66.linkstamp.admin.service.UserService;
import io.github.qyl66.linkstamp.admin.toolkit.PasswordHash;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static io.github.qyl66.linkstamp.admin.common.constant.RedisCacheConstant.LOCK_USER_REGISTER_KEY;
import static io.github.qyl66.linkstamp.admin.common.constant.RedisCacheConstant.USER_LOGIN_KEY;
import static io.github.qyl66.linkstamp.admin.common.enums.UserErrorCodeEnum.USER_EXIST;
import static io.github.qyl66.linkstamp.admin.common.enums.UserErrorCodeEnum.USER_NAME_EXIST;
import static io.github.qyl66.linkstamp.admin.common.enums.UserErrorCodeEnum.USER_SAVE_ERROR;

/**
 * 用户接口实现层
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, UserDO> implements UserService {

    private final RBloomFilter<String> userRegisterCachePenetrationBloomFilter;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final GroupService groupService;

    @Override
    public UserRespDTO getUserByUsername(String username) {
        if (!Objects.equals(username, UserContext.getUsername())) {
            throw new ClientException("无权查看该账号资料");
        }
        LambdaQueryWrapper<UserDO> queryWrapper = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, username);
        UserDO userDO = baseMapper.selectOne(queryWrapper);
        if (userDO == null) {
            throw new ServiceException(UserErrorCodeEnum.USER_NULL);
        }
        UserRespDTO result = new UserRespDTO();
        BeanUtils.copyProperties(userDO, result);
        return result;
    }

    @Override
    public Boolean hasUsername(String username) {
        return !userRegisterCachePenetrationBloomFilter.contains(username);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void register(UserRegisterReqDTO requestParam) {
        String password = encodePassword(requestParam.getPassword());
        if (!hasUsername(requestParam.getUsername())) {
            throw new ClientException(USER_NAME_EXIST);
        }
        RLock lock = redissonClient.getLock(LOCK_USER_REGISTER_KEY + requestParam.getUsername());
        if (!lock.tryLock()) {
            throw new ClientException(USER_NAME_EXIST);
        }
        try {
            UserDO user = BeanUtil.toBean(requestParam, UserDO.class);
            user.setPassword(password);
            int inserted = baseMapper.insert(user);
            if (inserted < 1) {
                throw new ClientException(USER_SAVE_ERROR);
            }
            groupService.saveGroup(requestParam.getUsername(), "默认分组");
            userRegisterCachePenetrationBloomFilter.add(requestParam.getUsername());
        } catch (DuplicateKeyException ex) {
            throw new ClientException(USER_EXIST);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void update(UserUpdateReqDTO requestParam) {
        if (!Objects.equals(requestParam.getUsername(), UserContext.getUsername())) {
            throw new ClientException("当前登录用户修改请求异常");
        }
        LambdaUpdateWrapper<UserDO> updateWrapper = Wrappers.lambdaUpdate(UserDO.class)
                .eq(UserDO::getUsername, requestParam.getUsername());
        UserDO user = BeanUtil.toBean(requestParam, UserDO.class);
        String password = requestParam.getPassword();
        user.setPassword(password == null || password.isEmpty() ? null : encodePassword(password));
        baseMapper.update(user, updateWrapper);
    }

    @Override
    public UserLoginRespDTO login(UserLoginReqDTO requestParam) {
        LambdaQueryWrapper<UserDO> queryWrapper = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, requestParam.getUsername())
                .eq(UserDO::getDelFlag, 0);
        UserDO userDO = baseMapper.selectOne(queryWrapper);
        if (userDO == null || !PasswordHash.matches(requestParam.getPassword(), userDO.getPassword())) {
            throw new ClientException("用户不存在");
        }
        if (!PasswordHash.isEncoded(userDO.getPassword())) {
            baseMapper.update(null, Wrappers.lambdaUpdate(UserDO.class)
                    .eq(UserDO::getUsername, userDO.getUsername())
                    .eq(UserDO::getPassword, userDO.getPassword())
                    .set(UserDO::getPassword, PasswordHash.encode(requestParam.getPassword())));
        }
        String session = JSON.toJSONString(Map.of("id", userDO.getId(), "realName", Objects.toString(userDO.getRealName(), "")));
        Map<Object, Object> hasLoginMap = stringRedisTemplate.opsForHash().entries(USER_LOGIN_KEY + requestParam.getUsername());
        if (CollUtil.isNotEmpty(hasLoginMap)) {
            stringRedisTemplate.expire(USER_LOGIN_KEY + requestParam.getUsername(), 30L, TimeUnit.MINUTES);
            String token = hasLoginMap.keySet().stream()
                    .findFirst()
                    .map(Object::toString)
                    .orElseThrow(() -> new ClientException("用户登录错误"));
            stringRedisTemplate.opsForHash().put(USER_LOGIN_KEY + requestParam.getUsername(), token, session);
            return new UserLoginRespDTO(token);
        }
        /**
         * Hash
         * Key：login_用户名
         * Value：
         *  Key：token标识
         *  Val：JSON 字符串（用户信息）
         */
        String uuid = UUID.randomUUID().toString();
        stringRedisTemplate.opsForHash().put(USER_LOGIN_KEY + requestParam.getUsername(), uuid, session);
        stringRedisTemplate.expire(USER_LOGIN_KEY + requestParam.getUsername(), 30L, TimeUnit.MINUTES);
        return new UserLoginRespDTO(uuid);
    }

    @Override
    public Boolean checkLogin(String username, String token) {
        return stringRedisTemplate.opsForHash().get(USER_LOGIN_KEY + username, token) != null;
    }

    private String encodePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 128) {
            throw new ClientException("密码长度须为 8–128 个字符");
        }
        return PasswordHash.encode(password);
    }

    @Override
    public void logout(String username, String token) {
        if (checkLogin(username, token)) {
            stringRedisTemplate.delete(USER_LOGIN_KEY + username);
            return;
        }
        throw new ClientException("用户Token不存在或用户未登录");
    }
}
