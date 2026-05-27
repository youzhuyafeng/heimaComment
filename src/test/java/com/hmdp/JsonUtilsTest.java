package com.hmdp;

import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.entity.Shop;
import com.hmdp.entity.User;
import org.junit.Test;

import javax.annotation.Resource;

public class JsonUtilsTest {


    @Test
    public void testJsonUtils() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        String s = mapper.writeValueAsString(new Shop());
        User shop = JSONUtil.toBean(s,User.class);
        System.out.println(shop.toString());
    }
}
