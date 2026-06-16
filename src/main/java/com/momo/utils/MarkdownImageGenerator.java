package com.momo.utils;

public class MarkdownImageGenerator {

    public static void generate(
            String markdown,
            String output) {

        String body =
                MarkdownUtil.markdownToHtml(markdown);

        String html = String.format("<!DOCTYPE html>\n" +
                "                <html>\n" +
                "                <head>\n" +
                "                    <meta charset=\"utf-8\">\n" +
                "                    <style>\n" +
                "                        body{\n" +
                "                            width:800px;\n" +
                "                            margin:30px auto;\n" +
                "                            padding:40px;\n" +
                "                            font-family:微软雅黑;\n" +
                "                            background:white;\n" +
                "                            color:#333;\n" +
                "                        }\n" +
                "\n" +
                "                        h1{\n" +
                "                            color:#409EFF;\n" +
                "                        }\n" +
                "\n" +
                "                        pre{\n" +
                "                            background:#f5f5f5;\n" +
                "                            padding:15px;\n" +
                "                        }\n" +
                "\n" +
                "                        code{\n" +
                "                            color:#e96900;\n" +
                "                        }\n" +
                "                    </style>\n" +
                "                </head>\n" +
                "                <body>\n" +
                "                %s\n" +
                "                </body>\n" +
                "                </html>", body);

        HtmlToImageUtil.htmlToImage(html, output);
    }
}