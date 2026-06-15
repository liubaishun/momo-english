package com.momo.controller;

import com.momo.service.WordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.nio.file.*;
import java.util.*;

@RestController
@RequestMapping("/api/words")
public class WordPersistenceController {

    private final String DATA_PATH = "data/";
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private WordService wordService;


    public WordPersistenceController() {
        new File(DATA_PATH).mkdirs();
    }

    /**
     * 4. 斩杀单个单词（按书隔离）
     */
    @PostMapping("/kill")
    public Map<String, String> killWord(@RequestBody Map<String, String> payload) {
        String bookName = payload.get("book");
        String word = payload.get("word");
        wordService.killWord(bookName, word);

        // 1. 创建 Map
        Map<String, String> result = new HashMap<>();
        // 2. 放入数据（字符串拼接直接在 put 时完成）
        result.put("status", "success");
        result.put("message", "成功斩杀: " + word);


        return result;
    }

    /**
     * 5. 取消斩杀/还原单个或多个单词
     */
    @PostMapping("/restore")
    public Map<String, String> restoreWords(@RequestBody Map<String, Object> payload) {
        String bookName = (String) payload.get("book");
        List<String> words = (List<String>) payload.get("words");
        wordService.restoreWords(bookName, words);

        // 假设 words 是一个 List 或 Collection
        int count = words.size();
        Map<String, String> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "成功还原 " + count + " 个单词");

        return result;
    }

    // 接收进度保存请求
    @PostMapping("/progress/save")
    public void saveProgress(@RequestBody Map<String, Object> progress) throws Exception {

        String bookId = (String) progress.get("bookId");
        int page = ((Number) progress.get("currentPage")).intValue();
        int index = ((Number) progress.get("lastWordIndex")).intValue();

        File file = new File(DATA_PATH + bookId + "_progress.json");

        Map<String, Integer> progressMap = new HashMap<>();

        if (file.exists() && file.length() > 0) {
            progressMap = mapper.readValue(
                    file,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Integer>>() {}
            );
        }

        progressMap.put(String.valueOf(page), index);

        mapper.writeValue(file, progressMap);
    }

    // 获取上次进度
    @GetMapping("/progress/load")
    public Map<String, Integer> loadProgress(String bookId) throws Exception {
        File file = new File(DATA_PATH + bookId + "_progress.json");

        if (!file.exists() || file.length() == 0) {
            return new HashMap<>();
        }

        try {
            return mapper.readValue(
                    file,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Integer>>() {}
            );
        } catch (Exception e) {
            // 文件损坏也兜底
            return new HashMap<>();
        }
    }

    /**
     * 1. 词书状态管理 (斩杀/还原)
     * POST /api/words/status?bookId=xxx&word=xxx&action=kill
     */
    @PostMapping("/status")
    public void updateWordStatus(@RequestParam String bookId, @RequestParam String word, @RequestParam String action) throws Exception {
        File file = new File(DATA_PATH + bookId + "_killed.json");
        Set<String> killedSet = file.exists() ? mapper.readValue(file, Set.class) : new HashSet<>();

        if ("kill".equals(action)) killedSet.add(word);
        else killedSet.remove(word);

        mapper.writeValue(file, killedSet);
    }



    /**
     * 3. 用户节奏偏好存储
     * POST /api/words/settings
     */
    @GetMapping ("/settings")
    public void saveSettings(@RequestBody Map<String, Integer> settings) throws Exception {
        mapper.writeValue(new File(DATA_PATH + "user_settings.json"), settings);
    }

    /**
     * 4. 效能看板数据 (模拟计算斩杀记录)
     * GET /api/words/report?bookId=xxx
     */
    @GetMapping("/report")
    public Map<String, Integer> getReport(@RequestParam String bookId) throws Exception {
        File file = new File(DATA_PATH + bookId + "_killed.json");
        Set<String> killedSet = file.exists() ? mapper.readValue(file, Set.class) : new HashSet<>();

        Map<String, Integer> report = new HashMap<>();
        report.put("todayKilled", killedSet.size()); // 简单示例：展示总斩杀量
        return report;
    }
}