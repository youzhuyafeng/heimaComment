package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session){

        if(RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式不正确！");
        }
        String code = RandomUtil.randomNumbers(6);
        stringRedisTemplate.opsForValue().set("login:code:"+phone, code,5 , TimeUnit.MINUTES);
        log.info("发送验证码:{}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session){
        if(RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            return Result.fail("手机号格式不正确！");
        }

        String code = stringRedisTemplate.opsForValue().get("login:code:"+loginForm.getPhone());

        if(!ObjectUtil.equals(code, loginForm.getCode())) {
            return Result.fail("验证码错误！");
        }

        User user = query().eq("phone", loginForm.getPhone()).one();
        if(user == null) {
            user = this.addUserByPhone(loginForm.getPhone());
        }
        String token = UUID.randomUUID().toString();
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user,userDTO);
        Map<String,String> map = new HashMap<>();
        map.put("id",userDTO.getId().toString());
        map.put("nickName",userDTO.getNickName());
        map.put("icon",userDTO.getIcon());
        stringRedisTemplate.opsForHash().putAll("login:token:"+token,map);
        stringRedisTemplate.expire("login:token:"+token,30, TimeUnit.MINUTES);
        return Result.ok(token);
    }

    private User addUserByPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomNumbers(6));
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        this.save(user);
        return user;
      }

}
