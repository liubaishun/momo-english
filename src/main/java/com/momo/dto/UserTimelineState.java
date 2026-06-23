package com.momo.dto;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class UserTimelineState {

    private int globalTick = 0;

    private long lastActiveTime;

    private  List<WordVO> retaliationBuffer = new CopyOnWriteArrayList<>();

    // 如果你的 UserTimelineState 在外面，请在里面加上这个：
    private Set<String> consumedWordSet = new HashSet<>();
    public Set<String> getConsumedWordSet() { return consumedWordSet; }

    public void setConsumedWordSet(Set<String> consumedWordSet) {
        this.consumedWordSet = consumedWordSet;
    }

    public int getGlobalTick() {
        return globalTick;
    }

    public void setGlobalTick(int globalTick) {
        this.globalTick = globalTick;
    }

    public long getLastActiveTime() {
        return lastActiveTime;
    }

    public void setLastActiveTime(long lastActiveTime) {
        this.lastActiveTime = lastActiveTime;
    }

    public List<WordVO> getRetaliationBuffer() {
        return retaliationBuffer;
    }

    public void setRetaliationBuffer(List<WordVO> retaliationBuffer) {
        this.retaliationBuffer = retaliationBuffer;
    }
}