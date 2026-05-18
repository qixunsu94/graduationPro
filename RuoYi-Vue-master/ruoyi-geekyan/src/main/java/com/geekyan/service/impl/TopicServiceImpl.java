package com.geekyan.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.geekyan.entity.Topic;
import com.geekyan.mapper.TopicMapper;
import com.geekyan.service.ITopicService;
import org.springframework.stereotype.Service;

@Service
public class TopicServiceImpl extends ServiceImpl<TopicMapper, Topic> implements ITopicService {
}
