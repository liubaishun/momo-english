package com.momo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momo.dto.WordVO;
import com.momo.service.WordKillService;
import com.momo.service.WordService;
import com.momo.service.strategy.RollingRefuelEngine;
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
    @Autowired
    private RollingRefuelEngine refuelEngine;

    @Autowired
    private WordKillService wordKillService;


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
     * 💎 斩杀页面双大盘数据拉取端点
     * GET /api/words/kill/list?bookId=xxxx&status=BURNING (拉取待背词表)
     * GET /api/words/kill/list?bookId=xxxx&status=FROZEN  (拉取已背词表)
     */
    @GetMapping("/kill/list")
    public ResponseEntity<List<WordVO>> getKillPageList(@RequestParam("bookId") String bookId, @RequestParam("status") String status) {
        if (bookId == null || status == null) {
            return ResponseEntity.badRequest().build();
        }
        List<WordVO> list = wordKillService.getKillPageWords(bookId, status,1L);

        return ResponseEntity.ok(list);
    }

    /**
     * 🛰️ 对应前端：刷新并加载当前词书的轰炸混编大盘
     * GET /api/word/bomb/load?bookId=xxx
     */
    @GetMapping("/bomb/load")
    public ResponseEntity<List<WordVO>> loadBombQueue(@RequestParam("bookId") String bookId,
                                                      @RequestParam(value = "status", required = false, defaultValue = "BURNING") String status,
                                                      @RequestParam("globalTick") int globalTick) {
        if (bookId == null || status == null) {
            return ResponseEntity.badRequest().build();
        }

        List<WordVO> list = wordKillService.getKillPageWords(bookId, status,1L);

        //List<WordVO> activeQueue = wordKillService.calculateEightDimensionalBombMagazine(list);
        List<WordVO> activeQueue = refuelEngine.memoryStatePredictionEngine(list, bookId, 1L,globalTick);

        return ResponseEntity.ok(activeQueue);
    }


    @PostMapping("/bomb/reset")
    public ResponseEntity<?> resetBookRound(@RequestParam String bookId) {
        // 1. 找到当前词书中所有处于待刷状态的单词
        // 2. 清空它们在当前轮次被标记的特殊排阵 Tick
        // 3. 让它们能够重新被 /bomb/load 接口检索到
        refuelEngine.resetBurningWordsForNewRound(bookId, 1L);
        return ResponseEntity.ok().build();
    }

    /**
     * 4. 斩杀单个单词（按书隔离，并记录难度分层）
     * 列表手动点击是为了“对刚导入的单词进行快速分拣，标记陌生、模糊、掌握”。
     * 此时用户在列表上的点击，本质上是在进行首次身份定义，而不是普通的复习滚动。
     */
    @PostMapping("/kill/kill")
    public Map<String, String> killWord(@RequestBody Map<String, Object> payload) {
        String bookId = (String) payload.get("book");
        String word = (String) payload.get("word");
        String masteryDegree = (String) payload.getOrDefault("status", "mastered");
        int currentTick = (int) payload.get("currentTick");

        // 调用 Service 处理
        wordKillService.processWordReview(bookId, word, masteryDegree, currentTick,1L);

        Map<String, String> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "成功处理单词: " + word);
        return result;
    }

    /**
     * 4. 斩杀单个单词（按书隔离，并记录难度分层）
     * 列表手动点击是为了“对刚导入的单词进行快速分拣，标记陌生、模糊、掌握”。
     * 此时用户在列表上的点击，本质上是在进行首次身份定义，而不是普通的复习滚动。
     */
    @PostMapping("/kill/bomb")
    public Map<String, String> bombWord(@RequestBody Map<String, Object> payload) {
        String bookId = (String) payload.get("book");
        String word = (String) payload.get("word");
        String masteryDegree = (String) payload.getOrDefault("masteryDegree", "mastered");
        String source = (String) payload.get("source");

        // 调用 Service 处理
        wordKillService.killordReview(0L,bookId, word, masteryDegree, source);

        Map<String, String> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "成功处理单词: " + word);
        return result;
    }


    /**
     * 📥 5. 取消斩杀/还原单个或多个单词（从冻结舱踢回燃烧区）
     */
    @PostMapping("/kill/restore")
    @SuppressWarnings("unchecked")
    public Map<String, String> restoreWords(@RequestBody Map<String, Object> payload) {
        String bookId = (String) payload.get("book"); // 战术统一：建议变量名与前端一致用 bookId 或 book
        List<String> words = (List<String>) payload.get("words");

        // 🎯 新增：获取前端传来的还原来源，默认兜底为 killPage
        String source = (String) payload.getOrDefault("source", "killPage");

        Map<String, String> result = new HashMap<>();

        if (bookId == null || words == null || words.isEmpty()) {
            result.put("status", "failed");
            result.put("message", "还原失败：缺少核心参数！");
            return result;
        }

        // 交付 Service 层，并将 source 战术下发
        wordService.restoreWords(1L,bookId, words, source);

        result.put("status", "success");
        result.put("message", "成功处理 " + words.size() + " 个单词的还原请求");
        return result;
    }

}