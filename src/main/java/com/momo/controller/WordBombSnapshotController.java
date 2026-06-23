package com.momo.controller;

import com.momo.dto.BombSnapshotDTO;
import com.momo.dto.WordVO;
import com.momo.service.WordBombSnapshotService;
import com.momo.service.strategy.RollingRefuelEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/words")
@CrossOrigin(origins = "*") // 允许前端混合调试跨域放行
public class WordBombSnapshotController {

    @Autowired
    private RollingRefuelEngine refuelEngine;
    @Autowired
    private WordBombSnapshotService wordBombSnapshotService;

    /**
     * ⚡ 场景一：拉取黑匣子:
     * 当在
     * GET http://localhost:8080/api/words/snapshot/load?bookId=xxx
     */
    @GetMapping("/snapshot/load")
    public ResponseEntity<?> loadSnapshot(@RequestParam String bookId) {
        try {
            BombSnapshotDTO dto = wordBombSnapshotService.loadSnapshot(bookId);
            if (dto == null) {
                // 🚀 返回 204 No Content，非常优雅地告诉前端：大盘是干净的，没有残留现场
                return ResponseEntity.noContent().build();
            }
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            Map<String, String> err = new HashMap<>();
            err.put("error", "解析后端黑匣子快照失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(err);
        }
    }

    /**
     * 📥 1. 前端滚动更新状态：不查不写数据库，极速进缓存，并注入分层时效
     */
    @PostMapping("/snapshot/save")
    public ResponseEntity<String> saveSnapshotToCache(@RequestBody WordVO incomingVO, @RequestParam String bookId) {
        if (incomingVO == null || incomingVO.getWord() == null) {
            return ResponseEntity.badRequest().body("❌ 战术载荷缺失");
        }

        // 1. 驱动轴前进：用户只要点一次反馈，后端的绝对时空轴就立刻原子自增 +1
        // 这一步确保了 userGlobalTick 能够精准实时记录用户在宇宙长河中走了多远
        refuelEngine.handleUserClickFeedback(bookId, "1", incomingVO);

        // 2. 纯粹的极速物理收容（折叠 I/O，不在这里做复杂的步长计算）
        // 所有的计算全部交给后续的预测引擎在批量消费时统一对齐
        String cacheKey = bookId + ":" + incomingVO.getWord();
        refuelEngine.getUserCacheSnapshot("1").put(cacheKey, incomingVO);

        return ResponseEntity.ok("⚡ 状态快照已极速收容，时空轴单向推进成功");
    }

    /**
     * 🧹 场景三：通关或主动退出时摧毁现场
     * POST http://localhost:8080/api/words/snapshot/clear?bookId=xxx
     */
    @PostMapping("/snapshot/clear")
    public ResponseEntity<?> clearSnapshot(@RequestParam String bookId) {
        try {
            wordBombSnapshotService.clearSnapshot(bookId);
            Map<String, Object> res = new HashMap<>();
            res.put("success", true);
            res.put("message", "任务圆满闭环，轰炸机快照已安全销毁");
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("擦除后端快照失败: " + e.getMessage());
        }
    }
}