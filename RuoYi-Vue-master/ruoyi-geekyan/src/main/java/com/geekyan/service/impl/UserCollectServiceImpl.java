package com.geekyan.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.geekyan.entity.UserCollect;
import com.geekyan.mapper.UserCollectMapper;
import com.geekyan.service.IUserCollectService;
import org.springframework.stereotype.Service;

@Service
public class UserCollectServiceImpl extends ServiceImpl<UserCollectMapper, UserCollect> implements IUserCollectService {
}
