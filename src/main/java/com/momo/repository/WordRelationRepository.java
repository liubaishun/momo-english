package com.momo.repository;


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
            "COALESCE(r.wrong_count, 0) as wrongCount, " + // 新增：错词数，索引变为 4
            "COALESCE(r.status, 'BURNING') as status, " +   // 索引变为 5
            "r.difficulty, " +                               // 索引变为 6
            "r.last_review as lastReview " +                 // 索引变为 7
            "FROM word d " +
            "LEFT JOIN word_relation r ON d.word = r.word AND d.book_id = r.book_id " +
            "WHERE d.book_id = :bookId " +
            "AND ( " +
            "   (:status = 'BURNING' AND (r.status = 'BURNING' OR r.status IS NULL)) " +
            "   OR " +
            "   (:status = 'FROZEN' AND r.status = 'FROZEN') " +
            ")", nativeQuery = true)
    List<Object[]> findWordsByBookAndStatusRaw(@Param("bookId") String bookId, @Param("status") String status);

    /**
     * 根据书本名称和单词内容查询记忆记录
     * 注意：如果你的表结构中 book 是 ID (Long)，请将参数类型改为 Long，并调整字段名
     */
    Optional<WordRelation> findByBookIdAndWord(String bookId, String word);

}