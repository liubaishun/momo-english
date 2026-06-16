package com.momo.controller;


import com.momo.model.Word;
import com.momo.service.WordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/words")
@CrossOrigin(origins = "*") // 允许跨域访问
public class WordController {

    @Autowired
    private WordService wordService;

    // 获取所有单词
    @GetMapping
    public ResponseEntity<List<Word>> getAllWords() {
        return ResponseEntity.ok(wordService.getAllWords());
    }

    // 根据ID获取单个单词
    @GetMapping("/{id}")
    public ResponseEntity<Word> getWordById(@PathVariable Long id) {
        return wordService.getWordById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 根据文本获取单词
    @GetMapping("/search/{text}")
    public ResponseEntity<Word> getWordByText(@PathVariable String text) {
        return wordService.findByWord(text)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 根据书籍ID获取单词列表
    @GetMapping("/book/{bookId}")
    public ResponseEntity<List<Word>> getWordsByBookId(@PathVariable String bookId) {
        return ResponseEntity.ok(wordService.getWordsByBookId(bookId));
    }

    // 创建新单词
    @PostMapping
    public ResponseEntity<Word> createWord(@RequestBody Word word) {
        try {
            Word createdWord = wordService.createWord(word);
            return ResponseEntity.status(201).body(createdWord);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    // 更新单词
    @PutMapping("/{id}")
    public ResponseEntity<Word> updateWord(@PathVariable Long id, @RequestBody Word wordDetails) {
        try {
            Word updatedWord = wordService.updateWord(id, wordDetails);
            return ResponseEntity.ok(updatedWord);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // 删除单词
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWord(@PathVariable Long id) {
        try {
            wordService.deleteWord(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}