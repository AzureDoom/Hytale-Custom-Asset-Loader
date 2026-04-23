package com.azuredoom.hytalecustomassetloader.model;

import java.util.Objects;

/**
 * Represents a fully resolved asset along with its metadata.
 *
 * @param id               the unique identifier of the asset
 * @param asset            the parsed asset instance
 * @param source           the origin of the asset
 * @param fingerprint      a stable fingerprint used for change detection
 * @param priority         the priority used for conflict resolution
 * @param overrideEligible whether this asset can be overridden by higher-priority sources
 */
public record AssetRecord<T>(
        String id,
        T asset,
        AssetSource source,
        String fingerprint,
        int priority,
        boolean overrideEligible) {

    public AssetRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(asset, "asset");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(fingerprint, "fingerprint");
    }
}
