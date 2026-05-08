# Hytale Custom Asset Loader

A lightweight Java library for loading custom Hytale-style asset definitions from:

- classpath resource folders
- packaged JAR resources
- external ZIP asset packs
- external JAR asset packs

It is designed to remove repeated bootstrap logic across systems like tags, classes, professions, skills, or other JSON-backed asset registries.

## Features

- Loads assets from a configurable resource folder such as `tags` or `classes`
- Supports exploded development directories and packaged JARs
- Scans an external asset-pack directory for `.zip` and `.jar` files
- Merges assets by ID in deterministic order
- Allows optional external override behavior
- Uses pluggable parsers, ID extractors, and logging adapters
- Keeps asset-specific validation and registration logic outside the loader
- Supports **live reloading** with diff-based updates (add/update/remove)

---

## Why This Exists

Many custom asset systems end up repeating the same bootstrap flow:

1. scan a classpath folder
2. read matching JSON files
3. scan a `mods/` directory for ZIP or JAR packs
4. parse matching entries into typed definitions
5. merge them by ID
6. register them into a registry

This library extracts that shared discovery and merge layer into one reusable component so each module only needs to provide:

- how to parse an asset
- how to extract its unique ID
- how to register it
- how to log messages in its host environment

---

## Core Concepts

### AssetLoader

The main reusable loader.

`AssetLoader<T>` is responsible for:

1. scanning the configured classpath resource folder
2. loading matching files from directories and JARs
3. scanning external asset packs from a configured directory
4. parsing matching files into typed asset definitions
5. merging them by ID

In addition to one-shot loading, `AssetLoader` can operate in a **stateful mode** that supports live reloading via snapshot diffing.

### AssetBootstrapper

A convenience wrapper around `AssetLoader`.

Use it when you want to:

- load all assets
- register them immediately
- get the merged result map back

⚠️ This is intended for **startup loading only**.

For live reload support, use a `ReloadableAssetRegistrar` instead.

### AssetDiscoveryOptions

Controls how assets are discovered:

- `resourceFolder` — classpath folder such as `tags`
- `fileExtension` — file suffix such as `.json`
- `externalPackDirectory` — folder containing ZIP or JAR packs
- `allowExternalOverrides` — whether external packs may replace built-in assets
- `failIfClasspathFolderMissing` — whether missing classpath resources should fail loading

### SPI Interfaces

The library is extended through three small interfaces:

- `AssetParser<T>` — parses a single file into an asset object
- `AssetIdExtractor<T>` — extracts the unique ID from a parsed asset
- `AssetLogger` — adapts logging to your platform or plugin

### Source Metadata

`AssetSource` and `AssetSourceKind` can be used by downstream asset definitions to remember where an asset came from.

Supported source kinds:

- `CLASSPATH_DIRECTORY`
- `CLASSPATH_JAR`
- `EXTERNAL_ZIP`
- `EXTERNAL_JAR`

---

## Installation

### Gradle (Standard)

```gradle
repositories {
    mavenCentral()
    maven {
        name = 'AzureDoom Maven'
        url = uri("https://maven.azuredoom.com/mods")
    }
}

dependencies {
    implementation 'com.azuredoom.hytalecustomassetloader:hytale-custom-asset-loader:1.1.3'
}
```

### Shade Into Your JAR

If you want to embed only **Hytale Custom Asset Loader** into your plugin jar, avoid unpacking the entire `runtimeClasspath`.

Create a dedicated `shade` configuration and only pull from that configuration when building your jar:

```gradle
configurations {
    shade
}

repositories {
    mavenCentral()
    maven {
        name = 'AzureDoom Maven'
        url = uri("https://maven.azuredoom.com/mods")
    }
}

dependencies {
    implementation 'com.azuredoom.hytalecustomassetloader:hytale-custom-asset-loader:1.1.3'
    shade 'com.azuredoom.hytalecustomassetloader:hytale-custom-asset-loader:1.1.3'
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from {
        configurations.shade.collect { it.isDirectory() ? it : zipTree(it) }
    }

    exclude 'META-INF/*.SF', 'META-INF/*.DSA', 'META-INF/*.RSA'
}
```

This approach shades only the dependency you explicitly place in the `shade` configuration.

### Notes on Shading

- `implementation` keeps the dependency available to your project at compile time
- `shade` is only used for embedding the selected jar into your final artifact
- using `runtimeClasspath` for shading is broader and can accidentally unpack unrelated libraries
- excluding signature files helps avoid invalid signed-jar metadata after repackaging

