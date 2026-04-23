package com.azuredoom.hytalecustomassetloader.model;

import java.util.Objects;

/**
 * Represents the result of reloading assets.
 *
 * <p>Contains both the previous and current snapshots, along with the computed diff
 * describing changes between them.</p>
 */
public record AssetReloadResult<T>(
        AssetSnapshot<T> previousSnapshot,
        AssetSnapshot<T> currentSnapshot,
        AssetDiff<T> diff) {

    public AssetReloadResult {
        Objects.requireNonNull(previousSnapshot, "previousSnapshot");
        Objects.requireNonNull(currentSnapshot, "currentSnapshot");
        Objects.requireNonNull(diff, "diff");
    }
}
