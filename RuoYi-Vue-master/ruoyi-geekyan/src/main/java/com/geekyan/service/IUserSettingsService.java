package com.geekyan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.geekyan.entity.UserSettings;

public interface IUserSettingsService extends IService<UserSettings> {
    UserSettings getOrCreateByUserId(Long userId);
}
