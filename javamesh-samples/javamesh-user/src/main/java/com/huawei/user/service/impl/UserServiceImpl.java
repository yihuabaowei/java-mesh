package com.huawei.user.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.PageHelper;
import com.huawei.user.common.api.CommonResult;
import com.huawei.user.common.constant.FailedInfo;
import com.huawei.user.common.util.PageUtil;
import com.huawei.user.common.util.UserFeignClient;
import com.huawei.user.entity.UserEntity;
import com.huawei.user.mapper.UserMapper;
import com.huawei.user.service.UserService;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class UserServiceImpl implements UserService {
    private static final String SUCCESS = "success";

    private static final String ROLE_ADMIN = "管理员";

    private static final String ROLE_OPERATOR = "操作员";

    private static final String ROLE_APPROVER = "审核员";

    private static final String HEALTHY = "正常";

    private static final String EXPIRED = "失效";
    private static final int PASSWORD_LENGTH = 10;

    private static final String PASSWORD_DIRECTORY = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";


    @Resource
    UserFeignClient userFeignClient;

    @Autowired
    UserMapper mapper;

    @Override
    public JSONObject login(String username, String password, String nativeLanguage, String userTimezone) {
        return userFeignClient.login(username, password, nativeLanguage, userTimezone);
    }

    @Override
    public UserEntity getUserInfo(HttpServletRequest request) {
        return (UserEntity) request.getSession().getAttribute("userInfo");
    }

    @Override
    public String logout() {
        return userFeignClient.logout();
    }

    @Override
    public String changePwd(HttpServletRequest request, Map<String, String> param) {
        String oldPassword = param.get("old_password");
        String password = param.get("password");
        String confirm = param.get("confirm");
        if (oldPassword.equals(password)) {
            return FailedInfo.OLD_NEW_PASSWORD_EQUALS;
        } else if (!password.equals(confirm)) {
            return FailedInfo.CONFIRM_PASSWORD_ERROR;
        } else {
            UserEntity userInfo = (UserEntity) request.getSession().getAttribute("userInfo");
            String userName = userInfo.getUserName();

            // 原密码加密
            String encodeOldPassword = encodePassword(userName, oldPassword);

            // 新密码加密
            String encodeNewPassword = encodePassword(userName, password);
            UserEntity user = mapper.selectUserByName(userName);

            // 原密码错误返回信息
            if (!user.getPassword().equals(encodeOldPassword)) {
                return FailedInfo.PASSWORD_ERROR;
            }

            // 修改密码
            int count = mapper.changePassword(userName, encodeNewPassword);
            if (count != 1) {
                return FailedInfo.CHANGE_PASSWORD_FAILED;
            }
            return SUCCESS;
        }
    }

    @Override
    public String register(UserEntity entity) {
        String userName = entity.getUserName();
        int count = mapper.countByName(userName);
        if (count > 0) {
            return FailedInfo.USERNAME_EXISTS;
        }
        String password = encodePassword(userName, entity.getPassword());
        entity.setPassword(password);
        switch (entity.getRole()) {
            case ROLE_OPERATOR:
                entity.setRole("OPERATOR");
                break;
            case ROLE_APPROVER:
                entity.setRole("APPROVER");
        }
        Timestamp timestamp = getTimestamp();
        entity.setCreateTime(timestamp);
        entity.setUpdateTime(timestamp);
        entity.setEnabled("T");
        count = mapper.insertUser(entity);
        if (count == 1) {
            return SUCCESS;
        }
        return FailedInfo.REGISTER_FAILED;
    }

    @Override
    public CommonResult listUser(String nickName, String userName, String role, String status, int pageSize, int current, String sorter, String order) {
        UserEntity user = new UserEntity();
        user.setNickName(nickName);
        user.setUserName(userName);
        if (StringUtils.isNotBlank(role)) {
            switch (role) {
                case ROLE_OPERATOR:
                    user.setRole("OPERATOR");
                    break;
                case ROLE_APPROVER:
                    user.setRole("APPROVER");
                    break;
                case ROLE_ADMIN:
                    user.setRole("ADMIN");
            }
        }
        if (StringUtils.isNotBlank(status)) {
            switch (status) {
                case HEALTHY:
                    user.setEnabled("T");
                    break;
                case EXPIRED:
                    user.setEnabled("F");
            }
        }
        String mSorter = sorter.equals("update_time") ? "last_modified_date" : sorter;
        if (order.equals("ascend")) {
            PageHelper.orderBy(mSorter + System.lineSeparator() + "ASC");
        } else {
            PageHelper.orderBy(mSorter + System.lineSeparator() + "DESC");
        }
        List<UserEntity> users = mapper.listUser(user);
        return CommonResult.success(PageUtil.startPage(users, current, pageSize), users.size());
    }

    @Override
    public String suspend(HttpServletRequest request, String[] usernames) {
        UserEntity user = (UserEntity) request.getSession().getAttribute("userInfo");
        String userName = user.getUserName();
        for (String name : usernames) {
            if (name.equals(userName) || name.equals("admin")) {
                return FailedInfo.SUSPEND_NOT_SELF_OR_ADMIN;
            }
        }
        int count = mapper.updateEnableByName(usernames, "F");
        int length = usernames.length;
        if (count == length) {
            return SUCCESS;
        } else if (count == 0) {
            return FailedInfo.SUSPEND_FAIL;
        } else {
            return FailedInfo.SUSPEND_NOT_ALL_SUCCESS;
        }

    }

    @Override
    public String enable(String[] usernames) {
        int count = mapper.updateEnableByName(usernames, "T");
        int length = usernames.length;
        if (count == length) {
            return SUCCESS;
        } else if (count == 0) {
            return FailedInfo.ENABLE_FAIL;
        } else {
            return FailedInfo.ENABLE_NOT_ALL_SUCCESS;
        }
    }

    @Override
    public CommonResult addUser(UserEntity user) {
        String userName = user.getUserName();
        int count = mapper.countByName(userName);
        if (count > 0) {
            return CommonResult.failed(FailedInfo.USERNAME_EXISTS);
        }
        String password = generatePassword();
        String encodePassword = encodePassword(userName, password);
        user.setPassword(encodePassword);
        switch (user.getRole()) {
            case ROLE_OPERATOR:
                user.setRole("OPERATOR");
                break;
            case ROLE_APPROVER:
                user.setRole("APPROVER");
                break;
            case ROLE_ADMIN:
                user.setRole("ADMIN");
        }
        Timestamp timestamp = getTimestamp();
        user.setCreateTime(timestamp);
        user.setUpdateTime(timestamp);
        user.setEnabled("T");
        count = mapper.insertUser(user);
        user.setPassword(password);
        if (count == 1) {
            return CommonResult.success(user);
        } else {
            return CommonResult.failed(FailedInfo.ADD_USER_FAIL);
        }
    }

    @Override
    public CommonResult resetPwd(UserEntity user) {
        String userName = user.getUserName();
        String password = generatePassword();
        user.setPassword(password);
        int count = mapper.updatePwdByName(userName, encodePassword(userName, password));
        if (count == 1) {
            return CommonResult.success(user);
        } else {
            return CommonResult.failed(FailedInfo.RESET_PWD_FAIL);
        }
    }

    @Override
    public String updateUser(UserEntity user) {
        switch (user.getRole()) {
            case ROLE_OPERATOR:
                user.setRole("OPERATOR");
                break;
            case ROLE_APPROVER:
                user.setRole("APPROVER");
                break;
            case ROLE_ADMIN:
                user.setRole("ADMIN");
        }
        int count = mapper.updateUser(user);
        if (count == 1) {
            return SUCCESS;
        }
        return FailedInfo.UPDATE_USER_FAIL;
    }

    private String generatePassword() {
        char chars[] = PASSWORD_DIRECTORY.toCharArray();
        StringBuilder sb = new StringBuilder();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            sb.append(chars[r.nextInt(chars.length)]);
        }
        return sb.toString();
    }

    private String encodePassword(String userName, String password) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("userId", userName);
        jsonObject.put("password", password);
        String result = userFeignClient.encodePassword(jsonObject);
        if (result.startsWith("\"") && result.endsWith("\"")) {
            result = result.substring(1, result.length() - 1);
        }
        return result;
    }

    private Timestamp getTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String nowDate = sdf.format(new Date());
        return Timestamp.valueOf(nowDate);
    }
}
