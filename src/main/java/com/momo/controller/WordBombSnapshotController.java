package com.momo.controller;

import com.momo.dto.BombSnapshotDTO;
import com.momo.dto.WordVO;
import com.momo.service.WordBombSnapshotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/words")
@CrossOrigin(origins = "*") // 允许前端混合调试跨域放行
public class WordBombSnapshotController {

    @Autowired
    private WordBombSnapshotService wordBombSnapshotService;

    /**
     * ⚡ 场景一：拉取黑匣子:
     *   当在
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


    // 🏎️ 核心：高并发内存蓄水池，替代直接的数据库 I/O
    public static final Map<String, WordVO> syncCachePool = new ConcurrentHashMap<>();


    /**
     * 📥 1. 前端滚动更新状态：不查不写数据库，极速进缓存，并注入分层时效
     */
    @PostMapping("/snapshot/save")
    public ResponseEntity<String> saveSnapshotToCache(@RequestBody WordVO incomingVO, @RequestParam String bookId) {
        if (incomingVO == null || incomingVO.getWord() == null) {
            return ResponseEntity.badRequest().body("❌ 战术载荷缺失");
        }

        String cacheKey = bookId + ":" + incomingVO.getWord();
        String status = incomingVO.getDifficulty();

        if (status != null) {
            switch (status.toUpperCase()) {
                case "STRANGER": // 🌋 陌生：5词后必杀回马枪
                    incomingVO.setTriggerTargetIndex(5);
                    break;
                case "VAGUE":    // 🟡 模糊：10词后回马枪
                    incomingVO.setTriggerTargetIndex(10);
                    break;
                case "FAMILIAR": // 🟢 熟悉：30词后回马枪（留空位供后续非常熟悉演化）
                    incomingVO.setTriggerTargetIndex(30);
                    break;
                default:
                    incomingVO.setTriggerTargetIndex(-1); // 无需强化
                    break;
            }
        }

        // 极速注入前线内存池，折叠I/O
        syncCachePool.put(cacheKey, incomingVO);
        return ResponseEntity.ok("⚡ 物理回马枪步长锁定，已注入内存池");
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