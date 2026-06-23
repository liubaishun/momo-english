package com.momo.task;

import com.momo.model.WordRelation;
import com.momo.repository.WordRelationRepository;
import com.momo.service.strategy.RollingRefuelEngine;
import lombok.extern.slf4j.Slf4j; // 1. 导入 Lombok 门面注解
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
public class WordRelationFlushTask {

    @Autowired
    private RollingRefuelEngine refuelEngine;

    @Autowired
    private WordRelationRepository wordRelationRepository;

    /**
     * 🛰️ 每 12 秒执行一次物理批量刷盘，收拢高频 I/O
     */
    @Scheduled(fixedDelay = 12000)
    @Transactional
    public void executeBatchFlush() {
        // 1. 弹出脏池快照
        List<WordRelation> dirtyList = refuelEngine.flushDirtyRelations();

        if (dirtyList.isEmpty()) {
            return;
        }
        try {
            long start = System.currentTimeMillis();
            //log.info("💾 发现内存脏数据 {} 条，开始启动异步批量持久化总线...", dirtyList.size());

            // 2. 利用 Spring Data 的 saveAll 进行底层 Batch 优化写入
            wordRelationRepository.saveAll(dirtyList);

            //log.info("🌟 批量刷盘大获成功！耗时: {} ms", (System.currentTimeMillis() - start));
        } catch (Exception e) {
            // log.error("💥 异步刷盘崩溃！正在回滚并重新拦截脏数据包...", e);
            // 容灾保护：如果刷盘失败，可以考虑把 dirtyList 重新 merge 回 dirtyRelationMap
            for (WordRelation rel : dirtyList) {
                refuelEngine.stageDirtyRelation(rel.getBookId(), rel.getWord(), rel);
            }
        }
    }
}