package com.azuredoom.hytalecustomassetloader;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.azuredoom.hytalecustomassetloader.model.AssetReloadResult;
import com.azuredoom.hytalecustomassetloader.spi.AssetLogger;
import com.azuredoom.hytalecustomassetloader.spi.ReloadableAssetRegistrar;

/**
 * Watches file-backed asset roots and re-applies diffs when assets change.
 *
 * <p>This intentionally watches only real filesystem directories. Packaged classpath JAR resources are still scanned on
 * manual reload, but they are not watchable without swapping classloaders.</p>
 */
public final class AssetReloader<T> implements Closeable {
    private final AssetLoader<T> loader;
    private final ReloadableAssetRegistrar<T> registrar;
    private final AssetLogger logger;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private WatchService watchService;
    private Thread watcherThread;

    /**
     * Creates a new asset reloader.
     *
     * @param loader    the asset loader used to load and reload assets
     * @param registrar the registrar that applies asset changes
     * @param logger    the logger used for reporting reload activity
     */
    public AssetReloader(AssetLoader<T> loader, ReloadableAssetRegistrar<T> registrar, AssetLogger logger) {
        this.loader = loader;
        this.registrar = registrar;
        this.logger = logger;
    }

    /**
     * Starts watching asset roots for changes and applies live reloads.
     *
     * <p>If no assets have been loaded yet, this will perform an initial load.</p>
     *
     * @throws IOException if the watch service cannot be initialized
     * @throws IllegalStateException if live reload is not enabled
     */
    public void start() throws IOException {
        if (!loader.options().enableLiveReload()) {
            throw new IllegalStateException("enableLiveReload must be true to start AssetReloader");
        }

        if (loader.currentSnapshot().mergedAssets().isEmpty()) {
            registrar.applyInitial(loader.loadAll().snapshot());
        } else {
            registrar.applyInitial(loader.currentSnapshot());
        }

        Set<Path> roots = new LinkedHashSet<>(loader.discoverWatchRoots());
        if (roots.isEmpty()) {
            logger.warn("Live reload enabled, but no watchable filesystem roots were discovered.");
            return;
        }

        watchService = FileSystems.getDefault().newWatchService();
        for (Path root : roots) {
            if (!Files.exists(root)) {
                continue;
            }
            root.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
            logger.info("Watching asset root " + root);
        }

        running.set(true);
        watcherThread = Thread.ofVirtual().name("asset-live-reloader").start(this::runWatcherLoop);
    }

    /**
     * Internal loop that listens for filesystem events and triggers reloads.
     *
     * <p>Applies a debounce delay before reloading to avoid excessive reloads.</p>
     */
    private void runWatcherLoop() {
        long debounceMillis = loader.options().reloadDebounce().toMillis();
        while (running.get()) {
            try {
                WatchKey key = watchService.take();
                if (key == null) {
                    continue;
                }
                key.pollEvents();
                key.reset();
                Thread.sleep(debounceMillis);

                AssetReloadResult<T> result = loader.reload();
                if (!result.diff().isEmpty()) {
                    registrar.applyReload(result);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                logger.warn("Live reload failed: " + e.getMessage());
            }
        }
    }

    /**
     * Stops the watcher and releases all associated resources.
     *
     * @throws IOException if the watch service cannot be closed
     */
    @Override
    public void close() throws IOException {
        running.set(false);
        if (watcherThread != null) {
            watcherThread.interrupt();
        }
        if (watchService != null) {
            watchService.close();
        }
    }
}
