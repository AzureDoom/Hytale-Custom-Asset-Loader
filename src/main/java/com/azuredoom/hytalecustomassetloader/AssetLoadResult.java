package com.azuredoom.hytalecustomassetloader;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable result of an asset loading operation.
 * <p>
 * This record wraps the merged asset map produced by an {@link AssetLoader}. The map is defensively copied and exposed
 * as an unmodifiable view to preserve load-result integrity.
 *
 * @param mergedAssets the merged assets keyed by their extracted identifiers
 * @param <T>          the asset definition type
 */
public record AssetLoadResult<T>(Map<String, T> mergedAssets) {

    /**
     * Creates a new immutable load result.
     *
     * @param mergedAssets the merged assets keyed by identifier
     * @throws NullPointerException if {@code mergedAssets} is {@code null}
     */
    public AssetLoadResult(Map<String, T> mergedAssets) {
        Objects.requireNonNull(mergedAssets, "mergedAssets");
        this.mergedAssets = Collections.unmodifiableMap(new LinkedHashMap<>(mergedAssets));
    }
}
