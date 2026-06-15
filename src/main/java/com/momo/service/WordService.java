package com.momo.service;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

@Service
public class WordService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    // 动态斩杀数据持久化本地路径
    private final String DATA_PATH = "data/";
    private final String KILLED_FILE_PATH = "killed_data.json";

    // 内存缓存：存放所有书的熟词库。 结构：{ "kaoyan": ["abandon", "evaluate"], "zhongkao": ["apple"] }
    private Map<String, Set<String>> killedDataMap = new HashMap<>();

    @PostConstruct
    public void init() {
        try {
            File file = new File(KILLED_FILE_PATH);
            if (file.exists()) {
                // 方法二：读取字节数组后转换（需指定编码）
                byte[] bytes = Files.readAllBytes(Paths.get(KILLED_FILE_PATH));
                String content = new String(bytes, StandardCharsets.UTF_8);
                killedDataMap = objectMapper.readValue(content, new TypeReference<Map<String, Set<String>>>() {
                });
            } else {
                // 初始化空文件
                // ... 在方法内部
                try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(KILLED_FILE_PATH), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    writer.write("{}");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            killedDataMap = new HashMap<>();
        }
    }

    /**
     * 读取指定单词书的静态资源
     */
    public List<Map<String, Object>> getStaticWordsByBook(String bookName) {
        try {
            // 比如存放在 src/main/resources/static/books/kaoyan.json
            ClassPathResource resource = new ClassPathResource("static/books/" + bookName + ".json");
            if (!resource.exists()) {
                return Collections.emptyList();
            }
            if (!resource.exists() || resource.contentLength() == 0) {
                return Collections.emptyList();
            }

            List<Map<String, Object>> mapList = objectMapper.readValue(
                    resource.getInputStream(),
                    new TypeReference<List<Map<String, Object>>>() {}
            );

            Map<String, List<String>> all = MasteredJsonStore.readAll();
            List<String> mastered = all.getOrDefault("u001", new ArrayList<>());


            return mapList;
        } catch (IOException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    /**
     * 获取某本书的熟词
     */
    public Set<String> getKilledWordsByBook(String bookName) {
        return killedDataMap.getOrDefault(bookName, new HashSet<>());
    }

    /**
     * 斩杀单词
     */
    public synchronized void killWord(String bookName, String word) {
        killedDataMap.computeIfAbsent(bookName, k -> new HashSet<>()).add(word);
        saveToFile();
    }

    /**
     * 批量或单个还原单词
     */
    public synchronized void restoreWords(String bookName, List<String> words) {
        if (killedDataMap.containsKey(bookName)) {
            killedDataMap.get(bookName).removeAll(words);
            saveToFile();
        }
    }

    private  synchronized void saveToFile() {
        ObjectMapper objectMapper = new ObjectMapper();
        Path path = Paths.get(KILLED_FILE_PATH);
        try {
            // 只有当路径中包含目录结构时，才去创建目录
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            // 2. 序列化
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(killedDataMap);

            // 3. 写入文件 (将字符串转为 UTF-8 字节数组)
            Files.write(Paths.get(KILLED_FILE_PATH), json.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}