package com.flowerconnect.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Storage backend configuration ({@code app.storage.*}).
 *
 * <p>Which backend is active is a <b>profile</b> decision, not a property one:
 * {@code LocalDiskStorageService} is active unless the {@code s3} profile is on,
 * and {@code S3StorageService} is active only when it is. That keeps "which
 * backend" out of a runtime switch that could silently point production at a
 * developer's disk.
 */
@Configuration
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    /**
     * Root directory for the local-disk backend, relative to the working
     * directory unless absolute. The default is {@code uploads}, which is the
     * path already listed in {@code .gitignore}.
     */
    private String localDirectory = "uploads";

    public String getLocalDirectory() {
        return localDirectory;
    }

    public void setLocalDirectory(String localDirectory) {
        this.localDirectory = localDirectory;
    }
}