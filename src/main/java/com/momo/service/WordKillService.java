package com.momo.service;

import com.momo.dto.WordKillProjection;
import com.momo.dto.WordVO;
import com.momo.model.WordRelation;
import com.momo.repository.WordRelationRepository;
import com.momo.service.strategy.RollingRefuelEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;


@Service
public class WordKillService {


    private static final int MAX_REVIEW_PER_ROUND = 30; // 🎯 抗雪崩：单轮最大到期复习词释放吞吐量
    private static final int MAX_KILL_COUNT = 5;         // 💎 斩杀满分飞升血条

    // 🔬 科学精密对齐：艾宾浩斯跨轮级联 CD 常数（单位：毫秒）
    private static final long CD_L1_20MIN = 20 * 60 * 1000L;
    private static final long CD_L2_1HOUR = 60 * 60 * 1000L;
    private static final long CD_L3_9HOUR = 9 * 60 * 60 * 1000L;
    private static final long CD_L4_2DAYS = 2 * 24 * 60 * 60 * 1000L;
    private static final long CD_L5_6DAYS = 6 * 24 * 60 * 60 * 1000L;
    private static final long OVERDUE_LIMIT_15DAYS = 15 * 24 * 60 * 60 * 1000L;


    @Autowired
    private WordRelationRepository wordRelationRepository; // 或者是你的 JPA Repository    @Autowired

    @Autowired
    private RollingRefuelEngine refuelEngine; // 或者是你的 JPA Repository

