package com.geekyan.controller;

import com.geekyan.entity.Word;
import com.geekyan.entity.WordBook;
import com.geekyan.service.IWordService;
import com.geekyan.service.IWordBookService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/geekyan/word")
public class WordController extends BaseController {

    @Autowired
    private IWordService wordService;

    @Autowired
    private IWordBookService wordBookService;

    @GetMapping("/search")
    public AjaxResult searchWord(@RequestParam String word) {
        Word result = wordService.searchWord(word);
        return success(result);
    }

    @GetMapping("/fuzzy")
    public AjaxResult fuzzySearch(@RequestParam String keyword, @RequestParam(defaultValue = "10") int limit) {
        List<Word> list = wordService.fuzzySearch(keyword, limit);
        return success(list);
    }

    @GetMapping("/book")
    public TableDataInfo wordBookList(@RequestParam(required = false) String bookName) {
        startPage();
        List<WordBook> list = wordBookService.getUserWordBook(getUserId(), bookName);
        return getDataTable(list);
    }

    @PostMapping("/book/add")
    public AjaxResult addToWordBook(@RequestBody Map<String, Object> params) {
        Long wordId = Long.valueOf(params.get("word_id").toString());
        String word = (String) params.get("word");
        String bookName = (String) params.getOrDefault("book_name", "默认生词本");
        wordBookService.addWord(getUserId(), wordId, word, bookName);
        return success();
    }

    @PostMapping("/book/remove")
    public AjaxResult removeFromWordBook(@RequestBody Map<String, Object> params) {
        Long wordId = Long.valueOf(params.get("word_id").toString());
        String bookName = (String) params.getOrDefault("book_name", "默认生词本");
        wordBookService.removeWord(getUserId(), wordId, bookName);
        return success();
    }
}
