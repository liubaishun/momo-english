package com.momo.utils;

import com.momo.model.Word;
import org.apache.commons.beanutils.BeanUtils;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class HelperUtil {

    public static   List<Word> convertWithBeanUtils(List<Map<String, Object>> kaoyan) {
        List<Word> wordList = new ArrayList<>();
        if (kaoyan == null) {
            return wordList;
        }

        for (Map<String, Object> map : kaoyan) {
            try {
                Word word = new Word();
                // BeanUtils.populate 会自动将 map 中的 key-value 匹配到 word 的同名属性
                // 注意：它会自动处理大部分基本类型的转换，但 String 到 String 最直接
                BeanUtils.populate(word, map);
                wordList.add(word);
            } catch (Exception e) {
                e.printStackTrace(); // 实际项目中应记录日志
            }
        }
        return wordList;
    }


    public static List<Map<String, String>> convertToStringMapList(List<Map<String, Object>> sourceList) {
        if (sourceList == null) {
            return null;
        }

        return sourceList.stream()
                .map(map -> {
                    // 创建一个新的 HashMap 来存储转换后的 String 键值对
                    Map<String, String> stringMap = new HashMap<>();
                    for (Map.Entry<String, Object> entry : map.entrySet()) {
                        String key = entry.getKey();
                        Object value = entry.getValue();
                        // 将 Object 转换为 String，处理 null 值情况
                        stringMap.put(key, value != null ? value.toString() : null);
                    }
                    return stringMap;
                })
                .collect(Collectors.toList());
    }
}
