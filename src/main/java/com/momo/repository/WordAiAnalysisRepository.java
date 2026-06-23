package com.momo.repository;


import com.momo.model.WordAiAnalysisEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WordAiAnalysisRepository extends JpaRepository<WordAiAnalysisEntity, Long> {

    /**
     * 🎯 战术核心拦截点：根据单词精准匹配缓存
     * @param word 干净的去空格小写单词
     */
    Optional<WordAiAnalysisEntity> findByWord(String word);

    /**
     * ⚡ 批量预加载弹夹：当大盘切到下一页（比如20个词）时，前端一次性把这些词全部打包发过来
     * 后端一次性查出所有已存在的 AI 缓存，从而找出哪些是“新词”，直接丢给线程池去后台偷偷刷 AI
     * 这样可以做到前台背词 0 延迟
     */
    List<WordAiAnalysisEntity> findByWordIn(List<String> words);

    /**
     * 🔍 原生雷达：检查某个单词是否已经在 AI 库中修筑了护城河
     */
    boolean existsByWord(String word);

    /**
     * 📊 战术统计：看看目前 AI 军火库里已经帮你深度剖析了多少个核心词汇
     */
    @Query("SELECT COUNT(w) FROM WordAiAnalysisEntity w")
    long countTotalAnalyzedWords();
}