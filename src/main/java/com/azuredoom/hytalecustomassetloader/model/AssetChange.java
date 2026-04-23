package com.azuredoom.hytalecustomassetloader.model;

import java.util.Objects;

/**
 * Represents a change between two versions of the same asset.
 *
 * @param previous the previous asset record
 * @param current  the current asset record
 */
public record AssetChange<T>(AssetRecord<T> previous, AssetRecord<T> current) {

    public AssetChange {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(current, "current");
    }
}
