package com.momo.dto;

public class WordVO {
    // === 1. 单词基本元数据（来自静态词库） ===
    private String word;        // 单词文本，如 "apple"
    private String phonetic;    // 音标，如 "/ˈæpl/"
    private String definition;  // 中文释义，如 "n. 苹果"

    // === 2. 用户记忆中间态数据（来自用户关系表 user_word_relation） ===
    private Integer reviewCount;// 成功斩杀/掌握次数（前端据此渲染 █░░░░ 进度条）
    // 🚨 【核心升级】全生命周期状态机控制线
    // 1. "RAW"      -> 原始大盘词（没有过滤、未激活）
    // 2. "BURNING"  -> 核心燃烧词（过滤后进入轰炸队列、待背、错词复习中）
    // 3. "FROZEN"   -> 铁血斩杀词（过滤后在斩杀页被干掉，不再出现）
    // 4. "ARCHIVED" -> 毕业归档词（通过多轮复习真正背完的词）
    private String status = "RAW";
    private String difficulty;  // 战术实时状态: "STUCK_LOOP"(特级卡壳), "REVIVE_FALL"(回炉), "INIT_STRANGER", "INIT_VAGUE"

    private Long lastReview;    // 最近一次复习/提取的时间戳（毫秒级）

    // === 3. 战术统计与生命周期计数器 ===
    private Integer totalCount; // 总过筛次数（掌握次数 + 错误次数）
    private String errorRate;   // 战术错误率（百分比字符串，如 "28.5%"）
    private int streak;         // 突击大循环中连续正确计数器 (0-3，满3次在前端物理剔除)
    private Integer wrongCount; // 本期错词次数（当前阶段生命周期计数器，掌握后可清零或转档）
    private Integer totalWrongCount; // 全局累计错词次数（【只增不减】终身审计计数器，用于揪出欺骗性最强的死角词）

    // === 4. 🔥 八维算法专属：脑科学动态演算及排序指标（由后端在内存中实时计算，无须入库） ===
    private Double dynamicPriorityScore; // 最终通过公式算出的【应激轰炸指数】，后端据此做降序排列
    private Integer reviewIntervalMinutes; // 当前复习设定的期望半衰期/复习间隔（分钟），用于艾宾浩斯留存率计算
    private Integer masteryLevel;        // 掌握熟练度分级 (0-5级)，由 reviewCount 动态映射，用于熟词降权
    /**
     * 熟悉深度（斩杀通关计数器）
     * 行为：每次在轰炸机或主表里【连续秒杀】或【顺利通关一组】，该值 +1
     * 触发线：只有当 familiarDepth >= 5 时，状态才允许从 "BURNING" 转为 "FROZEN" (真正冻结)
     */
    private Integer familiarDepth = 0;
    /**
     * 🛰️ 滚动轰炸机战术控制线
     * "EN_TO_CN" : 先英后中（默认，适合硬核盲测音标和拼写）
     * "CN_TO_EN" : 先中后英（反向轰炸，适合根据释义瞬间提取英文回忆）
     */
    private String displayStrategy = "EN_TO_CN";

    // === 🧠 记忆状态预测系统 (Memory State Prediction System) 核心指标 ===
    private Double difficultyAa = 50.0;       // 1. 当前动态难度 (0.0 - 100.0)
    private Double stability = 2.0;          // 2. 记忆稳定度 (Stability, 单位：天，初始生词给2天)
    private double memoryStrength = 0.0;     // 3. 当前记忆强度 (短期震荡指标)
    private double forgetProbability = 0.0;  // 4. 预测遗忘概率 (0.0 - 1.0)

    // 在 WordVO 中追加以下字段
    private int loopStage = 0;         // 当前处于第几级回马枪（0:未触发, 1:一级, 2:二级, 3:三级, 4:飞升归档）
    private long expireTimestamp = 0; // 当前级别的绝对死亡时间戳（currentTimeMillis + 有效期）


    private int triggerTargetIndex = -1; // 触发回马枪的绝对插队目标位置（-1代表未触发或已完成）

    // 补上 getter / setter
    public String getDisplayStrategy() { return displayStrategy; }
    public void setDisplayStrategy(String displayStrategy) { this.displayStrategy = displayStrategy; }

    // ... 保留你原有的 getter/setter ...
    public Integer getFamiliarDepth() {
        return familiarDepth != null ? familiarDepth : 0;
    }

    public void setFamiliarDepth(Integer familiarDepth) {
        this.familiarDepth = familiarDepth;
    }
    public WordVO() {
        this.wrongCount = 0;
        this.totalWrongCount = 0;
        this.reviewCount = 0;
        this.status = "RAW";
        this.streak = 0;
    }

