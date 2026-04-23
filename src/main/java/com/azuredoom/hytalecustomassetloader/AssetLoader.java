package com.azuredoom.hytalecustomassetloader;

import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipFile;

import com.azuredoom.hytalecustomassetloader.model.AssetSourceKind;
import com.azuredoom.hytalecustomassetloader.spi.AssetIdExtractor;
import com.azuredoom.hytalecustomassetloader.spi.AssetLogger;
import com.azuredoom.hytalecustomassetloader.spi.AssetParser;

/**
 * Generic loader for Hytale-style custom assets stored as files on the classpath or inside external asset packs.
 * <p>
 * The loader performs the common discovery and merge workflow shared by many asset systems:
 * <ol>
 * <li>scan a configured classpath resource folder</li>
 * <li>read matching files from exploded directories and packaged JARs</li>
 * <li>scan an external asset-pack directory for ZIP and JAR files</li>
 * <li>parse matching entries into typed asset definitions</li>
 * <li>merge them into a single ID-keyed result map</li>
 * </ol>
 * <p>
 * Classpath resources are loaded first. External ZIP and JAR packs are loaded afterward and may optionally override
 * previously loaded definitions depending on {@link AssetDiscoveryOptions#allowExternalOverrides()}.
 *
 * @param <T> the asset definition type produced by this loader
 */
public final class AssetLoader<T> {

    private final ClassLoader classLoader;

    private final AssetDiscoveryOptions options;

    private final AssetParser<T> parser;

    private final AssetIdExtractor<T> idExtractor;

    private final AssetLogger logger;

    /**
     * Creates a new asset loader.
     *
     * @param classLoader the class loader used to discover classpath resources
     * @param options     the discovery and merge options controlling loader behavior
     * @param parser      the parser used to convert a matching file into an asset definition
     * @param idExtractor extracts the unique identifier used to key each merged asset
     * @param logger      the logger used for informational and warning messages
     * @throws NullPointerException if any argument is {@code null}
     */
    public AssetLoader(
        ClassLoader classLoader,
        AssetDiscoveryOptions options,
        AssetParser<T> parser,
        AssetIdExtractor<T> idExtractor,
        AssetLogger logger
    ) {
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
        this.options = Objects.requireNonNull(options, "options");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.idExtractor = Objects.requireNonNull(idExtractor, "idExtractor");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Loads all assets from the configured classpath folder and external asset-pack directory.
     * <p>
     * Assets are merged into insertion order using their extracted IDs. Duplicate classpath assets are skipped with a
     * warning. External pack assets may override existing assets when enabled by configuration.
     *
     * @return a result object containing the merged assets keyed by ID
     * @throws RuntimeException if any discovery, I/O, or parsing step fails
     */
    public AssetLoadResult<T> loadAll() {
        try {
            Map<String, T> merged = new LinkedHashMap<>();
            loadClasspathResources(merged);
            loadExternalAssetPacks(merged);
            logger.info("Loaded " + merged.size() + " assets from folder '" + options.resourceFolder() + "'.");
            return new AssetLoadResult<>(merged);
        } catch (Exception e) {
            throw new RuntimeException(
                "Failed to load assets from resource folder '" + options.resourceFolder() + "'",
                e
            );
        }
    }

    /**
     * Scans the configured classpath resource folder and loads matching assets from each discovered location.
     * <p>
     * Supports both exploded file-system directories and packaged JAR resources.
     *
     * @param sink the map receiving parsed and merged assets
     * @throws Exception if resource enumeration or asset loading fails
     */
    private void loadClasspathResources(Map<String, T> sink) throws Exception {
        var resources = classLoader.getResources(options.resourceFolder());

        if (!resources.hasMoreElements()) {
            if (options.failIfClasspathFolderMissing()) {
                throw new IllegalStateException("Missing resource folder: " + options.resourceFolder());
            }
            logger.warn("No resource folder found on classpath: " + options.resourceFolder());
            return;
        }

        while (resources.hasMoreElements()) {
            var resourceUrl = resources.nextElement();
            var protocol = resourceUrl.getProtocol();

            if ("file".equals(protocol)) {
                loadFromDirectory(sink, resourceUrl);
            } else if ("jar".equals(protocol)) {
                loadFromClasspathJar(sink, resourceUrl);
            } else {
                logger.warn("Skipping unsupported protocol: " + protocol + " (" + resourceUrl + ")");
            }
        }
    }

    /**
     * Recursively walks an exploded classpath directory and loads each matching asset file it finds.
     *
     * @param sink        the map receiving parsed and merged assets
     * @param resourceUrl a {@code file:} URL pointing to the root asset directory
     * @throws Exception if directory traversal or asset parsing fails
     */
    private void loadFromDirectory(Map<String, T> sink, URL resourceUrl) throws Exception {
        var root = Paths.get(resourceUrl.toURI());

        try (var stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(options.fileExtension()))
                .forEach(path -> {
                    String relative = root.relativize(path).toString().replace('\\', '/');
                    String sourceName = options.resourceFolder() + "/" + relative;

                    try (InputStream input = Files.newInputStream(path)) {
                        T asset = parser.parse(input, sourceName, AssetSourceKind.CLASSPATH_DIRECTORY);
                        putDefinition(sink, asset, false, sourceName);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to load resource " + sourceName, e);
                    }
                });
        }
    }

