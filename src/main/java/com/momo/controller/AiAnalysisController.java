package com.momo.controller;

import com.momo.dto.WordAiDTO;
import com.momo.service.WordAiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*") // 🛡️ 彻底清洗跨域沙箱阻碍
public class AiAnalysisController {

    @Autowired
    private WordAiService aiAnalysisService;

    @GetMapping("/analysis")
    public WordAiDTO getWordAnalysis(@RequestParam("word") String word) {
        return aiAnalysisService.getOrGenerateAnalysis(word);
    }
}