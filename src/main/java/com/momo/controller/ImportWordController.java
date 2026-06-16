package com.momo.controller;

import com.momo.model.Word;
import com.momo.service.WordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * 导入的页面对于的后台接口
 */
@RestController
@RequestMapping("/api/words")
public class ImportWordController {

    private final String DATA_FILE_PATH = System.getProperty("user.dir") + "/src/main/resources/static/books/kaoyan.json";


    @Autowired
    private WordService wordService;

    /**
     * 1. 获取左侧单词书选项列表
     */
    @GetMapping("/import/books")
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

    /**
     * 2. 单条录入新词
     */
    @PostMapping("/import/words")
    public Map<String, Object> addSingleWord(@RequestBody Word newWord) {
        return batchImportWords(Collections.singletonList(newWord));
    }


    /**
     * 1. 查询全部词库数据（已升级为流式扫描引擎，彻底杜绝 StackOverflowError）
     */
    @GetMapping("/import/words")
    public List<Word> getAllWords() {
        List<Word> list = new ArrayList<>();
        File file = new File(DATA_FILE_PATH);
        if (!file.exists()) return list;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            String content = sb.toString().trim();
            if (content.isEmpty() || "[]".equals(content)) return list;

            // ================= 工业级单指针流式扫描内核 =================
            int len = content.length();
            int i = 0;
            // 越过最外层数组的 '['
            while (i < len && content.charAt(i) != '[') {
                i++;
            }
            i++; // 跳过 '['

            while (i < len) {
                // 1. 寻找当前单词大括号 '{' 的起点
                while (i < len && content.charAt(i) != '{') {
                    if (content.charAt(i) == ']') break; // 整个数组到头了
                    i++;
                }
                if (i >= len || content.charAt(i) == ']') break;
                i++; // 越过 '{'

                // 2. 截取当前大括号内部的所有键值对文本
                StringBuilder objBlock = new StringBuilder();
                while (i < len && content.charAt(i) != '}') {
                    objBlock.append(content.charAt(i));
                    i++;
                }
                i++; // 越过 '}'

                // 3. 精准无错解析单条对象内部的属性 (不再依赖 split 产生的大型数组)
                String blockStr = objBlock.toString().trim();
                if (!blockStr.isEmpty()) {
                    Word w = parseSingleWordBlock(blockStr);
                    if (w.getWord() != null && !w.getWord().isEmpty()) {
                        list.add(w);
                    }
                }
            }
            // =========================================================

        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }


    /**
     * 辅助解析核心：精准逐字符提取 K-V 对，完美兼容带逗号的例句
     */
    private Word parseSingleWordBlock(String block) {
        Word w = new Word();
        int len = block.length();
        int i = 0;
        while (i < len) {
            // 寻找Key的起点引号
            while (i < len && block.charAt(i) != '"') i++;
            if (i >= len) break;
            i++; // 跳过开引号

            // 提取Key
            StringBuilder keySb = new StringBuilder();
            while (i < len && block.charAt(i) != '"') {
                keySb.append(block.charAt(i));
                i++;
            }
            i++; // 跳过闭引号

            // 寻找冒号
            while (i < len && block.charAt(i) != ':') i++;
            i++; // 跳过冒号

            // 寻找Val的起点引号
            while (i < len && block.charAt(i) != '"') i++;
            if (i >= len) break;
            i++; // 跳过开引号

            // 提取Val（完美兼容内部带有英文逗号的例句文本）
            StringBuilder valSb = new StringBuilder();
            while (i < len && block.charAt(i) != '"') {
                // 简单处理转义字符
                if (block.charAt(i) == '\\' && i + 1 < len && block.charAt(i + 1) == '"') {
                    valSb.append('"');
                    i += 2;
                    continue;
                }
                valSb.append(block.charAt(i));
                i++;
            }
            i++; // 跳过闭引号

            String key = keySb.toString().trim();
            String val = valSb.toString().trim();
            switch (key) {
                case "id":
                    w.setId(val);
                    break;
                case "word":
                    w.setWord(val);
                    break;
                case "phonetic":
                    w.setPhonetic(val);
                    break;
                case "definition":
                    w.setDefinition(val);
                    break;
                case "example":
                    w.setExample(val);
                    break;
                case "category":
                    w.setCategory(val);
                    break;
            }

            // 迈向下一个K-V对
            while (i < len && block.charAt(i) != ',') i++;
            i++; // 跳过逗号
        }
        return w;
    }



    /**
     * 3. 批量追加词汇集
     */
    @PostMapping("/batch-import")
    public Map<String, Object> batchImportWords(@RequestBody List<Word> newWords) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Word> currentWords = getAllWords();
            for (Word w : newWords) {
                w.setId("momo_auto_" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 8));
                w.setBookId("kaoyan"); // 显式设置默认状态
                currentWords.add(w);
            }
            saveToFile(currentWords);
            wordService.importToDatabase(currentWords);
            response.put("status", "success");
            response.put("count", newWords.size());
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        return response;
    }

    /**
     * 4. 删除指定词条
     */
    @DeleteMapping("/words/{id}")
    public Map<String, Object> deleteWord(@PathVariable String id) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Word> currentWords = getAllWords();
            currentWords.removeIf(w -> w.getId().equals(id));
            saveToFile(currentWords);
            response.put("status", "success");
        } catch (Exception e) {
            response.put("status", "error");
        }
        return response;
    }

    // 将全量庞大词库高效写回本地物理持久化 JSON 文件
    private void saveToFile(List<Word> words) throws IOException {
        File file = new File(DATA_FILE_PATH);
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < words.size(); i++) {
            Word w = words.get(i);
            // 深度清洗数据中可能导致前端冲突的换行符
            String cleanDef = w.getDefinition().replace("\n", " ").replace("\"", "\\\"");
            String cleanEx = w.getExample().replace("\n", " ").replace("\"", "\\\"");

            sb.append(String.format("  {\"id\":\"%s\",\"word\":\"%s\",\"phonetic\":\"%s\",\"definition\":\"%s\",\"example\":\"%s\",\"category\":\"%s\"}",
                    w.getId(), w.getWord(), w.getPhonetic(), cleanDef, cleanEx, w.getCategory()));
            if (i < words.size() - 1) sb.append(",\n");
        }
        sb.append("\n]");
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            bw.write(sb.toString());
        }
    }


}