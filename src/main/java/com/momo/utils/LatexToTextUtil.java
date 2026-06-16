package com.momo.utils;


public class LatexToTextUtil {

    public static String convert(String markdown) {

        if (markdown == null) {
            return "";
        }

        String result = markdown;

        // 去掉 $$ 包裹
        result = result.replace("$$", "");

        // \text{xxx}
        result = result.replaceAll(
                "\\\\text\\{([^}]*)\\}",
                "$1");

        // 箭头
        result = result.replace("\\rightarrow", "→");

        // 小于等于
        result = result.replace("\\leq", "≤");

        // 大于等于
        result = result.replace("\\geq", "≥");

        // 不等于
        result = result.replace("\\neq", "≠");

        // 乘号
        result = result.replace("\\times", "×");

        // 除号
        result = result.replace("\\div", "÷");

        return result;
    }
}