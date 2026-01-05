package com.frausto.service.util;

import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;

public class InstanceTracker {
    final ConcurrentHashMap<String, Integer> nextIndices = new ConcurrentHashMap<>();
    final ConcurrentHashMap<String, PriorityQueue<Integer>> reusableIndices = new ConcurrentHashMap<>();

    public InstanceTracker() {
    }

    public String generateReusedName(String baseName) {
        reusableIndices.putIfAbsent(baseName, new PriorityQueue<>());
        nextIndices.putIfAbsent(baseName, 0);

        PriorityQueue<Integer> availableIndices = reusableIndices.get(baseName);

        int index;
        if (!availableIndices.isEmpty()) {
            index = availableIndices.poll();
        } else {
            index = nextIndices.get(baseName);
            nextIndices.put(baseName, index + 1);
        }
        
        return index == 0 ? baseName : baseName + "_" + index;
    }

    public void destroyReusedName(String name) {
        String baseName;
        int index;

        int underscorePos = name.lastIndexOf('_');
        if (underscorePos == -1) {
            baseName = name;
            index = 0;
        } else {
            baseName = name.substring(0, underscorePos);
            index = Integer.parseInt(name.substring(underscorePos + 1));
        }

        reusableIndices.putIfAbsent(baseName, new PriorityQueue<>());
        reusableIndices.get(baseName).offer(index);
    }
}
