package com.momo.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.momo.dto.WordAiDTO;
import com.momo.model.WordAiAnalysisEntity;
import com.momo.repository.WordAiAnalysisRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class WordAiService {

    @Autowired
    private WordAiAnalysisRepository aiRepository;

    @Autowired
    private LlmApiService llmApiService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${llm.api-key}")
    private String apiKey;

    @Value("${llm.base-url}")
    private String baseUrl;

    @Value("${llm.model}")
    private String model;

    /**
     * 获取或生成单词的 AI 分析（主核心业务方法）
     */
    public WordAiDTO getOrGenerateAnalysis(String word) {
        if (word == null || word.trim().isEmpty()) {
            throw new IllegalArgumentException("查询的单词不能为空");
        }

        String cleanWord = word.trim().toLowerCase();

        // 1. 战术查库（缓存命中）
        Optional<WordAiAnalysisEntity> cacheOpt = aiRepository.findByWord(cleanWord);
        if (cacheOpt.isPresent()) {
            // 💡 优雅复用你下面写好的原子化转换器
            return convertToDTO(cacheOpt.get());
        }

        // 2. 缓存未命中，击穿至 AI
        String rawJson = llmApiService.callLargeLanguageModel(cleanWord);

        // 3. 反序列化 DTO 并保存实体入库
        try {
            WordAiDTO aiDto = objectMapper.readValue(rawJson, WordAiDTO.class);

            WordAiAnalysisEntity newEntity = new WordAiAnalysisEntity();
            newEntity.setWord(cleanWord);
            newEntity.setPhonetic(aiDto.getPhonetic());
            newEntity.setEtymology(aiDto.getEtymology());
            newEntity.setMemoryStory(aiDto.getMemoryStory());

            // 将对象列表序列化为标准的 JSON 字符串保存入库
            newEntity.setRootBreakdown(objectMapper.writeValueAsString(aiDto.getRootBreakdown()));
            newEntity.setFamilyWords(objectMapper.writeValueAsString(aiDto.getFamilyWords()));
            newEntity.setExampleSentences(objectMapper.writeValueAsString(aiDto.getExampleSentences()));

            aiRepository.save(newEntity);
            return aiDto;
        } catch (Exception e) {
            // log.error("AI 吐出的格式不合规，反序列化轰炸机坠毁。原始数据: {}", rawJson, e);
            throw new RuntimeException("AI 数据装填发生格式故障", e);
        }
    }

    /**
     * 🛰️ 原子化转换器：将持久层 Entity 的 JSON 脏数据，清洗反序列化为规整的前端 DTO
     */
    private WordAiDTO convertToDTO(WordAiAnalysisEntity entity) {
        WordAiDTO dto = new WordAiDTO();

        // 1. 映射基础文本字段
        dto.setWord(entity.getWord());
        dto.setPhonetic(entity.getPhonetic());
        dto.setEtymology(entity.getEtymology());
        dto.setMemoryStory(entity.getMemoryStory());

        // 2. 战术解析：精准还原词根拆解 JSON 数组
        try {
            if (entity.getRootBreakdown() != null && !entity.getRootBreakdown().isEmpty()) {
                List<WordAiDTO.RootBreakdownItem> rootList = objectMapper.readValue(
                        entity.getRootBreakdown(),
                        new TypeReference<List<WordAiDTO.RootBreakdownItem>>() {}
                );
                dto.setRootBreakdown(rootList);
            } else {
                dto.setRootBreakdown(new ArrayList<>());
            }
        } catch (Exception e) {
            // log.error("❌ 单词 [{}] 词根拆解字段反序列化故障", entity.getWord(), e);
            dto.setRootBreakdown(new ArrayList<>());
        }

        // 3. 战术解析：精准还原同源家族词 JSON 数组
        try {
            if (entity.getFamilyWords() != null && !entity.getFamilyWords().isEmpty()) {
                List<WordAiDTO.FamilyWordItem> familyList = objectMapper.readValue(
                        entity.getFamilyWords(),
                        new TypeReference<List<WordAiDTO.FamilyWordItem>>() {}
                );
                dto.setFamilyWords(familyList);
            } else {
                dto.setFamilyWords(new ArrayList<>());
            }
        } catch (Exception e) {
            // log.error("❌ 单词 [{}] 同源衍生族反序列化故障", entity.getWord(), e);
            dto.setFamilyWords(new ArrayList<>());
        }

        // 4. 战术解析：精准还原 AI 例句 JSON 数组
        try {
            if (entity.getExampleSentences() != null && !entity.getExampleSentences().isEmpty()) {
                List<WordAiDTO.ExampleSentenceItem> sentenceList = objectMapper.readValue(
                        entity.getExampleSentences(),
                        new TypeReference<List<WordAiDTO.ExampleSentenceItem>>() {}
                );
                dto.setExampleSentences(sentenceList);
            } else {
                dto.setExampleSentences(new ArrayList<>());
            }
        } catch (Exception e) {
            // log.error("❌ 单词 [{}] 场景例句集群反序列化故障", entity.getWord(), e);
            dto.setExampleSentences(new ArrayList<>());
        }

        return dto;
    }
}