package com.momo.model;

public class Word {
    private String id;
    private String word;
    private String phonetic;
    private String definition;
    private String example;
    private String category;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getWord() { return word; }
    public void setWord(String word) { this.word = word; }
    public String getPhonetic() { return phonetic; }
    public void setPhonetic(String phonetic) { this.phonetic = phonetic; }
    public String getDefinition() { return definition; }
    public void setDefinition(String definition) { this.definition = definition; }
    public String getExample() { return example; }
    public void setExample(String example) { this.example = example; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}