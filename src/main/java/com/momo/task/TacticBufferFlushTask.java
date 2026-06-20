package com.momo.task;

import com.momo.controller.WordBombSnapshotController;
import com.momo.dto.WordVO;
import com.momo.model.WordRelation;
import com.momo.repository.WordRelationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Iterator;
import java.util.Map;

@Component
public class TacticBufferFlushTask {

    @Autowired
    private WordRelationRepository wordRelationRepository; // 底层 JPA/MyBatis 仓库

    /**
     * 🛰️ 定期收网：每 10 秒强制将前线战果批量同步到本地数据库
     */
    @Scheduled(fixedDelay = 10000)
/**
 * 🧠 记忆状态预测系统：前线三态快照（陌生/模糊/掌握）批量预测清洗中心
 * 作用：不查不写数据库，直接从控制层的 syncCachePool 提取前端的高频合并快照，批量更新脑科学指标
 */
    @Transactional
    public synchronized void flushCacheToMemoryPredictionEngine(String targetBookId) {
        // 1. 获取控制层内存池的全局快照 (存储着前端高频 save 进来的 WordVO)
        Map<String, WordVO> pool = WordBombSnapshotController.syncCachePool;
        if (pool.isEmpty()) return;

        System.out.println("📡 [记忆调度引擎] 开始批量清洗三态战报，当前待处理快照数: " + pool.size());

        Iterator<Map.Entry<String, WordVO>> iterator = pool.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, WordVO> entry = iterator.next();
            String key = entry.getKey();
            WordVO clientVO = entry.getValue();

            // 解析 Key -> "bookId:word"
            String[] parts = key.split(":");
            String bookId = parts[0];
            String word = parts[1];

            if (targetBookId != null && !targetBookId.equals(bookId)) {
                continue;
            }

            // 2. 从 SQLite 中捞取或初始化该词的长期记忆模型底座
            WordRelation relation = wordRelationRepository.findByBookIdAndWord(bookId, word).orElseGet(() -> {
                WordRelation newRel = new WordRelation();
                newRel.setBookId(bookId);
                newRel.setWord(word);
                newRel.setStability(2.0);  // 新词初始稳定度：2天
                newRel.setDifficultyAa(50.0); // 初始难度中庸线：50
                newRel.setStatus("BURNING");
                newRel.setWrongCount(0);
                newRel.setReviewCount(0);
                return newRel;
            });

            // 3. 💥 提取前端核心三态：获取前端快照中标记的实时难度应激状态 (difficulty 字段或自定义状态)
            // 假设前端传入的 clientVO.getDifficulty() 值为 "mastered" / "vague" / "stranger"
            String clientStatus = clientVO.getDifficulty();
            if (clientStatus == null) {
                clientStatus = "mastered"; // 兜底
            }

            relation.setReviewCount(relation.getReviewCount() + 1);
            relation.setLastReview(System.currentTimeMillis());

            // =========================================================================
            // 🔄 四个核心指标根据【三态梯度方案】进行精准演化
            // =========================================================================
            switch (clientStatus.toLowerCase()) {

                case "mastered": // 🟢 掌握：主动回忆彻底成功
                    relation.setWrongCount(0); // 连续错误中断
                    // 难度加速下修，最低降至 10.0
                    relation.setDifficultyAa(Math.max(10.0, relation.getDifficultyAa() - 4.0));

                    // 稳定度跨越式递增。成长因子受天生难度制约（越简单的词，稳定度涨得越疯狂）
                    double masteredGrowth = 2.5 - (relation.getDifficultyAa() / 100.0);
                    relation.setStability(relation.getStability() * Math.max(1.5, masteredGrowth));

                    // 🎯 第八章：长期掌握词判定。如果稳定度跨越 90 天阈值，移入 FROZEN 舱
                    if (relation.getStability() >= 90.0) {
                        relation.setStatus("FROZEN");
                    } else {
                        relation.setStatus("BURNING");
                    }
                    break;

                case "vague": // 🟡 模糊：游走在遗忘的边缘
                    relation.setWrongCount(0); // 模糊不算彻底失败，连续错误清零
                    // 难度微调，稍微增加一点认知负担
                    relation.setDifficultyAa(Math.min(100.0, relation.getDifficultyAa() + 2.0));

                    // 稳定度小幅踩刹车（打8折）。这意味着它在下次排序时，遗忘概率会小幅升高，在短期内会再次出现
                    relation.setStability(Math.max(1.0, relation.getStability() * 0.8));
                    relation.setStatus("BURNING"); // 强行留在燃烧区死磕
                    break;

                case "stranger": // 🔴 陌生：突触彻底断裂，完全不会
                    // 连续错误数自增 (对应你的 wrongStreak)
                    relation.setWrongCount(relation.getWrongCount() + 1);
                    relation.setTotalWrongCount(relation.getTotalWrongCount() + 1);

                    // 难度直线飙升
                    relation.setDifficultyAa(Math.min(100.0, relation.getDifficultyAa() + 15.0));

                    // 稳定度遭到无情重创（打35折），滑坡降维！
                    // 稳定度大幅缩水后，下一次计算时 ForgetProbability 会接近 100%，被高频轰炸机前排打捞！
                    relation.setStability(Math.max(0.5, relation.getStability() * 0.35));
                    relation.setStatus("BURNING");
                    break;
            }

            // 4. 将最新指标固化落库
            wordRelationRepository.save(relation);

            // 5. 洗刷干净，从前线缓存池中无情剔除该词，完成合并折叠
            iterator.remove();
        }

        System.out.println("🎉 [记忆调度引擎] 三态认知流转闭环批量落库完成！");
    }
}