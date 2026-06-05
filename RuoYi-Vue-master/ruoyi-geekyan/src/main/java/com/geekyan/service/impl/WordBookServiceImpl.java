package com.geekyan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.geekyan.entity.Word;
import com.geekyan.entity.WordBook;
import com.geekyan.entity.WordBookCategory;
import com.geekyan.mapper.WordBookCategoryMapper;
import com.geekyan.mapper.WordBookMapper;
import com.geekyan.mapper.WordMapper;
import com.geekyan.service.IReviewTaskService;
import com.geekyan.service.IWordBookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class WordBookServiceImpl extends ServiceImpl<WordBookMapper, WordBook> implements IWordBookService {

    private static final Logger log = LoggerFactory.getLogger(WordBookServiceImpl.class);

    @Autowired
    private IReviewTaskService reviewTaskService;

    @Autowired
    private WordMapper wordMapper;

    @Autowired
    private WordBookCategoryMapper wordBookCategoryMapper;

    @Override
    public List<WordBook> getUserWordBook(Long userId, String bookName, String sortBy) {
        LambdaQueryWrapper<WordBook> wrapper = new LambdaQueryWrapper<WordBook>()
                .eq(WordBook::getUserId, userId);
        if (bookName != null && !bookName.isEmpty()) {
            wrapper.eq(WordBook::getBookName, bookName);
        }
        if ("alpha".equalsIgnoreCase(sortBy)) {
            wrapper.orderByAsc(WordBook::getWord);
        } else {
            wrapper.orderByDesc(WordBook::getCreateTime);
        }
        return list(wrapper);
    }

    @Override
    public void addWord(Long userId, Long wordId, String word, String bookName) {
        String realBookName = bookName != null ? bookName : "默认生词本";
        Long bookId = ensureCategory(userId, realBookName);

        // 如果 wordId 无效(0或null)，尝试从 word 表中查找已有记录
        if (wordId == null || wordId <= 0) {
            Word existingWord = wordMapper.selectOne(new LambdaQueryWrapper<Word>()
                    .eq(Word::getWord, word.toLowerCase()));
            if (existingWord != null) {
                wordId = existingWord.getId();
            }
        }

        // 去重检查：同一用户同一单词本中不允许重复添加同一单词
        // 优先查找是否有 wordId=0 的旧记录（前端未获取到wordId时创建的），如果有则升级
        if (wordId != null && wordId > 0) {
            WordBook legacyEntry = getOne(new LambdaQueryWrapper<WordBook>()
                    .eq(WordBook::getUserId, userId)
                    .eq(WordBook::getWord, word)
                    .eq(WordBook::getBookName, realBookName)
                    .eq(WordBook::getWordId, 0L));
            if (legacyEntry != null) {
                // 升级旧记录，将 wordId 设置为有效值
                legacyEntry.setWordId(wordId);
                if (legacyEntry.getBookId() == null) {
                    legacyEntry.setBookId(bookId);
                }
                updateById(legacyEntry);
                return;
            }
        }

        // 常规去重检查
        WordBook existing = getOne(new LambdaQueryWrapper<WordBook>()
                .eq(WordBook::getUserId, userId)
                .eq(WordBook::getWord, word)
                .eq(WordBook::getBookName, realBookName));
        if (existing != null) {
            // 如果已存在的记录 wordId 为0且当前 wordId 有效，则升级
            if ((existing.getWordId() == null || existing.getWordId() <= 0)
                    && wordId != null && wordId > 0) {
                existing.setWordId(wordId);
                if (existing.getBookId() == null) {
                    existing.setBookId(bookId);
                }
                updateById(existing);
            }
            if (existing.getBookId() == null) {
                existing.setBookId(bookId);
                updateById(existing);
            }
            return;
        }

        WordBook wb = new WordBook();
        wb.setUserId(userId);
        wb.setWordId(wordId != null ? wordId : 0L);
        wb.setWord(word);
        wb.setBookName(realBookName);
        wb.setBookId(bookId);
        wb.setMasteryLevel(0);
        wb.setReviewCount(0);
        wb.setCreateTime(java.time.LocalDateTime.now());
        wb.setUpdateTime(java.time.LocalDateTime.now());
        save(wb);

        try {
            String answerContent = buildWordAnswerContent(wordId);
            if (answerContent == null || answerContent.isEmpty()) {
                answerContent = buildWordAnswerContentByWord(word);
            }
            reviewTaskService.createReviewTask(userId, "word", wb.getId(), word, answerContent, "english");
        } catch (Exception e) {
            log.warn("创建单词复习任务失败: {}", e.getMessage());
        }
    }

    private String buildWordAnswerContent(Long wordId) {
        Word w = null;
        if (wordId != null && wordId > 0) {
            w = wordMapper.selectById(wordId);
        }
        // 如果wordId无效，尝试用单词名查找
        if (w == null) {
            return null; // 无法构建，后续会尝试补充
        }
        StringBuilder sb = new StringBuilder();
        if (w.getPhonetic() != null && !w.getPhonetic().isEmpty()) {
            sb.append(w.getPhonetic());
        }
        if (w.getMeanings() != null && !w.getMeanings().isEmpty()) {
            if (sb.length() > 0)
                sb.append("\n");
            sb.append(w.getMeanings());
        }
        if (w.getExamples() != null && !w.getExamples().isEmpty()) {
            if (sb.length() > 0)
                sb.append("\n");
            String examples = w.getExamples().length() > 150
                    ? w.getExamples().substring(0, 150) + "..."
                    : w.getExamples();
            sb.append("例句：").append(examples);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /** 根据单词字符串构建answerContent（当wordId无效时使用） */
    private String buildWordAnswerContentByWord(String word) {
        if (word == null || word.isEmpty())
            return null;
        Word w = wordMapper.selectOne(new LambdaQueryWrapper<Word>()
                .eq(Word::getWord, word.toLowerCase()).last("LIMIT 1"));
        if (w == null)
            return null;
        StringBuilder sb = new StringBuilder();
        if (w.getPhonetic() != null && !w.getPhonetic().isEmpty()) {
            sb.append(w.getPhonetic());
        }
        if (w.getMeanings() != null && !w.getMeanings().isEmpty()) {
            if (sb.length() > 0)
                sb.append("\n");
            sb.append(w.getMeanings());
        }
        if (w.getExamples() != null && !w.getExamples().isEmpty()) {
            if (sb.length() > 0)
                sb.append("\n");
            String examples = w.getExamples().length() > 150
                    ? w.getExamples().substring(0, 150) + "..."
                    : w.getExamples();
            sb.append("例句：").append(examples);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private Long ensureCategory(Long userId, String bookName) {
        String realBookName = bookName != null && !bookName.isEmpty() ? bookName : "默认生词本";
        WordBookCategory category = wordBookCategoryMapper.selectOne(new LambdaQueryWrapper<WordBookCategory>()
                .eq(WordBookCategory::getUserId, userId)
                .eq(WordBookCategory::getName, realBookName)
                .last("LIMIT 1"));
        if (category != null) {
            return category.getId();
        }

        category = new WordBookCategory();
        category.setUserId(userId);
        category.setName(realBookName);
        category.setIsDefault("默认生词本".equals(realBookName) ? 1 : 0);
        category.setSortOrder(0);
        category.setCreateTime(java.time.LocalDateTime.now());
        category.setUpdateTime(java.time.LocalDateTime.now());
        try {
            wordBookCategoryMapper.insert(category);
        } catch (Exception e) {
            WordBookCategory existing = wordBookCategoryMapper.selectOne(new LambdaQueryWrapper<WordBookCategory>()
                    .eq(WordBookCategory::getUserId, userId)
                    .eq(WordBookCategory::getName, realBookName)
                    .last("LIMIT 1"));
            if (existing != null) {
                return existing.getId();
            }
            throw e;
        }
        return category.getId();
    }

    @Override
    public void removeWord(Long userId, Long wordId, String bookName) {
        LambdaQueryWrapper<WordBook> wrapper = new LambdaQueryWrapper<WordBook>()
                .eq(WordBook::getUserId, userId);
        WordBook existing = getOne(new LambdaQueryWrapper<WordBook>()
                .eq(WordBook::getId, wordId)
                .eq(WordBook::getUserId, userId));
        if (existing != null) {
            removeById(existing.getId());
        } else {
            remove(wrapper.eq(WordBook::getWordId, wordId)
                    .eq(WordBook::getBookName, bookName != null ? bookName : "默认生词本"));
        }
    }

    @Override
    public List<String> getUserBookNames(Long userId) {
        List<WordBook> all = list(new LambdaQueryWrapper<WordBook>()
                .eq(WordBook::getUserId, userId)
                .select(WordBook::getBookName)
                .groupBy(WordBook::getBookName));
        return all.stream()
                .map(WordBook::getBookName)
                .filter(name -> name != null && !name.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public void renameBook(Long userId, String oldName, String newName) {
        Long newBookId = ensureCategory(userId, newName);
        List<WordBook> list = list(new LambdaQueryWrapper<WordBook>()
                .eq(WordBook::getUserId, userId)
                .eq(WordBook::getBookName, oldName));
        for (WordBook wb : list) {
            wb.setBookName(newName);
            wb.setBookId(newBookId);
        }
        updateBatchById(list);

        WordBookCategory oldCategory = wordBookCategoryMapper.selectOne(new LambdaQueryWrapper<WordBookCategory>()
                .eq(WordBookCategory::getUserId, userId)
                .eq(WordBookCategory::getName, oldName)
                .last("LIMIT 1"));
        if (oldCategory != null) {
            wordBookCategoryMapper.deleteById(oldCategory.getId());
        }
    }

    @Override
    public void deleteBook(Long userId, String bookName) {
        remove(new LambdaQueryWrapper<WordBook>()
                .eq(WordBook::getUserId, userId)
                .eq(WordBook::getBookName, bookName));
        WordBookCategory category = wordBookCategoryMapper.selectOne(new LambdaQueryWrapper<WordBookCategory>()
                .eq(WordBookCategory::getUserId, userId)
                .eq(WordBookCategory::getName, bookName)
                .last("LIMIT 1"));
        if (category != null) {
            wordBookCategoryMapper.deleteById(category.getId());
        }
    }

    @Override
    public void setDefaultBook(Long userId, String bookName) {
        List<WordBook> defaultList = list(new LambdaQueryWrapper<WordBook>()
                .eq(WordBook::getUserId, userId)
                .eq(WordBook::getBookName, "默认生词本"));
        if (!defaultList.isEmpty() && !bookName.equals("默认生词本")) {
            renameBook(userId, "默认生词本", "旧默认生词本");
        }
        renameBook(userId, bookName, "默认生词本");
    }
}
