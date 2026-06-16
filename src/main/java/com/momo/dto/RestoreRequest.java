package com.momo.dto;

import java.util.List;

public class RestoreRequest {
    private String book;
    private List<String> words;

    // Getters and Setters...


    public String getBook() {
        return book;
    }

    public void setBook(String book) {
        this.book = book;
    }

    public List<String> getWords() {
        return words;
    }

    public void setWords(List<String> words) {
        this.words = words;
    }
}