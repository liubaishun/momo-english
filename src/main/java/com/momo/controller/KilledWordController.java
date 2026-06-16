package com.momo.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.momo.service.WordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.util.*;


/**
 * 滚动斩杀页面对于的接口
 */
@RestController
@RequestMapping("/api/words")
@CrossOrigin(origins = "*") // 允许前端本地跨域调用
public class KilledWordController {

    private final String DATA_PATH = "data/";
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private WordService wordService;


    // 动态斩杀单词的本地存储路径
    private static final String FILE_PATH = System.getProperty("user.dir") + File.separator + "dynamic_killed.json";
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KilledWordController() {
        // 初始化检查：如果文件不存在，则创建空的 JSON 数组文件
        try {
            File file = new File(FILE_PATH);
            if (!file.exists()) {
                objectMapper.writeValue(file, new ArrayList<Integer>());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    // 接收进度保存请求
    @PostMapping("/progress/save")
    public void saveProgress(@RequestBody Map<String, Object> progress) throws Exception {
        String bookId = (String) progress.get("bookId");
        String lastWord = (String) progress.get("lastWord"); // 允许它为 null

        // ⚡ 只强校验词书 ID 不能为空
        if (bookId == null || bookId.trim().isEmpty()) {
            throw new IllegalArgumentException("词书ID参数缺失");
        }

        File file = new File(DATA_PATH + bookId + "_progress.json");

        // 直接写入。即便 {"bookId":"xxx", "lastWord":null} 也是完全合法的 JSON 格式
        mapper.writeValue(file, progress);
    }

    // 获取上次进度
    @GetMapping("/progress/load")
    public Map<String, Object> loadProgress(@RequestParam String bookId) throws Exception {
        File file = new File(DATA_PATH + bookId + "_progress.json");

        // ⚡ 核心修复：检查文件是否存在，且长度是否大于 0
        if (!file.exists() || file.length() == 0) {
            return new HashMap<>(); // 返回一个空的 Map，前端解析后就是一个 {}
        }

        try {
            return mapper.readValue(file, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            // 如果文件虽然有内容但格式错误，记录日志并返回空 Map
            System.err.println("进度文件解析失败: " + e.getMessage());
            return new HashMap<>();
        }
    }


    /**
     * 接口 1：获取后端动态斩杀的全部单词 ID 列表
     * GET http://localhost:8080/api/dynamic-killed
     */
    @GetMapping("/dynamic-killed")
    public ResponseEntity<Map<String, Object>> getDynamicKilled() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Integer> killedIds = readKilledIdsFromFile();
            response.put("success", true);
            response.put("killedIds", killedIds);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "读取后端库失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 接口 2：动态斩杀新单词（追加到本地库）
     * POST http://localhost:8080/api/kill-word
     * Body: { "wordId": 105 }
     */
    @PostMapping("/kill-word")
    public ResponseEntity<Map<String, Object>> killWord(@RequestBody Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();
        if (!payload.containsKey("wordId")) {
            response.put("success", false);
            response.put("message", "缺少 wordId");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            int wordId = Integer.parseInt(payload.get("wordId").toString());
            List<Integer> killedIds = readKilledIdsFromFile();

            if (!killedIds.contains(wordId)) {
                killedIds.add(wordId);
                writeKilledIdsToFile(killedIds);
            }

            response.put("success", true);
            response.put("message", "单词已成功持久化写入 Java 后端库");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "操作失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 接口 3：批量/单个从动态库中移除并还原单词
     * POST http://localhost:8080/api/words/restore-word
     * Body: { "wordIds": [105, 106, 107] }
     */
    @PostMapping("/restore-word")
    public ResponseEntity<Map<String, Object>> restoreWord(@RequestBody Map<String, List<Integer>> payload) {
        Map<String, Object> response = new HashMap<>();
        if (!payload.containsKey("wordIds")) {
            response.put("success", false);
            response.put("message", "参数错误，必须包含 wordIds 数组");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            List<Integer> targetsToRestore = payload.get("wordIds");
            List<Integer> killedIds = readKilledIdsFromFile();

            // 过滤掉需要还原的 ID
            killedIds.removeIf(targetsToRestore::contains);
            writeKilledIdsToFile(killedIds);

            response.put("success", true);
            response.put("message", "成功复活并从 Java 后端库中移除");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "操作失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    // 读取本地存储文件的私有工具方法
    private synchronized List<Integer> readKilledIdsFromFile() throws IOException {
        File file = new File(FILE_PATH);
        return objectMapper.readValue(file, new TypeReference<List<Integer>>() {
        });
    }

    // 写入本地存储文件的私有工具方法
    private synchronized void writeKilledIdsToFile(List<Integer> killedIds) throws IOException {
        File file = new File(FILE_PATH);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, killedIds);
    }
}