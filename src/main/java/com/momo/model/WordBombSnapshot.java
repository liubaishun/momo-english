package com.momo.model;


import javax.persistence.*;
import java.io.Serializable;

/**
 * <h3>🧠 艾宾浩斯记忆黑匣子 - 全屏应激轰炸机现场镜像实体</h3>
 * <p>
 * <b>【定位与美学】：</b><br>
 * 本类是整个高效背词系统的“核心电信号快照”。它打破了传统数据库多表关联的臃肿设计，
 * 采用高性能的 Key-Value（键值对）轻量化架构，将前端内存中的整个随机洗牌队列压缩为单条记录。
 * </p>
 * <p>
 * <b>【性能考量】：</b><br>
 * 在用户进行高频应激打分（1-生词、2-模糊、Enter-斩杀）时，前端会以秒级甚至更短的频率触发异步落库。
 * 本实体通过唯一主键 {@code bookId} 锁定单条记录，使底层 Hibernate 在持久化时执行主键级别的
 * UPSERT（存在则覆写更新，不存在则插入），保证每次同步都在数毫秒内完成，绝不卡顿前端事件循环。
 * </p>
 *
 * @author Xiao Liu (Sausage Hunter)
 * @since 2026-06
 */
@Entity
@Table(name = "word_bomb_snapshot")
public class WordBombSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 🔑 <b>【战术隔离锚点】—— 词书主键 ID</b>
     * <p>
     * <b>语义解析：</b> 每一个词书大盘（如：MBA核心词、CET-6）在同一时刻有且仅能有一个全屏轰炸现场。<br>
     * <b>设计哲学：</b> 直接以 bookId 作为原生数据库主键，从根本上隔离了不同词书之间的记忆状态，
     * 同时也达成了底层的“战术原子锁”效果。每次更新直接主键覆盖，零表连接开销。
     * </p>
     */
    @Id
    @Column(name = "book_id", length = 64, nullable = false)
    private String bookId;

    /**
     * 🎯 <b>【瞬时应激坐标轴】—— 当前背词队列的指针索引</b>
     * <p>
     * <b>语义解析：</b> 记录用户在当前洗牌后的乱序队列中，已经斩杀/扫描到了哪一个具体的单词位置（数组下标）。<br>
     * <b>艾宾浩斯降级核心：</b> 当跨设备唤醒或崩溃恢复时，Java 业务层（Service）就是通过操作这个指针来调整记忆体感的。<br>
     * <ul>
     * <li>若离线时间 {@code <= 20分钟} (L1级别)：指针原封不动。</li>
     * <li>若离线时间在 20分钟~1小时 (L2级别)：Java 层会将此值执行 {@code Math.max(0, bombIndex - 2)}，强行战术倒带 2 个词。</li>
     * <li>若离线时间在 1小时~9小时 (L3级别)：强行执行 {@code Math.max(0, bombIndex - 5)}，深度倒带唤醒冷切的工作记忆。</li>
     * </ul>
     * </p>
     */
    @Column(name = "bomb_index", nullable = false)
    private Integer bombIndex = 0;

    /**
     * 🌋 <b>【动态伏兵战术大盘】—— 全量乱序记忆队列大文本 (JSON 字符串)</b>
     * <p>
     * <b>语义解析：</b> 这是整个类的“硬核核心”。通过把前端内存中打乱的、处于“闪回中”或“尾部罚站中”的
     * 复杂对象数组压缩成一串标准的 JSON 字符串。在底层，Hibernate 将其映射为 {@code LONGTEXT} (大对象)。<br>
     * <b>高度解耦工业美学：</b> 后端在 DTO 层通过 Jackson 的 {@code JsonNode} 接收，在 Entity 层直接存为文本。
     * 这意味着<b>后端完全不关心前端单词对象内部包含什么字段</b>（如释义、音标、错题率等）。
     * 未来前端即使疯狂重构单词属性，后端的 Java 实体类和数据库表结构也【不需要改动一行代码】！<br>
     * <b>超时回炉依仗：</b> 当离线时间超过24小时甚至一个月（L5级别深度冷切）时，Java 业务层会提取此字段，
     * 利用 {@code Jackson} 读取未斩杀的生词切片：{@code bombList.slice(bombIndex)}，将其批量打回大盘重新洗牌，
     * 并无情丢弃当时无意义的乱序现场。
     * </p>
     */
    // ❌ 删掉旧的 @Lob 和 LONGTEXT
    // @Lob
    // @Column(name = "bomb_list_json", nullable = false, columnDefinition = "LONGTEXT")

    // 🛡️ 改为对 SQLite 最完美的标准 TEXT 映射
    @Column(name = "word_bomb_list_json", nullable = false, columnDefinition = "TEXT")
    private String bombListJson;

    /**
     * ⏱️ <b>【艾宾浩斯生命倒计时】—— 最后一次高频同步的时间戳</b>
     * <p>
     * <b>语义解析：</b> 记录当前黑匣子最后一次被刷新时的 Unix 毫秒级时间戳（{@code System.currentTimeMillis()}）。<br>
     * <b>认知学决策唯一判据：</b> 当一个月后或者数小时后再次开机触发 {@code startWordBomb()} 时，
     * 启动流第一步就是计算时差：{@code long timeDiff = System.currentTimeMillis() - snapshot.getUpdateTime();}。<br>
     * 它是划分 L1（记忆余热）到 L5（深度冷切大清洗）五级认知阶梯的关键。
     * </p>
     */
    @Column(name = "update_time", nullable = false)
    private Long updateTime;

    // =========================================================================
    // 🧱 构造函数群 (Constructors)
    // =========================================================================

    /**
     * Hibernate 核心规范所必需的无参构造函数。
     * 确保在底层通过反射实例化时，能够平滑构筑代理对象。
     */
    public WordBombSnapshot() {
    }

    /**
     * 工业级快速覆写全参构造函数
     *
     * @param bookId       词书锚点
     * @param bombIndex    应激当前指针
     * @param bombListJson 乱序全量队列大文本
     * @param updateTime   艾宾浩斯时间常数
     */
    public WordBombSnapshot(String bookId, Integer bombIndex, String bombListJson, Long updateTime) {
        this.bookId = bookId;
        this.bombIndex = bombIndex;
        this.bombListJson = bombListJson;
        this.updateTime = updateTime;
    }

    // =========================================================================
    // 🚀 标准访问器 (Getters & Setters)
    // =========================================================================

    public String getBookId() { return bookId; }
    public void setBookId(String bookId) { this.bookId = bookId; }

    public Integer getBombIndex() { return bombIndex; }
    public void setBombIndex(Integer bombIndex) { this.bombIndex = bombIndex; }

    public String getBombListJson() { return bombListJson; }
    public void setBombListJson(String bombListJson) { this.bombListJson = bombListJson; }

    public Long getUpdateTime() { return updateTime; }
    public void setUpdateTime(Long updateTime) { this.updateTime = updateTime; }

    // =========================================================================
    // 🔍 辅助诊断覆盖 (Diagnostic Tools)
    // =========================================================================

    @Override
    public String toString() {
        return "WordBombSnapshot{" +
                "bookId='" + bookId + '\'' +
                ", bombIndex=" + bombIndex +
                ", bombListLength=" + (bombListJson != null ? bombListJson.length() : 0) + " chars" +
                ", updateTime=" + updateTime +
                '}';
    }
}