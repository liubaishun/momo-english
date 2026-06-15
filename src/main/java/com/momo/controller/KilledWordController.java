package com.momo.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*") // 允许前端本地跨域调用
public class KilledWordController {

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
     * POST http://localhost:8080/api/restore-word
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
        return objectMapper.readValue(file, new TypeReference<List<Integer>>() {});
    }

    // 写入本地存储文件的私有工具方法
    private synchronized void writeKilledIdsToFile(List<Integer> killedIds) throws IOException {
        File file = new File(FILE_PATH);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, killedIds);
    }
}