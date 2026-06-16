package com.momo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momo.dto.WordVO;
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
 */
@RestController
@RequestMapping("/api/words")
public class FilterWordController {


    private final String DATA_PATH = "data/";
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private WordService wordService;


    public FilterWordController() {
        new File(DATA_PATH).mkdirs();
    }


    /**
     * 1. 统一查询接口：通过 status 参数分流活跃词表与熟词表
     * 请求路径示例: /api/words/list?book=book01&status=BURNING
     */
    @GetMapping("/list")
    public ResponseEntity<List<WordVO>> getWordsByStatus(
            @RequestParam("book") String bookId,
            @RequestParam("status") String status) {

        // 从数据库中根据 book_id 和 status (BURNING 或 FROZEN) 查询出对应的单词列表
        List<WordVO> list = wordService.getWordsByBookAndStatus(bookId, status);
        return ResponseEntity.ok(list);
    }

    /**
     * 4. 斩杀单个单词（按书隔离，并记录难度分层）
     * 列表手动点击是为了“对刚导入的单词进行快速分拣，标记陌生、模糊、掌握”。
     * 此时用户在列表上的点击，本质上是在进行首次身份定义，而不是普通的复习滚动。
     */
    @PostMapping("/filter/kill")
    public Map<String, String> killWord(@RequestBody Map<String, Object> payload) {
        // 1. 获取核心参数
        String bookId = (String) payload.get("book");
        String word = (String) payload.get("word");
        String status = (String) payload.getOrDefault("status", "easy"); // 默认是 easy 轰炸

        // 2. 获取前端传来的来源（可能是 null）
        String source = (String) payload.get("source");

        // ⚡ 核心智能纠偏（双保险）：
        // 如果 status 是分拣特定的三个标签，哪怕前端漏传了 source，后端也强行校准为 "list"
        if ("mastered".equals(status) || "vague".equals(status) || "stranger".equals(status)) {
            source = "list";
        }
        // 如果不是上述三个标签，且前端没传 source，则顺理成章兜底为常规大轰炸 "bomb"
        else if (source == null) {
            source = "bomb";
        }

        // 3. 调用 Service 处理业务逻辑
        wordService.processWordReview(bookId, word, status, source);

        // 4. 返回结果
        Map<String, String> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "成功处理单词: " + word);
        return result;
    }


    /**
     * 📥 5. 取消斩杀/还原单个或多个单词（从冻结舱踢回燃烧区）
     */
    @PostMapping("/restore")
    @SuppressWarnings("unchecked") // 压制强转 List 的未检查警告
    public Map<String, String> restoreWords(@RequestBody Map<String, Object> payload) {
        // 1. 安全提取参数
        String bookName = (String) payload.get("book");
        List<String> words = (List<String>) payload.get("words");

        Map<String, String> result = new HashMap<>();

        // 2. 参数健壮性战术校验
        if (bookName == null || words == null || words.isEmpty()) {
            result.put("status", "failed");
            result.put("message", "还原失败：缺少核心参数 book 或 words 列表为空！");
            return result;
        }

        // 3. 交付 Service 层执行铁血回炉流转
        wordService.restoreWords(bookName, words);

        // 4. 统计数量并装配标准的响应结果
        int count = words.size();
        result.put("status", "success");
        result.put("message", "成功还原 " + count + " 个单词");

        return result;
    }


}