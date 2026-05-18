package com.geekyan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.geekyan.entity.Word;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WordMapper extends BaseMapper<Word> {
}
