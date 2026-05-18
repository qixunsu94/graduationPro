package com.geekyan.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.geekyan.entity.Feedback;
import com.geekyan.mapper.FeedbackMapper;
import com.geekyan.service.IFeedbackService;
import org.springframework.stereotype.Service;

@Service
public class FeedbackServiceImpl extends ServiceImpl<FeedbackMapper, Feedback> implements IFeedbackService {
}
