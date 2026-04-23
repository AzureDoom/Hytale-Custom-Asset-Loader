package com.azuredoom.hytalecustomassetloader.spi;

/**
 * Minimal logging abstraction used by the asset loader.
 * <p>
 * This interface keeps the loader independent of any specific logging framework or plugin API. Callers can adapt their
 * own logging system by implementing these methods.
 */
public interface AssetLogger {

    /**
     * Logs an informational message.
     *
     * @param message the message to log
     */
    void info(String message);

    /**
     * Logs a warning message.
     *
     * @param message the message to log
     */
    void warn(String message);
}
