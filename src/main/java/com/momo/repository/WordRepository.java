package com.momo.repository;

import com.momo.model.Word;


import org.springframework.data.jpa.repository.JpaRepository;
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
}