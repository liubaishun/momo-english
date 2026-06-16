package com.momo.model;

import lombok.Data;


@Data
public class WordDetailDTO {
    private String word;
    private String phonetic;
    private String definition;
    private Integer reviewCount;
    private String status;
    private String difficulty;
    private Long lastReview;

    // 无参构造函数（建议保留，以防某些序列化框架需要）
    public WordDetailDTO() {
    }

    // 全参构造函数
    public WordDetailDTO(String word, String phonetic, String definition,
                         Integer reviewCount, String status,
                         String difficulty, Long lastReview) {
        this.word = word;
        this.phonetic = phonetic;
        this.definition = definition;
        this.reviewCount = reviewCount;
        this.status = status;
        this.difficulty = difficulty;
        this.lastReview = lastReview;
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

    public String getDefinition() {
        return definition;
    }

    public void setDefinition(String definition) {
        this.definition = definition;
    }

    public Integer getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(Integer reviewCount) {
        this.reviewCount = reviewCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public Long getLastReview() {
        return lastReview;
    }

    public void setLastReview(Long lastReview) {
        this.lastReview = lastReview;
    }
}
