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

    @Column(name = "review_count")
    private Integer reviewCount = 0;

    @Column(name = "wrong_count")
    private Integer wrongCount = 0;

    private String status = "BURNING";

    /**
     * difficulty 字段不再是一个静态的“死标签”，而是一个记录单词“出身背景”与“血烈程度”的战术指针。它的取值可以分为两大阵营：
     *
     * 阵营 A：初始化清洗出身（初次分拣）
     * 当数据状态为 FROZEN 或 BURNING，且 difficulty 属于这一组时，说明它们是第一天导入时被分拣出来的。
     *
     * INIT_MASTERED（初始化-掌握）：
     *
     * 含义：用户在第一天看列表时，一眼认出并直接秒杀的熟词。
     *
     * 数据状态：status = 'FROZEN', review_count = 5。
     *
     * 战术价值：这类词熟练度最高，未来做“周期性破冰复习”时，可以把它们的复习延迟到 60~90 天以后。
     *
     * INIT_VAGUE（初始化-模糊）：
     *
     * 含义：用户第一天分拣时觉得“似懂非懂”的词。
     *
     * 数据状态：status = 'BURNING', review_count = 2。
     *
     * 战术价值：轰炸机拉取时，它们带有 2 次基础分，只需要在轰炸机里再对 3 次就能通关。
     *
     * INIT_STRANGER（初始化-陌生）：
     *
     * 含义：第一天分拣时用户承认完全不会的词。
     *
     * 数据状态：status = 'BURNING', review_count = 0。
     *
     * 战术价值：纯新词、死穴词。轰炸机引擎可以对这类词进行加权高频曝光。
     *
     *
     *
     *
     * 阵营 B：轰炸机百炼成钢（常规斩杀通关）
     * 当单词在全屏大轰炸中老老实实挺过 5 次 easy 通关，在进入 FROZEN 状态的瞬间，后端根据它在这个过程中的错词次数（wrongCount）进行复盘，动态赋予以下三个标签之一：
     *
     * SMOOTH_KILL（顺畅斩杀）：
     *
     * 含义：在轰炸机浮现的历程中，一次都没有错过（wrong_count == 0），直接打满 5 次通关。
     *
     * 战术价值：属于用户的潜在高熟练度词汇，短期内绝对不会忘，遗忘复习周期可设为 30 天。
     *
     * NORMAL_KILL（常规斩杀）：
     *
     * 含义：在轰炸机里错过，但错得不多（例如 0 < wrong_count <= 3），属于正常记忆波动的词。
     *
     * 战术价值：中等熟练度，维持艾宾浩斯经典记忆曲线复习。
     *
     * HARD_KILL（惨烈斩杀）：
     *
     * 含义：硬骨头、顽固死角！虽然最终达到了 5 次 easy 进入了冻结舱，但在背它的过程中，反复被点过马枪打回燃烧区很多次（wrong_count > 3）。
     *
     * 战术价值：极度危险词！ 哪怕它现在进了熟词表（FROZEN），用户大概率也是短期强行记住的，极易遗忘。算法应当在 3~5 天内，强行将它再次激活拉进大轰炸进行巩固。
     */
    private String difficulty;

    @Column(name = "last_review")
    private Long lastReview;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
}
