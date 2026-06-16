package com.momo.controller;

import com.momo.utils.MarkdownImageGenerator;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.nio.file.Files;

@RestController
@RequestMapping("/api")
public class MarkdownController {

    @PostMapping("/markdown/image")
    public void export(
            @RequestBody String markdown,
            HttpServletResponse response)
            throws Exception {

        File file =
                File.createTempFile("markdown_", ".png");

        // 🔍 核心清洗黑魔法：用正则表达式拦截并修复 LaTeX 公式污染
        if (markdown != null) {
            // 1. 匹配 $$\text{内容}$$ 并将其转换为标准的 Markdown 行内代码块 `内容`
            markdown = markdown.replaceAll("\\$\\$\\s*\\\\text\\{(.*?)\\}\\s*\\$\\$", "`$1`");

            // 2. 容错处理：匹配普通的 $\text{内容}$
            markdown = markdown.replaceAll("\\$\\s*\\\\text\\{(.*?)\\}\\s*\\$", "`$1`");

            // 3. 容错处理：如果还有残留的单独的 $$ 包裹
            markdown = markdown.replaceAll("\\$\\$(.*?)\\$\\$", "`$1`");
        }

        MarkdownImageGenerator.generate(
                markdown,
                file.getAbsolutePath());

        response.setContentType("image/png");

        response.setHeader(
                "Content-Disposition",
                "attachment; filename=markdown.png");

        Files.copy(
                file.toPath(),
                response.getOutputStream());

        response.flushBuffer();

        file.delete();
    }
}