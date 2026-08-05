package com.momo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momo.dto.WordVO;
import com.momo.service.WordFilterService;
import com.momo.service.WordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 过滤的页面对于的后台接口
 * <p>
 * 1. 数据的初始大盘（清洗阶段）
 * 当词书导入后，单词并不是一锅端，而是根据你在过滤页面的“清洗”结果打上战术标签：
 * BURNING（待背词表）：继承在清洗时被你判定为 ⚡ 模糊、🌋 陌生，或者背词次数未满 $5$ 次（MAX_KILL_COUNT = 5）的硬骨头。
 * FROZEN（已过词表）：在清洗时被你一刀切掉、直接标记为 💎 掌握 的熟词，它们直接进入冷冻库
 */
@RestController
@RequestMapping("/api/words")
@CrossOrigin(origins = "*") // 允许前端本地跨域调用
public class FilterWordController {


    private final String DATA_PATH = "data/";
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private WordService wordService;
    @Autowired
    private WordFilterService filterService;


    public FilterWordController() {
        new File(DATA_PATH).mkdirs();
    }


    /**
     * 1. 统一查询接口：通过 status 参数分流活跃词表与熟词表
     * 请求路径示例: /api/words/filter/list?book=book01&status=RAW
     */
    @GetMapping("/filter/list")
    public ResponseEntity<List<WordVO>> getWordsByStatus(@RequestParam("book") String bookId, @RequestParam("status") String status) {

        // 从数据库中根据 book_id 和 status (RAW 或 FROZEN) 查询出对应的单词列表
        List<WordVO> list = filterService.getWordsByBookAndStatus(bookId, status);
        return ResponseEntity.ok(list);
    }

    /**
     * 4. 斩杀单个单词（按书隔离，并记录难度分层）
     * 列表手动点击是为了“对刚导入的单词进行快速分拣，标记陌生、模糊、掌握”。
     * 此时用户在列表上的点击，本质上是在进行首次身份定义，而不是普通的复习滚动。
     */
    @PostMapping("/filter/word")
    public Map<String, String> killWord(@RequestBody Map<String, Object> payload) {
        String bookId = (String) payload.get("book");
        String word = (String) payload.get("word");
        String masteryDegree = (String) payload.getOrDefault("masteryDegree", "mastered");
        String source = (String) payload.get("source");

        // 调用 Service 处理
        filterService.processWordReview(1L, bookId, word, masteryDegree, source);

        Map<String, String> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "成功处理单词: " + word);
        return result;
    }


    /**
     * 📥 5. 取消斩杀/还原单个或多个单词（从冻结舱踢回燃烧区）
     */
    @PostMapping("/filter/restore")
    @SuppressWarnings("unchecked")
    public Map<String, String> restoreWords(@RequestBody Map<String, Object> payload) {
        String bookId = (String) payload.get("book"); // 战术统一：建议变量名与前端一致用 bookId 或 book
        List<String> words = (List<String>) payload.get("words");

        // 🎯 新增：获取前端传来的还原来源，默认兜底为 filterPage
        String source = (String) payload.getOrDefault("source", "filterPage");

        Map<String, String> result = new HashMap<>();

        if (bookId == null || words == null || words.isEmpty()) {
            result.put("status", "failed");
            result.put("message", "还原失败：缺少核心参数！");
            return result;
        }

        // 交付 Service 层，并将 source 战术下发
        filterService.restoreWords(1L,bookId, words, source);

        result.put("status", "success");
        result.put("message", "成功处理 " + words.size() + " 个单词的还原请求");
        return result;
    }


}