    /**
     * Loads matching asset files from a classpath JAR.
     *
     * @param sink        the map receiving parsed and merged assets
     * @param resourceUrl a {@code jar:} URL pointing to the asset root inside a JAR
     * @throws Exception if the JAR cannot be opened or any entry cannot be parsed
     */
    private void loadFromClasspathJar(Map<String, T> sink, URL resourceUrl) throws Exception {
        var connection = (JarURLConnection) resourceUrl.openConnection();

        try (var jarFile = connection.getJarFile()) {
            var entries = jarFile.entries();

            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                String name = entry.getName();

                if (
                    !entry.isDirectory()
                        && name.startsWith(options.resourceFolder() + "/")
                        && name.endsWith(options.fileExtension())
                ) {
                    try (InputStream input = jarFile.getInputStream(entry)) {
                        T asset = parser.parse(input, name, AssetSourceKind.CLASSPATH_JAR);
                        putDefinition(sink, asset, false, name);
                    }
                }
            }
        }
    }

    /**
     * Scans the configured external asset-pack directory for ZIP and JAR files and loads matching assets from them.
     * <p>
     * External packs are processed in filename sort order to provide deterministic override behavior.
     *
     * @param sink the map receiving parsed and merged assets
     * @throws Exception if the directory cannot be listed or an archive cannot be processed
     */
    private void loadExternalAssetPacks(Map<String, T> sink) throws Exception {
        var assetPackDir = options.externalPackDirectory();

        if (!Files.exists(assetPackDir) || !Files.isDirectory(assetPackDir)) {
            return;
        }

        try (var stream = Files.list(assetPackDir)) {
            stream.filter(Files::isRegularFile)
                .filter(path -> {
                    String name = path.getFileName().toString().toLowerCase();
                    return name.endsWith(".zip") || name.endsWith(".jar");
                })
                .sorted()
                .forEach(path -> {
                    try {
                        loadFromExternalArchive(sink, path);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to load asset pack " + path, e);
                    }
                });
        }
    }

    /**
     * Loads matching assets from a single external ZIP or JAR archive.
     *
     * @param sink        the map receiving parsed and merged assets
     * @param archivePath the archive file to inspect
     * @throws Exception if the archive cannot be opened or an entry cannot be parsed
     */
    private void loadFromExternalArchive(Map<String, T> sink, Path archivePath) throws Exception {
        try (var zipFile = new ZipFile(archivePath.toFile())) {
            var entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                var name = entry.getName();

                if (
                    !entry.isDirectory()
                        && name.startsWith(options.resourceFolder() + "/")
                        && name.endsWith(options.fileExtension())
                ) {
                    var kind = archivePath.toString().toLowerCase().endsWith(".jar")
                        ? AssetSourceKind.EXTERNAL_JAR
                        : AssetSourceKind.EXTERNAL_ZIP;

                    try (var input = zipFile.getInputStream(entry)) {
                        var sourceName = archivePath.getFileName() + "!/" + name;
                        var asset = parser.parse(input, sourceName, kind);
                        putDefinition(sink, asset, options.allowExternalOverrides(), sourceName);
                    }
                }
            }
        }
    }

    /**
     * Inserts a parsed asset into the merged result map using the configured ID extractor and duplicate-handling rules.
     *
     * @param sink             the merged asset map
     * @param asset            the parsed asset to insert
     * @param overrideExisting whether an existing asset with the same ID should be replaced
     * @param sourceName       the human-readable source name used in log and error messages
     * @throws NullPointerException  if {@code asset} is {@code null}
     * @throws IllegalStateException if the extracted ID is {@code null} or blank
     */
    private void putDefinition(
        Map<String, T> sink,
        T asset,
        boolean overrideExisting,
        String sourceName
    ) {
        Objects.requireNonNull(asset, "asset");

        var id = idExtractor.getId(asset);
        if (id == null || id.isBlank()) {
            throw new IllegalStateException("Asset from " + sourceName + " has null or blank id");
        }

        var existing = sink.get(id);
        if (existing == null) {
            sink.put(id, asset);
            logger.info("Loaded asset '" + id + "' from " + sourceName);
            return;
        }

        if (overrideExisting) {
            sink.put(id, asset);
            logger.info("Overrode asset '" + id + "' from " + sourceName);
        } else {
            logger.warn(
                "Skipping duplicate built-in/classpath asset '" + id + "' from "
                    + sourceName + " because one was already loaded"
            );
        }
    }
}
