package com.geekyan.controller;

import com.geekyan.entity.UserSettings;
import com.geekyan.service.IUserSettingsService;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.framework.web.service.SysLoginService;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.framework.security.context.AuthenticationContextHolder;
import com.ruoyi.system.service.ISysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/geekyan/account")
public class VisitorController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(VisitorController.class);

    @Autowired
    private ISysUserService userService;

    @Autowired
    private SysLoginService loginService;

    @Autowired
    private IUserSettingsService userSettingsService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private AuthenticationManager authenticationManager;

    private String authenticateAndCreateToken(String username, String password) {
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(username,
                password);
        AuthenticationContextHolder.setContext(authenticationToken);
        try {
            Authentication authentication = authenticationManager.authenticate(authenticationToken);
            LoginUser loginUser = (LoginUser) authentication.getPrincipal();
            return tokenService.createToken(loginUser);
        } finally {
            AuthenticationContextHolder.clearContext();
        }
    }

    @PostMapping("/logout")
    public AjaxResult logout() {
        try {
            com.ruoyi.common.core.domain.model.LoginUser loginUser = com.ruoyi.common.utils.SecurityUtils
                    .getLoginUser();
            if (loginUser != null) {
                tokenService.delLoginUser(loginUser.getToken());
            }
        } catch (Exception e) {
            log.warn("退出登录时删除token失败: {}", e.getMessage());
        }
        return AjaxResult.success("退出成功");
    }

    @Anonymous
    @PostMapping("/visitor-login")
    public AjaxResult visitorLogin(@RequestBody Map<String, String> loginInfo) {
        String username = loginInfo.get("username");
        String password = loginInfo.get("password");

        if (StringUtils.isEmpty(username) || StringUtils.isEmpty(password)) {
            return AjaxResult.error("用户名和密码不能为空");
        }

        if (username.length() < 2 || username.length() > 20) {
            return AjaxResult.error("用户名长度必须在2到20个字符之间");
        }

        if (password.length() < 5 || password.length() > 20) {
            return AjaxResult.error("密码长度必须在5到20个字符之间");
        }

        boolean isNewUser = false;
        SysUser sysUser = new SysUser();
        sysUser.setUserName(username);
        if (userService.checkUserNameUnique(sysUser)) {
            isNewUser = true;
            sysUser.setNickName(username);
            sysUser.setPassword(SecurityUtils.encryptPassword(password));
            sysUser.setRoleIds(new Long[] { 2L });
            boolean regResult = userService.registerUser(sysUser);
            if (!regResult) {
                return AjaxResult.error("注册失败，请稍后重试");
            }
            userService.insertUserAuth(sysUser.getUserId(), sysUser.getRoleIds());
        }

        String token;
        try {
            token = authenticateAndCreateToken(username, password);
        } catch (Exception e) {
            return AjaxResult.error("登录失败: " + e.getMessage());
        }

        SysUser user = userService.selectUserByUserName(username);
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("account_id", user.getUserId());
        data.put("username", username);
        data.put("isNewUser", isNewUser);

        return AjaxResult.success(data);
    }

    @Anonymous
    @PostMapping("/login")
    public AjaxResult login(@RequestBody Map<String, String> loginInfo) {
        String username = loginInfo.get("username");
        String password = loginInfo.get("password");

        if (StringUtils.isEmpty(username) || StringUtils.isEmpty(password)) {
            return AjaxResult.error("用户名和密码不能为空");
        }

        SysUser sysUser = new SysUser();
        sysUser.setUserName(username);
        if (userService.checkUserNameUnique(sysUser)) {
            return AjaxResult.error("用户不存在，请先注册");
        }

        String token;
        try {
            token = authenticateAndCreateToken(username, password);
        } catch (Exception e) {
            return AjaxResult.error("用户名或密码错误");
        }

        SysUser user = userService.selectUserByUserName(username);
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("account_id", user.getUserId());
        data.put("username", username);
        data.put("isNewUser", false);

        return AjaxResult.success(data);
    }

    @Anonymous
    @PostMapping("/register")
    public AjaxResult register(@RequestBody Map<String, String> registerInfo) {
        String username = registerInfo.get("username");
        String password = registerInfo.get("password");

        if (StringUtils.isEmpty(username) || StringUtils.isEmpty(password)) {
            return AjaxResult.error("用户名和密码不能为空");
        }

        if (username.length() < 2 || username.length() > 20) {
            return AjaxResult.error("用户名长度必须在2到20个字符之间");
        }

        if (password.length() < 5 || password.length() > 20) {
            return AjaxResult.error("密码长度必须在5到20个字符之间");
        }

        SysUser sysUser = new SysUser();
        sysUser.setUserName(username);
        if (!userService.checkUserNameUnique(sysUser)) {
            return AjaxResult.error("用户名已存在，请直接登录");
        }

        sysUser.setNickName(username);
        sysUser.setPassword(SecurityUtils.encryptPassword(password));
        sysUser.setRoleIds(new Long[] { 2L });
        boolean regResult = userService.registerUser(sysUser);
        if (!regResult) {
            return AjaxResult.error("注册失败，请稍后重试");
        }
        userService.insertUserAuth(sysUser.getUserId(), sysUser.getRoleIds());

        String token;
        try {
            token = authenticateAndCreateToken(username, password);
        } catch (Exception e) {
            return AjaxResult.error("注册成功但自动登录失败，请手动登录");
        }

        SysUser user = userService.selectUserByUserName(username);
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("account_id", user.getUserId());
        data.put("username", username);
        data.put("isNewUser", true);

        return AjaxResult.success(data);
    }

    @GetMapping("/info")
    public AjaxResult getInfo() {
        Long userId = getUserId();
        SysUser user = userService.selectUserById(userId);
        UserSettings settings = userSettingsService.getOrCreateByUserId(userId);

        Map<String, Object> data = new HashMap<>();
        data.put("account_id", userId);
        data.put("username", user != null ? user.getUserName() : "");
        data.put("today_chat_count", 0);
        data.put("total_chat_count", 0);
        data.put("target_language_label", settings.getTargetLanguage() != null ? settings.getTargetLanguage() : "英语");

        return AjaxResult.success(data);
    }

    @GetMapping("/settings")
    public AjaxResult getSettings() {
        UserSettings settings = userSettingsService.getOrCreateByUserId(getUserId());
        return success(settings);
    }

    @PostMapping("/settings")
    public AjaxResult updateSettings(@RequestBody UserSettings settings) {
        settings.setUserId(getUserId());
        return toAjax(userSettingsService.updateById(settings));
    }
}
