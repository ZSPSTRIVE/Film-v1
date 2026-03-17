package com.jelly.cinema.service.impl;

import lombok.Data;

import java.util.LinkedHashSet;
import java.util.Set;

@Data
public class TvboxSyncResult {

    private final Set<Long> affectedMediaIds = new LinkedHashSet<>();

    private int createdMediaCount;

    private int updatedMediaCount;

    private int filteredNoiseCount;

    private int syncedExternalResourceCount;

    public boolean hasChanges() {
        return createdMediaCount > 0
                || updatedMediaCount > 0
                || syncedExternalResourceCount > 0;
    }

    public int changedMediaCount() {
        return createdMediaCount + updatedMediaCount;
    }
}