    @Transactional
    public void processWordReview(String bookId, String word, String masteryDegree, int currentTick, Long userId) {

        // 1. 获取最新大盘原始投影
        WordKillProjection p = wordRelationRepository.findKillWordByUserId(userId, bookId, word);
        if (p == null) {
            return; // 极端防御性降级
        }

        long now = System.currentTimeMillis();
        String degree = Optional.ofNullable(masteryDegree).orElse("").toUpperCase();

        // 🎯 【核心改动】：持久化降维。优先从引擎的内存脏池里取，取不到再去查 DB，实现内存防穿透
        String dirtyKey = bookId + ":" + word;
        WordRelation relation = refuelEngine.getDirtyRelationMap().computeIfAbsent(dirtyKey, k ->
                wordRelationRepository.findByUserIdAndBookIdAndWord(userId,bookId, word).orElseGet(() -> {
                    WordRelation newRel = new WordRelation();
                    newRel.setBookId(bookId);
                    newRel.setWord(word);
                    newRel.setStability(2.0);
                    newRel.setDifficultyAa(50.0);
                    newRel.setStatus("BURNING");
                    newRel.setReviewCount(0);
                    newRel.setWrongCount(0);
                    return newRel;
                })
        );
        relation.setReviewCount((relation.getReviewCount() != null ? relation.getReviewCount() : 0) + 1);
        relation.setLastReview(now);

        Map<String, WordVO> userCache = refuelEngine.getUserCacheSnapshot(String.valueOf(userId));
        List<WordVO> retaliationBuffer = refuelEngine.getState(userId, bookId).getRetaliationBuffer();
        // 🎯 核心防死锁：提前计算出当前常规大盘可以参与排阵的候选词基数（剔除已被雷锁定的词）
        Set<String> currentlyLockedWords = retaliationBuffer.stream().map(WordVO::getWord).collect(Collectors.toSet());
        int estimatedBaseSize = (int) retaliationBuffer.stream().filter(v -> !currentlyLockedWords.contains(v.getWord())).count();

        int remainingSteps = 0;
        if (userCache != null && !userCache.isEmpty()) {
            List<Map.Entry<String, WordVO>> entries;
            synchronized (userCache) {
                entries = new ArrayList<>(userCache.entrySet());
            }
            // 缓存积压长度:当前缓存中存在的陌生、模糊 等词的长度;
            remainingSteps = entries.size();
        }


        // 计算衍生战术指标：动态错误率
        int total = Optional.ofNullable(p.getTotalCount()).orElse(0);
        int wrong = Optional.ofNullable(p.getWrongCount()).orElse(0);
        int totalWrong = Optional.ofNullable(p.getTotalWrongCount()).orElse(0);
        String errorRate = total > 0 ? String.format("%.0f%%", ((double) wrong / total) * 100) : "0%";

        // 2. 完美对应 WordVO 的全参构造器 (其中 streak 默认注入 0)
        WordVO vo = new WordVO(p.getWord(), p.getPhonetic(), p.getDefinition(), p.getReviewCount(), p.getStatus(),
                p.getDifficulty(), p.getDifficultyAa(), p.getLastReview(), total, errorRate, 0,
                wrong, totalWrong, 1, 0, 0, 0, 0);

        // 🚀 初始化设定：咬住前端当前的真理发射刻度
        vo.setTriggerTargetIndex(currentTick);


        long expireDuration = 0L;
        double diffRatio = (p.getDifficultyAa() != null ? p.getDifficultyAa() : 50.0) / 100.0;

        // 错峰相对步长偏移量（默认 0：如果不进地雷缓冲区则不产生偏移）
        int stepOffset = 0;

        // 3. 极速注入演化矩阵（同时同步给 VO 快照和 数据库Relation）
        switch (degree) {
            case "STRANGER": // 🌋 陌生：5词后必杀回马枪
                stepOffset = 5;
                vo.setLoopStage(1);

                relation.setWrongCount(wrong + 1);
                relation.setStability(Math.max(0.4, (p.getStability() != null ? p.getStability() : 2.0) * 0.30));
                relation.setDifficultyAa(Math.min(100.0, (p.getDifficultyAa() != null ? p.getDifficultyAa() : 50.0) + 15.0));
                expireDuration = 15 * 60 * 1000L; // 15分钟有效期
                break;

            case "VAGUE":    // 🟡 模糊：10词后回马枪
                stepOffset = 10;
                vo.setLoopStage(2);

                relation.setWrongCount(wrong + 1); // 模糊也按错词高危算
                relation.setStability(Math.max(0.8, (p.getStability() != null ? p.getStability() : 2.0) * 0.70));
                relation.setDifficultyAa(Math.min(100.0, (p.getDifficultyAa() != null ? p.getDifficultyAa() : 50.0) + 4.0));
                expireDuration = 2 * 60 * 60 * 1000L; // 2小时
                break;

            case "FAMILIAR": // 🌀 自动滚动或常规滑行
                stepOffset = 0;

                relation.setWrongCount(Math.max(0, wrong - 1));
                double familiarGrowth = 1.6 - (diffRatio * 0.4);
                relation.setStability((p.getStability() != null ? p.getStability() : 2.0) * Math.max(1.2, familiarGrowth));
                relation.setDifficultyAa(Math.max(0.0, (p.getDifficultyAa() != null ? p.getDifficultyAa() : 50.0) - 5.0));
                expireDuration = 12 * 60 * 60 * 1000L; // 12小时
                break;

            case "EASY":     // 🟢 熟词审计（30词后回马枪抽查）
                stepOffset = 30;
                vo.setLoopStage(3);

                relation.setWrongCount(0);
                double easyGrowth = 3.0 - (diffRatio * 1.0);
                relation.setStability((p.getStability() != null ? p.getStability() : 2.0) * Math.max(2.0, easyGrowth));
                relation.setDifficultyAa(Math.max(0.0, (p.getDifficultyAa() != null ? p.getDifficultyAa() : 50.0) - 15.0));
                expireDuration = 72 * 60 * 60 * 1000L; // 72小时
                break;

            case "PREV":
                refuelEngine.handleRollbackFeedback(bookId, userId + "", word, userId);
                return;

            case "NEXT":
                refuelEngine.handleUserClickFeedback(bookId, userId + "", vo);
                return;

            default:
                vo.setTriggerTargetIndex(-1);
                break;
        }

        // =========================================================================
        // 🛰️ 【平替落库】：极速注入内存脏池，不阻塞当前 HTTP 请求
        // =========================================================================
        refuelEngine.stageDirtyRelation(bookId, word, relation);

        // =========================================================================
        // 🛰️ 【借鉴融合点二】：联动引擎自适应 Stride，注入绝对真理时空阵位
        // =========================================================================
        // 传入刚保存的最新稳定性，让引擎计算自适应智能步长（基础词剩余传 0，等引擎二次修正）
        int stride = refuelEngine.calculateAdaptiveStride(relation.getStability(), degree, remainingSteps, estimatedBaseSize);

        // 🎯 核心防覆盖修正线：
        // 如果是无埋雷意图的词（adaptiveStride返回-1），或者自动滑行的词，则直接赋予负分（直接进常规大盘，不埋雷）
        if (stride <= 0 || stepOffset == 0) {
            vo.setTriggerTargetIndex(-1);
        } else {
            // 💥 时空裂变：以 stride 为主，如果它发生了防死锁压缩，以它为准；否则用我们基础设定的 stepOffset
            int finalStride = Math.min(stepOffset, stride);

            // 🚀 终极真理公式：当前前端真实所在的步数 + 最终收缩步长 = 后端格子的物理捕获点
            vo.setTriggerTargetIndex(currentTick + finalStride);
        }

        vo.setExpireTimestamp(now + expireDuration);

        // 4. 将最新计算出的演化数值反哺进 WordVO 快照中，保证缓存和大盘的一致性
        vo.setStability(relation.getStability());
        vo.setDifficultyAa(relation.getDifficultyAa());
        vo.setWrongCount(relation.getWrongCount());
        vo.setDifficulty(degree); // 将本次动作暂存

        // 🎯 写入高速前线内存池，等待 /bomb/load 一枪提走
        refuelEngine.cacheWordSnapshot(userId + "", bookId, vo.getWord(), vo);
    }


