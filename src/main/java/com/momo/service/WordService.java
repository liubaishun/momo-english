package com.momo.service;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.momo.dto.KillRequest;
import com.momo.dto.WordVO;
import com.momo.model.Word;
import com.momo.model.WordDetailDTO;
import com.momo.model.WordRelation;
import com.momo.repository.WordRelationRepository;
import com.momo.repository.WordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class WordService {


    @Autowired
    private WordRepository wordRepository;
    @Autowired
    private WordRelationRepository wordRelationRepository; // 或者是你的 JPA Repository


    private static final int MAX_KILL_COUNT = 5; // 触发彻底冻结的硬性阈值
    private final ObjectMapper objectMapper = new ObjectMapper();
    // 动态斩杀数据持久化本地路径
    private final String DATA_PATH = "data/";
    private final String KILLED_FILE_PATH = "killed_data.json";

    // 内存缓存：存放所有书的熟词库。 结构：{ "kaoyan": { "abandon": { "status": "hard", "lastReview": 1715600000 }, "absent": { "status": "easy", "lastReview": 1715600000 }  } }
    private Map<String, Map<String, WordState>> killedDataMap = new HashMap<>();

    @PostConstruct
    public void init() {
        try {
            File file = new File(KILLED_FILE_PATH);
            if (file.exists()) {
                // 方法二：读取字节数组后转换（需指定编码）
                byte[] bytes = Files.readAllBytes(Paths.get(KILLED_FILE_PATH));
                String content = new String(bytes, StandardCharsets.UTF_8);
                killedDataMap = objectMapper.readValue(content, new TypeReference<Map<String, Map<String, WordState>>>() {
                });
            } else {
                // 初始化空文件
                // ... 在方法内部
                try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(KILLED_FILE_PATH), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    writer.write("{}");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            killedDataMap = new HashMap<>();
        }
    }


    /**
     * 读取指定单词书的静态资源
     */
    public List<Map<String, Object>> getStaticWordsByBook(String bookName) {
        try {
            // 比如存放在 src/main/resources/static/books/kaoyan.json
            ClassPathResource resource = new ClassPathResource("static/books/" + bookName + ".json");
            if (!resource.exists()) {
                return Collections.emptyList();
            }
            if (!resource.exists() || resource.contentLength() == 0) {
                return Collections.emptyList();
            }

            List<Map<String, Object>> mapList = objectMapper.readValue(resource.getInputStream(), new TypeReference<List<Map<String, Object>>>() {
            });

            Map<String, List<String>> all = MasteredJsonStore.readAll();
            List<String> mastered = all.getOrDefault("u001", new ArrayList<>());


            return mapList;
        } catch (IOException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }


    public List<Word> getAllWords() {
        return wordRepository.findAll();
    }

    public Optional<Word> getWordById(Long id) {
        return wordRepository.findById(id);
    }

    public Optional<Word> findByWord(String text) {
        return wordRepository.findByWord(text);
    }

    public List<Word> getWordsByBookId(String bookId) {
        return wordRepository.findByBookId(bookId);
    }

    public Word createWord(Word word) {
        // 检查是否已存在
        if (wordRepository.findByWord(word.getWord()).isPresent()) {
            throw new RuntimeException("Word already exists: " + word.getWord());
        }
        return wordRepository.save(word);
    }

    public Word updateWord(Long id, Word wordDetails) {
        Word word = wordRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Word not found with id: " + id));

        word.setWord(wordDetails.getWord());
        word.setPhonetic(wordDetails.getPhonetic());
        word.setDefinition(wordDetails.getDefinition());
        word.setExample(wordDetails.getExample());
        word.setCategory(wordDetails.getCategory());
        word.setBookId(wordDetails.getBookId());

        return wordRepository.save(word);
    }

    public void deleteWord(Long id) {
        Word word = wordRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Word not found with id: " + id));
        wordRepository.delete(word);
    }




    public void importToDatabase(List<Word> words) {
        // JPA 的 saveAll 在大数据量下性能较差，因为它是逐个执行 persist/merge
        // 建议分批处理，每批清理持久化上下文以节省内存
        int batchSize = 1000;
        for (int i = 0; i < words.size(); i++) {
            wordRepository.save(words.get(i));

            if (i % batchSize == 0 && i > 0) {
                wordRepository.flush();
                // 如果使用 Hibernate，可能需要 clear() 来释放一级缓存
                // ((EntityManager) entityManager).clear();
            }
        }
    }

    public List<WordDetailDTO> getWordsByStatus(String bookId, String status) {
        List<Object[]> results = wordRelationRepository.findWordsByBookAndStatusRaw(bookId, status);


        return  results.stream().map(obj -> new WordDetailDTO(
                (String) obj[0],  // word
                (String) obj[1],  // phonetic
                (String) obj[2],  // definition
                ((Number) obj[3]).intValue(), // reviewCount (处理可能的 BigDecimal/Long)
                (String) obj[4],  // status
                (String) obj[5],  // difficulty
                obj[6] != null ? ((Number) obj[6]).longValue() : null // lastReview
        )).collect(Collectors.toList());

    }


    public List<WordVO> getWordsByBookAndStatus(String bookId, String status) {
        // 1. 从 Repository 获取原生打平的数据
        List<Object[]> rawList = wordRelationRepository.findWordsByBookAndStatusRaw(bookId, status);
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

//        WordVO wordVO = new WordVO();
//        wordVO.setWord("abandon");
//        wordVO.setPhonetic("/əˈbændən/");
//        wordVO.setDefinition("vt. 放弃，遗弃");
//        wordVO.setDifficulty("easy");
//        wordVO.setStatus("BURNING");
//        wordVO.setTotalCount(8);
//        wordVO.setErrorRate("37.5%");
//        wordVO.setStatus("BURNING");
//        wordVO.setLastReview(System.currentTimeMillis());
//        voList.add(wordVO);

        return voList;
    }


    /**
     * 获取某本书的熟词
     */
    public Set<String> getKilledWordsByBook(String bookName) {
        return killedDataMap.getOrDefault(bookName, new HashMap<>()).keySet();
    }

    /**
     * 核心升级：带有难度评级的斩杀逻辑
     */
    public synchronized void killWordWithStatus(String bookName, String word, String status) {
        // 1. 获取该书的 Map，如果没有则创建
        Map<String, WordState> bookMap = killedDataMap.computeIfAbsent(bookName, k -> new HashMap<>());

        // 2. 更新或创建单词状态
        bookMap.put(word, new WordState(status, System.currentTimeMillis()));

        // 3. 持久化
        saveToFile();
    }


    /**
     * 🛰️ 战术核心 1：处理 /filter/kill 接口（清洗、分拣与滚动斩杀）
     *
     *
     *                       [ 接收请求 /filter/kill ]
     *                                |
     *                    [ 查询或创建 WordRelation 记录 ]
     *                                |
     *                      { 判定请求来源 source }
     *                                |
     *               +----------------+----------------+
     *               | (source == "list")              | (else / null / "bomb")
     *               v                                 v
     *       【 通道一：列表首次分拣 】         【 通道二：常规全屏大轰炸 】
     *               |                                 |
     *       +-------+-------+                 +-------+-------+
     *       |               |                 | 'easy'        | 'wrong'
     *   (mastered)       (vague)              v               v
     *    直接冻结       留存燃烧区       [reviewCount +1]   [wrongCount +1]
     *   Count = 5       Count = 2             |             状态保持 BURNING
     *   DIFFICULTY:     DIFFICULTY:    { reviewCount >= 5? }
     *   INIT_MASTERED   INIT_VAGUE            |
     *                                +--------+--------+
     *                                | 是              | 否
     *                                v                 v
     *                         [ 晋级 FROZEN ]    [ 保持 BURNING ]
     *                                |
     *                         【 动态难度打标 】
     *                         - wrongs == 0 -> SMOOTH_KILL
     *                         - wrongs <= 3 -> NORMAL_KILL
     *                         - wrongs >  3 -> HARD_KILL
     *
     *
     */
    @Transactional
    public void processWordReview(String bookId, String word, String userStatus, String source) {
        // 1. 寻找已有记录，没有则当场初始化入舱（新词首次触碰）
        WordRelation record = wordRelationRepository.findByBookIdAndWord(bookId, word)
                .orElseGet(() -> {
                    WordRelation newRecord = new WordRelation();
                    newRecord.setBookId(bookId);
                    newRecord.setWord(word);
                    newRecord.setReviewCount(0);
                    newRecord.setWrongCount(0);
                    newRecord.setStatus("BURNING"); // 默认扔进燃烧区
                    return newRecord;
                });

        // 确保各种 Count 字段不为 null，增强健壮性
        int currentReviewCount = record.getReviewCount() != null ? record.getReviewCount() : 0;
        int currentWrongCount = record.getWrongCount() != null ? record.getWrongCount() : 0;

        // 🧭 通道一：来自“列表手动分拣”（系统初始化首次清洗）
        if ("list".equals(source)) {
            switch (userStatus) {
                case "mastered": // 【初始化 - 掌握】
                    record.setStatus("FROZEN");
                    record.setReviewCount(5);   // 次数一步顶满
                    record.setDifficulty("INIT_MASTERED");
                    break;

                case "vague":    // 【初始化 - 模糊】
                    record.setStatus("BURNING");
                    record.setReviewCount(2);   // 赠送 2 次起步分，轰炸机里再对 3 次即可通关
                    record.setDifficulty("INIT_VAGUE");
                    break;

                case "stranger": // 【初始化 - 陌生】
                default:
                    record.setStatus("BURNING");
                    record.setReviewCount(0);   // 次数归零，铁血重训
                    record.setDifficulty("INIT_STRANGER");
                    break;
            }
        }
        // 🌋 通道二：来自“全屏视觉大轰炸”的常规滚动滚动
        else {
            if ("easy".equals(userStatus)) {
                int newReviewCount = currentReviewCount + 1;
                record.setReviewCount(newReviewCount);

                // 🎯 触发通关生死线判定（满 5 次）
                if (newReviewCount >= 5) {
                    record.setStatus("FROZEN"); // 晋升冻结舱熟词

                    // 🧠 终极进化：根据在这本书中犯错的次数，动态结算该词的血烈程度
                    if (currentWrongCount == 0) {
                        record.setDifficulty("SMOOTH_KILL"); // 顺畅斩杀（一次没错，极高熟练度）
                    } else if (currentWrongCount <= 3) {
                        record.setDifficulty("NORMAL_KILL"); // 常规斩杀（轻微波动）
                    } else {
                        record.setDifficulty("HARD_KILL");   // 惨烈斩杀（硬骨头，未来需高频抽查）
                    }
                } else {
                    record.setStatus("BURNING"); // 没满 5 次，继续在燃烧区受训
                }
            }
            else if ("wrong".equals(userStatus)) {
                // 吃到马枪，错词率累加，打回大轰炸池队列尾部
                record.setWrongCount(currentWrongCount + 1);
                record.setStatus("BURNING");
            }
        }

        // 刷新最后触碰/复习的时间戳
        record.setLastReview(System.currentTimeMillis());

        // 持久化落地
        wordRelationRepository.save(record);
    }

    public WordRelation getRecord(String bookId, String word) {
        if (bookId == null || word == null) {
            return null;
        }

        // 查询数据库
        Optional<WordRelation> recordOpt = wordRelationRepository.findByBookIdAndWord(bookId, word);

        // 如果存在则返回，否则返回 null（或者你可以选择在这里创建一个新的 WordRelation 对象）
        return recordOpt.orElse(null);

        // 【可选】如果希望不存在时自动创建默认记录，可以使用以下逻辑：
        /*
        return recordOpt.orElseGet(() -> {
            WordRelation newRecord = new WordRelation();
            newRecord.setBookName(bookName);
            newRecord.setWord(word);
            newRecord.setStatus("NEW"); // 默认状态
            newRecord.setReviewCount(0);
            newRecord.setWrongCount(0);
            // 注意：这里只是创建对象，如果需要持久化到数据库，需要调用 save()
            // wordRelationRepository.save(newRecord);
            return newRecord;
        });
        */
    }

    /**
     * 📥 战术核心：批量将单词从冻结舱（熟词表）打回燃烧区重练
     * 完美闭环：状态退化、计数回炉、错词累加、打上 RESTORED 特种烙印
     *
     * @param bookId 书籍ID (对应前端的 book)
     * @param words  需要打回的单词列表
     */
    @Transactional
    public void restoreWords(String bookId, List<String> words) {
        if (words == null || words.isEmpty()) {
            return; // 战术空检，防止空指针
        }

        long currentTime = System.currentTimeMillis();

        // ⚡ 核心演进：地毯式循环遍历处理每一个要回炉的单词
        for (String word : words) {
            // 1. 查询记忆关联记录
            Optional<WordRelation> relationOpt = wordRelationRepository.findByBookIdAndWord(bookId, word);

            if (relationOpt.isPresent()) {
                WordRelation record = relationOpt.get();

                // 2. 铁血状态降级：从冻结舱蒸发，强制扔回燃烧区火力网
                record.setStatus("BURNING");

                // 3. 计数器清洗复位
                record.setReviewCount(0); // 记忆退化，通关计数全额清零，必须重新在大轰炸里积满 5 次

                int currentWrongCount = record.getWrongCount() != null ? record.getWrongCount() : 0;
                record.setWrongCount(currentWrongCount + 1); // 再次遭遇遗忘，错词记录加 1

                // 4. 身份重塑：统一改名为“二战回炉攻坚词”
                record.setDifficulty("RESTORED");

                // 5. 刷新最后触碰时间戳
                record.setLastReview(currentTime);

                // 6. 持久化保存
                wordRelationRepository.save(record);

                System.out.println("【战术打回】单词 [" + word + "] 已成功降级为 BURNING，打上 [RESTORED] 标签！");
            } else {
                // 边缘防御：如果数据库根本没有这个词的记录，打印警告，跳过（不破坏整个批量队列的连续性）
                System.err.println("【打回警告】未找到书籍 " + bookId + " 中单词 [" + word + "] 的关联记录，自动跳过。");
            }
        }
    }


    private synchronized void saveToFile() {
        ObjectMapper objectMapper = new ObjectMapper();
        Path path = Paths.get(KILLED_FILE_PATH);
        try {
            // 只有当路径中包含目录结构时，才去创建目录
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            // 2. 序列化
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(killedDataMap);

            // 3. 写入文件 (将字符串转为 UTF-8 字节数组)
            Files.write(Paths.get(KILLED_FILE_PATH), json.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * 核心算法：当用户判定【掌握】时，推进艾宾浩斯 CD 冷却时间
     */
    public void promoteWordCD(WordState state) {
        long currentTime = System.currentTimeMillis();
        int currentReview = state.getReviewCount();
        long cdInterval;

        // 根据当前的复抽成功次数，计算下一次解冻的延迟时间 (毫秒级转换)
        switch (currentReview) {
            case 0:
                cdInterval = 20 * 60 * 1000L;          // 1次通关 -> 20分钟后偷袭
                break;
            case 1:
                cdInterval = 60 * 60 * 1000L;          // 复抽1次成功 -> 1小时后偷袭
                break;
            case 2:
                cdInterval = 9 * 60 * 60 * 1000L;       // 复抽2次成功 -> 9小时后(隔夜)
                break;
            case 3:
                cdInterval = 2 * 24 * 60 * 60 * 1000L;  // 复抽3次成功 -> 2天后
                break;
            case 4:
                cdInterval = 6 * 24 * 60 * 60 * 1000L;  // 复抽4次成功 -> 6天后
                break;
            default:
                // 已经通过了第5次复抽
                state.setStatus("archive");             // 晋升为终极熟词，打入冷宫封存
                return;
        }

        state.setNextReviewTime(currentTime + cdInterval);
        state.setReviewCount(currentReview + 1); // 跨越一个复抽层级
        state.setStatus("reviewing");             // 锁定为复抽航行状态
    }

    /**
     * 核心算法：当复抽翻车，或者在突击营点击【陌生】时，执行剥夺和打回重修惩罚
     */
    public void punishWordToQueue(WordState state) {
        state.setStreak(0);          // 突击计数清零
        state.setReviewCount(0);      // 剥夺所有复抽成就，贬为庶民
        state.setNextReviewTime(0L);  // 清空冷却时间，立刻解冻
        state.setStatus("queue");      // 强行塞回正在轰炸的突击营状态
    }

    /**
     * 更新某个单词的最新记忆状态，并立刻同步持久化到本地 JSON 文件中
     *
     * @param bookName 单词书名（例如 "kaoyan"）
     * @param state    已经经过算法修改过的最新 WordState 状态对象
     */
    public void updateWordState(String bookName, WordState state) {
        // 🛡️ 1. 安全锁：确保该单词书的外层 Map 抽屉存在，防患于未然
        //bookMemoryMap.putIfAbsent(bookName, new HashMap<>());
        killedDataMap.putIfAbsent(bookName, new HashMap<>());

        // 2. 根据单词书名拿到进度子表，然后以 word 为 Key，把最新的状态对象覆写进去
        killedDataMap.get(bookName).put(state.getWord(), state);

        // 💾 3. 核心持久化：内存更新完毕后，立刻调用同步锁方法，把整个大账本完整写进 killed_data.json
        saveToFile();
    }


    /**
     * 核心算法：智能生成 20 词的动态轰炸工作队列
     *
     * @param bookName         单词书名（如 kaoyan）
     * @param allWordsFromBook 该单词书下的全量单词列表（通常从你的本地 json 词库中读取）
     * @param limitSize        期望的轰炸机队列大小（固定为 20）
     */
    public List<Map<String, String>> generateSmartQueue(String bookName, List<Map<String, String>> allWordsFromBook, int limitSize) {
        long currentTime = System.currentTimeMillis();

        // 确保该单词书的内存记录已初始化
        killedDataMap.putIfAbsent(bookName, new HashMap<>());
        Map<String, WordState> progressMap = killedDataMap.get(bookName);

        // --- 🛡️ 1. 记忆滑坡拦截：检查超期严重的词，直接重置重修 ---
        long maxGracePeriod = 7 * 24 * 60 * 60 * 1000L; // 宽限期：7天
        for (WordState ws : progressMap.values()) {
            if ("reviewing".equals(ws.getStatus()) && ws.getNextReviewTime() > 0) {
                // 如果当前时间已经超过了规定的复习时间 7 天以上
                if (currentTime - ws.getNextReviewTime() > maxGracePeriod) {
                    punishWordToQueue(ws); // 剥夺成就，打回最前线重修
                }
            }
        }

        // 最终返回给前端的 20 词战术包装盒
        List<Map<String, String>> finalQueue = new ArrayList<>();
        // 记录已经进入本次队列的单词，防止重复
        Set<String> selectedWordStrings = new HashSet<>();

        // --- 🔄 2. 搜集目前“正在突击营”里的老兵 (state == "queue") ---
        for (Map<String, String> w : allWordsFromBook) {
            String wStr = w.get("word");
            WordState ws = progressMap.get(wStr);
            if (ws != null && "queue".equals(ws.getStatus())) {
                finalQueue.add(w);
                selectedWordStrings.add(wStr);
                if (finalQueue.size() >= limitSize) {
                    return finalQueue; // 突击营里的词已经塞满了 20 个
                }
            }
        }

        // --- 🛰️ 3. 限额捞取“已解冻”的复抽熟词 (分期付款机制) ---
        List<WordState> eligibleReviews = new ArrayList<>();
        for (WordState ws : progressMap.values()) {
            if ("reviewing".equals(ws.getStatus()) && currentTime >= ws.getNextReviewTime()) {
                eligibleReviews.add(ws);
            }
        }

        // 优质老兵优先：按照 reviewCount 从大到小排序，容易被唤醒的先来
        eligibleReviews.sort((a, b) -> Integer.compare(b.getReviewCount(), a.getReviewCount()));

        // 严格控制单次混入的复习词数量，上限为 5 个，抗雪崩核心！
        int reviewQuota = Math.min(5, eligibleReviews.size());
        for (int i = 0; i < reviewQuota; i++) {
            WordState ws = eligibleReviews.get(i);
            // 从全量词库中找到这个单词的完整释义对象
            for (Map<String, String> w : allWordsFromBook) {
                if (w.get("word").equals(ws.getWord())) {
                    if (!selectedWordStrings.contains(ws.getWord())) {
                        finalQueue.add(w);
                        selectedWordStrings.add(ws.getWord());
                    }
                    break;
                }
            }
            if (finalQueue.size() >= limitSize) {
                return finalQueue;
            }
        }

        // --- 🆕 4. 纯生词补仓：如果格子还没凑满 20 个，从词库捞新词 ---
        for (Map<String, String> w : allWordsFromBook) {
            String wStr = w.get("word");
            WordState ws = progressMap.get(wStr);

            // 只要这个词在进度本里不存在（纯生词），或者状态是初始的 pool
            if (ws == null || "pool".equals(ws.getStatus())) {
                if (!selectedWordStrings.contains(wStr)) {
                    // 初始化该新词进入突击营状态
                    WordState initVs = getOrInitWordState(bookName, wStr);
                    initVs.setStatus("queue");

                    finalQueue.add(w);
                    selectedWordStrings.add(wStr);
                }
            }
            if (finalQueue.size() >= limitSize) {
                break;
            }
        }

        saveToFile(); // 补仓完毕，同步一次最新的单词状态到 json 磁盘
        return finalQueue;
    }


    /**
     * 获取或初始化某个单词在特定单词书中的状态
     *
     * @param bookName 单词书名（例如 "kaoyan"）
     * @param word     具体的单词（例如 "abandon"）
     * @return 坚固、绝对不为空的 WordState 状态对象
     */
    public WordState getOrInitWordState(String bookName, String word) {
        // 🛡️ 1. 安全锁：确保该单词书的外层 Map 容器存在。如果用户第一次切换到这本书，自动开辟空间
        killedDataMap.putIfAbsent(bookName, new HashMap<>());

        // 2. 取出这本书对应的所有单词进度子表
        Map<String, WordState> wordMap = killedDataMap.get(bookName);

        // 3. 核心分流判定
        if (!wordMap.containsKey(word)) {
            // 🆕 情况 A：进度本里没有这个词。说明这是刚从小黑屋（大词库）里捞出来的纯生词！
            // 现场实例化一个干净的状态：streak=0, reviewCount=0, state="pool"
            WordState newState = new WordState(word);

            // 塞进内存子表中，防止下次查询再丢失
            wordMap.put(word, newState);

            return newState; // 返回这个崭新的生词状态
        }

        // 🔄 情况 B：这词之前背过，进度本里早就记录了它的 streak、CD 时间和复抽层级
        // 直接把老档案抽出来返回，供后续算法去推进或剥夺
        return wordMap.get(word);
    }
}