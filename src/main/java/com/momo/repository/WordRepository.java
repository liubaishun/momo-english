package com.momo.repository;

import com.momo.model.Word;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WordRepository extends JpaRepository<Word, Long> {
    // 这里会自动拥有 findAll(), save(), deleteById() 等方法

    // 根据单词文本查找
    Optional<Word> findByWord(String wordText);

    // 根据书籍ID查找所有单词
    List<Word> findByBookId(String bookId);

    // 根据分类查找
    List<Word> findByCategory(String category);


    // 🎯 铁血精准拦截：捞出所有音标为 '---' 或者为 null 的脏数据记录
    List<Word> findByPhoneticOrPhoneticIsNull(String phonetic);


    // 🎯 战术修正：不再盲目全表扫描，只捞出你指定的 book_id 下面被污染的 5000 多个词
    // 或者只捞出真实的考研大盘词书（把 "你的bookId" 传进来，或者直接全量限制）
    @Query("SELECT w FROM Word w WHERE (w.phonetic = '---' OR w.phonetic IS NULL) AND w.bookId IS NOT NULL")
    List<Word> findActualDirtyWords();


    @Query(value = "SELECT d.word, d.phonetic, d.definition, " +
            "COALESCE(r.review_count, 0) as reviewCount, " +
            "COALESCE(r.wrong_count, 0) as wrongCount, " +
            "CASE WHEN r.id IS NULL THEN 'RAW' ELSE 'FILTERED' END as status, " + // 🎯 核心裁决：关系表无主键即未过滤，有主键即过滤
            "r.difficulty, " +
            "r.last_review as lastReview " +
            "FROM word d " +
            "LEFT JOIN word_relation r ON d.word = r.word AND d.book_id = r.book_id " +
            "WHERE d.book_id = :bookId " +
            "AND ( " +
            "   (:status = 'RAW' AND r.id IS NULL) " +          // 🎯 链路1：查没有过滤的（关系表没有对应数据）
            "   OR " +
            "   (:status = 'FILTERED' AND r.id IS NOT NULL) " + // 🎯 链路2：查已经过滤的（只要关系表有数据，其余全算过滤后）
            ")", nativeQuery = true)
    List<Object[]> findWordsByBookAndStatusRaw(@Param("bookId") String bookId, @Param("status") String status);

}