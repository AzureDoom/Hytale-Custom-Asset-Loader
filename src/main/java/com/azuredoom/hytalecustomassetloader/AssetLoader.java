package com.azuredoom.hytalecustomassetloader;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.azuredoom.hytalecustomassetloader.model.*;
import com.azuredoom.hytalecustomassetloader.spi.AssetIdExtractor;
import com.azuredoom.hytalecustomassetloader.spi.AssetLogger;
import com.azuredoom.hytalecustomassetloader.spi.AssetParser;

/**
 * Generic loader for Hytale-style custom assets stored as files on the classpath or inside external asset packs.
 * <p>
 * For live-reload use cases this loader preserves the last merged snapshot and can produce a diff against a newly
 * rebuilt snapshot.
 * </p>
 */
public final class AssetLoader<T> {

    private final ClassLoader classLoader;

    private final AssetDiscoveryOptions options;

    private final AssetParser<T> parser;

    private final AssetIdExtractor<T> idExtractor;

    private final AssetLogger logger;

    private volatile AssetSnapshot<T> currentSnapshot = AssetSnapshot.empty();

    /**
     * Creates a new asset loader.
     *
     * @param classLoader the class loader used to discover classpath assets
     * @param options     the discovery and reload options
     * @param parser      the parser used to read asset definitions
     * @param idExtractor the extractor used to obtain asset IDs
     * @param logger      the logger used for load diagnostics
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
     * Loads all assets and replaces the current snapshot with the newly built snapshot.
     *
     * @return the result of the load operation
     * @throws RuntimeException if asset discovery or parsing fails
     */
    public synchronized AssetLoadResult<T> loadAll() {
        try {
            AssetSnapshot<T> snapshot = scanSnapshot();
            currentSnapshot = snapshot;
            logger.info(
                "Loaded " + snapshot.mergedAssets().size() + " assets from folder '" + options.resourceFolder() + "'."
            );
            return new AssetLoadResult<>(snapshot);
        } catch (Exception e) {
            throw new RuntimeException(
                "Failed to load assets from resource folder '" + options.resourceFolder() + "'",
                e
            );
        }
    }

    /**
     * Reloads all assets, computes a diff against the current snapshot, and updates the current snapshot.
     *
     * @return the result of the reload operation, including the previous snapshot, current snapshot, and diff
     * @throws RuntimeException if asset discovery or parsing fails
     */
    public synchronized AssetReloadResult<T> reload() {
        try {
            AssetSnapshot<T> previous = currentSnapshot;
            AssetSnapshot<T> next = scanSnapshot();
            AssetDiff<T> diff = AssetDiff.between(previous, next);
            currentSnapshot = next;
            return new AssetReloadResult<>(previous, next, diff);
        } catch (Exception e) {
            throw new RuntimeException(
                "Failed to reload assets from resource folder '" + options.resourceFolder() + "'",
                e
            );
        }
    }

    /**
     * Returns the most recently loaded snapshot.
     *
     * @return the current asset snapshot
     */
    public AssetSnapshot<T> currentSnapshot() {
        return currentSnapshot;
    }

    /**
     * Returns the discovery options used by this loader.
     *
     * @return the asset discovery options
     */
    public AssetDiscoveryOptions options() {
        return options;
    }

