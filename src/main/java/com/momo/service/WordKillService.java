package com.momo.service;

import com.momo.controller.WordBombSnapshotController;
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

    private RollingRefuelEngine refuelEngine; // 或者是你的 JPA Repository

    @Transactional
    public void processWordReview(String bookId, String word, String masteryDegree, String source) {

        WordKillProjection p = wordRelationRepository.findKillWord(bookId,word);

        // 计算衍生战术指标：动态错误率
        int total = Optional.ofNullable(p.getTotalCount()).orElse(0);
        int wrong = Optional.ofNullable(p.getWrongCount()).orElse(0);

        String errorRate = "0%";

        // 3. 完美对应 WordVO 的全参构造器 (其中 streak 默认注入 0，等待进入全屏滚动时被接管)
        WordVO vo = new WordVO(p.getWord(), p.getPhonetic(), p.getDefinition(), p.getReviewCount(), p.getStatus(), p.getDifficulty(), p.getDifficultyAa(), p.getLastReview(), total, errorRate, 0, // streak 初始值
                wrong, p.getTotalWrongCount(), 1, 0, 0, 0, 0);

        String cacheKey = bookId + ":" + word;
        long now = System.currentTimeMillis();
        // 极速注入前线内存池，折叠I/O
        if (masteryDegree != null) {
            switch (masteryDegree.toUpperCase()) {
                case "STRANGER": // 🌋 陌生：5词后必杀回马枪
                    vo.setTriggerTargetIndex(5);
                    // 🌋 一级强化：完全不会，插队+5位置。有效期 30 分钟
                    vo.setLoopStage(1);
                    vo.setExpireTimestamp(now + (30 * 60 * 1000));
                    break;
                case "VAGUE":    // 🟡 模糊：10词后回马枪
                    // 🟡 二级强化：模糊认识，插队+10位置。有效期 6 小时
                    vo.setLoopStage(2);
                    vo.setExpireTimestamp(now + (6 * 60 * 60 * 1000));
                    vo.setTriggerTargetIndex(10);
                    break;
                case "EASY": // 🟢 熟悉：30词后回马枪（留空位供后续非常熟悉演化）
                    // 🟢 三级强化：从二级回马枪挺过来的熟词审计，插队+20位置。有效期 24 小时
                    if (vo.getLoopStage() == 2) {
                        vo.setLoopStage(3);
                        vo.setExpireTimestamp(now + (24 * 60 * 60 * 1000));
                        vo.setTriggerTargetIndex(30);
                    }
                    break;
                case "PREV": // 🟢 熟悉：30词后回马枪（留空位供后续非常熟悉演化）
                    // 🟢 三级强化：从二级回马枪挺过来的熟词审计，插队+20位置。有效期 24 小时
                    refuelEngine.handleRollbackFeedback(bookId,word);
                    break;
                case "NEXT": // 🟢 熟悉：30词后回马枪（留空位供后续非常熟悉演化）
                    // 🟢 三级强化：从二级回马枪挺过来的熟词审计，插队+20位置。有效期 24 小时
                    refuelEngine.handleUserClickFeedback(bookId,vo);
                    break;
                default:
                    vo.setTriggerTargetIndex(-1); // 无需强化
                    break;
            }
        }
        WordBombSnapshotController.syncCachePool.put(cacheKey, vo);
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
    public List<WordVO> getKillPageWords(String bookId, String status) {
        // 1. 一枪轰出，拿到的直接是强类型、带属性名映射的持久化投影
        List<WordKillProjection> rawProjections = wordRelationRepository.findKillPageWordsRaw(bookId, status);
        List<WordVO> voList = new ArrayList<>();

        // 2. 流式防御转换：利用全参构造函数优雅总装
        for (WordKillProjection p : rawProjections) {
            // 计算衍生战术指标：动态错误率
            int total = p.getTotalCount() != null ? p.getTotalCount() : 0;
            int wrong = p.getWrongCount() != null ? p.getWrongCount() : 0;

            String errorRate = "0%";
            if (total > 0) {
                double rate = (wrong * 100.0) / total;
                errorRate = String.format("%.1f%%", rate);
            }

            // 3. 完美对应 WordVO 的全参构造器 (其中 streak 默认注入 0，等待进入全屏滚动时被接管)
            WordVO vo = new WordVO(p.getWord(), p.getPhonetic(), p.getDefinition(), p.getReviewCount(), p.getStatus(), p.getDifficulty(), p.getDifficultyAa(), p.getLastReview(), total, errorRate, 0, // streak 初始值
                    wrong, p.getTotalWrongCount(), 1, 0, 0, 0, 0);

            voList.add(vo);
        }
        return voList;
    }


    /**
     * 🧠 融合分层失效策略的——记忆状态预测引擎
     */
    /**
     * 🧠 融合“5-10-30绝对步长回马枪”的记忆状态预测引擎
     *
     * @param allBurningVOs 从数据库大盘捞出来的、当前处于燃烧区的所有高危候选词汇
     */
    public List<WordVO> memoryStatePredictionEngine(List<WordVO> allBurningVOs, String bookId) {
        if (allBurningVOs == null || allBurningVOs.isEmpty()) {
            return Collections.emptyList();
        }

        long now = System.currentTimeMillis();
        Map<String, WordVO> pool = WordBombSnapshotController.syncCachePool;
        List<WordVO> retaliationList = new ArrayList<>();

        // =========================================================================
        // 🔄 步骤一：提取缓存池中的回马枪种子，执行长期模型反哺，并收拢到内存插队列表
        // =========================================================================
        if (!pool.isEmpty()) {
            Iterator<Map.Entry<String, WordVO>> iterator = pool.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, WordVO> entry = iterator.next();
                String key = entry.getKey();
                WordVO cachedVO = entry.getValue();

                if (key.startsWith(bookId + ":")) {
                    // 1. 捞取底层长期持久化模型
                    WordRelation relation = wordRelationRepository.findByBookIdAndWord(bookId, cachedVO.getWord()).orElseGet(() -> {
                        WordRelation newRel = new WordRelation();
                        newRel.setBookId(bookId);
                        newRel.setWord(cachedVO.getWord());
                        newRel.setStability(2.0);
                        newRel.setDifficultyAa(50.0);
                        newRel.setStatus("BURNING");
                        return newRel;
                    });

                    // 2. 反哺长期脑科学指标（保证数据面平滑演进）
                    relation.setReviewCount(relation.getReviewCount() + 1);
                    relation.setLastReview(now);

                    String status = cachedVO.getDifficulty() != null ? cachedVO.getDifficulty().toUpperCase() : "FAMILIAR";
                    switch (status) {
                        case "STRANGER": // 陌生
                            relation.setWrongCount(relation.getWrongCount() + 1);
                            relation.setStability(Math.max(0.5, relation.getStability() * 0.35)); // 稳定度崩塌
                            relation.setDifficultyAa(Math.min(100.0, relation.getDifficultyAa() + 12.0));
                            break;
                        case "VAGUE": // 模糊
                            relation.setStability(Math.max(1.0, relation.getStability() * 0.8)); // 踩刹车
                            relation.setDifficultyAa(Math.min(100.0, relation.getDifficultyAa() + 2.0));
                            break;
                        case "EASY": // 熟悉
                            relation.setWrongCount(0);
                            double growth = 2.5 - (relation.getDifficultyAa() / 100.0);
                            relation.setStability(relation.getStability() * Math.max(1.4, growth)); // 膨胀
                            break;
                    }
                    wordRelationRepository.save(relation);

                    // 3. 收集需要进行物理回马枪插队的种子
                    if (cachedVO.getTriggerTargetIndex() > 0) {
                        retaliationList.add(cachedVO);
                    }

                    // 清洗内存，防止二次合并
                    iterator.remove();
                }
            }
        }

        // =========================================================================
        // 🔮 步骤二：计算大盘正常候选词的艾宾浩斯遗忘概率，并生成基础序列
        // =========================================================================
        List<WordVO> baseSequence = new ArrayList<>();
        for (WordVO vo : allBurningVOs) {
            // 计算流逝时间并使用公式 P_forget = 1 - e^(-t/S)
            double stability = vo.getStability() > 0 ? vo.getStability() : 2.0;
            long lastReviewTime = vo.getLastReview() != null ? vo.getLastReview() : 0L;
            double daysPassed = (lastReviewTime == 0L) ? 1.5 : (double) (now - lastReviewTime) / (1000 * 60 * 60 * 24);

            double forgetProbability = 1.0 - Math.exp(-daysPassed / stability);
            int wrongStreak = vo.getWrongCount() != null ? vo.getWrongCount() : 0;

            // 核心调度排序分
            double score = (forgetProbability * 100.0) + (vo.getDifficultyAa() * 0.5) + (wrongStreak * 10.0);
            vo.setDynamicPriorityScore(score);
            baseSequence.add(vo);
        }

        // 基础队列按遗忘高危度降维排序
        baseSequence.sort((v1, v2) -> Double.compare(v2.getDynamicPriorityScore(), v1.getDynamicPriorityScore()));

        // =========================================================================
        // ⚔️ 步骤三：【终极融合】将基础队列截取至大弹夹，并用回马枪种子强行物理插队
        // =========================================================================
        int finalCapacity = 40; // 适当放宽大弹夹长度，以便容纳 +30 位置的回马枪熟词
        List<WordVO> finalMagazine = new ArrayList<>();

        int baseIdx = 0;
        for (int i = 0; i < finalCapacity; i++) {
            // 检查当前物理位置 (i) 是否命中了某个回马枪种子的绝对阵位
            WordVO matchedRetaliationWord = null;
            for (WordVO rWord : retaliationList) {
                if (rWord.getTriggerTargetIndex() == i) {
                    matchedRetaliationWord = rWord;
                    break;
                }
            }

            if (matchedRetaliationWord != null) {
                // 💥 拦截！回马枪强制插队浮现！
                // 重置插队标记防止无限连环插队，重新随机分配盲盒展示策略
                matchedRetaliationWord.setTriggerTargetIndex(-1);
                boolean isChineseFirst = java.util.concurrent.ThreadLocalRandom.current().nextBoolean();
                matchedRetaliationWord.setDisplayStrategy(isChineseFirst ? "ZH_FIRST" : "EN_FIRST");

                finalMagazine.add(matchedRetaliationWord);
                retaliationList.remove(matchedRetaliationWord); // 移出处理完的种子
            } else {
                // 正常填充基础遗忘高危词
                if (baseIdx < baseSequence.size()) {
                    WordVO normalVo = baseSequence.get(baseIdx++);
                    boolean isChineseFirst = java.util.concurrent.ThreadLocalRandom.current().nextBoolean();
                    normalVo.setDisplayStrategy(isChineseFirst ? "ZH_FIRST" : "EN_FIRST");
                    finalMagazine.add(normalVo);
                }
            }
        }

        // 截取前 30 个词作为滚动冲锋弹夹返回给前端 UI 渲染
        int windowSize = Math.min(finalMagazine.size(), 30);
        return new ArrayList<>(finalMagazine.subList(0, windowSize));
    }

}