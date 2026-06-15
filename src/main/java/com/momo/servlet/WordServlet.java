package com.momo.servlet;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;

@WebServlet("/api/words")
public class WordServlet extends HttpServlet {

    // 读取 json 文件数据返回给前端
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=utf-8");
        String path = getServletContext().getRealPath("/WEB-INF/classes/words_data.json");

        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line);
        }
        br.close();
        response.getWriter().write(sb.toString());
    }

    // 后台管理：接收前端添加的单词并保存（此处仅写核心逻辑，实际开发需解析JSON并追加写入）
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("utf-8");
        String word = request.getParameter("word");
        String category = request.getParameter("category");
        // TODO: 将新单词解析并写回 words_data.json
        response.getWriter().write("{\"status\":\"success\"}");
    }
}