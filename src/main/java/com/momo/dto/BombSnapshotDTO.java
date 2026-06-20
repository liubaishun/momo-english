package com.momo.dto;


public class BombSnapshotDTO {
    private String bookId;
    private Integer bombIndex;
    private String bombList; // 🔑 将原来的 List<JsonNode> 直接改为 String，强行解耦！
    private Long updateTime;

    // Getters & Setters ...
    public String getBookId() { return bookId; }
    public void setBookId(String bookId) { this.bookId = bookId; }
    public Integer getBombIndex() { return bombIndex; }
    public void setBombIndex(Integer bombIndex) { this.bombIndex = bombIndex; }
    public String getBombList() { return bombList; } // 🔑 String 类型
    public void setBombList(String bombList) { this.bombList = bombList; }
    public Long getUpdateTime() { return updateTime; }
    public void setUpdateTime(Long updateTime) { this.updateTime = updateTime; }
}