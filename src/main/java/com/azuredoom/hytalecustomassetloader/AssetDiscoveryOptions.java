package com.azuredoom.hytalecustomassetloader;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Immutable configuration for discovering custom assets.
 * <p>
 * These options control where assets are searched for, which files qualify as asset definitions, and how duplicate or
 * missing resources are handled during loading.
 *
 * @param resourceFolder               the root classpath folder to scan, such as {@code "tags"} or {@code "classes"}
 * @param fileExtension                the required file extension for matching asset files, such as {@code ".json"}
 * @param externalPackDirectory        the directory containing external ZIP or JAR asset packs
 * @param allowExternalOverrides       whether assets loaded from external packs may replace previously loaded assets
 *                                     with the same ID
 * @param failIfClasspathFolderMissing whether loading should fail when the classpath resource folder is missing
 */
public record AssetDiscoveryOptions(
    String resourceFolder,
    String fileExtension,
    Path externalPackDirectory,
    boolean allowExternalOverrides,
    boolean failIfClasspathFolderMissing
) {

    /**
     * Creates a new asset discovery configuration.
     *
     * @param resourceFolder               the root classpath folder to scan, such as {@code "tags"} or
     *                                     {@code "classes"}
     * @param fileExtension                the required file extension for matching asset files, such as {@code ".json"}
     * @param externalPackDirectory        the directory containing external ZIP or JAR asset packs
     * @param allowExternalOverrides       whether assets loaded from external packs may override existing assets
     * @param failIfClasspathFolderMissing whether a missing classpath resource folder should be treated as an error
     * @throws NullPointerException if {@code resourceFolder}, {@code fileExtension}, or {@code externalPackDirectory}
     *                              is {@code null}
     */
    public AssetDiscoveryOptions(
        String resourceFolder,
        String fileExtension,
        Path externalPackDirectory,
        boolean allowExternalOverrides,
        boolean failIfClasspathFolderMissing
    ) {
        this.resourceFolder = Objects.requireNonNull(resourceFolder, "resourceFolder");
        this.fileExtension = Objects.requireNonNull(fileExtension, "fileExtension");
        this.externalPackDirectory = Objects.requireNonNull(externalPackDirectory, "externalPackDirectory");
        this.allowExternalOverrides = allowExternalOverrides;
        this.failIfClasspathFolderMissing = failIfClasspathFolderMissing;
    }
}
