package com.momo.service.strategy;

import com.momo.controller.WordBombSnapshotController;
import com.momo.dto.WordVO;
import com.momo.model.WordRelation;
import com.momo.repository.WordRelationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 🛰️ 滚动补仓核心引擎（基于一次下发5个词的滑动阵位编排）
 */


@Service
public class RollingRefuelEngine {

    @Autowired
    private WordRelationRepository wordRelationRepository; // 或者是你的 JPA Repository


    // 纯内存高频快照池（暂存前 1~4 次的点击，第 5 次时合并清洗）
    private final Map<String, WordVO> syncCachePool = new ConcurrentHashMap<>();

    // 跨批次地雷滑行缓冲区：用来存放那些因步长太长（如+10, +30），在前5个词里根本来不及排进去、需要滑行到下一批次（甚至下下批次）的词
    private final List<WordVO> globalRetaliationBuffer = new CopyOnWriteArrayList<>();

    /**
     * 🍏 前 1~4 次极速异步上报调用：只进缓存，绝不查写数据库
     */
    public void saveSnapshotToCache(String bookId, WordVO incomingVO) {
        String cacheKey = bookId + ":" + incomingVO.getWord();
        syncCachePool.put(cacheKey, incomingVO);
    }


    // =========================================================================
// 🛰️ 请确保在类中或缓存(如 Redis)中为该用户维护以下两个核心持久化变量
// =========================================================================
// 1. 绝对时空计数器：用户每点一次【反馈接口】，该值在后端原子自增 +1
    private int userGlobalTick = 0;

    /**
     * 🧠 终极时空对齐版——滚动预测引擎（支持动态错峰、不定期提前刷新接口）
     * * @param allBurningVOs 从数据库大盘捞出来的候选高危词汇
     *
     * @param bookId 词书ID
     * @return 永远严丝合缝的 5 词冲锋包
     */
    // 每次点击“下一步”的反馈接口：驱动轴前进
    public void handleUserClickFeedback(String bookId, WordVO snapshot) {
        WordBombSnapshotController.syncCachePool.put(bookId + ":" + snapshot.getWord(), snapshot);
        this.userGlobalTick++; // 🔥 实时单向驱动绝对时空轴前进！
    }

    // 每次点击“上一步”的回滚接口：驱动轴后退，地雷协同后退
    public void handleRollbackFeedback(String bookId, String currentWord) {
        if (this.userGlobalTick > 0) {
            this.userGlobalTick--; // 🔥 时间倒流
        }
        // 协同修正：刚才埋在前面的雷，由于时空后退，相对位置要保持，绝对阵位同步 -1
        for (WordVO rWord : globalRetaliationBuffer) {
            if (rWord.getWord().equals(currentWord)) {
                rWord.setTriggerTargetIndex(rWord.getTriggerTargetIndex() - 1);
                break;
            }
        }
    }


