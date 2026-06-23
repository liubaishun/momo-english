package com.momo.service.strategy;

import com.momo.dto.UserTimelineState;
import com.momo.dto.WordVO;
import com.momo.model.WordRelation;
import com.momo.repository.WordRelationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 🛰️ 滚动补仓核心引擎（精益防御版）
 */


/**
 * 🧠 谢泼德 - 认知记忆遗忘公式 (Shepard Forgetting Formula)
 * ===========================================================
 * *
 * ForgetProbability = 1.0 - exp(- ( daysPassed / stability ))
 * * ┌──────────────┐      ┌───────────────────────────────────┐
 * │  1.0 (基准)  │ ───> │ 代表完全遗忘的极限概率绝对上限值  │
 * └──────────────┘      └───────────────────────────────────┘
 * │
 * ▼   [减去]
 * ┌──────────────┐      ┌───────────────────────────────────┐
 * │  e (自然底)  │ ───> │ 数学常数约 2.71828，构建非线性衰减 │
 * └──────────────┘      └───────────────────────────────────┘
 * │
 * ▼   [指数权重]
 * ┌──────────────┐      ┌───────────────────────────────────┐
 * │  daysPassed  │ ───> │ 距离上一次复习过去的时间 (分子：天)│
 * └──────────────┘      └───────────────────────────────────┘
 * │
 * ▼   [除以]
 * ┌──────────────┐      ┌───────────────────────────────────┐
 * │  stability   │ ───> │ 记忆稳定度 (分母：值越大，指数越小)│
 * └──────────────┘      └───────────────────────────────────┘
 */

// 遗忘概率随时间演变趋势图 (ASCII)
// 这幅图形象地展示了为什么算法要对分值进行降序排列。随着 daysPassed（天数）拉长，遗忘概率会沿着斜率无限逼近 $100\%$：

@Service
public class RollingRefuelEngine {

    @Autowired
    private WordRelationRepository wordRelationRepository;

    private final Map<String, Map<String, WordVO>> userCachePool = new ConcurrentHashMap<>();
    private final Map<String, UserTimelineState> userStateMap = new ConcurrentHashMap<>();
    private final Map<String, Integer> userTickMap = new ConcurrentHashMap<>();

    private String buildTimelineKey(Object userId, String bookId) {
        return userId + ":" + bookId;
    }

    public UserTimelineState getState(Long userId, String bookId) {
        String key = buildTimelineKey(userId, bookId);
        return userStateMap.computeIfAbsent(key, k -> {
            UserTimelineState state = new UserTimelineState();
            state.setRetaliationBuffer(new ArrayList<>());
            return state;
        });
    }

    public void handleUserClickFeedback(String bookId, String userId, WordVO snapshot) {
        Map<String, WordVO> cache = userCachePool.computeIfAbsent(userId, k -> Collections.synchronizedMap(new LinkedHashMap<>()));
        cache.put(bookId + ":" + snapshot.getWord(), snapshot);

        String timelineKey = buildTimelineKey(userId, bookId);
        userTickMap.merge(timelineKey, 1, Integer::sum);
    }

    public void handleRollbackFeedback(String bookId, String userIdStr, String currentWord, Long userId) {
        String timelineKey = buildTimelineKey(userIdStr, bookId);
        Integer userGlobalTick = userTickMap.getOrDefault(timelineKey, 0);

        if (userGlobalTick > 0) {
            userTickMap.put(timelineKey, userGlobalTick - 1);
        }

        List<WordVO> retaliationBuffer = getState(userId, bookId).getRetaliationBuffer();
        for (WordVO rWord : retaliationBuffer) {
            if (rWord.getWord().equals(currentWord)) {
                rWord.setTriggerTargetIndex(rWord.getTriggerTargetIndex() - 1);
                break;
            }
        }
    }




