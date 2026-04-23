package com.azuredoom.hytalecustomassetloader.spi;

import com.azuredoom.hytalecustomassetloader.model.AssetReloadResult;
import com.azuredoom.hytalecustomassetloader.model.AssetSnapshot;

/**
 * Contract for consumers that apply asset changes incrementally.
 * <p>
 * Implementations receive add, update, and remove callbacks as assets change, allowing efficient synchronization
 * without full reloads.
 * </p>
 */
public interface ReloadableAssetRegistrar<T> {

    /**
     * Adds a new asset.
     *
     * @param id    the asset identifier
     * @param asset the asset instance
     */
    void add(String id, T asset);

    /**
     * Updates an existing asset.
     *
     * @param id            the asset identifier
     * @param previousAsset the previous asset instance
     * @param currentAsset  the updated asset instance
     */
    void update(String id, T previousAsset, T currentAsset);

    /**
     * Removes an existing asset.
     *
     * @param id    the asset identifier
     * @param asset the asset instance being removed
     */
    void remove(String id, T asset);

    /**
     * Applies an initial snapshot by adding all assets.
     *
     * @param snapshot the snapshot to apply
     */
    default void applyInitial(AssetSnapshot<T> snapshot) {
        for (var entry : snapshot.mergedAssets().entrySet()) {
            add(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Applies an incremental reload result.
     * <p>
     * Removals are processed first, followed by updates, then additions.
     * </p>
     *
     * @param result the reload result to apply
     */
    default void applyReload(AssetReloadResult<T> result) {
        for (var entry : result.diff().removed().entrySet()) {
            remove(entry.getKey(), entry.getValue().asset());
        }
        for (var entry : result.diff().updated().entrySet()) {
            update(entry.getKey(), entry.getValue().previous().asset(), entry.getValue().current().asset());
        }
        for (var entry : result.diff().added().entrySet()) {
            add(entry.getKey(), entry.getValue().asset());
        }
    }
}
