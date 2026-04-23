package com.azuredoom.hytalecustomassetloader.spi;

import java.io.InputStream;

import com.azuredoom.hytalecustomassetloader.model.AssetSourceKind;

/**
 * Parses a single asset file into a typed asset definition.
 * <p>
 * Implementations are responsible for interpreting the raw file contents and constructing the caller's domain object.
 * The loader provides the source name and source kind to support better validation and error reporting.
 *
 * @param <T> the asset definition type produced by the parser
 */
@FunctionalInterface
public interface AssetParser<T> {

    /**
     * Parses an asset from the provided input stream.
     *
     * @param stream     the input stream for the asset file
     * @param sourceName a human-readable source name such as {@code tags/example.json} or
     *                   {@code mypack.zip!/tags/example.json}
     * @param sourceKind the origin kind describing where the asset was loaded from
     * @return the parsed asset definition
     * @throws Exception if the asset cannot be read, validated, or parsed
     */
    T parse(InputStream stream, String sourceName, AssetSourceKind sourceKind) throws Exception;
}
