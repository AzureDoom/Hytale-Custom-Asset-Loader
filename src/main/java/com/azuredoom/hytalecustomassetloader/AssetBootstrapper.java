package com.azuredoom.hytalecustomassetloader;

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

    /**
     * Creates a new bootstrapper using the provided loader and registrar.
     *
     * @param loader    the loader responsible for discovering and parsing assets
     * @param registrar a callback that registers each loaded asset with the caller's registry or service layer
     * @throws NullPointerException if either argument is {@code null}
     */
    public AssetBootstrapper(AssetLoader<T> loader, Consumer<T> registrar) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.registrar = Objects.requireNonNull(registrar, "registrar");
    }

    /**
     * Executes the full bootstrap sequence.
     * <p>
     * This method loads all assets from the configured discovery sources, then passes each merged asset to the
     * registrar in iteration order. The returned map is the same merged result produced by the loader.
     *
     * @return an immutable map of merged assets keyed by their extracted IDs
     * @throws RuntimeException if asset discovery, parsing, or registration fails
     */
    public Map<String, T> bootstrap() {
        var result = loader.loadAll();
        for (var asset : result.mergedAssets().values()) {
            registrar.accept(asset);
        }
        return result.mergedAssets();
    }
}
