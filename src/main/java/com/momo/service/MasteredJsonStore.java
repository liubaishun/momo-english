package com.momo.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.*;


public class MasteredJsonStore {

    private static final String PATH = "/Users/liubaishun/Documents/yintech/momo-english/src/main/resources/static/books/user_word_mastered.json";
    private static final ObjectMapper mapper = new ObjectMapper();

    public static Map<String, List<String>> readAll() {
        try {
            File file = new File(PATH);
            if (!file.exists()) return new HashMap<>();

            return mapper.readValue(file, mapper.getTypeFactory().constructMapType(Map.class, String.class, List.class));
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    public static void writeAll(Map<String, List<String>> data) {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(PATH), data);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Map<String, Map<String, Set<String>>> loadJson() {
        File file = new File("user_word_mastered.json");

        try {
            if (!file.exists()) {
                return new HashMap<>();
            }

//            return mapper.readValue(
//                    file,
//                    new TypeReference<Map<String, Map<String, Set<String>>>>() {}
//            );
            return new HashMap<>();

        } catch (Exception e) {
            e.printStackTrace();
            return new HashMap<>();
        }
    }


    public static void saveJson(Map<String, Map<String, Set<String>>> data) throws IOException {
        mapper.writerWithDefaultPrettyPrinter().writeValue(new File("user_word_mastered.json"), data);
    }


    public  static void  kill(String user, String book, String word) {

        Map<String, Map<String, Set<String>>> data = loadJson();

        data.computeIfAbsent(user, u -> new HashMap<>()).computeIfAbsent(book, b -> new HashSet<>()).add(word);

        try {
            saveJson(data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}