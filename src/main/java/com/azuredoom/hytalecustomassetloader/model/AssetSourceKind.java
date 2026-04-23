package com.azuredoom.hytalecustomassetloader.model;

/**
 * Enumerates the supported origins for loaded assets.
 */
public enum AssetSourceKind {

    /**
     * Asset loaded from an exploded classpath directory.
     */
    CLASSPATH_DIRECTORY,

    /**
     * Asset loaded from a JAR already present on the classpath.
     */
    CLASSPATH_JAR,

    /**
     * Asset loaded from an external ZIP asset pack.
     */
    EXTERNAL_ZIP,

    /**
     * Asset loaded from an external JAR asset pack.
     */
    EXTERNAL_JAR,

    /**
     * Asset loaded from an external directory
     */
    EXTERNAL_DIRECTORY
}
