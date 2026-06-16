package com.momo.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import javax.persistence.*;

// Word 实体
@Entity
@Table(name = "word")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Word {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 如果 id 是 String 且非自增，可能需要改为 @GenericGenerator 或手动赋值
    private String id; // 建议统一为 Long，如果数据库确实是 String 类型，请保留 String 并移除 GeneratedValue 或改用 UUID 策略

    @Column(nullable = false, unique = true)
    private String word;

    private String phonetic;

    @Column(columnDefinition = "TEXT")
    private String definition;

    private String example;
    private String category;

    @Column(name = "book_id")
    private String bookId;


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public String getPhonetic() {
        return phonetic;
    }

    public void setPhonetic(String phonetic) {
        this.phonetic = phonetic;
    }

    public String getDefinition() {
        return definition;
    }

    public void setDefinition(String definition) {
        this.definition = definition;
    }

    public String getExample() {
        return example;
    }

    public void setExample(String example) {
        this.example = example;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getBookId() {
        return bookId;
    }

    public void setBookId(String bookId) {
        this.bookId = bookId;
    }
}
