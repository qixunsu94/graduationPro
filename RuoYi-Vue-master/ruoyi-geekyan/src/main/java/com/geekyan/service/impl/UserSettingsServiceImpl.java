package com.geekyan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.geekyan.entity.UserSettings;
import com.geekyan.mapper.UserSettingsMapper;
import com.geekyan.service.IUserSettingsService;
import org.springframework.stereotype.Service;

@Service
public class UserSettingsServiceImpl extends ServiceImpl<UserSettingsMapper, UserSettings> implements IUserSettingsService {

    @Override
    public UserSettings getOrCreateByUserId(Long userId) {
        UserSettings settings = getOne(new LambdaQueryWrapper<UserSettings>()
                .eq(UserSettings::getUserId, userId));
        if (settings == null) {
            settings = new UserSettings();
            settings.setUserId(userId);
            settings.setAutoPlayingVoice(0);
            settings.setAutoTextShadow(0);
            settings.setAutoPronunciation(0);
            settings.setPlayingVoiceSpeed("1.0");
            settings.setTargetLanguage("英语");
            settings.setAiProvider("ZHIPU");
            save(settings);
        }
        return settings;
    }
}