    @Transactional
    public void killordReview(Long userId,String bookId, String word, String masteryDegree, String source) {
        WordRelation record = wordRelationRepository.findByUserIdAndBookIdAndWord(userId,bookId, word)
                .orElseGet(() -> {
                    WordRelation newRecord = new WordRelation();
                    newRecord.setBookId(bookId);
                    newRecord.setWord(word);
                    newRecord.setReviewCount(0);
                    newRecord.setWrongCount(0);
                    newRecord.setStatus("BURNING");
                    return newRecord;
                });

        int currentReviewCount = record.getReviewCount() != null ? record.getReviewCount() : 0;
        int currentWrongCount = record.getWrongCount() != null ? record.getWrongCount() : 0;


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
        record.setLastReview(System.currentTimeMillis());
        wordRelationRepository.save(record);
    }


    /**
     * 🚀 战术总装：为前端生成/补仓 20 词核心轰炸弹夹 (Omnipotent Queue Generator)
     * 融合了：状态机出词优先级、抗雪崩分期付款、高价值优先打捞
     */
    public List<WordVO> generateBombingRoundQueue(List<WordVO> allBookWords) {
        long now = System.currentTimeMillis();

        // 1. 提起大盘中所有处于活跃轰炸状态的词 (BURNING)
        List<WordVO> burningPool = allBookWords.stream().filter(w -> "BURNING".equals(w.getStatus())).collect(Collectors.toList());

        // 2. 提取已经通关(FROZEN)但艾宾浩斯 CD 已经解冻的复习债务，同时触发【4.1 时间衰减因子拦截】
        List<WordVO> expiredReviewBuffer = new ArrayList<>();
        List<WordVO> finalBombingList = new ArrayList<>();

        // 区分“已经在死磕的”和“等待加入的”
        for (WordVO w : allBookWords) {
            if ("BURNING".equals(w.getStatus()) && w.getReviewCount() > 0) {
                // 说明是正在队列里滚动的词，无条件保留在当前批次
                finalBombingList.add(w);
            } else if ("FROZEN".equals(w.getStatus()) && w.getLastReview() != null) {
                // 计算当前时间与预计复习时间的时差（这里假设复习时间轴利用 last_review + CD 衍生）
                long currentCD = getNormalCD(w.getWrongCount()); // 依据周期错词数或斩杀难度计算CD步长
                long nextReviewTime = w.getLastReview() + currentCD;

                if (now >= nextReviewTime) {
                    long overdueDuration = now - nextReviewTime;

                    // 🌋 蓝图 4.1：抗雪崩多级时差拦截
                    if (overdueDuration >= OVERDUE_LIMIT_15DAYS) {
                        // 判定彻底遗忘：直接融化归档，打回活跃燃烧区，降级重修
                        w.setStatus("BURNING");
                        w.setReviewCount(0);
                        w.setWrongCount(0); // 周期错词清零
                        w.setDifficulty("REVIVE_FALL"); // 标记为假熟词跌落
                    } else if (overdueDuration > (3 * currentCD)) {
                        // 记忆滑坡扣减：斩杀血条扣减 2 点，变相缩短下一次解冻时间
                        w.setReviewCount(Math.max(0, w.getReviewCount() - 2));
                        w.setLastReview(now); // 强行下修
                    } else {
                        // 正常的待解冻债务
                        expiredReviewBuffer.add(w);
                    }
                }
            }
        }

        // 📊 蓝图 4.2 & 4.3：流量分期付款与高价值优先排序 (按错词数从小到大排，优先打捞优质丝滑词)
        List<WordVO> allowedReviews = expiredReviewBuffer.stream().sorted(Comparator.comparingInt(WordVO::getWrongCount)) // 优先捞0错、1错的高多巴胺正反馈词
                .limit(MAX_REVIEW_PER_ROUND).peek(w -> {
                    w.setStatus("BURNING"); // 临时解冻，送入轰炸区接受黑盒提取测试
                    w.setDifficulty("REVIVE_FALL"); // 赋予橙色一级警戒
                }).collect(Collectors.toList());

        finalBombingList.addAll(allowedReviews);

        // 🩸 补仓机制（第五章）：如果大部队严重不满 20 个，从纯生词池（reviewCount==0）里打捞新鲜血液填满
        int currentSize = finalBombingList.size();
        if (currentSize < 20) {
            int shortage = 20 - currentSize;
            List<WordVO> freshBlood = burningPool.stream().filter(w -> w.getReviewCount() == 0 && !"REVIVE_FALL".equals(w.getDifficulty()) && !"KILL_RECOUP".equals(w.getDifficulty())).limit(shortage).collect(Collectors.toList());
            finalBombingList.addAll(freshBlood);
        }

        return finalBombingList;
    }


