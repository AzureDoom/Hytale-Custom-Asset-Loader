package com.azuredoom.hytalecustomassetloader.model;

import java.util.Objects;

/**
 * Describes the origin of a loaded asset.
 *
 * @param kind the category of source the asset came from
 * @param name the human-readable source path or archive entry name
 */
public record AssetSource(
    AssetSourceKind kind,
    String name
) {

    /**
     * Creates a new asset source descriptor.
     *
     * @param kind the category of source the asset came from
     * @param name the human-readable source path or archive entry name
     * @throws NullPointerException if either argument is {@code null}
     */
    public AssetSource {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(name, "name");
    }
}
