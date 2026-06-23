package com.momo.model;


import javax.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "word_ai_analysis")
public class WordAiAnalysisEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String word;

    @Column(length = 100)
    private String phonetic;

    @Column(columnDefinition = "TEXT")
    private String etymology;

    @Column(name = "memory_story", columnDefinition = "TEXT")
    private String memoryStory;

    // 🎯 直接将 JSON 字符串映射为具体的持久化文本，交由 Service 层或 Converter 处理
    @Column(name = "root_breakdown", columnDefinition = "JSON")
    private String rootBreakdown;

    @Column(name = "family_words", columnDefinition = "JSON")
    private String familyWords;

    @Column(name = "example_sentences", columnDefinition = "JSON")
    private String exampleSentences;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public String getPhonetic() {
        return phonetic;
    }

    public void setPhonetic(String phonetic) {
        this.phonetic = phonetic;
    }

    public String getEtymology() {
        return etymology;
    }

    public void setEtymology(String etymology) {
        this.etymology = etymology;
    }

    public String getMemoryStory() {
        return memoryStory;
    }

    public void setMemoryStory(String memoryStory) {
        this.memoryStory = memoryStory;
    }

    public String getRootBreakdown() {
        return rootBreakdown;
    }

    public void setRootBreakdown(String rootBreakdown) {
        this.rootBreakdown = rootBreakdown;
    }

    public String getFamilyWords() {
        return familyWords;
    }

    public void setFamilyWords(String familyWords) {
        this.familyWords = familyWords;
    }

    public String getExampleSentences() {
        return exampleSentences;
    }

    public void setExampleSentences(String exampleSentences) {
        this.exampleSentences = exampleSentences;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}