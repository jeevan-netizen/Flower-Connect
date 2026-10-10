package com.flowerconnect.storage;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * S3 {@link StorageService} — <b>a declared stub, not an implementation</b>.
 *
 * <p>Plan task 3.8 asks for "an S3 implementation pluggable by profile". The
 * profile switch is real: with the {@code s3} profile active this bean replaces
 * {@link LocalDiskStorageService} and every other storage call site keeps working
 * unchanged. What is <em>not</em> real is the upload itself. The AWS SDK is not
 * a dependency of this project, and adding it would bring a large dependency
 * tree, a credential-resolution story and a bucket-provisioning story for code
 * that has no environment to run in yet.
 *
 * <p>So this class throws on every method instead of pretending to succeed. That
 * choice is the point of the stub:
 * <ul>
 *   <li>A silently inert implementation would let a production deployment
 *       activate the profile, serve 201s, and write nothing — the failure would
 *       surface as missing product images rather than as a deploy error.</li>
 *   <li>Throwing fails loudly on the first upload, naming the gap, and the
 *       resulting 500 is traceable to this class.</li>
 * </ul>
 *
 * <p>Completing it is a self-contained piece of work: add the AWS SDK v2 S3
 * dependency, hold an S3Client bean, implement the four interface methods against
 * {@code putObject}/{@code getObject}/{@code deleteObject}/{@code headObject},
 * and add the bucket/region/prefix properties this class documents. Nothing in
 * the domain layer or the HTTP API changes. See docs/decisions.md (D-26).
 */
@Service
@Profile("s3")
public class S3StorageService implements StorageService {

    /**
     * Configuration this stub documents but does not read. A real implementation
     * needs, at minimum: bucket, region, key prefix, path-style access (for
     * MinIO-compatible stores), and credential resolution (instance profile,
     * environment, or explicit keys).
     */
    static final String NOT_IMPLEMENTED =
            "S3 storage is not implemented in Phase 3f (plan task 3.8): this build ships the "
                    + "StorageService interface, a working local-disk backend and a profile switch, but no "
                    + "AWS SDK dependency. Activate no 's3' profile in this build, or deploy an image "
                    + "store that implements StorageService. See docs/decisions.md (D-26).";

    @Override
    public void store(String key, byte[] content) {
        throw new StorageException(NOT_IMPLEMENTED);
    }

    @Override
    public void delete(String key) {
        throw new StorageException(NOT_IMPLEMENTED);
    }

    @Override
    public boolean exists(String key) {
        throw new StorageException(NOT_IMPLEMENTED);
    }

    @Override
    public String describe() {
        return "s3-stub:not-implemented";
    }
}