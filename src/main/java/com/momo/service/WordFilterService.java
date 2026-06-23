package com.momo.service;


import com.momo.dto.WordVO;
import com.momo.model.WordRelation;
import com.momo.repository.WordRelationRepository;
import com.momo.repository.WordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


@Service
@Transactional
public class WordFilterService {


    @Autowired
    private WordRepository wordRepository;
    @Autowired
    private WordRelationRepository wordRelationRepository; // 或者是你的 JPA Repository

    public List<WordVO> getWordsByBookAndStatus(String bookId, String status) {
        // 1. 从 Repository 获取原生打平的数据
        List<Object[]> rawList = wordRepository.findWordsByBookAndStatusRaw(bookId, status);
        List<WordVO> voList = new ArrayList<>();

        // 2. 遍历并动态计算战术指标
        for (Object[] row : rawList) {
            WordVO vo = new WordVO();
            vo.setWord((String) row[0]);
            vo.setPhonetic((String) row[1]);
            vo.setDefinition((String) row[2]);

            // 索引 3: reviewCount
            int reviewCount = row[3] != null ? ((Number) row[3]).intValue() : 0;
            vo.setReviewCount(reviewCount);

            // 索引 4: wrongCount (新增字段)
            int wrongCount = row[4] != null ? ((Number) row[4]).intValue() : 0;

            // 索引 5: status
            vo.setStatus((String) row[5]);

            // 索引 6: difficulty
            vo.setDifficulty((String) row[6]);

            // 索引 7: lastReview
            vo.setLastReview(row[7] != null ? ((Number) row[7]).longValue() : null);

            // 计算总数和错误率
            int totalCount = reviewCount + wrongCount;
            vo.setTotalCount(totalCount);

            if (totalCount == 0) {
                vo.setErrorRate("0%");
            } else {
                double rate = (wrongCount * 100.0) / totalCount;
                vo.setErrorRate(String.format("%.1f%%", rate));
            }

            voList.add(vo);
        }
        return voList;
    }

    /**
     * 📥 战术核心：批量将单词从冻结舱（熟词表）打回燃烧区重练
     * 完美闭环：状态退化、计数回炉、错词累加、打上 RESTORED 特种烙印
     *
     * @param bookId 书籍ID (对应前端的 book)
     * @param words  需要打回的单词列表
     */
    @Transactional
    public void restoreWords(Long userId,String bookId, List<String> words, String source) {
        if (words == null || words.isEmpty()) {
            return; // 战术空检，防止空指针
        }
        // ⚡ 地毯式循环遍历处理每一个要回炉的单词
        for (String word : words) {
            Optional<WordRelation> relationOpt = wordRelationRepository.findByUserIdAndBookIdAndWord(userId,bookId, word);
            if (relationOpt.isPresent()) {
                WordRelation record = relationOpt.get();
                wordRelationRepository.delete(record);
            } else {
                // 边缘防御：未找到关联记录则自动跳过
                System.err.println("【打回警告】未找到书籍 " + bookId + " 中单词 [" + word + "] 的关联记录，自动跳过。");
            }
        }
    }