    /*
    ==================================================================================================
                 🛰️ /api/words/bomb/load 后端八维矩阵算力引擎流转大盘
==================================================================================================

     【 📥 输入端 】：从数据库捞出的全量 `BURNING` 状态 WordVO 列表 (allBurningVOs)
                          │
                          ▼
 ┌────────────────────────────────────────────────────────────────────────────────┐
 │ 🧠 第一步：八维认知矩阵叠加运算（针对每个 WordVO 单兵进行内存洗礼）                     │
 └───────────────────────┬────────────────────────────────────────────────────────┘
                         │
                         ├─► 1. [lastReview] ──► 计算冷切时差 `timeDiffMin` (全新词默认1440分)
                         ├─► 2. [difficulty] ──► 动态状态加权 `stateWeight` (STUCK_LOOP 顶格 4.5)
                         ├─► 3. [wrongCount] ──► 本期翻车应激惩罚分 (wc * 1.2)
                         ├─► 4. [totalWrongCount] ─► 终身顽固死角审计分 (twc * 0.8)
                         ├─► 5. [reviewCount] ──► 映射 MasteryLevel，激活段位降权衰减机制
                         └─► 6. [interval/error] ─► 模拟艾宾浩斯遗忘曲线，得出遗忘失效率分 (0~5分)
                         │
                         ▼ 【 🔗 核心公式动态合流 】
     finalStressScore = ((timeDiffMin * 0.3 + 遗忘分 * 8) * stateWeight + 错词惩罚) * 段位衰减
                         │
                         ▼
             [ 写入 vo.setDynamicPriorityScore() ]
                         │
                         ▼
 ┌────────────────────────────────────────────────────────────────────────────────┐
 │ ⚔️ 第二步：绝对应激降序排列（按脑科学痛苦指数，将大盘从高到低铁血切分）                   │
 └───────────────────────┬────────────────────────────────────────────────────────┘
                         │
                         ▼ 降序排列 (Sort)
                [ 最高应激得分 (最痛苦的词) ]
                            │
                            ▼
                [ 中度遗忘生词 / 模糊词 ]
                            │
                            ▼
                [ 段位极高/刚复习完的伪熟词 ]
                         │
                         ▼
 ┌────────────────────────────────────────────────────────────────────────────────┐
 │ 🎯 第三步：战术弹夹容量截取与波次划分 (Wave Slicing)                                 │
 └───────────────────────┬────────────────────────────────────────────────────────┘
                         │
                         ▼ 截取前 40 个最具有突击价值的硬骨头
     */