    /**
     * 🧠 终极时空对齐版——滚动预测引擎（完美适配：提前同步、上一步回退、极速错峰）
     *
     * @param allBurningVOs 从数据库捞出的常规候选高危词大盘
     * @param bookId        词书ID
     * @param userId        用户ID
     * @param globalTick    🎯 前端绝对长河当前的真实进度锚点（最高指挥官）极其天才的“绝对时空阵位（Grid Tick）伏击机制
     * @return 永远严丝合缝的 5 词轰炸弹夹
     */
    public List<WordVO> memoryStatePredictionEngine(List<WordVO> allBurningVOs, String bookId, Long userId, int globalTick) {
        if (allBurningVOs == null || allBurningVOs.isEmpty()) {
            return Collections.emptyList();
        }

        long now = System.currentTimeMillis();
        // ─── 🛡️ 核心时空对齐：强制以后端/前端最高共识对齐绝对 Tick ───
        userTickMap.put(buildTimelineKey(userId, bookId), globalTick);
        List<WordVO> retaliationBuffer = getState(userId, bookId).getRetaliationBuffer();


        /* ===================================================================================
         * 🛰️ 🚀【步骤一：惰性时间清洗 & 地雷超时失效审计】时空物理流转图
         * ===================================================================================
         */
        if (!retaliationBuffer.isEmpty()) {
            Iterator<WordVO> bufferIterator = retaliationBuffer.iterator();
            while (bufferIterator.hasNext()) {
                WordVO rWord = bufferIterator.next();

                if (now > rWord.getExpireTimestamp()) {
                    bufferIterator.remove(); // 物理卸载引信

                    // ⚡ 修正：基于前端传入的最前线绝对刻度对齐
                    int deltaTick = rWord.getTriggerTargetIndex() - globalTick;

                    for (WordVO baseVo : allBurningVOs) {
                        if (baseVo.getWord().equals(rWord.getWord())) {
                            baseVo.setWrongCount((baseVo.getWrongCount() != null ? baseVo.getWrongCount() : 0) + 1);
                            baseVo.setLastReview(now);

                            double penalty = deltaTick > 15 ? 0.15 : (deltaTick > 6 ? 0.3 : 0.5);
                            double currentStability = baseVo.getStability() != null ? baseVo.getStability() : 2.0;
                            baseVo.setStability(Math.max(0.5, currentStability * penalty));
                            break;
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 🔄 步骤二：提取缓存池快照（纯内存高能流盘，彻底剥离数据库 I/O）
        // =========================================================================
        Map<String, WordVO> userCache = userCachePool.get(String.valueOf(userId));

        // 提前计算出当前常规大盘可以参与排阵的候选词基数（剔除已被雷锁定的词）
        Set<String> currentlyLockedWords = retaliationBuffer.stream().map(WordVO::getWord).collect(Collectors.toSet());
        int estimatedBaseSize = (int) allBurningVOs.stream().filter(v -> !currentlyLockedWords.contains(v.getWord())).count();

        if (userCache != null && !userCache.isEmpty()) {
            List<Map.Entry<String, WordVO>> entries;
            synchronized (userCache) {
                entries = new ArrayList<>(userCache.entrySet());
            }

            String prefix = bookId + ":";
            Set<String> wordsToUpdate = new HashSet<>();
            List<WordVO> newRetaliationWords = new ArrayList<>();

            for (Map.Entry<String, WordVO> entry : entries) {
                String key = entry.getKey();
                WordVO cachedVO = entry.getValue(); // 🚀 这已经是 Service 层算好的、最完美的真理快照了！

                if (key.startsWith(prefix)) {
                    // 🎯 【真理对齐】：不再重新计算 Stride！直接使用 Service 层已经校准好的 TriggerTargetIndex
                    // 如果它在 Service 里算出来需要埋雷（即 targetIndex > 0）
                    if (cachedVO.getTriggerTargetIndex() != null && cachedVO.getTriggerTargetIndex() > 0) {

                        wordsToUpdate.add(cachedVO.getWord());
                        newRetaliationWords.add(cachedVO); // 直接送入战场前线雷区

                        estimatedBaseSize = Math.max(0, estimatedBaseSize - 1);
                    }

                    // 消费完毕，立刻在高速缓存中物理抹除，防止重复装填
                    userCache.remove(key);
                }
            }

            // 将本轮实时打分孵化出的新地雷，无缝合流进雷区缓冲区
            if (!wordsToUpdate.isEmpty()) {
                retaliationBuffer.removeIf(r -> wordsToUpdate.contains(r.getWord()));
                retaliationBuffer.addAll(newRetaliationWords);
            }
        }

        // =========================================================================
        // 🔮 步骤三：基础大盘常规词排序
        // =========================================================================
        /*
         * 🧠 谢泼德 - 认知记忆遗忘公式 (Shepard Forgetting Formula)
         * ===========================================================
         *
         * ForgetProbability = 1 - exp(- ( daysPassed / stability ))
         * * daysPassed : 距离上次复习过去的天数 (流逝时间)
         *   stability  : 单词当前的记忆稳定度 (抗遗忘阻尼)
         * * 演变趋势: 天数越多 -> 概率越高 -> 综合 Score 越高 -> 越先复习
         */
        Set<String> lockedWords = retaliationBuffer.stream().map(WordVO::getWord).collect(Collectors.toSet());
        // 获取当前用户已经看过的常规大盘词集合
        Set<String> consumedWords = getState(userId, bookId).getConsumedWordSet();
        List<WordVO> baseSequence = new ArrayList<>();

        for (WordVO vo : allBurningVOs) {
            // 🎯 核心过滤：如果这个词已经在雷区，或者在当前批次里已经被消费过了，直接跳过！
            if (lockedWords.contains(vo.getWord()) || consumedWords.contains(vo.getWord())) continue;

            double stability = vo.getStability() != null && vo.getStability() > 0 ? vo.getStability() : 2.0;
            long lastReviewTime = vo.getLastReview() != null ? vo.getLastReview() : 0L;
            double daysPassed = (lastReviewTime == 0L) ? 1.5 : (double) (now - lastReviewTime) / (1000 * 60 * 60 * 24);

            double forgetProbability = 1.0 - Math.exp(-daysPassed / stability);
            int wrongStreak = vo.getWrongCount() != null ? vo.getWrongCount() : 0;
            double difficulty = vo.getDifficultyAa() != null ? vo.getDifficultyAa() : 50.0;

            double score = (forgetProbability * 100.0) + (difficulty * 0.5) + (wrongStreak * 10.0);
            vo.setDynamicPriorityScore(score);
            baseSequence.add(vo);
        }

        // 🚨 极端防空保护：如果过滤完之后大盘空了，说明一轮已经背完了，自动熔断清空已消费集合，开启新的一轮轮转
        if (baseSequence.isEmpty() && !consumedWords.isEmpty()) {
            consumedWords.clear();
            // 重新来一次流转
            for (WordVO vo : allBurningVOs) {
                if (!lockedWords.contains(vo.getWord())) {
                    baseSequence.add(vo);
                }
            }
        }
        baseSequence.sort((v1, v2) -> {
            // 1. 全面防御对象本身为 null 的极端情况
            if (v1 == null && v2 == null) return 0;
            if (v1 == null) return 1;
            if (v2 == null) return -1;

            // 2. 提取分值，并提供 0.0 作为绝对安全的默认兜底分
            double s1 = v1.getDynamicPriorityScore() != null ? v1.getDynamicPriorityScore() : 0.0;
            double s2 = v2.getDynamicPriorityScore() != null ? v2.getDynamicPriorityScore() : 0.0;

            // 3. 执行安全的降序排列
            return Double.compare(s2, s1);
        });

        // =========================================================================
        // ⚔️ 步骤四：【绝对对齐】以最高指挥官 globalTick 为绝对起点向后装填 5 个格子
        // =========================================================================
        List<WordVO> nextFivePacket = new ArrayList<>();
        int baseIdx = 0;

        for (int i = 0; i < 5; i++) {
            // 🎯 终极修复：格子开辟的绝对阵位，完全与传入的前端真实进度 globalTick 融为一体
            // 🎯 targetGridTick 分别代表：15, 16, 17, 18, 19 号传送带格子
            int targetGridTick = globalTick + i;

            WordVO matchedRetaliationWord = null;
            // 🛸 核心：去地雷缓冲区里捞取“炸弹”
            for (WordVO rWord : retaliationBuffer) {
                // 当 i = 0 时，targetGridTick = 15。此时正好对上了 apple 的 triggerTargetIndex (15)！
                if (rWord.getTriggerTargetIndex() == targetGridTick && now <= rWord.getExpireTimestamp()) {
                    matchedRetaliationWord = rWord;
                    break;
                }
            }

            if (matchedRetaliationWord != null) {
                // 💥 轰！抓到了地雷词！强行插塞到当前返回包（nextFivePacket）的第一位！
                String strategy = decideDisplayStrategy(matchedRetaliationWord.getStability(), matchedRetaliationWord.getWrongCount(), true);
                matchedRetaliationWord.setDisplayStrategy(strategy);
                nextFivePacket.add(matchedRetaliationWord);
                retaliationBuffer.remove(matchedRetaliationWord);// 炸完从雷区物理移除
            } else {
                // 🍏 如果当前格子没有踩雷，才从常规大盘（baseSequence）里捞高危词补仓:防塌陷保护。
                if (baseIdx < baseSequence.size()) {
                    WordVO normalVo = baseSequence.get(baseIdx++);
                    String strategy = decideDisplayStrategy(normalVo.getStability(), normalVo.getWrongCount(), false);
                    normalVo.setDisplayStrategy(strategy);
                    nextFivePacket.add(normalVo);

                    // 🎯 核心动作：只要常规大盘词被吐出去了，立刻将其拉黑，下一次调用接口绝对不会再出现它！
                    consumedWords.add(normalVo.getWord());
                }
            }
        }

        return nextFivePacket;
    }

    // 🛰️ 异步刷盘临时缓冲区：Map<"bookId:word", WordRelation>
    private final Map<String, WordRelation> dirtyRelationMap = new ConcurrentHashMap<>();

    public Map<String, WordRelation> getDirtyRelationMap() {
        return dirtyRelationMap;
    }

    /**
     * 暂存需要落库的脏数据（纯内存，无 I/O 开销）
     */
    public void stageDirtyRelation(String bookId, String word, WordRelation relation) {
        String key = bookId + ":" + word;
        // 采用 merge 或直接 put，高频点击时会自动覆盖，实现“高频修改、只落库最后一次”的去重合并效果
        dirtyRelationMap.put(key, relation);
    }

    /**
     * 获取并清空当前的脏数据池（供定时任务提取）
     */
    public List<WordRelation> flushDirtyRelations() {
        if (dirtyRelationMap.isEmpty()) {
            return Collections.emptyList();
        }
        List<WordRelation> stageList = new ArrayList<>(dirtyRelationMap.values());
        // 提取后清空，准备承接下一轮实时写入
        dirtyRelationMap.keySet().removeAll(dirtyRelationMap.keySet());
        return stageList;
    }

    /**
     * 🧠 记忆科学决策器：根据单词熟练度，动态解耦并分配展示策略
     *
     * @param stability    记忆稳定性系数（越小越不稳定）
     * @param wrongCount   错误连击数
     * @param isFromBuffer 是否来自高频雷区缓冲区
     * @return "EN_FIRST" (正向识别) 或 "ZH_FIRST" (深层逆向提取)
     */
    private String decideDisplayStrategy(Double stability, Integer wrongCount, boolean isFromBuffer) {
        double safeStability = stability != null ? stability : 2.0;
        int safeWrong = wrongCount != null ? wrongCount : 0;

        // ─── 阶梯 1：铁血红线防御（极度陌生或高频出错词） ───
        // 如果错误连击数很高，或者属于刚刚点过“陌生”被丢进雷区的超级新词（稳定性暴跌到了1.0以下）
        if (safeWrong >= 2 || safeStability < 1.0) {
            return "EN_FIRST"; // 必须先看英文！降低认知负荷，防止用户心态崩溃
        }

        // ─── 阶梯 3：高维拔高突破（高度熟悉词） ───
        // 如果稳定性已经很高（说明用户连续答对，或者是基础大盘里很久没错过的高稳词）
        if (safeStability >= 4.0 && safeWrong == 0) {
            // 既然正向已经无感，必须强行开启 90% 的逆向提取轰炸，逼迫大脑回忆拼写
            return ThreadLocalRandom.current().nextDouble() < 0.90 ? "ZH_FIRST" : "EN_FIRST";
        }

        // ─── 阶梯 2：常态中景交织战场（模糊/常规过渡词） ───
        // 稳定性在 1.0 ~ 4.0 之间。根据稳定性线性拉高“逆向提取”的概率
        // 稳定性越高，说明越有自信心，ZH_FIRST 的概率就越大
        double zhProbability = 0.30 + (safeStability / 10.0); // 概率区间大概在 40% ~ 70% 之间

        // 如果是雷区词（比如刚点过模糊），稍微调高一点正向识别率给予缓冲
        if (isFromBuffer) {
            zhProbability -= 0.10;
        }

        return ThreadLocalRandom.current().nextDouble() < zhProbability ? "ZH_FIRST" : "EN_FIRST";
    }


    public void resetBurningWordsForNewRound(String bookId, Long userId) {
        List<WordVO> retaliationBuffer = getState(userId, bookId).getRetaliationBuffer();
        Set<String> consumedWordSet = getState(userId, bookId).getConsumedWordSet();
        retaliationBuffer.clear();
        consumedWordSet.clear(); // 🎯 记得清理干净
        Map<String, WordVO> userCache = userCachePool.get(String.valueOf(userId));
        if (userCache != null) {
            userCache.keySet().removeIf(key -> key.startsWith(bookId + ":"));
        }

        userTickMap.put(buildTimelineKey(userId, bookId), 0);
        System.out.println("🛰️ [时空编排器核心] 词书: " + bookId + " 绝对零点对齐成功！");
    }

    /**
     * 🛰️ 智能时空编排器——自适应动态错峰步长计算（科学遗忘曲线对齐版）
     *
     * @param stability        单词当前的记忆稳定性系数
     * @param state            用户的反馈状态 (STRANGER, VAGUE, FAMILIAR, EASY)
     * @param remainingSteps   当前批量结算时，该词前方积压的未消费步数
     * @param baseSequenceSize 燃烧大盘中除去地雷后，真正可用的常规词储备量
     * @return 完美的绝对伏击相对步长 (返回 -1 表示不埋雷，直接流放大盘)
     */
    public int calculateAdaptiveStride(double stability, String state, int remainingSteps, int baseSequenceSize) {
        // 1. 注入绝对防御性低保，稳定性至少为 0.4
        stability = Math.max(0.4, stability);
        int rawStride;

        switch (state.toUpperCase()) {
            case "STRANGER": // 🌋 陌生：必须闪电贴脸，强行锁定在下一批的黄金前线
                // 修正：我们要扣除前方已经消耗的步数，确保它精准落在新弹夹的第 3~5 个位置，而不是被拉长
                rawStride = Math.max(4, 5 - remainingSteps);
                break;

            case "VAGUE": // ⚡ 模糊：中短期强化，基于记忆稳定性动态错峰
                // 稳定性越低复现越快，同时加上 remainingSteps 做微幅错峰平滑，最高不超过 12 步
                rawStride = (int) Math.min(12, (stability * 3) + (remainingSteps / 2));
                rawStride = Math.max(6, rawStride); // 至少给 6 步，挪出当前 5 词弹夹
                break;

            case "FAMILIAR": // 🍏 熟悉：进入远景伏击圈，拉长步长
                // 表现良好的词，根据其稳定性拉伸空间，给基础大盘留出消化时间，通常在 15~30 步之间
                rawStride = (int) Math.min(30, stability * 8);
                rawStride = Math.max(15, rawStride);
                break;

            case "EASY": // 💎 简单/掌握：突触通路极其牢固
            default:
                // 科学分流：判定为低频维护词，直接返回 -1。不放进地雷缓冲区，彻底流放大盘，让出正面战场
                return -1;
        }

        // =========================================================================
        // 🚧 💥【核心防御性熔断机制】：防止常规词匮乏导致的时空塌陷（死锁）
        // =========================================================================
        // 如果算出来的步长比大盘里剩下所有的基础词还要多，说明大盘已经见底了（单词变少）
        if (baseSequenceSize <= rawStride) {
            if (baseSequenceSize <= 0) {
                // 🚨 极端场景：大盘空了，全部词都在雷区。强行给个安全步长 5，配合前端触发一轮重置
                return 5;
            }
            // 💡 降维收缩：将步长强行压缩到当前常规词的“最后一个格子”之后（即 baseSequenceSize + 1）
            // 这样既能保证步骤四有基础词填仓，又能让这个错题以最快速度在常规词消耗完时“无缝顶上”！
            return baseSequenceSize + 1;
        }

        return rawStride;
    }
    /**
     * ⚡ 极速注入前线内存池，供外层 Service 异步上报时调用
     */
    public void cacheWordSnapshot(String userId, String bookId, String word, WordVO vo) {
        // 使用 ConcurrentHashMap 的 computeIfAbsent 确保线程安全
        Map<String, WordVO> userCache = this.userCachePool.computeIfAbsent(
                userId,
                k -> Collections.synchronizedMap(new LinkedHashMap<>())
        );

        // 统一拼装全局唯一的 cacheKey (bookId:word)
        String cacheKey = bookId + ":" + word;
        userCache.put(cacheKey, vo);
    }


    /**
     * 🪐 外界获取当前用户高频快照池的只读视图（规避外层直接破坏缓存结构）
     */
    public Map<String, WordVO> getUserCacheSnapshot(String userId) {
        Map<String, WordVO> userCache = this.userCachePool.get(userId);
        if (userCache == null) {
            return Collections.emptyMap();
        }
        // 返回一个不可变快照，防止外部其他 Service 破坏引擎内部的缓存
        synchronized (userCache) {
            return new LinkedHashMap<>(userCache);
        }
    }
}