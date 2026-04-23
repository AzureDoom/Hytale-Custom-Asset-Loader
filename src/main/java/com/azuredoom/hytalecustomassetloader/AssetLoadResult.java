package com.azuredoom.hytalecustomassetloader;

import com.azuredoom.hytalecustomassetloader.model.AssetSnapshot;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable result of an asset loading operation.
 *
 * <p>This record exposes both the merged asset map and the full snapshot produced by an
 * {@link AssetLoader}. The merged asset map is defensively copied and exposed as an
 * unmodifiable view to preserve result integrity.</p>
 *
 * @param mergedAssets the merged assets keyed by their extracted identifiers
 * @param snapshot     the full snapshot produced by the load operation
 * @param <T>          the asset definition type
 */
public record AssetLoadResult<T>(Map<String, T> mergedAssets, AssetSnapshot<T> snapshot) {

    /**
     * Creates a new immutable load result.
     *
     * @param mergedAssets the merged assets keyed by identifier
     * @param snapshot     the full asset snapshot
     * @throws NullPointerException if {@code mergedAssets} or {@code snapshot} is {@code null}
     */
    public AssetLoadResult(Map<String, T> mergedAssets, AssetSnapshot<T> snapshot) {
        this.mergedAssets = Collections.unmodifiableMap(
                new LinkedHashMap<>(Objects.requireNonNull(mergedAssets, "mergedAssets")));
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
    }

    /**
     * Creates a new load result from the provided snapshot.
     *
     * @param snapshot the snapshot to expose through this load result
     * @throws NullPointerException if {@code snapshot} is {@code null}
     */
    public AssetLoadResult(AssetSnapshot<T> snapshot) {
        this(snapshot.mergedAssets(), snapshot);
    }
}
