package com.azuredoom.hytalecustomassetloader.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a fully merged view of all loaded assets at a point in time.
 *
 * @param mergedAssets the final resolved assets keyed by ID
 * @param recordsById  the underlying asset records keyed by ID
 */
public record AssetSnapshot<T>(
    Map<String, T> mergedAssets,
    Map<String, AssetRecord<T>> recordsById
) {

    public AssetSnapshot {
        Objects.requireNonNull(mergedAssets, "mergedAssets");
        Objects.requireNonNull(recordsById, "recordsById");
        mergedAssets = Collections.unmodifiableMap(new LinkedHashMap<>(mergedAssets));
        recordsById = Collections.unmodifiableMap(new LinkedHashMap<>(recordsById));
    }

    /**
     * Returns an empty asset snapshot.
     *
     * @param <T> the asset type
     * @return an empty snapshot with no assets
     */
    public static <T> AssetSnapshot<T> empty() {
        return new AssetSnapshot<>(Map.of(), Map.of());
    }
}