---

## Basic Usage

```java
var loader = new AssetLoader<>(
    plugin.getClass().getClassLoader(),
    new AssetDiscoveryOptions(
        "classes",
        ".json",
        Paths.get("mods").toAbsolutePath().normalize(),
        true,
        true
    ),
    this::loadClass,
    ClassDefinition::id,
    new AssetLogger() {
        @Override
        public void info(String message) {
            plugin.getLogger().info(message);
        }

        @Override
        public void warn(String message) {
            plugin.getLogger().warning(message);
        }
    }
);

var result = loader.loadAll();

for (var definition : result.mergedAssets().values()) {
    registry.register(definition);
}
```

---

## Live Reload

The loader can operate in **live reload mode**, allowing assets to be updated at runtime without restarting your application.

### What Live Reload Does

On reload, the loader:

1. rescans all sources (classpath + external packs)
2. rebuilds a full merged snapshot
3. computes a **diff** against the previous state
4. applies:
    - added assets
    - updated assets
    - removed assets

This ensures:
- deleted files are properly removed
- overridden assets fall back correctly
- registries stay consistent

---

### Using Live Reload

Instead of a one-shot bootstrap, use a reloadable registrar:

```java
var loader = new AssetLoader<>(...);

var registrar = new ReloadableAssetRegistrar<MyAsset>() {
    @Override
    public void add(String id, MyAsset asset) {
        registry.register(asset);
    }

    @Override
    public void update(String id, MyAsset oldAsset, MyAsset newAsset) {
        registry.unregister(oldAsset);
        registry.register(newAsset);
    }

    @Override
    public void remove(String id, MyAsset asset) {
        registry.unregister(asset);
    }
};
```

Initial load:
```java
var snapshot = loader.loadInitial();
registrar.applyInitial(snapshot);
```

Reload:
```java
var result = loader.reload();
registrar.applyDiff(result.diff());
```

Watching for File Changes

You can optionally enable automatic reloads using a watcher:
```java
var reloader = new AssetReloader(loader, registrar);
reloader.startWatching();
```

This monitors:

- external pack directory (mods/)
- exploded classpath directories (development mode)

Configuration

Live reload behavior is controlled via AssetDiscoveryOptions:
```java
new AssetDiscoveryOptions(
    "classes",
    ".json",
    Paths.get("mods"),
    true,
    true,
    true,              // enableLiveReload
    true,              // watchExternalPacks
    true,              // watchExplodedClasspath
    Duration.ofMillis(500) // debounce
);
```

Limitations

- Packaged classpath JAR resources cannot be hot-reloaded without a new classloader
- External .zip / .jar packs are reloaded as whole units
- Reload is eventually consistent (debounced for stability)

---

## Using AssetBootstrapper

```java
var bootstrapper = new AssetBootstrapper<>(
    loader,
    registry::register
);

var mergedAssets = bootstrapper.bootstrap();
```

---

## Example Parser

```java
private MyAssetDefinition loadAsset(
    InputStream stream,
    String sourceName,
    AssetSourceKind sourceKind
) throws Exception {
    try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
        var root = GSON.fromJson(reader, JsonObject.class);

        if (root == null) {
            throw new IllegalStateException("Asset JSON was empty: " + sourceName);
        }

        var id = root.get("id").getAsString();

        return new MyAssetDefinition(
            id,
            new AssetSource(sourceKind, sourceName)
        );
    }
}
```

---

## Load Order and Overrides

Assets are loaded in this order:

1. classpath directories
2. classpath JAR resources
3. external ZIP and JAR packs from the configured pack directory

External asset packs are processed in sorted filename order.

Duplicate handling rules:

- duplicate built-in or classpath assets are skipped with a warning
- external assets may override earlier assets if `allowExternalOverrides` is enabled

This makes load order predictable and allows pack authors to control priority through filenames.

---

## Intended Use Cases

This library is a good fit for systems that load JSON definitions such as:

- tags
- classes
- professions
- abilities
- items
- recipes
- skill trees
- status effects
- custom registries

---

## Design Goals

- keep asset discovery generic
- keep asset parsing domain-specific
- avoid duplicated bootstrap logic across modules
- make override behavior explicit
- remain lightweight and easy to embed in plugins

---

## License

MIT
