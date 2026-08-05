package com.ai;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.bge.small.en.v15.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.util.List;

public class RagDemoApplication {

    // 🔑 替换为你的真实 API Key（例如阿里云百炼/DeepSeek/SiliconFlow 生成的 Key）
    private static final String API_KEY = "sk-lrfyyomulujgvppfqhzdjpdktgwtaahhqhdoiqswwvgcxvys";
    // 🌐 如果使用阿里云百炼，BaseURL 为: https://dashscope.aliyuncs.com/compatible-mode/v1
    // 🌐 如果使用 DeepSeek，BaseURL 为: https://api.deepseek.com
    private static final String BASE_URL = "https://api.siliconflow.cn/v1";

    public static void main(String[] args) {
        System.out.println("================== 🚀 RAG 全流程 Demo 启动 ==================\n");

        // 1. 初始化 Chat 模型 (用于第四步生成回答)
        // 初始化 Chat 模型
        ChatLanguageModel chatModel = OpenAiChatModel.builder()
                .baseUrl(BASE_URL)
                .apiKey(API_KEY)
                // 推荐使用的开源高性价比/免费模型
                .modelName("Qwen/Qwen2.5-72B-Instruct")
                // 👈 RAG 场景务必设为 0.0，防止模型胡言乱语或修改数字
                .temperature(0.0)
                .build();

        // 2. 初始化本地 Embedding 模型（重点修正：本地运行，不消耗 API，不走网络）
        EmbeddingModel embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();
        EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

        // =========================================================================
        // 第一步：准备资料（建知识库 / 预处理）
        // =========================================================================
        System.out.println("【第一步】：准备资料 & 切片 & 存入向量数据库...");

        String rawCorporatePolicy = "《2026年企业员工管理规章手册》\n" +
                "第一条 考勤制度：员工工作时间为 09:00 - 18:00，午休 1.5 小时。\n" +
                "第二条 差旅补贴标准：\n" +
                "   1. 交通补贴：员工出差优先乘坐高铁二等座，飞机需部门VP特批。\n" +
                "   2. 餐饮补贴：员工国内出差每日餐饮补贴统一为 150 元，凭发票报销或按天直接发放。\n" +
                "   3. 住宿标准：一线城市（北上广深）住宿标准上限为 500 元/晚，其他城市上限 350 元/晚。\n" +
                "第三条 年假规则：入职满一年享有 7 天带薪年假，每多一年增加 1 天，上限 15 天。";

        // 1.1 文档切片
        Document document = Document.from(rawCorporatePolicy);
        DocumentSplitter splitter = DocumentSplitters.recursive(200, 30);
        List<TextSegment> segments = splitter.split(document);

        // 1.2 向量化并存入内存向量库
        for (TextSegment segment : segments) {
            embeddingStore.add(embeddingModel.embed(segment).content(), segment);
        }
        System.out.println("✅ 成功将规章手册切分为 " + segments.size() + " 个小片段并存入向量库！\n");

        // =========================================================================
        // 第二步：精准翻书（向量检索）
        // =========================================================================
        String userQuestion = "我们公司出差一天餐饮补贴多少钱？按什么标准报销？";
        System.out.println("【第二步】：用户提问 👉 \"" + userQuestion + "\"");

        // 检索匹配度最高的前 2 个片段
        List<EmbeddingMatch<TextSegment>> relevantMatches = embeddingStore.findRelevant(
                embeddingModel.embed(userQuestion).content(), 2
        );

        StringBuilder contextBuilder = new StringBuilder();
        for (int i = 0; i < relevantMatches.size(); i++) {
            String text = relevantMatches.get(i).embedded().text();
            System.out.println(" 📄 抓取到的相关卡片 [" + (i + 1) + "]: " + text.trim());
            contextBuilder.append(text).append("\n");
        }
        System.out.println("✅ 精准抓取完成！\n");

        // =========================================================================
        // 第三步：组合提示词（Prompt 拼接）
        // =========================================================================
        System.out.println("【第三步】：组装带有【背景资料】的提示词（Prompt）...");

        // 组装 Prompt 时可以加强约束
        String prompt = "你是一个严格严谨的企业知识库助手。\n" +
                "请严格根据下面提供的【背景资料】来回答用户的问题。\n" +
                "【重要规则】：必须准确还原背景资料中的任何数字、年份和文本，严禁擅自修改数字，严禁凭空想象！\n\n" +
                "【背景资料】:\n" +
                "第三条 年假规则：入职满一年享有 7 天带薪年假，每多一年增加 1 天，上限 15 天。\n" +
                "《2026年企业员工管理规章手册》\n" +
                "第一条 考勤制度：员工工作时间为 09:00 - 18:00，午休 1.5 小时。\n" +
                "第二条 差旅补贴标准：\n" +
                "1. 交通补贴：员工出差优先乘坐高铁二等座，飞机需部门VP特批。\n" +
                "2. 餐饮补贴：员工国内出差每日餐饮补贴统一为 150 元，凭发票报销或按天直接发放。\n" +
                "3. 住宿标准：一线城市（北上广深）住宿标准上限为 500 元/晚，其他城市上限 350 元/晚。\n\n" +
                "【用户问题】:\n" +
                "我们公司出差一天餐饮补贴多少钱？按什么标准报销？";

        System.out.println("--- 最终送给大模型的 Prompt ---");
        System.out.println(prompt);
        System.out.println("----------------------------------\n");

        // =========================================================================
        // 第四步：输出精准答案
        // =========================================================================
        System.out.println("【第四步】：大模型生成回答中...");
        String response = chatModel.generate(prompt);

        System.out.println("\n🎉 【大模型最终回答】：");
        System.out.println(response);
        System.out.println("\n================== 🏁 RAG 流程结束 ==================");
    }
}