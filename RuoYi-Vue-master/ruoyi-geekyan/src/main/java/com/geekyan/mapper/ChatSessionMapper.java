package com.geekyan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.geekyan.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}
