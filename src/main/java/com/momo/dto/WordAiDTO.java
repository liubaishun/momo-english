package com.momo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class WordAiDTO {

    private String word;
    private String phonetic;

    @JsonProperty("root_breakdown")
    private List<RootBreakdownItem> rootBreakdown;

    private String etymology;

    @JsonProperty("memory_story")
    private String memoryStory;

    @JsonProperty("family_words")
    private List<FamilyWordItem> familyWords;

    @JsonProperty("example_sentences")
    private List<ExampleSentenceItem> exampleSentences;

    @Data
    public static class RootBreakdownItem {
        private String part;
        private String meaning;
        private String type; // prefix | root | suffix
    }

    @Data
    public static class FamilyWordItem {
        private String word;
        private String meaning;
    }

    @Data
    public static class ExampleSentenceItem {
        private String en;
        private String zh;
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

    public List<RootBreakdownItem> getRootBreakdown() {
        return rootBreakdown;
    }

    public void setRootBreakdown(List<RootBreakdownItem> rootBreakdown) {
        this.rootBreakdown = rootBreakdown;
    }

    public String getEtymology() {
        return etymology;
    }

    public void setEtymology(String etymology) {
        this.etymology = etymology;
    }

    public String getMemoryStory() {
        return memoryStory;
    }

    public void setMemoryStory(String memoryStory) {
        this.memoryStory = memoryStory;
    }

    public List<FamilyWordItem> getFamilyWords() {
        return familyWords;
    }

    public void setFamilyWords(List<FamilyWordItem> familyWords) {
        this.familyWords = familyWords;
    }

    public List<ExampleSentenceItem> getExampleSentences() {
        return exampleSentences;
    }

    public void setExampleSentences(List<ExampleSentenceItem> exampleSentences) {
        this.exampleSentences = exampleSentences;
    }
}