    // 全参构造函数
    public WordVO(String word, String phonetic, String definition, Integer reviewCount, String status,
                  String difficulty, double difficultyAa, Long lastReview, Integer totalCount, String errorRate, int streak,
                  Integer wrongCount, Integer totalWrongCount, Integer familiarDepth, Integer masteryLevel, int loopStage, int expireTimestamp, int triggerTargetIndex) {
        this.word = word;
        this.phonetic = phonetic;
        this.definition = definition;
        this.difficulty = difficulty;
        this.reviewCount = reviewCount != null ? reviewCount : 0;
        this.status = status != null ? status : "RAW";
        this.difficultyAa = difficultyAa;
        this.lastReview = lastReview;
        this.totalCount = totalCount != null ? totalCount : 0;
        this.errorRate = errorRate;
        this.streak = streak;
        this.masteryLevel = masteryLevel;
        this.familiarDepth = familiarDepth;
        this.loopStage = loopStage;
        this.triggerTargetIndex = triggerTargetIndex;
        this.expireTimestamp = expireTimestamp;
        this.wrongCount = wrongCount != null ? wrongCount : 0;
        this.totalWrongCount = totalWrongCount != null ? totalWrongCount : 0;
    }

    // === 🚀 新增八维算力字段的 Getters 和 Setters ===
    public Double getDynamicPriorityScore() {
        return dynamicPriorityScore;
    }

    public void setDynamicPriorityScore(Double dynamicPriorityScore) {
        this.dynamicPriorityScore = dynamicPriorityScore;
    }

    public Integer getReviewIntervalMinutes() {
        // 如果数据库没有存个性化的间隔，根据当前掌握次数给一个基础艾宾浩斯期望间隔（分钟）
        if (this.reviewIntervalMinutes == null) {
            // 0次=2分钟，1次=30分钟，2次=12小时(720分)，3次=24小时(1440分)
            if (this.reviewCount == 0) return 2;
            if (this.reviewCount == 1) return 30;
            if (this.reviewCount == 2) return 720;
            return 1440;
        }
        return reviewIntervalMinutes;
    }

    public void setReviewIntervalMinutes(Integer reviewIntervalMinutes) {
        this.reviewIntervalMinutes = reviewIntervalMinutes;
    }

    public Integer getMasteryLevel() {
        // 动态将 reviewCount 映射为 0-5 级的掌握段位，用于高熟练度强制降权
        if (this.reviewCount == 0) return 0;
        if (this.reviewCount <= 2) return 1;
        if (this.reviewCount <= 5) return 2;
        if (this.reviewCount <= 8) return 3;
        if (this.reviewCount <= 12) return 4;
        return 5;
    }

    public void setMasteryLevel(Integer masteryLevel) {
        this.masteryLevel = masteryLevel;
    }

    // === 保留你原有的基础 Getters and Setters ===
    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public String getPhonetic() {
        return phonetic;
    }

    public void setPhonetic(String phonetic) {
        this.phonetic = phonetic;
    }

    public String getDefinition() {
        return definition;
    }

    public void setDefinition(String definition) {
        this.definition = definition;
    }

    public Integer getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(Integer reviewCount) {
        this.reviewCount = reviewCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public Long getLastReview() {
        return lastReview;
    }

    public void setLastReview(Long lastReview) {
        this.lastReview = lastReview;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }

    public String getErrorRate() {
        return errorRate;
    }

    public void setErrorRate(String errorRate) {
        this.errorRate = errorRate;
    }

    public int getStreak() {
        return streak;
    }

    public void setStreak(int streak) {
        this.streak = streak;
    }

    public Integer getWrongCount() {
        return wrongCount;
    }

    public void setWrongCount(Integer wrongCount) {
        this.wrongCount = wrongCount;
    }

    public Integer getTotalWrongCount() {
        return totalWrongCount;
    }

    public void setTotalWrongCount(Integer totalWrongCount) {
        this.totalWrongCount = totalWrongCount;
    }


    public Double getDifficultyAa() {
        return difficultyAa;
    }

    public void setDifficultyAa(Double difficultyAa) {
        this.difficultyAa = difficultyAa;
    }

    public Double getStability() {
        return stability;
    }

    public void setStability(Double stability) {
        this.stability = stability;
    }

    public double getMemoryStrength() {
        return memoryStrength;
    }

    public void setMemoryStrength(double memoryStrength) {
        this.memoryStrength = memoryStrength;
    }

    public double getForgetProbability() {
        return forgetProbability;
    }

    public void setForgetProbability(double forgetProbability) {
        this.forgetProbability = forgetProbability;
    }

    public int getLoopStage() {
        return loopStage;
    }

    public void setLoopStage(int loopStage) {
        this.loopStage = loopStage;
    }

    public long getExpireTimestamp() {
        return expireTimestamp;
    }

    public void setExpireTimestamp(long expireTimestamp) {
        this.expireTimestamp = expireTimestamp;
    }

    public Integer getTriggerTargetIndex() {
        return triggerTargetIndex;
    }

    public void setTriggerTargetIndex(Integer triggerTargetIndex) {
        this.triggerTargetIndex = triggerTargetIndex;
    }

}