package com.azuredoom.hytalecustomassetloader;

import com.azuredoom.hytalecustomassetloader.model.AssetReloadResult;
import com.azuredoom.hytalecustomassetloader.spi.ReloadableAssetRegistrar;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Coordinates the full asset bootstrap flow for a specific asset type.
 * <p>
 * This class acts as a thin orchestration layer over an {@link AssetLoader}, taking the merged assets discovered by the
 * loader and passing each one to a caller-provided registrar. It is useful when a plugin or module wants a simple
 * one-call bootstrap step that both loads and registers assets.
 *
 * @param <T> the asset definition type being loaded and registered
 */
public final class AssetBootstrapper<T> {
    private final AssetLoader<T> loader;
    private final Consumer<T> registrar;
    private final ReloadableAssetRegistrar<T> reloadableRegistrar;

    /**
     * Creates a bootstrapper that performs one-time registration for loaded assets.
     *
     * @param loader    the asset loader used to discover and load assets
     * @param registrar the consumer that receives each loaded asset
     */
    public AssetBootstrapper(AssetLoader<T> loader, Consumer<T> registrar) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.registrar = Objects.requireNonNull(registrar, "registrar");
        this.reloadableRegistrar = null;
    }

    /**
     * Creates a bootstrapper that supports incremental reload behavior.
     *
     * @param loader              the asset loader used to discover, load, and reload assets
     * @param reloadableRegistrar the registrar that applies initial and incremental asset changes
     */
    public AssetBootstrapper(AssetLoader<T> loader, ReloadableAssetRegistrar<T> reloadableRegistrar) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.reloadableRegistrar = Objects.requireNonNull(reloadableRegistrar, "reloadableRegistrar");
        this.registrar = null;
    }

    /**
     * Loads all assets and applies the initial registration flow.
     *
     * <p>If a {@link ReloadableAssetRegistrar} was provided, the initial snapshot is applied through it.
     * Otherwise, each merged asset is passed to the one-time registrar.</p>
     *
     * @return the merged assets keyed by asset ID
     */
    public Map<String, T> bootstrap() {
        var result = loader.loadAll();
        if (reloadableRegistrar != null) {
            reloadableRegistrar.applyInitial(result.snapshot());
        } else {
            for (var asset : result.mergedAssets().values()) {
                registrar.accept(asset);
            }
        }
        return result.mergedAssets();
    }

    /**
     * Reloads assets and applies the resulting incremental changes.
     *
     * @return the result of the reload operation
     * @throws IllegalStateException if this bootstrapper was not created with a
     *                               {@link ReloadableAssetRegistrar}
     */
    public AssetReloadResult<T> reload() {
        if (reloadableRegistrar == null) {
            throw new IllegalStateException("reload() requires a ReloadableAssetRegistrar");
        }
        var result = loader.reload();
        reloadableRegistrar.applyReload(result);
        return result;
    }
}
