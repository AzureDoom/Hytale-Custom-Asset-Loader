package com.azuredoom.hytalecustomassetloader.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Represents the incremental difference between two asset snapshots.
 * <p>
 * The diff categorizes changes into:
 * </p>
 * <ul>
 * <li>{@code added} – assets present only in the current snapshot</li>
 * <li>{@code updated} – assets present in both snapshots but with changes</li>
 * <li>{@code removed} – assets present only in the previous snapshot</li>
 * </ul>
 */
public record AssetDiff<T>(
    Map<String, AssetRecord<T>> added,
    Map<String, AssetChange<T>> updated,
    Map<String, AssetRecord<T>> removed
) {

    public AssetDiff {
        Objects.requireNonNull(added, "added");
        Objects.requireNonNull(updated, "updated");
        Objects.requireNonNull(removed, "removed");
        added = Collections.unmodifiableMap(new LinkedHashMap<>(added));
        updated = Collections.unmodifiableMap(new LinkedHashMap<>(updated));
        removed = Collections.unmodifiableMap(new LinkedHashMap<>(removed));
    }

    /**
     * Returns whether this diff contains no changes.
     *
     * @return {@code true} if no assets were added, updated, or removed
     */
    public boolean isEmpty() {
        return added.isEmpty() && updated.isEmpty() && removed.isEmpty();
    }

    /**
     * Computes the difference between two asset snapshots.
     *
     * @param previous the previous snapshot
     * @param current  the current snapshot
     * @param <T>      the asset type
     * @return a diff describing the changes between the snapshots
     */
    public static <T> AssetDiff<T> between(AssetSnapshot<T> previous, AssetSnapshot<T> current) {
        Map<String, AssetRecord<T>> added = new LinkedHashMap<>();
        Map<String, AssetChange<T>> updated = new LinkedHashMap<>();
        Map<String, AssetRecord<T>> removed = new LinkedHashMap<>();

        Map<String, AssetRecord<T>> previousRecords = previous.recordsById();
        Map<String, AssetRecord<T>> currentRecords = current.recordsById();

        Set<String> ids = new LinkedHashSet<>();
        ids.addAll(previousRecords.keySet());
        ids.addAll(currentRecords.keySet());

        for (String id : ids) {
            AssetRecord<T> before = previousRecords.get(id);
            AssetRecord<T> after = currentRecords.get(id);

            if (before == null && after != null) {
                added.put(id, after);
            } else if (before != null && after == null) {
                removed.put(id, before);
            } else if (before != null) {
                boolean changed = !Objects.equals(before.fingerprint(), after.fingerprint())
                    || !Objects.equals(before.source(), after.source());
                if (changed) {
                    updated.put(id, new AssetChange<>(before, after));
                }
            }
        }

        return new AssetDiff<>(added, updated, removed);
    }
}