    /**
     * Discovers filesystem roots that can be watched for live reload.
     * <p>
     * This may include the external asset pack directory and exploded classpath directories, depending on the
     * configured options.
     * </p>
     *
     * @return the discovered watch roots
     * @throws RuntimeException if classpath watch roots cannot be resolved
     */
    public Set<Path> discoverWatchRoots() {
        Set<Path> roots = new LinkedHashSet<>();

        if (options.watchExternalPacks()) {
            roots.add(options.externalPackDirectory().toAbsolutePath().normalize());
        }

        if (options.watchExplodedClasspathDirectories()) {
            try {
                Enumeration<URL> resources = classLoader.getResources(options.resourceFolder());
                while (resources.hasMoreElements()) {
                    URL resourceUrl = resources.nextElement();
                    if ("file".equals(resourceUrl.getProtocol())) {
                        roots.add(Paths.get(resourceUrl.toURI()).toAbsolutePath().normalize());
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to discover classpath watch roots", e);
            }
        }

        return Collections.unmodifiableSet(roots);
    }

    /**
     * Scans all configured asset sources and builds a merged snapshot.
     *
     * @return the rebuilt asset snapshot
     * @throws Exception if asset discovery or parsing fails
     */
    private AssetSnapshot<T> scanSnapshot() throws Exception {
        List<Candidate<T>> candidates = new ArrayList<>();
        loadClasspathResources(candidates);
        loadExternalAssetPacks(candidates);
        return buildSnapshot(candidates);
    }

    /**
     * Builds a merged snapshot from the provided candidates.
     * <p>
     * Assets are merged by ID in iteration order. Duplicate classpath assets are skipped, while override-eligible
     * candidates may replace existing entries.
     * </p>
     *
     * @param candidates the discovered asset candidates
     * @return the merged snapshot
     */
    private AssetSnapshot<T> buildSnapshot(List<Candidate<T>> candidates) {
        Map<String, T> mergedAssets = new LinkedHashMap<>();
        Map<String, AssetRecord<T>> recordsById = new LinkedHashMap<>();

        for (Candidate<T> candidate : candidates) {
            String id = requireId(candidate.asset(), candidate.source().name());
            AssetRecord<T> nextRecord = new AssetRecord<>(
                id,
                candidate.asset(),
                candidate.source(),
                candidate.fingerprint(),
                candidate.priority(),
                candidate.overrideEligible()
            );

            AssetRecord<T> existingRecord = recordsById.get(id);
            if (existingRecord == null) {
                mergedAssets.put(id, candidate.asset());
                recordsById.put(id, nextRecord);
                logger.info("Loaded asset '" + id + "' from " + candidate.source().name());
                continue;
            }

            if (candidate.overrideEligible()) {
                mergedAssets.put(id, candidate.asset());
                recordsById.put(id, nextRecord);
                logger.info("Overrode asset '" + id + "' from " + candidate.source().name());
            } else {
                logger.warn(
                    "Skipping duplicate built-in/classpath asset '" + id + "' from " + candidate.source().name()
                        + " because one was already loaded"
                );
            }
        }

        return new AssetSnapshot<>(mergedAssets, recordsById);
    }

    /**
     * Discovers and loads all asset files found under {@link AssetDiscoveryOptions#resourceFolder()} on the classpath.
     * <p>
     * Both exploded directory entries and JAR entries are supported. If no matching resource folder is found and
     * {@link AssetDiscoveryOptions#failIfClasspathFolderMissing()} is {@code true}, an exception is thrown.
     * </p>
     *
     * @param sink the candidate list to populate
     * @throws Exception if classpath enumeration or asset parsing fails
     */
    private void loadClasspathResources(List<Candidate<T>> sink) throws Exception {
        Enumeration<URL> resources = classLoader.getResources(options.resourceFolder());
        if (!resources.hasMoreElements()) {
            if (options.failIfClasspathFolderMissing()) {
                throw new IllegalStateException("Missing resource folder: " + options.resourceFolder());
            }
            logger.warn("No resource folder found on classpath: " + options.resourceFolder());
            return;
        }

        while (resources.hasMoreElements()) {
            URL resourceUrl = resources.nextElement();
            String protocol = resourceUrl.getProtocol();
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
     * Loads assets from an exploded classpath directory identified by a {@code file://} URL.
     *
     * @param sink        the candidate list to populate
     * @param resourceUrl the {@code file://} URL pointing to the resource folder root
     * @throws Exception if the directory cannot be walked or an asset cannot be parsed
     */
    private void loadFromDirectory(List<Candidate<T>> sink, URL resourceUrl) throws Exception {
        Path root = Paths.get(resourceUrl.toURI());
        try (var stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(options.fileExtension()))
                .sorted()
                .forEach(path -> loadDirectoryAsset(sink, root, path));
        }
    }

    /**
     * Parses and registers a single asset file found inside an exploded classpath directory.
     *
     * @param sink the candidate list to populate
     * @param root the resource folder root used to compute the relative path
     * @param path the absolute path to the asset file
     */
    private void loadDirectoryAsset(List<Candidate<T>> sink, Path root, Path path) {
        String relative = root.relativize(path).toString().replace('\\', '/');
        String sourceName = options.resourceFolder() + "/" + relative;
        try (InputStream input = Files.newInputStream(path)) {
            T asset = parser.parse(input, sourceName, AssetSourceKind.CLASSPATH_DIRECTORY);

            addCandidateIfParsed(
                sink,
                asset,
                new AssetSource(AssetSourceKind.CLASSPATH_DIRECTORY, sourceName),
                fingerprintForFile(path, relative),
                100,
                false
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to load resource " + sourceName, e);
        }
    }

    /**
     * Loads assets from a JAR on the classpath identified by a {@code jar://} URL.
     *
     * @param sink        the candidate list to populate
     * @param resourceUrl the {@code jar://} URL pointing to the resource folder inside the JAR
     * @throws Exception if the JAR cannot be opened or an asset cannot be parsed
     */
    private void loadFromClasspathJar(List<Candidate<T>> sink, URL resourceUrl) throws Exception {
        JarURLConnection connection = (JarURLConnection) resourceUrl.openConnection();
        try (var jarFile = connection.getJarFile()) {
            List<? extends ZipEntry> entries = Collections.list(jarFile.entries());
            entries.stream()
                .filter(entry -> !entry.isDirectory())
                .filter(entry -> entry.getName().startsWith(options.resourceFolder() + "/"))
                .filter(entry -> entry.getName().endsWith(options.fileExtension()))
                .sorted(Comparator.comparing(ZipEntry::getName))
                .forEach(entry -> loadClasspathJarAsset(sink, jarFile, entry));
        }
    }

    /**
     * Parses and registers a single asset entry found inside a classpath JAR.
     *
     * @param sink    the candidate list to populate
     * @param jarFile the open JAR file containing the entry
     * @param entry   the ZIP entry representing the asset file
     */
    private void loadClasspathJarAsset(List<Candidate<T>> sink, java.util.jar.JarFile jarFile, ZipEntry entry) {
        String name = entry.getName();
        try (InputStream input = jarFile.getInputStream(entry)) {
            T asset = parser.parse(input, name, AssetSourceKind.CLASSPATH_JAR);

            addCandidateIfParsed(
                sink,
                asset,
                new AssetSource(AssetSourceKind.CLASSPATH_JAR, name),
                fingerprintForZipEntry(jarFile.getName(), entry),
                100,
                false
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to load classpath jar resource " + name, e);
        }
    }

    /**
     * Scans the external asset pack directory and loads assets from any contained pack directories, ZIP files, or JARs.
     * <p>
     * If the configured external pack directory does not exist or is not a directory, this method returns without
     * loading anything.
     * </p>
     *
     * @param sink the candidate list to populate
     * @throws Exception if the directory cannot be listed or an asset pack cannot be loaded
     */
    private void loadExternalAssetPacks(List<Candidate<T>> sink) throws Exception {
        Path assetPackDir = options.externalPackDirectory();
        if (!Files.exists(assetPackDir) || !Files.isDirectory(assetPackDir)) {
            return;
        }

        try (var stream = Files.list(assetPackDir)) {
            stream.sorted()
                .forEach(path -> {
                    try {
                        if (Files.isDirectory(path)) {
                            loadFromExternalDirectory(sink, path);
                            return;
                        }

                        if (Files.isRegularFile(path)) {
                            String name = path.getFileName().toString().toLowerCase();
                            if (name.endsWith(".zip") || name.endsWith(".jar")) {
                                loadFromExternalArchive(sink, path);
                            }
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to load asset pack " + path, e);
                    }
                });
        }
    }

    /**
     * Loads assets from an exploded (directory-based) external asset pack.
     * <p>
     * The pack is expected to contain a subdirectory matching {@link AssetDiscoveryOptions#resourceFolder()}. If that
     * subdirectory does not exist the pack is silently skipped.
     * </p>
     *
     * @param sink     the candidate list to populate
     * @param packRoot the root directory of the external asset pack
     * @throws Exception if the directory cannot be walked or an asset cannot be parsed
     */
    private void loadFromExternalDirectory(List<Candidate<T>> sink, Path packRoot) throws Exception {
        Path assetRoot = packRoot.resolve(options.resourceFolder());
        if (!Files.exists(assetRoot) || !Files.isDirectory(assetRoot)) {
            return;
        }

        try (var stream = Files.walk(assetRoot)) {
            stream.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(options.fileExtension()))
                .sorted()
                .forEach(path -> {
                    String relative = assetRoot.relativize(path).toString().replace('\\', '/');
                    String sourceName = packRoot.getFileName() + "!/" + options.resourceFolder() + "/" + relative;

                    try (InputStream input = Files.newInputStream(path)) {
                        T asset = parser.parse(input, sourceName, AssetSourceKind.EXTERNAL_DIRECTORY);

                        addCandidateIfParsed(
                            sink,
                            asset,
                            new AssetSource(AssetSourceKind.EXTERNAL_DIRECTORY, sourceName),
                            fingerprintForFile(path, relative),
                            200,
                            options.allowExternalOverrides()
                        );
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to load external directory resource " + sourceName, e);
                    }
                });
        }
    }

    /**
     * Loads assets from an external ZIP or JAR archive asset pack.
     *
     * @param sink        the candidate list to populate
     * @param archivePath the path to the ZIP or JAR file
     * @throws Exception if the archive cannot be opened or an asset cannot be parsed
     */
    private void loadFromExternalArchive(List<Candidate<T>> sink, Path archivePath) throws Exception {
        try (ZipFile zipFile = new ZipFile(archivePath.toFile())) {
            List<? extends ZipEntry> entries = Collections.list(zipFile.entries());
            entries.stream()
                .filter(entry -> !entry.isDirectory())
                .filter(entry -> entry.getName().startsWith(options.resourceFolder() + "/"))
                .filter(entry -> entry.getName().endsWith(options.fileExtension()))
                .sorted(Comparator.comparing(ZipEntry::getName))
                .forEach(entry -> loadExternalArchiveAsset(sink, archivePath, zipFile, entry));
        }
    }

    /**
     * Parses and registers a single asset entry found inside an external ZIP or JAR archive.
     *
     * @param sink        the candidate list to populate
     * @param archivePath the path to the archive, used to determine the source kind and fingerprint
     * @param zipFile     the open archive file containing the entry
     * @param entry       the ZIP entry representing the asset file
     */
    private void loadExternalArchiveAsset(List<Candidate<T>> sink, Path archivePath, ZipFile zipFile, ZipEntry entry) {
        AssetSourceKind kind = archivePath.toString().toLowerCase().endsWith(".jar")
            ? AssetSourceKind.EXTERNAL_JAR
            : AssetSourceKind.EXTERNAL_ZIP;
        String sourceName = archivePath.getFileName() + "!/" + entry.getName();
        try (InputStream input = zipFile.getInputStream(entry)) {
            T asset = parser.parse(input, sourceName, kind);

            addCandidateIfParsed(
                sink,
                asset,
                new AssetSource(kind, sourceName),
                fingerprintForZipEntry(archivePath.toAbsolutePath().normalize().toString(), entry),
                200,
                options.allowExternalOverrides()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to load asset pack entry " + sourceName, e);
        }
    }

    /**
     * Extracts and validates the ID of an asset.
     *
     * @param asset      the asset instance
     * @param sourceName the human-readable source name used in error messages
     * @return the non-blank asset ID
     * @throws IllegalStateException if the extracted ID is {@code null} or blank
     */
    private String requireId(T asset, String sourceName) {
        Objects.requireNonNull(asset, "asset");
        String id = idExtractor.getId(asset);
        if (id == null || id.isBlank()) {
            throw new IllegalStateException("Asset from " + sourceName + " has null or blank id");
        }
        return id;
    }

    /**
     * Builds a fingerprint for a filesystem-backed asset.
     *
     * @param path     the file path
     * @param relative the relative path within the asset root
     * @return a fingerprint string for change detection
     * @throws IOException if file metadata cannot be read
     */
    private String fingerprintForFile(Path path, String relative) throws IOException {
        FileTime modifiedTime = Files.getLastModifiedTime(path);
        long size = Files.size(path);
        return "file:" + path.toAbsolutePath().normalize() + "|rel:" + relative + "|mtime:" + modifiedTime.toMillis()
            + "|size:" + size;
    }

    /**
     * Builds a fingerprint for an archive-backed asset entry.
     *
     * @param archiveName the archive name or path
     * @param entry       the archive entry
     * @return a fingerprint string for change detection
     */
    private String fingerprintForZipEntry(String archiveName, ZipEntry entry) {
        return "archive:" + archiveName
            + "|entry:" + entry.getName()
            + "|size:" + entry.getSize()
            + "|crc:" + entry.getCrc()
            + "|time:" + entry.getTime();
    }

    /**
     * Adds a parsed asset candidate to the provided sink.
     * <p>
     * If the parser returned {@code null}, the asset is ignored. This allows parsers to skip files that are present in
     * the same asset folder but belong to a different asset type.
     * </p>
     *
     * @param sink             the candidate list to add to
     * @param asset            the parsed asset instance, or {@code null} if the parser chose to ignore it
     * @param source           the source the asset was loaded from
     * @param fingerprint      the fingerprint used for change detection
     * @param priority         the load priority used when merging assets
     * @param overrideEligible whether this candidate may override an existing asset with the same ID
     */
    private void addCandidateIfParsed(
        List<Candidate<T>> sink,
        T asset,
        AssetSource source,
        String fingerprint,
        int priority,
        boolean overrideEligible
    ) {
        if (asset == null) {
            logger.info("Skipping asset from " + source.name() + " because parser returned null");
            return;
        }

        sink.add(
            new Candidate<>(
                asset,
                source,
                fingerprint,
                priority,
                overrideEligible
            )
        );
    }

    /**
     * Internal holder for a parsed asset and the metadata needed to merge and fingerprint it.
     *
     * @param asset            the parsed asset instance
     * @param source           the source the asset was loaded from
     * @param fingerprint      the fingerprint used for change detection
     * @param priority         the load priority used when ordering candidates before merging
     * @param overrideEligible whether this candidate may replace an existing asset with the same ID
     */
    private record Candidate<T>(
        T asset,
        AssetSource source,
        String fingerprint,
        int priority,
        boolean overrideEligible
    ) {}
}
