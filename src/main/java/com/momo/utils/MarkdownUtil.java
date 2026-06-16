package com.momo.utils;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;

public class MarkdownUtil {

    public static String markdownToHtml(String markdown) {

        Parser parser = Parser.builder().build();

        HtmlRenderer renderer =
                HtmlRenderer.builder().build();

        return renderer.render(parser.parse(markdown));
    }
}