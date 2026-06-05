package com.geekyan.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geekyan.entity.*;
import com.geekyan.mapper.*;
import com.geekyan.service.IUserSettingsService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.system.service.ISysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/geekyan/user")
public class UserProfileController extends BaseController {

    @Autowired
    private ISysUserService userService;

    @Autowired
    private IUserSettingsService userSettingsService;

    @Autowired
    private LearningRecordMapper learningRecordMapper;

    @Autowired
    private WordBookMapper wordBookMapper;

    @Autowired
    private AiNoteMapper aiNoteMapper;

    @Autowired
    private LongSentenceMapper longSentenceMapper;

    @Autowired
    private ReadingNoteMapper readingNoteMapper;

    @Autowired
    private SearchHistoryMapper searchHistoryMapper;

    @GetMapping("/profile")
    public AjaxResult getProfile() {
        Long userId = getUserId();
        SysUser user = userService.selectUserById(userId);
        UserSettings settings = userSettingsService.getOrCreateByUserId(userId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("nickName", user.getNickName());
        data.put("avatarUrl", buildAvatarUrl(user.getAvatar()));
        data.put("gender", convertGender(user.getSex()));
        data.put("phone", maskPhone(user.getPhonenumber()));
        data.put("email", maskEmail(user.getEmail()));
        data.put("registerTime", user.getCreateTime());

        data.put("examInfo", buildExamInfo(settings));
        data.put("studyStats", buildStudyStats(userId));
        data.put("preferences", buildPreferences(settings));
        data.put("memberInfo", buildMemberInfo(userId));

        return success(data);
    }

    @PostMapping("/exam-info")
    public AjaxResult updateExamInfo(@RequestBody Map<String, Object> params) {
        UserSettings settings = userSettingsService.getOrCreateByUserId(getUserId());
        if (params.containsKey("targetYear")) {
            settings.setTargetYear(toInteger(params.get("targetYear")));
        }
        if (params.containsKey("targetSchool")) {
            settings.setTargetSchool(toString(params.get("targetSchool")));
        }
        if (params.containsKey("targetMajor")) {
            settings.setTargetMajor(toString(params.get("targetMajor")));
        }
        if (params.containsKey("targetScore")) {
            settings.setTargetScore(toInteger(params.get("targetScore")));
        }
        if (params.containsKey("examSubjects")) {
            Object subjects = params.get("examSubjects");
            if (subjects instanceof List) {
                settings.setExamSubjects(JSON.toJSONString(subjects));
            } else if (subjects instanceof String) {
                settings.setExamSubjects((String) subjects);
            }
        }
        return toAjax(userSettingsService.updateById(settings));
    }

    @PostMapping("/preferences")
    public AjaxResult updatePreferences(@RequestBody Map<String, Object> params) {
        UserSettings settings = userSettingsService.getOrCreateByUserId(getUserId());
        if (params.containsKey("autoAddWord")) {
            settings.setAutoAddWord(toInteger(params.get("autoAddWord")));
        }
        if (params.containsKey("defaultDict")) {
            settings.setDefaultDict(toString(params.get("defaultDict")));
        }
        if (params.containsKey("dailyWordGoal")) {
            settings.setDailyWordGoal(toInteger(params.get("dailyWordGoal")));
        }
        if (params.containsKey("dailyQuestionGoal")) {
            settings.setDailyQuestionGoal(toInteger(params.get("dailyQuestionGoal")));
        }
        if (params.containsKey("readingMode")) {
            settings.setReadingMode(toString(params.get("readingMode")));
        }
        if (params.containsKey("pushEnabled")) {
            settings.setPushEnabled(toInteger(params.get("pushEnabled")));
        }
        if (params.containsKey("pushTime")) {
            settings.setPushTime(toString(params.get("pushTime")));
        }
        return toAjax(userSettingsService.updateById(settings));
    }

    private Map<String, Object> buildExamInfo(UserSettings settings) {
        if (settings.getTargetYear() == null && settings.getTargetSchool() == null
                && settings.getTargetMajor() == null && settings.getTargetScore() == null) {
            return null;
        }
        Map<String, Object> examInfo = new LinkedHashMap<>();
        examInfo.put("targetYear", settings.getTargetYear());
        examInfo.put("targetSchool", settings.getTargetSchool());
        examInfo.put("targetMajor", settings.getTargetMajor());
        examInfo.put("targetScore", settings.getTargetScore());
        if (settings.getExamSubjects() != null && !settings.getExamSubjects().isEmpty()) {
            try {
                examInfo.put("examSubjects", JSON.parseArray(settings.getExamSubjects()));
            } catch (Exception e) {
                examInfo.put("examSubjects", Collections.singletonList(settings.getExamSubjects()));
            }
        } else {
            examInfo.put("examSubjects", null);
        }
        return examInfo;
    }

    private Map<String, Object> buildStudyStats(Long userId) {
        long totalRecords = learningRecordMapper.selectCount(new LambdaQueryWrapper<LearningRecord>()
                .eq(LearningRecord::getUserId, userId));
        long totalWords = wordBookMapper.selectCount(new LambdaQueryWrapper<WordBook>()
                .eq(WordBook::getUserId, userId));
        long totalNotes = aiNoteMapper.selectCount(new LambdaQueryWrapper<AiNote>()
                .eq(AiNote::getUserId, userId));
        long totalSentences = longSentenceMapper.selectCount(new LambdaQueryWrapper<LongSentence>()
                .eq(LongSentence::getUserId, userId));
        long totalReadingNotes = readingNoteMapper.selectCount(new LambdaQueryWrapper<ReadingNote>()
                .eq(ReadingNote::getUserId, userId));

        int streak = calculateStreak(userId);
        long totalStudyDays = calculateTotalStudyDays(userId);

        long totalDuration = 0;
        List<LearningRecord> allRecords = learningRecordMapper.selectList(new LambdaQueryWrapper<LearningRecord>()
                .eq(LearningRecord::getUserId, userId)
                .isNotNull(LearningRecord::getDuration));
        for (LearningRecord r : allRecords) {
            if (r.getDuration() != null)
                totalDuration += r.getDuration();
        }

        int masteryScore = calculateMasteryScore(totalRecords,
                totalWords, streak);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("streakDays", streak);
        stats.put("totalStudyDays", totalStudyDays);
        stats.put("totalQuestions", totalRecords);
        stats.put("totalWords", totalWords);
        stats.put("totalNotes", totalNotes);
        stats.put("totalReadingMinutes", totalDuration / 60);
        stats.put("totalSentences", totalSentences);
        stats.put("totalReadingNotes", totalReadingNotes);
        stats.put("masteryScore", masteryScore);
        stats.put("rankPercentile", null);
        return stats;
    }

    private Map<String, Object> buildPreferences(UserSettings settings) {
        Map<String, Object> prefs = new LinkedHashMap<>();
        prefs.put("autoAddWord", settings.getAutoAddWord() != null ? settings.getAutoAddWord() == 1 : true);
        prefs.put("defaultDict", settings.getDefaultDict() != null ? settings.getDefaultDict()
                : "LDOCE 5 (English-Chinese, with audio)");
        prefs.put("dailyWordGoal", settings.getDailyWordGoal() != null ? settings.getDailyWordGoal() : 20);
        prefs.put("dailyQuestionGoal", settings.getDailyQuestionGoal() != null ? settings.getDailyQuestionGoal() : 10);
        prefs.put("readingMode", settings.getReadingMode() != null ? settings.getReadingMode() : "bilingual");
        prefs.put("pushEnabled", settings.getPushEnabled() != null ? settings.getPushEnabled() == 1 : true);
        prefs.put("pushTime", settings.getPushTime() != null ? settings.getPushTime() : "20:00");
        return prefs;
    }

    private Map<String, Object> buildMemberInfo(Long userId) {
        Map<String, Object> member = new LinkedHashMap<>();
        member.put("memberType", "free");
        member.put("expireTime", null);
        long monthCalls = searchHistoryMapper.selectCount(new LambdaQueryWrapper<SearchHistory>()
                .eq(SearchHistory::getUserId, userId)
                .ge(SearchHistory::getCreateTime, LocalDateTime.now().minusMonths(1)));
        member.put("remainingApiCalls", Math.max(0, 500 - monthCalls));
        return member;
    }

    private int calculateStreak(Long userId) {
        int streak = 0;
        LocalDate date = LocalDate.now();
        while (true) {
            LocalDateTime dayStart = date.atStartOfDay();
            LocalDateTime dayEnd = dayStart.plusDays(1);
            long count = learningRecordMapper.selectCount(new LambdaQueryWrapper<LearningRecord>()
                    .eq(LearningRecord::getUserId, userId)
                    .ge(LearningRecord::getCreateTime, dayStart)
                    .lt(LearningRecord::getCreateTime, dayEnd));
            if (count > 0) {
                streak++;
                date = date.minusDays(1);
            } else {
                break;
            }
        }
        return streak;
    }

    private long calculateTotalStudyDays(Long userId) {
        List<LearningRecord> records = learningRecordMapper.selectList(new LambdaQueryWrapper<LearningRecord>()
                .eq(LearningRecord::getUserId, userId)
                .select(LearningRecord::getCreateTime));
        Set<String> days = new HashSet<>();
        for (LearningRecord r : records) {
            if (r.getCreateTime() != null) {
                days.add(r.getCreateTime().toLocalDate().toString());
            }
        }
        return days.size();
    }

    private int calculateMasteryScore(long totalRecords,
            long totalWords, int streak) {
        int score = 0;
        score += Math.min(25, (int) totalRecords);
        score += Math.min(10, (int) totalWords / 5);
        score += Math.min(15, streak * 3);
        score += Math.min(20, (int) (totalRecords > 0 ? 15 : 0));
        return Math.min(100, Math.max(0, score));
    }

    private String buildAvatarUrl(String avatar) {
        if (avatar == null || avatar.isEmpty()) {
            return null;
        }
        if (avatar.startsWith("http")) {
            return avatar;
        }
        return "/profile/avatar/" + avatar;
    }

    private String convertGender(String sex) {
        if ("0".equals(sex))
            return "男";
        if ("1".equals(sex))
            return "女";
        return "保密";
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.isEmpty())
            return null;
        if (phone.length() >= 7) {
            return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
        }
        return phone;
    }

    private String maskEmail(String email) {
        if (email == null || email.isEmpty())
            return null;
        int atIndex = email.indexOf("@");
        if (atIndex > 2) {
            return email.substring(0, 2) + "***" + email.substring(atIndex);
        }
        return email;
    }

    private Integer toInteger(Object obj) {
        if (obj == null)
            return null;
        if (obj instanceof Number)
            return ((Number) obj).intValue();
        try {
            return Integer.parseInt(obj.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private String toString(Object obj) {
        return obj != null ? obj.toString() : null;
    }
}
