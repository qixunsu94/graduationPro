package com.geekyan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.geekyan.entity.ErrorRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ErrorRecordMapper extends BaseMapper<ErrorRecord> {
}
