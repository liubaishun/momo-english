package com.momo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class LlmApiService {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(LlmApiService.class);

    // 1. 🛰️ 接收配置文件里用逗号隔开的原始 Key 字符串
    @Value("${llm.api-key}")
    private String rawApiKeys;

    @Value("${llm.base-url:https://generativelanguage.googleapis.com/v1beta/openai}")
    private String baseUrl;

    @Value("${llm.model:gemini-2.5-flash}")
    private String modelName;

    // 2. 🗃️ 战术装填阵列与线程安全轮询指针
    private List<String> apiKeyList = new ArrayList<>();
    private final AtomicInteger indexPointer = new AtomicInteger(0);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .protocols(Arrays.asList(Protocol.HTTP_1_1))
            .build();

    // 3. 🏁 初始化生命周期挂钩：项目启动时自动将字符串切割洗净装填进阵列
    @PostConstruct
    public void initKeyArray() {
        if (rawApiKeys != null && !rawApiKeys.trim().isEmpty()) {
            String[] keys = rawApiKeys.split(",");
            for (String key : keys) {
                if (!key.trim().isEmpty()) {
                    apiKeyList.add(key.trim());
                }
            }
        }
        log.info("🛡️ [战术多Key系统] 阵列初始化完毕，当前在线火力点共计: {} 个", apiKeyList.size());
        if (apiKeyList.isEmpty()) {
            log.error("❌ [严重警告] 未检测到任何可用的 Gemini API Key，请检查配置文件！");
        }
    }

    /**
     * 🛰️ 获取当前轮班的 API Key
     */
    private String getNextAvailableKey() {
        if (apiKeyList.isEmpty()) {
            return "";
        }
        // 使用原子计数器防止多线程高频切词并发时越界，取模实现无限循环轮询
        int idx = Math.abs(indexPointer.getAndIncrement() % apiKeyList.size());
        return apiKeyList.get(idx);
    }

    public String callLargeLanguageModel(String word) {
        String systemPrompt = "你是一个精通印欧语系语言学、认知心理学与英语词源学专家。" +
                "你必须严格按照要求的 JSON 格式返回数据，不要包含任何 Markdown 标记（如 ```json 标记），不要包含多余解释。\n" +
                "【重要高亮规则】：在输出 `memory_story`（联想故事）和 `etymology`（词源）时，" +
                "必须将映射词根核心含义的中文关键词，使用 <mark>标签 包裹起来进行视觉强调。";

        String userPrompt = String.format(
                "请对单词 \"%s\" 进行全方位的深度认知分析。返回的 JSON 结构模板必须如下：\n" +
                        "{\n" +
                        "  \"word\": \"%s\",\n" +
                        "  \"phonetic\": \"标准音标\",\n" +
                        "  \"root_breakdown\": [{\"part\": \"部件\", \"meaning\": \"核心含义\", \"type\": \"prefix|root|suffix\"}],\n" +
                        "  \"etymology\": \"历史词源演变核心点\",\n" +
                        "  \"memory_story\": \"符合认知直觉的记忆口诀或联想故事\",\n" +
                        "  \"family_words\": [{\"word\": \"同源词\", \"meaning\": \"中文释义\"}],\n" +
                        "  \"example_sentences\": [{\"en\": \"英文例句\", \"zh\": \"中文翻译\"}]\n" +
                        "}", word, word
        );

        int maxRetry = 4;       // 最大轰炸重试次数
        int retryCount = 0;     // 当前重试计数
        long delay = 2500;      // 初始延迟 2.5 秒

        while (true) {
            // 🎯 核心重构：每次循环（无论是首次还是 429 重试），都重新去捞一个轮班的 Key！
            String activeKey = getNextAvailableKey();

            try {
                Map<String, Object> requestMap = new HashMap<>();
                requestMap.put("model", modelName);
                requestMap.put("messages", Arrays.asList(
                        new HashMap<String, Object>() {{ put("role", "system"); put("content", systemPrompt); }},
                        new HashMap<String, Object>() {{ put("role", "user");   put("content", userPrompt); }}
                ));
                requestMap.put("temperature", 0.2);

                String requestJson = objectMapper.writeValueAsString(requestMap);
                RequestBody body = RequestBody.create(requestJson, MediaType.parse("application/json; charset=utf-8"));

                String cleanUrl = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";

                Request request = new Request.Builder()
                        .url(cleanUrl)
                        .addHeader("Authorization", "Bearer " + activeKey) // 👈 注入当前轮班的 Key
                        .post(body)
                        .build();

                if (retryCount == 0) {
                    // 脱敏打印当前的 Key 的前 6 位，方便后台监控轮询状态
                    String keyMask = activeKey.length() > 6 ? activeKey.substring(0, 6) + "***" : "UnknownKey";
                    log.info("🚀 正在将单词 [{}] 轰炸至 Gemini, 执勤Key: [{}], URL: {}", word, keyMask, cleanUrl);
                } else {
                    log.warn("🔄 正在对单词 [{}] 发起第 {} 次战术重试，已自动切换值班 Key...", word, retryCount);
                }

                try (Response response = client.newCall(request).execute()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    int statusCode = response.code();

                    // 🎯 429 / 503 铁血熔断隔离墙
                    if (statusCode == 429 || statusCode == 503) {
                        if (++retryCount < maxRetry) {
                            String errorType = (statusCode == 429) ? "429 频次受限" : "503 服务器过载";
                            log.warn("⚠️ 触碰大模型边界墙 [{}], 触发自动战术后撤，{} 秒后自动换 Key 重试...",
                                    errorType, (double) delay / 1000);

                            try { Thread.sleep(delay); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                            delay *= 2;
                            continue;   // ⚡ 直接冲进下一次 while 循环，自动换下一个 Key 重新冲锋！
                        }
                    }

                    if (!response.isSuccessful()) {
                        log.error("❌ 大模型请求失败！状态码: {}, 错误响应: {}", statusCode, responseBody);
                        throw new IOException("大模型服务器拒绝，状态码: " + statusCode);
                    }

                    JsonNode rootNode = objectMapper.readTree(responseBody);
                    JsonNode choices = rootNode.path("choices");
                    if (choices.isMissingNode() || choices.isEmpty()) {
                        throw new RuntimeException("大模型未返回有效文本内容");
                    }

                    String rawContent = choices.get(0).path("message").path("content").asText();

                    if (rawContent.contains("```")) {
                        rawContent = rawContent.replaceAll("```json|```", "").trim();
                    }

                    return rawContent;
                }

            } catch (Exception e) {
                if (++retryCount < maxRetry) {
                    log.warn("💥 链路捕获异常 [{}], {} 秒后发起第 {} 次战术修复重试...", e.getMessage(), (double) delay / 1000, retryCount);
                    try { Thread.sleep(delay); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    delay *= 2;
                } else {
                    log.error("💥 大模型多 Key 调用链路彻底崩塌，已重试 {} 次，单词: {}", maxRetry, word, e);
                    throw new RuntimeException("大模型网络总线异常: " + e.getMessage(), e);
                }
            }
        }
    }
}