package com.geekyan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.geekyan.entity.ErrorRecord;
import com.geekyan.mapper.ErrorRecordMapper;
import com.geekyan.service.IErrorRecordService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ErrorRecordServiceImpl extends ServiceImpl<ErrorRecordMapper, ErrorRecord> implements IErrorRecordService {

    @Override
    public List<ErrorRecord> getUserErrorRecords(Long userId, String subject) {
        LambdaQueryWrapper<ErrorRecord> wrapper = new LambdaQueryWrapper<ErrorRecord>()
                .eq(ErrorRecord::getUserId, userId);
        if (subject != null && !subject.isEmpty()) {
            wrapper.eq(ErrorRecord::getSubject, subject);
        }
        return list(wrapper.orderByDesc(ErrorRecord::getCreateTime));
    }

    @Override
    public void saveErrorRecord(Long userId, ErrorRecord record) {
        record.setUserId(userId);
        record.setReviewCount(0);
        record.setMasteryLevel(0);
        record.setNextReviewTime(LocalDateTime.now().plusDays(1));
        save(record);
    }
}
