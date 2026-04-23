package com.azuredoom.hytalecustomassetloader;

import java.nio.file.Path;
import java.time.Duration;
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
 * @param enableLiveReload              whether live reloading is enabled
 * @param watchExternalPacks            whether the external pack directory should be watched for changes
 * @param watchExplodedClasspathDirectories whether exploded classpath directories should be watched for changes
 * @param reloadDebounce                the debounce delay applied before reloading after filesystem events
 */
public record AssetDiscoveryOptions(
        String resourceFolder,
        String fileExtension,
        Path externalPackDirectory,
        boolean allowExternalOverrides,
        boolean failIfClasspathFolderMissing,
        boolean enableLiveReload,
        boolean watchExternalPacks,
        boolean watchExplodedClasspathDirectories,
        Duration reloadDebounce) {

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
            boolean failIfClasspathFolderMissing) {
        this(
                resourceFolder,
                fileExtension,
                externalPackDirectory,
                allowExternalOverrides,
                failIfClasspathFolderMissing,
                false,
                false,
                false,
                Duration.ofMillis(250));
    }

    /**
     * Creates a validated asset discovery configuration.
     *
     * @param resourceFolder                    the root classpath folder to scan, such as {@code "tags"} or
     *                                          {@code "classes"}
     * @param fileExtension                     the required file extension for matching asset files, such as
     *                                          {@code ".json"}
     * @param externalPackDirectory             the directory containing external ZIP or JAR asset packs
     * @param allowExternalOverrides            whether assets loaded from external packs may replace previously loaded
     *                                          assets with the same ID
     * @param failIfClasspathFolderMissing      whether loading should fail when the classpath resource folder is missing
     * @param enableLiveReload                  whether live reloading is enabled
     * @param watchExternalPacks                whether the external pack directory should be watched for changes
     * @param watchExplodedClasspathDirectories whether exploded classpath directories should be watched for changes
     * @param reloadDebounce                    the debounce delay applied before reloading after filesystem events
     * @throws NullPointerException if any required reference parameter is {@code null}
     * @throws IllegalArgumentException if {@code resourceFolder} or {@code fileExtension} is blank,
     *                                  or if {@code reloadDebounce} is not positive
     */
    public AssetDiscoveryOptions {
        resourceFolder = Objects.requireNonNull(resourceFolder, "resourceFolder");
        fileExtension = Objects.requireNonNull(fileExtension, "fileExtension");
        externalPackDirectory = Objects.requireNonNull(externalPackDirectory, "externalPackDirectory");
        reloadDebounce = Objects.requireNonNull(reloadDebounce, "reloadDebounce");

        if (resourceFolder.isBlank()) {
            throw new IllegalArgumentException("resourceFolder must not be blank");
        }
        if (fileExtension.isBlank()) {
            throw new IllegalArgumentException("fileExtension must not be blank");
        }
        if (reloadDebounce.isNegative() || reloadDebounce.isZero()) {
            throw new IllegalArgumentException("reloadDebounce must be positive");
        }
    }
}
