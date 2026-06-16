package com.momo.service;


/**
 * 已经斩杀过的单词
 */
public class KilledWord {

    private String id;               // 单词ID
    private String word;             // 单词本身
    private int streak;              // 滚动循环中连续正确计数器 (0-3)，前端用，后端做持久化兜底
    private int reviewCount;         // 跨轮次成功复抽并通关的次数 (0-5)
    private long nextReviewTime;     // 下一次允许复抽的绝对时间戳 (CD 机制，单位：毫秒)
    public String status;            // 'hard', 'medium', 'easy'
    public long lastReview;          // 上次复习时间戳

    public KilledWord() {
    }

    public KilledWord(String status, long lastReview) {
        this.status = status;
        this.lastReview = lastReview;
    }

    // === 便利构造函数（新词初始化） ===
    public KilledWord(String word) {
        this.word = word;
        this.streak = 0;
        this.reviewCount = 0;
        this.nextReviewTime = 0L;
    }


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public int getStreak() {
        return streak;
    }

    public void setStreak(int streak) {
        this.streak = streak;
    }

    public int getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(int reviewCount) {
        this.reviewCount = reviewCount;
    }

    public long getNextReviewTime() {
        return nextReviewTime;
    }

    public void setNextReviewTime(long nextReviewTime) {
        this.nextReviewTime = nextReviewTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getLastReview() {
        return lastReview;
    }

    public void setLastReview(long lastReview) {
        this.lastReview = lastReview;
    }
}