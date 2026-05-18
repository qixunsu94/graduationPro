package com.geekyan.controller;

import com.geekyan.entity.ErrorRecord;
import com.geekyan.service.IErrorRecordService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/geekyan/error")
public class ErrorRecordController extends BaseController {

    @Autowired
    private IErrorRecordService errorRecordService;

    @GetMapping("/list")
    public TableDataInfo list(@RequestParam(required = false) String subject) {
        startPage();
        List<ErrorRecord> list = errorRecordService.getUserErrorRecords(getUserId(), subject);
        return getDataTable(list);
    }

    @PostMapping
    public AjaxResult add(@RequestBody ErrorRecord record) {
        errorRecordService.saveErrorRecord(getUserId(), record);
        return success();
    }

    @PutMapping
    public AjaxResult edit(@RequestBody ErrorRecord record) {
        return toAjax(errorRecordService.updateById(record));
    }

    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return toAjax(errorRecordService.removeByIds(java.util.Arrays.asList(ids)));
    }
}
