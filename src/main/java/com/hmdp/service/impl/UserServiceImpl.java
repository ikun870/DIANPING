package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import jakarta.servlet.http.HttpSession;
import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import jakarta.annotation.Resource;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        //2.生成验证码
        String code = RandomUtil.randomNumbers(6);
        //3.保存验证码到session
        //注意：session一般保存在服务器端缓存中，而不是客户端
        //session.setAttribute("code", code);
        //4.将验证码保存到Redis set key value 过期时间
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //5.发送验证码
        log.info("发送验证码成功，验证码为：{}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session, String token) {
        // 如果前端携带了token，先检查Redis中是否存在
        if (StrUtil.isNotBlank(token)) {
            String redisKey = RedisConstants.LOGIN_USER_KEY + token;
            Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(redisKey);
            if (!userMap.isEmpty()) {
                // token有效，直接返回
                return Result.ok(token);
            }
        }
        // 原有登录逻辑
        //1.校验手机号
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            return Result.fail("手机号格式错误");
        }
        //2.从session中获取验证码
        //Object code = session.getAttribute("code");
        //从Redis中获取验证码
        String code = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + loginForm.getPhone());
        if (code == null) {
            return Result.fail("验证码不存在");
        }
        if (!code.equals(loginForm.getCode())) {
            return Result.fail("验证码错误");
        }
        //3.查询用户
        User user = query().eq("phone", loginForm.getPhone()).one();
        if (user == null) {
            //4.创建新用户
            user = createUserWithPhone(loginForm.getPhone());
        }
        //5.校验密码
        if (loginForm.getPassword()!=null&&!loginForm.getPassword().equals(user.getPassword())) {
            return Result.fail("密码错误");
        }
        //6.生成token
        String newToken = UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        
        //8.保存用户信息到Redis
        Map<String, Object> map = BeanUtil.beanToMap(userDTO);
        Map<String, String> stringMap = map.entrySet().stream()
            .collect(Collectors.toMap(Entry::getKey, e -> e.getValue() != null ? e.getValue().toString() : null));
        stringRedisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY + newToken, stringMap);
        
        //10.设置过期时间
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + newToken, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return Result.ok(newToken);
    }

    private User createUserWithPhone(String phone) {
        //1.创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10)); 
       
        //2.保存用户
        save(user);
        return user;
    }
}
