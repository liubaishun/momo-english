package com.momo.service.dict;

import com.momo.model.Word;
import com.momo.repository.WordRepository;
import com.opencsv.CSVReader;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PhoneticImportService {


    @Autowired
    private WordRepository wordRepository;

    @Transactional
    public void importPhonetic(String csvPath) throws Exception {

        // 读取CSV
        Map<String, String> phoneticMap = new HashMap<>();

        try (CSVReader reader = new CSVReader(
                new FileReader(csvPath))) {

            String[] line;

            // 跳过表头
            reader.readNext();

            while ((line = reader.readNext()) != null) {

                if (line.length < 2) {
                    continue;
                }

                String word = line[0].trim().toLowerCase();
                String phonetic = line[1].trim();

                phoneticMap.put(word, phonetic);
            }
        }

        System.out.println("词库加载完成：" + phoneticMap.size());

        // 查询数据库所有单词
        List<Word> words = wordRepository.findAll();

        int success = 0;

        for (Word word : words) {

            String phonetic = phoneticMap.get(word.getWord().toLowerCase());

            if (phonetic != null && !phonetic.trim().isEmpty()){

                word.setPhonetic(phonetic);

                success++;
            }
        }

        wordRepository.saveAll(words);

        System.out.println("更新成功：" + success + " 个单词");
    }
}