    /**
     * 🧠 终极融合版——滚动预测引擎（完美适配：提前刷新、上一步回退、极速反馈）
     */
    public List<WordVO> memoryStatePredictionEngine(List<WordVO> allBurningVOs, String bookId) {
        if (allBurningVOs == null || allBurningVOs.isEmpty()) {
            return Collections.emptyList();
        }
        long now = System.currentTimeMillis();
        Map<String, WordVO> pool = WordBombSnapshotController.syncCachePool;

        // =========================================================================
        // 🧼 步骤一：【惰性时间清洗】地雷超时失效审计（修正：采用统一的绝对 Tick 命名）
        // =========================================================================
        if (!globalRetaliationBuffer.isEmpty()) {
            Iterator<WordVO> bufferIterator = globalRetaliationBuffer.iterator();
            while (bufferIterator.hasNext()) {
                WordVO rWord = bufferIterator.next();

                if (now > rWord.getExpireTimestamp()) {
                    globalRetaliationBuffer.remove(rWord);

                    // ⚡ 修正：利用绝对伏击点减去当前所在绝对点，算出当时埋下的距离
                    int deltaTick = rWord.getTriggerTargetIndex() - userGlobalTick;

                    for (WordVO baseVo : allBurningVOs) {
                        if (baseVo.getWord().equals(rWord.getWord())) {
                            baseVo.setWrongCount(baseVo.getWrongCount() + 1);
                            baseVo.setLastReview(now);
                            double penalty = deltaTick > 15 ? 0.15 : (deltaTick > 6 ? 0.3 : 0.5);
                            baseVo.setStability(Math.max(0.5, baseVo.getStability() * penalty));
                            break;
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 🔄 步骤二：提取缓存池快照（💥 时空对齐重大修正 💥）
        // =========================================================================
        if (!pool.isEmpty()) {
            List<Map.Entry<String, WordVO>> entries = new ArrayList<>(pool.entrySet());

            // ⚡ 核心设计：因为反馈接口已经让 userGlobalTick 前进了（当前处于大盘前线最新位置）
            // 在批量消费这批缓存时，由于 entry 是按点击顺序存入的
            // 越早点击的词，其按下时的绝对 Tick 其实越小。
            // 所以：某个词按下时的真实刻位 = 当前最新 userGlobalTick - 缓存池里排在它后面的词的总数 - 1
            int remainingSteps = entries.size();

            for (Map.Entry<String, WordVO> entry : entries) {
                String key = entry.getKey();
                WordVO cachedVO = entry.getValue();

                if (key.startsWith(bookId + ":")) {
                    WordRelation relation = wordRelationRepository.findByBookIdAndWord(bookId, cachedVO.getWord()).orElseGet(() -> {
                        WordRelation newRel = new WordRelation();
                        newRel.setBookId(bookId);
                        newRel.setWord(cachedVO.getWord());
                        newRel.setStability(2.0);
                        newRel.setDifficultyAa(50.0);
                        newRel.setStatus("BURNING");
                        return newRel;
                    });

                    relation.setReviewCount(relation.getReviewCount() + 1);
                    relation.setLastReview(now);

                    String status = cachedVO.getDifficulty() != null ? cachedVO.getDifficulty().toUpperCase() : "FAMILIAR";
                    int stride = -1;
                    long expireDuration = 0L;

                    switch (status) {
                        case "STRANGER":
                            relation.setWrongCount(relation.getWrongCount() + 1);
                            relation.setStability(Math.max(0.5, relation.getStability() * 0.35));
                            relation.setDifficultyAa(Math.min(100.0, relation.getDifficultyAa() + 12.0));
                            stride = 5;
                            expireDuration = 30 * 60 * 1000L;
                            break;
                        case "VAGUE":
                            relation.setStability(Math.max(1.0, relation.getStability() * 0.8));
                            relation.setDifficultyAa(Math.min(100.0, relation.getDifficultyAa() + 2.0));
                            stride = 10;
                            expireDuration = 6 * 60 * 60 * 1000L;
                            break;
                        case "EASY":
                            relation.setStability(Math.max(1.0, relation.getStability() * 0.8));
                            relation.setDifficultyAa(Math.min(100.0, relation.getDifficultyAa() + 2.0));
                            stride = 20;
                            expireDuration = 6 * 60 * 60 * 1000L;
                            break;
                        case "FAMILIAR":
                            relation.setWrongCount(0);
                            double growth = 2.5 - (relation.getDifficultyAa() / 100.0);
                            relation.setStability(relation.getStability() * Math.max(1.4, growth));
                            stride = 30;
                            expireDuration = 24 * 60 * 60 * 1000L;
                            break;
                    }
                    wordRelationRepository.save(relation);

                    if (stride > 0) {
                        // 💥 【全网最精准绝对阵位推演】
                        // 还原这个词在被按下那一瞬间，宇宙长河真正的绝对 Tick
                        int wordClickAbsoluteTick = userGlobalTick - remainingSteps;
                        int absoluteTriggerTick = wordClickAbsoluteTick + stride;

                        // 规范并锁死绝对阵位字段
                        cachedVO.setTriggerTargetIndex(absoluteTriggerTick);
                        cachedVO.setExpireTimestamp(now + expireDuration);

                        globalRetaliationBuffer.removeIf(r -> r.getWord().equals(cachedVO.getWord()));
                        globalRetaliationBuffer.add(cachedVO);
                    }

                    pool.remove(key); // 干净擦除缓存
                    remainingSteps--;  // 时空推演指针收缩
                }
            }
        }

        // =========================================================================
        // 🔮 步骤三：基础大盘常规词排序（算分过滤逻辑保持原样）
        // =========================================================================
        List<WordVO> baseSequence = new ArrayList<>();
        for (WordVO vo : allBurningVOs) {
            boolean isLockedInLoop = false;
            for (WordVO r : globalRetaliationBuffer) {
                if (r.getWord().equals(vo.getWord())) {
                    isLockedInLoop = true;
                    break;
                }
            }
            if (isLockedInLoop) continue;

            double stability = vo.getStability() > 0 ? vo.getStability() : 2.0;
            long lastReviewTime = vo.getLastReview() != null ? vo.getLastReview() : 0L;
            double daysPassed = (lastReviewTime == 0L) ? 1.5 : (double) (now - lastReviewTime) / (1000 * 60 * 60 * 24);

            double forgetProbability = 1.0 - Math.exp(-daysPassed / stability);
            int wrongStreak = vo.getWrongCount() != null ? vo.getWrongCount() : 0;

            double score = (forgetProbability * 100.0) + (vo.getDifficultyAa() * 0.5) + (wrongStreak * 10.0);
            vo.setDynamicPriorityScore(score);
            baseSequence.add(vo);
        }
        baseSequence.sort((v1, v2) -> Double.compare(v2.getDynamicPriorityScore(), v1.getDynamicPriorityScore()));

        // =========================================================================
        // ⚔️ 步骤四：【绝对对齐】以当前真实的 userGlobalTick 为基准，向后装填 5 个绝对格子
        // =========================================================================
        List<WordVO> nextFivePacket = new ArrayList<>();
        int baseIdx = 0;

        for (int i = 0; i < 5; i++) {
            int targetGridTick = userGlobalTick + i; // 正在编排的绝对时空格子位置

            WordVO matchedRetaliationWord = null;
            for (WordVO rWord : globalRetaliationBuffer) {
                // ⚡ 修正字段名：统一使用 getTriggerAbsoluteTick() 判定
                if (rWord.getTriggerTargetIndex() == targetGridTick && now <= rWord.getExpireTimestamp()) {
                    matchedRetaliationWord = rWord;
                    break;
                }
            }

            if (matchedRetaliationWord != null) {
                boolean isChineseFirst = java.util.concurrent.ThreadLocalRandom.current().nextBoolean();
                matchedRetaliationWord.setDisplayStrategy(isChineseFirst ? "ZH_FIRST" : "EN_FIRST");

                nextFivePacket.add(matchedRetaliationWord);
                globalRetaliationBuffer.remove(matchedRetaliationWord);
            } else {
                if (baseIdx < baseSequence.size()) {
                    WordVO normalVo = baseSequence.get(baseIdx++);
                    boolean isChineseFirst = java.util.concurrent.ThreadLocalRandom.current().nextBoolean();
                    normalVo.setDisplayStrategy(isChineseFirst ? "ZH_FIRST" : "EN_FIRST");
                    nextFivePacket.add(normalVo);
                }
            }
        }

        return nextFivePacket;
    }
}