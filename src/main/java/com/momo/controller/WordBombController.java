package com.momo.controller;


import com.momo.dto.KillRequest;
import com.momo.model.Word;
import com.momo.dto.Req;
import com.momo.repository.WordRepository;
import com.momo.service.MasteredJsonStore;
import com.momo.service.WordService;

import com.momo.service.WordState;
import com.momo.utils.HelperUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/words")
@CrossOrigin(origins = "*") // 允许前端本地网页跨域访问
public class WordBombController {

    @Autowired
    private WordService wordService;

    @Autowired
    private WordRepository wordRepository;

    /**
     * 1. 获取左侧单词书选项列表
     */
    @GetMapping("/books")
    public List<Map<String, String>> getWordBooks() {

        List<Map<String, String>> wordBooks = Stream.of(new AbstractMap.SimpleEntry<>("kaoyan", "2027考研闪过"), new AbstractMap.SimpleEntry<>("zhongkao", "中考核心词汇"), new AbstractMap.SimpleEntry<>("gaokao", "高考突击词汇"), new AbstractMap.SimpleEntry<>("cet46", "四六级通关词")

        ).map(entry -> {
            Map<String, String> map = new HashMap<>();
            map.put("id", entry.getKey());
            map.put("name", entry.getValue());
            return map;
        }).collect(Collectors.toList());

        return wordBooks;

    }

//    /**
//     * 2. 根据选定的单词书获取其全部单词
//     */
//    @GetMapping("/list")
//    public List<Map<String, Object>> getWordsByBook(@RequestParam("book") String bookName) {
//
//        return wordService.getStaticWordsByBook(bookName);
//    }

    // 。1. 标记已掌握 API
    @PostMapping("/mastered")
    public void mastered(@RequestBody Req req) {

        Map<String, List<String>> all = MasteredJsonStore.readAll();

        List<String> list = all.getOrDefault(req.userId, new ArrayList<>());

        if (!list.contains(req.book)) {
            list.add(req.book);
        }

        all.put(req.userId, list);

        MasteredJsonStore.writeAll(all);
    }

    @PostMapping("/mastered/kill")
    public void kill(@RequestBody KillRequest req) {
        MasteredJsonStore.kill(req.getUser(), req.getBook(), req.getWord());
    }


    /**
     * 3. 获取某本单词书被斩杀的熟词
     */
    @GetMapping("/killed")
    public Set<String> getKilledWords(@RequestParam("book") String bookName) {
        return wordService.getKilledWordsByBook(bookName);
    }


    @GetMapping("/getWords")
    public ResponseEntity<?> getWordsPage(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "300") int size) {

        // 使用 Pageable 数据库分页查询
        // Pageable pageable = PageRequest.of(page, size);
//        return ResponseEntity.ok(wordRepository.findAll(pageable));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/page")
    public Map<String, Object> getWordsByPage(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String category) {

        // 1. 读取 words_data.json 并筛选 category (参考原有逻辑)
        //List<Word> allWords = loadAllWordsFromFile();
        List<Map<String, Object>> kaoyan = wordService.getStaticWordsByBook("kaoyan");

        List<Word> allWords = new ArrayList<>();

        allWords = HelperUtil.convertWithBeanUtils(kaoyan);
        List<Word> filtered = allWords.stream().filter(w -> category == null || w.getCategory().equals(category)).collect(Collectors.toList());

        // 2. 计算分页切片
        int total = filtered.size();
        int start = (page - 1) * size;
        int end = Math.min(start + size, total);

        List<Word> pageList = (start > total) ? new ArrayList<>() : filtered.subList(start, end);


        // 3. 返回结构
        Map<String, Object> response = new HashMap<>();
        response.put("data", pageList);
        response.put("total", total);
        response.put("totalPages", (int) Math.ceil((double) total / size));
        response.put("currentPage", page);

        return response;
    }


    // 模拟你原有的获取整本书全量单词的方法，实际开发中替换成你从本地 json 读出来的全量 List
    private List<Map<String, String>> mockLoadAllWordsFromBook(String bookName) {
        // 这里对接你原本的词库加载逻辑，比如从 resources/kaoyan.json 读取全部单词
        return new ArrayList<>();
    }

    /**
     * 1. 核心大轰炸动态队列获取接口
     */
    @GetMapping("/getSmartQueue")
    public List<Map<String, String>> getSmartQueue(@RequestParam String bookName) {
        //List<Map<String, String>> allWords = mockLoadAllWordsFromBook(bookName);

        List<Map<String, Object>> objectList = wordService.getStaticWordsByBook("kaoyan");
        List<Map<String, String>> allWords = HelperUtil.convertToStringMapList(objectList);
        // 调用 Service 生成科学分配的 20 个词
        return wordService.generateSmartQueue(bookName, allWords, 20);
    }

    /**
     * 2. 用户点击【掌握】时的状态推进接口
     */
    @PostMapping("/master")
    public Map<String, String> masterWord(@RequestBody Map<String, String> payload) {
        String bookName = payload.get("book");
        String word = payload.get("word");

        WordState ws = wordService.getOrInitWordState(bookName, word);

        // 推进艾宾浩斯 CD
        wordService.promoteWordCD(ws);
        // 保存更新
        wordService.updateWordState(bookName, ws);

        Map<String, String> response = new HashMap<>();
        response.put("newState", ws.getStatus());
        return response;
    }

    /**
     * 3. 用户点击【陌生 / 翻车】时的惩罚打回接口
     */
    @PostMapping("/wrong")
    public Map<String, String> wrongWord(@RequestBody Map<String, String> payload) {
        String bookName = payload.get("book");
        String word = payload.get("word");

        WordState ws = wordService.getOrInitWordState(bookName, word);

        // 执行魔鬼降级惩罚，直接打回突击营重修
        wordService.punishWordToQueue(ws);
        // 保存更新
        wordService.updateWordState(bookName, ws);

        Map<String, String> response = new HashMap<>();

        response.put("newState", ws.getStatus());
        return response;
    }
}