    /**
     * 🛰️ 战术核心 1：处理 /filter/kill 接口（清洗、分拣与滚动斩杀）
     * <p>
     * <p>
     * [ 接收请求 /filter/kill ]
     * |
     * [ 查询或创建 WordRelation 记录 ]
     * |
     * { 判定请求来源 source }
     * |
     * +----------------+----------------+
     * | (source == "filterPage")              | (else / null / "killPage")
     * v                                 v
     * 【 通道一：列表首次分拣 】         【 通道二：常规全屏大轰炸 】
     * |                                 |
     * +-------+-------+                 +-------+-------+
     * |               |                 | 'easy'        | 'wrong'
     * (mastered)       (vague)              v               v
     * 直接冻结       留存燃烧区       [reviewCount +1]   [wrongCount +1]
     * Count = 5       Count = 2             |             状态保持 BURNING
     * DIFFICULTY:     DIFFICULTY:    { reviewCount >= 5? }
     * INIT_MASTERED   INIT_VAGUE            |
     * +--------+--------+
     * | 是              | 否
     * v                 v
     * [ 晋级 FROZEN ]    [ 保持 BURNING ]
     * |
     * 【 动态难度打标 】
     * - wrongs == 0 -> SMOOTH_KILL
     * - wrongs <= 3 -> NORMAL_KILL
     * - wrongs >  3 -> HARD_KILL
     *
     *
     */
    @Transactional
    public void processWordReview(Long userId, String bookId, String word, String masteryDegree, String source) {
        WordRelation record = wordRelationRepository.findByUserIdAndBookIdAndWord(userId,bookId, word)
                .orElseGet(() -> {
                    WordRelation newRecord = new WordRelation();
                    newRecord.setBookId(bookId);
                    newRecord.setWord(word);
                    newRecord.setUserId(userId);
                    newRecord.setReviewCount(0);
                    newRecord.setWrongCount(0);
                    newRecord.setStatus("BURNING");
                    return newRecord;
                });

        int currentReviewCount = record.getReviewCount() != null ? record.getReviewCount() : 0;
        int currentWrongCount = record.getWrongCount() != null ? record.getWrongCount() : 0;

        // 🧭 通道一：来自“过滤页面（FilterPage）”的手动分拣/全屏分拣（系统首次清洗分流）
        if ("filterPage".equals(source)) {
            switch (masteryDegree) {
                case "mastered": // 【初始化 - 掌握】对应前端 mastered 按钮
                    record.setStatus("FROZEN");
                    record.setReviewCount(5);   // 步子拉满，直接通关
                    record.setDifficulty("INIT_MASTERED");
                    break;

                case "vague":    // 【初始化 - 模糊】
                    record.setStatus("BURNING");
                    record.setReviewCount(2);   // 赠送 2 次起步分，斩杀页面再对 3 次即可通关
                    record.setDifficulty("INIT_VAGUE");
                    break;

                case "stranger": // 【初始化 - 陌生】
                default:
                    record.setStatus("BURNING");
                    record.setReviewCount(0);   // 毫无印象，零分重训
                    record.setDifficulty("INIT_STRANGER");
                    break;
            }
        }
        // 🌋 通道二：来自大轰炸、或者专用的“极致斩杀页面（killPage）”的常规高频滚动
        else {
            if ("mastered".equals(masteryDegree)) {
                int newReviewCount = currentReviewCount + 1;
                record.setReviewCount(newReviewCount);

                // 🎯 触发通关生死线判定（满 5 次）
                if (newReviewCount >= 5) {
                    record.setStatus("FROZEN"); // 晋升冻结舱熟词

                    if (currentWrongCount == 0) {
                        record.setDifficulty("SMOOTH_KILL"); // 顺畅斩杀
                    } else if (currentWrongCount <= 3) {
                        record.setDifficulty("NORMAL_KILL"); // 常规斩杀
                    } else {
                        record.setDifficulty("HARD_KILL");   // 惨烈斩杀（后续重点抽查）
                    }
                } else {
                    record.setStatus("BURNING"); // 没满 5 次，继续留在燃烧区
                }
            } else if ("wrong".equals(masteryDegree)) {
                // 吃到马枪，错词率累加
                record.setWrongCount(currentWrongCount + 1);
                record.setStatus("BURNING");

                // ⚡【核心血条惩罚机制】：如果是在斩杀阶段（killPage）答错，
                // 必须剥夺其部分甚至全部 reviewCount，防止钻空子闪现通关
                if ("killPage".equals(source)) {
                    // 惩罚策略：斩杀页答错，血条直接打回 0 次或 1 次（这里采用清零，实施铁血重训）
                    record.setReviewCount(0);
                    record.setDifficulty("RE_TRAINING"); // 标记为再训死磕词
                }
            }
        }
        record.setUserId(userId);
        record.setLastReview(System.currentTimeMillis());
        wordRelationRepository.save(record);
    }
}