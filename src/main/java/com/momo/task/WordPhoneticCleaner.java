//package com.momo.task;
//
//import com.momo.model.Word;
//import com.momo.repository.WordRepository;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.http.HttpEntity;
//import org.springframework.http.HttpHeaders;
//import org.springframework.http.HttpMethod;
//import org.springframework.http.ResponseEntity;
//import org.springframework.http.client.SimpleClientHttpRequestFactory;
//import org.springframework.stereotype.Component;
//import org.springframework.web.client.RestTemplate;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;
//
///**
// * 🛰️ 深度潜航网络清洗总线（极速防封 + 跨境高延迟优化版）
// * 核心战术：1.5秒极致限速 -> 10秒跨境超时容错 -> 100% 模拟真人浏览器 Header
// */
//@Component
//public class WordPhoneticCleaner implements CommandLineRunner {
//
//    private final WordRepository wordRepository;
//    private final RestTemplate restTemplate;
//
//    public WordPhoneticCleaner(WordRepository wordRepository) {
//        this.wordRepository = wordRepository;
//
//        // 🎯 核心调校 1：拉长连接超时与读取超时，给跨境高延迟留足缓冲空间（10秒）
//        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
//        factory.setConnectTimeout(15000);
//        factory.setReadTimeout(15000);
//        this.restTemplate = new RestTemplate(factory);
//    }
//
//    @Override
//    public void run(String... args) {
//        System.out.println("⚠️ [深海潜航清洗总线] 正在启动，正在盘点真实的 word 库...");
//
//        // 1. 过滤幽灵数据，锁定你真实的 5543 条词书大盘
//        List<Word> dirtyWords = wordRepository.findActualDirtyWords();
//
//        if (dirtyWords == null || dirtyWords.isEmpty()) {
//            System.out.println("🟢 [深海潜航清洗总线] 大盘一片丝滑！有效单词未发现 '---'，清洗停止。");
//            return;
//        }
//
//        System.out.printf("🚨 [深海潜航清洗总线] 清点完毕！捕获到本词书内 %d 个脏音标单词！\n", dirtyWords.size());
//        System.out.println("🚀 降速潜航模式开启，正在以 1.5 秒/词 的安全频率突破 Cloudflare 防线...");
//
//        List<Word> readyToSave = new ArrayList<>();
//        int successCount = 0;
//
//        for (int i = 0; i < dirtyWords.size(); i++) {
//            Word entity = dirtyWords.get(i);
//            String rawWord = entity.getWord().trim().toLowerCase();
//
//            // 2. 核心网络突围：携带浏览器伪装请求 Dictionary API
//            String realPhonetic = fetchPhoneticWithBrowserDisguise(rawWord);
//
//            if (realPhonetic != null && !realPhonetic.isEmpty()) {
//                entity.setPhonetic(realPhonetic);
//                successCount++;
//                System.out.printf("🔄 [%d/%d] 跨境同步成功: %s -> [%s]\n", i + 1, dirtyWords.size(), rawWord, realPhonetic);
//            } else {
//                // 如果该词在公网上确实没查到（或偶然断线），设为空白占位，防止再次拉取，且绝不留下 '---'
//                entity.setPhonetic("");
//                System.out.printf("❌ [%d/%d] 跨境链路穿透失败(或无此词): %s\n", i + 1, dirtyWords.size(), rawWord);
//            }
//
//            readyToSave.add(entity);
//
//            // 3. 📦 每 20 个词强行刷一次盘（由于速度变慢，调小批次防止内存挂起）
//            if (readyToSave.size() >= 20) {
//                wordRepository.saveAll(readyToSave);
//                wordRepository.flush();
//                readyToSave.clear();
//            }
//
//            // 🛡️ 战术黄金线：将访问间隔拉长至 1500 毫秒（1.5秒），彻底洗白你的请求特征，规避封锁风险
//            try {
//                Thread.sleep(2000);
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//            }
//        }
//
//        // 提交余数
//        if (!readyToSave.isEmpty()) {
//            wordRepository.saveAll(readyToSave);
//            wordRepository.flush();
//        }
//
//        System.out.printf("🏁 [深海潜航清洗总线] 刮骨疗毒圆满结束！标准 IPA 音标已成功成功补全 %d 个，SQLite 固化闭合！\n", successCount);
//    }
//
//    /**
//     * 🛰️ 核心突围函数：携带真人浏览器 Header 壳，并 100% 保留首尾斜杠 /.../ 格式
//     */
//    private String fetchPhoneticWithBrowserDisguise(String word) {
//        if (word == null || word.trim().isEmpty()) return null;
//        try {
//            String cleanWord = word.trim().toLowerCase();
//            String url = "https://api.dictionaryapi.dev/api/v2/entries/en/" + cleanWord;
//
//            HttpHeaders headers = new HttpHeaders();
//            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
//            headers.set("Accept", "application/json");
//            HttpEntity<String> entity = new HttpEntity<>(headers);
//
//            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
//
//            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
//                String rawBody = response.getBody();
//
//                // 🎯 潜航调校 3：修正正则捕获组，将两侧的斜杠直接打包进 matcher.group(1)
//                Pattern phoneticPattern = Pattern.compile("\"phonetic\"\\s*:\\s*\"(/-?[^/^\"]+/)\"");
//                Matcher matcher = phoneticPattern.matcher(rawBody);
//                if (matcher.find()) {
//                    return matcher.group(1).trim(); // 👈 完美返回带斜杠样式，例如：/ˌɒpəˈɹeɪʃənz/
//                }
//
//                // 备用文本节点正则，同样死锁斜杠
//                Pattern textPattern = Pattern.compile("\"text\"\\s*:\\s*\"(/-?[^/^\"]+/)\"");
//                Matcher textMatcher = textPattern.matcher(rawBody);
//                if (textMatcher.find()) {
//                    return textMatcher.group(1).trim();
//                }
//            }
//        } catch (Exception e) {
//            // 静默消化跨境网络波动
//        }
//        return null;
//    }
//}