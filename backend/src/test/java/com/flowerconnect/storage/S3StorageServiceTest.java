package com.flowerconnect.storage;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The S3 backend is a <b>declared stub</b>, and this test is what makes that
 * declaration checkable rather than a claim in a comment (plan task 3.8, D-26).
 *
 * <p>Two things are asserted. First, every method refuses to pretend: it throws,
 * naming the gap. A silently inert implementation would return 201s from a
 * production deployment and store nothing. Second, the profile switch is real:
 * activating {@code s3} replaces the local-disk bean, so the interface is
 * genuinely pluggable even though the S3 half is unfinished.
 */
class S3StorageServiceTest {

    @Test
    void everyOperationRefusesRatherThanSilentlySucceeding() {
        S3StorageService storage = new S3StorageService();

        assertStubbed(() -> storage.store("product-images/1/a.jpg", new byte[]{1}));
        assertStubbed(() -> storage.delete("product-images/1/a.jpg"));
        assertStubbed(() -> storage.exists("product-images/1/a.jpg"));
    }

    @Test
    void describeSaysItIsNotImplementedRatherThanNamingABucket() {
        assertThat(new S3StorageService().describe()).isEqualTo("s3-stub:not-implemented");
    }

    @Test
    void theLocalDiskBackendIsActiveWhenNoS3ProfileIsSet() {
        contextRunner().run(context -> assertThat(context.getBean(StorageService.class))
                .isInstanceOf(LocalDiskStorageService.class));
    }

    @Test
    void theS3StubReplacesTheLocalDiskBackendWhenTheS3ProfileIsActive() {
        contextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("s3"))
                .run(context -> {
                    assertThat(context.getBeansOfType(StorageService.class)).hasSize(1);
                    assertThat(context.getBean(StorageService.class))
                            .isInstanceOf(S3StorageService.class);
                });
    }

    @Test
    void activatingTheProdProfileAloneStillSelectsTheWorkingBackend() {
        // The stub must only be reachable when someone deliberately asks for it,
        // so a deployment that merely runs the prod profile keeps a backend that
        // actually stores bytes.
        contextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .run(context -> assertThat(context.getBean(StorageService.class))
                        .isInstanceOf(LocalDiskStorageService.class));
    }

    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner().withUserConfiguration(StorageScanConfiguration.class);
    }

    private void assertStubbed(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("not implemented")
                .hasMessageContaining("D-26");
    }

    /** Scans the storage package so {@code @Profile} is evaluated as in production. */
    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = LocalDiskStorageService.class)
    static class StorageScanConfiguration {
    }
}