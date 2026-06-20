package com.momo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momo.dto.BombSnapshotDTO;
import com.momo.model.WordBombSnapshot;
import com.momo.repository.WordBombSnapshotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class WordBombSnapshotService {

    @Autowired
    private WordBombSnapshotRepository snapshotRepository;

    @Autowired
    private ObjectMapper objectMapper; // Spring Boot 自带的高性能 Jackson 映射器

    /**
     * 🔒 战术保存/更新黑匣子快照
     */
    @Transactional
    public void saveSnapshot(BombSnapshotDTO dto) {
        // 🚀 零解析，直接原样落库，性能飞起，彻底绝缘所有 JDBC 映射异常
        WordBombSnapshot snapshot = new WordBombSnapshot(dto.getBookId(), dto.getBombIndex(), dto.getBombList(), dto.getUpdateTime());
        snapshotRepository.save(snapshot);
    }

    /**
     * 🛰️ 读取并恢复指定词书的黑匣子
     */
    /**
     * 🛰️ 读取并恢复指定词书的黑匣子（严密防空安全版）
     */
    @Transactional(readOnly = true)
    public BombSnapshotDTO loadSnapshot(String bookId) { // 🚀 纯字符串流转，不再需要抛出 JsonProcessingException
        Optional<WordBombSnapshot> oSnapshot = snapshotRepository.findById(bookId);

        // 🛡️ Java 8 战术防御：若无快照，直接大方返回 null
        if (!oSnapshot.isPresent()) {
            return null;
        }

        // 🏁 100% 确保有值
        WordBombSnapshot snapshot = oSnapshot.get();

        BombSnapshotDTO dto = new BombSnapshotDTO();
        dto.setBookId(snapshot.getBookId());
        dto.setBombIndex(snapshot.getBombIndex());
        dto.setUpdateTime(snapshot.getUpdateTime());

        // 🛡️ 防御：如果数据库里存的纯文本由于某种意外为空，按无快照处理
        String jsonStr = snapshot.getBombListJson();
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            return null;
        }

        // 🚀 【核心简化】：不再用 Jackson 去死磕反序列化了！
        // 直接把这串 4KB 的标准 JSON 字符串赋值给 DTO 抛回前端，绝无类型转换异常
        dto.setBombList(jsonStr);

        return dto;
    }

    /**
     * 🧹 战术销毁已闭环的快照现场
     */
    @Transactional
    public void clearSnapshot(String bookId) {
        if (snapshotRepository.existsById(bookId)) {
            snapshotRepository.deleteById(bookId);
        }
    }
}