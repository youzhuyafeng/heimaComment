package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.BeanUtils;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class RefreshInterceptor implements HandlerInterceptor {

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    //对所有路径进行拦截，刷新token有效期
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("Authorization");
        Map<Object,Object> map = stringRedisTemplate.opsForHash().entries("login:token:"+token);
        if(map.isEmpty()){
            return true;
        }
        UserDTO user = BeanUtil.fillBeanWithMap(map,new UserDTO(),false);
        UserHolder.saveUser(user);
        stringRedisTemplate.expire("login:token:"+token,30, TimeUnit.MINUTES);
        return true;
    }
}
