package com.momo.dto;


public class WordVO {
    // === 1. 单词基本元数据（来自静态词库） ===
    private String word;        // 单词文本，如 "apple"
    private String phonetic;    // 音标，如 "/ˈæpl/"
    private String definition;  // 中文释义，如 "n. 苹果"

    // === 2. 用户记忆中间态数据（来自 user_word_relation 表） ===
    private Integer reviewCount;// 斩杀/熟练次数（前端据此渲染 █░░░░ 进度条）
    private String status;      // 生命周期状态: "BURNING" 或 "FROZEN"
    private String difficulty;  // 最近一次打分: "hard", "medium", "easy"
    private Long lastReview;    // 上次复习时间戳
    // === 3. 🔥 现状新增：战术统计指标（由后端动态计算或聚合） ===
    private Integer totalCount; // 总过筛次数（掌握次数 + 错误次数）
    private String errorRate;   // 战术错误率（百分比字符串，如 "28.5%"）

    // 无参构造函数
    public WordVO() {}

    // 全参构造函数（方便在 Mapper 或 Service 中快捷组装）
    public WordVO(String word, String phonetic, String definition, Integer reviewCount, String status, String difficulty, Long lastReview,Integer totalCount, String errorRate) {
        this.word = word;
        this.phonetic = phonetic;
        this.definition = definition;
        this.reviewCount = reviewCount != null ? reviewCount : 0; // 防空指针，默认0次
        this.status = status != null ? status : "BURNING";
        this.difficulty = difficulty;
        this.lastReview = lastReview;
        this.totalCount = totalCount;
        this.errorRate = errorRate;
    }

    // === Getters and Setters ===
    public String getWord() { return word; }
    public void setWord(String word) { this.word = word; }

    public String getPhonetic() { return phonetic; }
    public void setPhonetic(String phonetic) { this.phonetic = phonetic; }

    public String getDefinition() { return definition; }
    public void setDefinition(String definition) { this.definition = definition; }

    public Integer getReviewCount() { return reviewCount; }
    public void setReviewCount(Integer reviewCount) { this.reviewCount = reviewCount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public Long getLastReview() { return lastReview; }
    public void setLastReview(Long lastReview) { this.lastReview = lastReview; }

    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }

    public String getErrorRate() { return errorRate; }
    public void setErrorRate(String errorRate) { this.errorRate = errorRate; }
}