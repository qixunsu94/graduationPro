package com.geekyan.controller;

import com.geekyan.entity.Topic;
import com.geekyan.service.ITopicService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/geekyan/topic")
public class TopicController extends BaseController {

    @Autowired
    private ITopicService topicService;

    @GetMapping("/list")
    public TableDataInfo list(Topic topic) {
        startPage();
        List<Topic> list = topicService.list(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Topic>()
                        .eq(topic.getStatus() != null, Topic::getStatus, topic.getStatus())
                        .orderByAsc(Topic::getSort));
        return getDataTable(list);
    }

    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id) {
        return success(topicService.getById(id));
    }

    @PostMapping
    public AjaxResult add(@RequestBody Topic topic) {
        return toAjax(topicService.save(topic));
    }

    @PutMapping
    public AjaxResult edit(@RequestBody Topic topic) {
        return toAjax(topicService.updateById(topic));
    }

    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return toAjax(topicService.removeByIds(java.util.Arrays.asList(ids)));
    }
}
