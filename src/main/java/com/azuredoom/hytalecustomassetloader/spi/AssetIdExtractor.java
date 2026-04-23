package com.azuredoom.hytalecustomassetloader.spi;

/**
 * Extracts the unique identifier for a parsed asset.
 * <p>
 * The loader uses this interface to determine how assets are keyed in the merged result map and to detect duplicates.
 *
 * @param <T> the asset definition type
 */
@FunctionalInterface
public interface AssetIdExtractor<T> {

    /**
     * Returns the identifier for the given asset.
     *
     * @param asset the parsed asset
     * @return the asset's unique identifier
     */
    String getId(T asset);
}
