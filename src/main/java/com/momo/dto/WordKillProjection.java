package com.momo.dto;

/**
 * 🛰️ 战术映射：专门用来在持久层无缝吞噬原始 SQL 结果的镜像投影
 */

public interface WordKillProjection {
    String getWord();

    String getPhonetic();

    String getDefinition();

    Integer getReviewCount();

    Integer getWrongCount();

    String getStatus();

    String getDifficulty(); // 对应原有的 String 战术标记

    Long getLastReview();

    Integer getTotalCount();

    Integer getTotalWrongCount();

    // 🎯 升级补齐：脑科学底层指标投影读取
    Double getStability();

    Double getDifficultyAa();
}