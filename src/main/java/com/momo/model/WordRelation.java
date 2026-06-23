package com.momo.model;



import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import javax.persistence.*;

// WordRelation 实体
@Entity
@Table(name = "word_relation")
@Data
@NoArgsConstructor
@AllArgsConstructor

public class WordRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "book_id")
    private String bookId;

    private String word;

    @Column(name = "user_id")
    private Long userId;

    private int streak;         // 突击大循环中连续正确计数器 (0-3，满3次在前端物理剔除)

    /**
     * 当前复习周期的“斩杀血条”
     * 范围：0 ~ 5 (MAX_KILL_COUNT)
     * 行为：单次会话中每按一次 Easy/掌握 加 1 分，满 5 分直接物理飞升进入 FROZEN 状态
     */
    @Column(name = "review_count")
    private Integer reviewCount = 0;


    /**
     * 全局累计错词次数（审计计数器）
     * 行为：【只增不减】，只要在轰炸机界面点错一次，终身累加 1
     * 战术价值：用于后期大数据盘点，精准揪出欺骗性最强的顽固死角词
     */
    private Integer totalWrongCount;



    /**
     * 本期错词次数（生命周期计数器）
     * 触发时机：
     * 1. 当单词初次导入、从 FROZEN 解冻、或手动回炉时，该字段【立刻清零(0)】。
     * 2. 在轰炸机界面每答错一次，该值加 1。
     * 战术价值：在 reviewCount == 5 触发毕业的瞬间，后端依据此字段的值，
     * 将身份动态擦除并重写为 SMOOTH_KILL / NORMAL_KILL / HARD_KILL。
     */
    @Column(name = "wrong_count")
    private Integer wrongCount = 0;


    /*
    status (String): 单词的物理隔离区。
    BURNING（燃烧区/活跃）：单词当前正在被用户死磕，或处于轰炸机的候选池中。
    FROZEN（冷冻区/归档）：单词已经通关，移出高频轰炸队列，进入复习大盘。
    reviewCount (Integer): 当前周期的“斩杀血条”。
    范围是 0 到 5。每按一次 Easy 加 1 分，满 5 分直接物理飞升进入 FROZEN 状态。
     */
    private String status = "BURNING";


    private String difficulty;

    @Column(name = "difficulty_aa")
    private Double difficultyAa;


    /**
     * 上次复习/斩杀时间
     */
    @Column(name = "last_review")
    private Long lastReview;


    private Integer totalCount; // 总过筛次数（掌握次数 + 错误次数）






    // === 5. 遗忘对抗终极指标 ===
    /**
     * 熟悉深度（斩杀通关计数器）
     * 行为：每次在轰炸机或主表里【连续秒杀】或【顺利通关一组】，该值 +1
     * 触发线：只有当 familiarDepth >= 5 时，状态才允许从 "BURNING" 转为 "FROZEN" (真正冻结)
     */
    private Integer familiarDepth = 0;

    private Double stability = 2.0;          // 2. 记忆稳定度 (Stability, 单位：天，初始生词给2天)

    private Long sessionLastActiveTime;



    // ... 保留你原有的 getter/setter ...
    public Integer getFamiliarDepth() { return familiarDepth != null ? familiarDepth : 0; }
    public void setFamiliarDepth(Integer familiarDepth) { this.familiarDepth = familiarDepth; }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getBookId() {
        return bookId;
    }

    public void setBookId(String bookId) {
        this.bookId = bookId;
    }

    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public Integer getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(Integer reviewCount) {
        this.reviewCount = reviewCount;
    }

    public Integer getWrongCount() {
        return wrongCount;
    }

    public void setWrongCount(Integer wrongCount) {
        this.wrongCount = wrongCount;
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

    public Integer getTotalWrongCount() {
        return totalWrongCount;
    }

    public void setTotalWrongCount(Integer totalWrongCount) {
        this.totalWrongCount = totalWrongCount;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }

    public int getStreak() {
        return streak;
    }

    public void setStreak(int streak) {
        this.streak = streak;
    }

    public Double getStability() {
        return stability;
    }

    public void setStability(Double stability) {
        this.stability = stability;
    }

    public Double getDifficultyAa() {
        return difficultyAa;
    }

    public void setDifficultyAa(Double difficultyAa) {
        this.difficultyAa = difficultyAa;
    }

    public Long getSessionLastActiveTime() {
        return sessionLastActiveTime;
    }

    public void setSessionLastActiveTime(Long sessionLastActiveTime) {
        this.sessionLastActiveTime = sessionLastActiveTime;
    }
}
