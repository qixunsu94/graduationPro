package com.geekyan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.geekyan.entity.ErrorRecord;
import java.util.List;

public interface IErrorRecordService extends IService<ErrorRecord> {
    List<ErrorRecord> getUserErrorRecords(Long userId, String subject);
    void saveErrorRecord(Long userId, ErrorRecord record);
}
