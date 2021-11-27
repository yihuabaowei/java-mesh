package com.huawei.user.service;

import com.alibaba.fastjson.JSONObject;
import com.huawei.user.common.api.CommonResult;
import com.huawei.user.entity.UserEntity;
import org.springframework.security.core.userdetails.UserDetails;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.Map;

public interface UserService {

    JSONObject login(String username, String password, String nativeLanguage, String userTimezone);

    /*CommonResult getUserInfo(HttpServletRequest request);*/

    CommonResult getUserInfo(HttpServletResponse response,String userName);

    /*String logout();*/

    String changePwd(String userName,Map<String, String> param);

    String register(UserEntity entity);

    CommonResult listUser(String nickName, String userName, String role, String status, int pageSize, int current, String sorter, String order);

    String suspend(String userName, String[] usernames);

    String enable(String[] usernames);

    CommonResult addUser(UserEntity user);

    CommonResult resetPwd(UserEntity user);

    String updateUser(UserEntity user);

    UserDetails loadUserByUsername(String username);

    String login(String username, String password);
}