    public List<WordVO> calculateEightDimensionalBombMagazine(List<WordVO> allBurningVOs) {
        if (allBurningVOs == null || allBurningVOs.isEmpty()) {
            return Collections.emptyList();
        }

        long now = System.currentTimeMillis();

        // =========================================================================
        // 🧠 第一步：八维认知矩阵叠加运算
        // =========================================================================
        for (WordVO vo : allBurningVOs) {

            // 🎲 核心随机算法：利用高并发安全的 ThreadLocalRandom 抛硬币
            boolean isChineseFirst = ThreadLocalRandom.current().nextBoolean();
            String strategy = isChineseFirst ? "ZH_FIRST" : "EN_FIRST";

            // 🎯 核心注入：指挥前端滚动的指挥官字段
            vo.setDisplayStrategy(strategy);

            // 1. 最近复习时间与复习间隔（维度：lastReview）
            long lastReviewTime = vo.getLastReview() != null ? vo.getLastReview() : 0L;
            // 计算冷切时差（分钟）。如果是全新词（lastReview为0），默认冷切时间为1天（1440分钟）
            double timeDiffMin = (lastReviewTime == 0L) ? 1440.0 : (double) (now - lastReviewTime) / (1000 * 60);

            // 2. 实时应激状态权重（维度：difficulty / 针对全屏轰炸进行了状态清洗）
            double stateWeight = 1.0;
            String diff = vo.getDifficulty();
            if (diff != null) {
                switch (diff) {
                    case "STUCK_LOOP":
                        stateWeight = 4.5;
                        break; // 死循环卡壳词，赋予特级高压权
                    case "REVIVE_FALL":
                        stateWeight = 3.5;
                        break; // 起死回生跌落词
                    case "INIT_STRANGER":
                        stateWeight = 2.0;
                        break; // 手动标记生词
                    case "INIT_VAGUE":
                        stateWeight = 1.5;
                        break; // 模糊词
                    default:
                        stateWeight = 1.0;
                        break;
                }
            }

            // 3. 历史翻车与审计惩罚（维度：totalWrongCount & wrongCount）
            // 终身错词（totalWrongCount）代表长期顽固错词，本期错词（wrongCount）代表当前阶段正在死磕的词
            int twc = vo.getTotalWrongCount() != null ? vo.getTotalWrongCount() : 0;
            int wc = vo.getWrongCount() != null ? vo.getWrongCount() : 0;
            double wrongPenalty = (twc * 0.8) + (wc * 1.2);

            // 4. 段位熟练度衰减系数（维度：masteryLevel / reviewCount）
            // 利用 VO 内部通过 reviewCount 映射出的 masteryLevel 进行降权，防止高基数熟词霸占前排
            int level = vo.getMasteryLevel();
            double masteryAttenuation = Math.max(0.1, 1.0 - (level * 0.15));

            // 5. 艾宾浩斯遗忘度与复习间隔模拟（维度：reviewIntervalMinutes / errorRate）
            // 获取当前期望半衰期（分钟）
            int intervalMin = vo.getReviewIntervalMinutes();
            // 结合复习间隔计算记忆留存率 (Retention Rate)
            double retention = Math.exp(-(timeDiffMin / (intervalMin + 1)));
            double forgettingCurveScore = (1.0 - retention) * 5.0; // 遗忘度分（0 ~ 5分浮动）

            // =========================================================================
            // 🔗 核心公式合流：计算最终的【应激轰炸指数】 (DynamicPriorityScore)
            // =========================================================================
            // 逻辑：(基础时间开销 + 遗忘曲线失效率) * 应激系数 + 顽固错词惩罚，最后经过段位降权
            double finalStressScore = ((timeDiffMin * 0.3 + forgettingCurveScore * 8) * stateWeight + wrongPenalty) * masteryAttenuation;

            // 写入 VO 内存过渡字段
            vo.setDynamicPriorityScore(finalStressScore);
        }

        // =========================================================================
        // ⚔️ 第二步：绝对应激降序排列
        // =========================================================================
        // 分数越高，代表大脑越遗忘、历史错得越多、越卡壳，越应该优先进入弹夹进行轰炸
        allBurningVOs.sort((v1, v2) -> Double.compare(v2.getDynamicPriorityScore(), v1.getDynamicPriorityScore()));

        // =========================================================================
        // 🎯 第三步：战术弹夹容量截取与波次划分 (Wave Slicing)
        // =========================================================================
        // 轰炸机单次弹夹最佳容量为 40 词
        int maxCapacity = Math.min(allBurningVOs.size(), 40);
        List<WordVO> magazine = new ArrayList<>(allBurningVOs.subList(0, maxCapacity));

        // 为了防止最痛苦的 10 个词连续撞面砸晕大脑，对弹夹前半段（前 20 个高危词）进行局部微洗牌打散
        if (magazine.size() > 10) {
            int shuffleRange = magazine.size() / 2;
            List<WordVO> headSlice = new ArrayList<>(magazine.subList(0, shuffleRange));
            Collections.shuffle(headSlice);

            // 把洗牌后的前半段重新缝合回去
            for (int i = 0; i < shuffleRange; i++) {
                magazine.set(i, headSlice.get(i));
            }
        }

        return magazine;
    }


