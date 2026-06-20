package com.momo.repository;

import com.momo.dto.WordKillProjection;
import com.momo.model.WordRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WordRelationRepository extends JpaRepository<WordRelation, Long> {

    /**
     * 根据 bookId 和 status 查询单词详情
     *
     * @param bookId 书籍ID
     * @param status 状态 ('BURNING' 或 'FROZEN')
     * @return 单词详情列表
     */
    @Query(value = "SELECT d.word, d.phonetic, d.definition, " +
            "COALESCE(r.review_count, 0) as reviewCount, " +
            "COALESCE(r.wrong_count, 0) as wrongCount, " +
            "COALESCE(r.status, 'BURNING') as status, " +
            "r.difficulty, " +
            "r.last_review as lastReview " +
            "FROM word d " +
            "LEFT JOIN word_relation r ON d.word = r.word AND d.book_id = r.book_id " +
            "WHERE d.book_id = :bookId " +
            "AND ( " +
            "   (:status = 'BURNING' AND (r.difficulty IS NULL)) " + // 🎯 只要没打过标的，才是真正的“待过词”
            "   OR " +
            "   (:status = 'FROZEN' AND (r.difficulty IN ('INIT_MASTERED', 'INIT_VAGUE', 'INIT_STRANGER'))) " + // 🎯 只要打过标的，全是“已过词”
            ")", nativeQuery = true)
    List<Object[]> findWordsByBookAndStatusRaw(@Param("bookId") String bookId, @Param("status") String status);


    /**
     * 根据书本名称和单词内容查询记忆记录
     * 注意：如果你的表结构中 book 是 ID (Long)，请将参数类型改为 Long，并调整字段名
     */
    Optional<WordRelation> findByBookIdAndWord(String bookId, String word);


    /**
     * 🎯 战术核心：斩杀页面专用（待背词表 / 已背词表）双大盘拉取
     * * 1. 当 status = 'BURNING' (待背词表)：
     * 捞取已经过了分拣，但尚未在斩杀阶段通关的所有硬骨头（status == 'BURNING'），且过滤掉处于 24h 熔断禁闭的词。
     * 2. 当 status = 'FROZEN' (已背词表)：
     * 捞取所有已经顺利斩杀毕业、或秒杀进入冻结舱（status == 'FROZEN'）的熟词。
     */

    @Query(value = "SELECT d.word as word, " +
            "d.phonetic as phonetic, " +
            "d.definition as definition, " +
            "COALESCE(r.review_count, 0) as reviewCount, " +
            "COALESCE(r.wrong_count, 0) as wrongCount, " +
            "COALESCE(r.status, 'BURNING') as status, " +
            "r.difficulty as difficulty, " +
            "r.stability as stability, " +
            "r.difficulty_aa as difficultyAa, " +
            "r.last_review as lastReview, " +
            "COALESCE(r.total_count, 0) as totalCount, " +
            "COALESCE(r.total_wrong_count, 0) as totalWrongCount " + // 🎯 补齐第 10 列：终身审计错词数
            "FROM word d " +
            "INNER JOIN word_relation r ON d.word = r.word AND d.book_id = r.book_id " +
            "WHERE d.book_id = :bookId " +
            "AND ( " +
            "   (:status = 'BURNING' AND r.status = 'BURNING' AND (r.difficulty IS NULL OR r.difficulty != 'STUCK_LOOP')) " +
            "   OR " +
            "   (:status = 'FROZEN' AND r.status = 'FROZEN') " +
            ")", nativeQuery = true)
    List<WordKillProjection> findKillPageWordsRaw(@Param("bookId") String bookId, @Param("status") String status);



    @Query(value = "SELECT d.word as word, " +
            "d.phonetic as phonetic, " +
            "d.definition as definition, " +
            "COALESCE(r.review_count, 0) as reviewCount, " +
            "COALESCE(r.wrong_count, 0) as wrongCount, " +
            "COALESCE(r.status, 'BURNING') as status, " +
            "r.difficulty as difficulty, " +
            "r.last_review as lastReview, " +
            "COALESCE(r.total_count, 0) as totalCount, " +
            "COALESCE(r.total_wrong_count, 0) as totalWrongCount, " +
            "COALESCE(r.stability, 0.0) as stability, " +          // 🧠 投影：脑科学稳定性系数
            "COALESCE(r.difficulty_aa, 0.0) as difficultyAa " +     // 🧠 投影：自适应难度系数
            "FROM word d " +
            "INNER JOIN word_relation r ON d.word = r.word AND d.book_id = r.book_id " +
            "WHERE d.book_id = :bookId " +                         // 🎯 纠正：book_id 匹配词书
            "AND d.word = :word", nativeQuery = true)              // 🎯 纠正：word 匹配精准单词
    WordKillProjection findKillWord(@Param("bookId") String bookId, @Param("word") String word);

}