package com.momo.dto;

public interface WordRawProjection {
    String getWord();
    String getPhonetic();
    String getDefinition();
    String getStatus();

    // 因为是只考虑 word 单表，JPA 发现 SQL 里没这几个字段时，会默认返回 null，绝不报错
    Integer getReviewCount();
    Integer getWrongCount();
    String getDifficulty();
    Long getLastReview();
}