    /**
     * 👁️ 辅助计算：依据斩杀难度计算出标准艾宾浩斯解冻CD步长
     */
    private long getNormalCD(int wrongCount) {
        // 错词越多的硬骨头，CD解冻越快，高频轰炸；0错的丝滑词生命寿命成倍延长
        if (wrongCount == 0) return CD_L5_6DAYS;
        if (wrongCount == 1) return CD_L4_2DAYS;
        if (wrongCount <= 3) return CD_L3_9HOUR;
        return CD_L1_20MIN; // 顽固死角词每20分钟就自动浮出水面接受截击
    }


    /**
     * 🚀 获取斩杀页面大盘数据（完全告别 Object[] 索引苦海）
     *
     * @param bookId 书籍ID
     * @param status 'BURNING' 代表待背词，'FROZEN' 代表已背词
     */
    public List<WordVO> getKillPageWords(String bookId, String status,Long userId) {
        // 1. 一枪轰出，拿到的直接是强类型、带属性名映射的持久化投影
        List<WordKillProjection> rawProjections = wordRelationRepository.findKillPageWordsRaw(userId,bookId, status);
        List<WordVO> voList = new ArrayList<>();

        for (WordKillProjection p : rawProjections) {
            if (p == null) continue;

            WordVO vo = new WordVO();

            // 1. 基础字符串字段防空
            vo.setWord(p.getWord() != null ? p.getWord() : "");
            vo.setPhonetic(p.getPhonetic() != null ? p.getPhonetic() : "");
            vo.setDefinition(p.getDefinition() != null ? p.getDefinition() : "");

            // 2. 状态与难度字段防空
            vo.setStatus(p.getStatus() != null ? p.getStatus() : "RAW");
            vo.setDifficulty(p.getDifficulty() != null ? p.getDifficulty() : "INIT_STRANGER");
            vo.setDisplayStrategy("EN_TO_CN"); // 默认值

            // 3. 数值字段防空与自动拆箱保护
            Integer reviewCount = p.getReviewCount();
            vo.setReviewCount(reviewCount != null ? reviewCount : 0);

            Integer totalCount = p.getTotalCount();
            int total = (totalCount != null) ? totalCount : 0;
            vo.setTotalCount(total);

            Integer wrongCount = p.getWrongCount();
            int wrong = (wrongCount != null) ? wrongCount : 0;
            vo.setWrongCount(wrong);

            Integer totalWrongCount = p.getTotalWrongCount();
            vo.setTotalWrongCount(totalWrongCount != null ? totalWrongCount : 0);

            Long lastReview = p.getLastReview();
            vo.setLastReview(lastReview != null ? lastReview : 0L);

            // 4. Double 类型字段防空
            Double stability = p.getStability();
            vo.setStability(stability != null ? stability : 2.0);

            Double difficultyAa = p.getDifficultyAa();
            vo.setDifficultyAa(difficultyAa != null ? difficultyAa : 50.0);

            // 5. 计算衍生字段 (确保分母不为0)
            String errorRate = "0%";
            if (total > 0) {
                double rate = (wrong * 100.0) / total;
                errorRate = String.format("%.1f%%", rate);
            }
            vo.setErrorRate(errorRate);

            // 6. 初始化其他战术指标
            vo.setStreak(0);
            vo.setFamiliarDepth(0);
            vo.setDynamicPriorityScore(0.0); // 建议后续补充真实计算逻辑
            vo.setMasteryLevel(0);
            vo.setReviewIntervalMinutes(0);

            voList.add(vo);
        }
        return voList;
    }

}