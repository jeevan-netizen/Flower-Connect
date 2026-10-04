package com.flowerconnect.catalog.service;

import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.domain.ProductImage;
import com.flowerconnect.catalog.dto.ProductImageResponse;
import com.flowerconnect.catalog.mapper.ProductMapper;
import com.flowerconnect.catalog.repository.ProductImageRepository;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.storage.StorageException;
import com.flowerconnect.storage.StorageService;
import com.flowerconnect.storage.image.ImageProcessor;
import com.flowerconnect.storage.image.ImageUploadProperties;
import com.flowerconnect.storage.image.ProcessedImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Orchestration of the image pipeline: what gets stored, under what key, how the
 * cover is decided, and what is refused before any write happens (plan task 3.8).
 *
 * <p>Byte-level rules (sniffing, decoding, resizing) belong to
 * {@code ImageProcessorTest}, and the one-primary invariant under concurrency
 * belongs to {@code ProductImageIntegrationTest}. What is left here is the
 * sequencing the service owns, which is exactly where the interesting mistakes
 * live: writing the object when the request should have been refused, keeping the
 * client's filename in the path, or leaving a file behind when the row fails.
 */
@ExtendWith(MockitoExtension.class)
class ProductImageServiceTest {

    private static final String VENDOR_EMAIL = "florist@test.com";
    private static final Long PRODUCT_ID = 42L;

    @Mock
    private ProductService productService;
    @Mock
    private ProductImageRepository imageRepository;
    @Mock
    private StorageService storageService;
    @Mock
    private ImageProcessor imageProcessor;
    @Mock
    private ProductMapper mapper;

    private ImageUploadProperties properties;
    private ProductImageService service;
    private Product product;

    @BeforeEach
    void setUp() {
        properties = new ImageUploadProperties();
        service = new ProductImageService(productService, imageRepository, storageService,
                imageProcessor, properties, mapper);

        product = Product.builder().id(PRODUCT_ID)
                .vendor(VendorProfile.builder().id(7L).build())
                .build();
        // Lenient: most of these tests assert a refusal that happens before the
        // ownership lookup or the mapping is reached, and a strict stub would
        // fail them for stubbing rather than for behaviour.
        lenient().when(productService.requireOwnedProduct(VENDOR_EMAIL, PRODUCT_ID)).thenReturn(product);
        lenient().when(mapper.toImageResponse(any(ProductImage.class)))
                .thenAnswer(invocation -> ProductImageResponse.builder().build());
    }

    private ProcessedImage processed(byte[] content, String extension) {
        String mimeType = "png".equals(extension) ? "image/png" : "image/jpeg";
        return new ProcessedImage(content, mimeType, extension, 40, 30);
    }

    private MultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content);
    }

    private MultipartFile jpegUpload(String name) {
        return file(name, "jpeg-bytes".getBytes(StandardCharsets.UTF_8));
    }

    private void stubProcessed(byte[] content, String extension) {
        when(imageProcessor.process(any())).thenReturn(processed(content, extension));
    }

    private ProductImage existingImage(Long id, int sortOrder, boolean primary) {
        return ProductImage.builder().id(id).product(product).sortOrder(sortOrder)
                .storageKey("product-images/" + PRODUCT_ID + "/" + id + ".jpg")
                .mimeType("image/jpeg").fileSize(10).primary(primary).build();
    }

    // ------------------------------------------------------------------
    // What is stored, and under what name
    // ------------------------------------------------------------------

    @Test
    void theStoredKeyIsServerGeneratedAndCarriesNoTraceOfTheClientsName() {
        stubProcessed("stored".getBytes(StandardCharsets.UTF_8), "jpg");
        when(imageRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(List.of());

        service.upload(VENDOR_EMAIL, PRODUCT_ID, jpegUpload("../../../../etc/passwd.jpg"), false);

        ArgumentCaptor<ProductImage> saved = ArgumentCaptor.forClass(ProductImage.class);
        verify(imageRepository).saveAndFlush(saved.capture());
        String key = saved.getValue().getStorageKey();
        assertThat(key).startsWith("product-images/" + PRODUCT_ID + "/")
                .endsWith(".jpg")
                .doesNotContain("..", "etc", "passwd");
        verify(storageService).store(key, "stored".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void twoUploadsOfTheSameFileGetDifferentKeys() {
        stubProcessed("stored".getBytes(StandardCharsets.UTF_8), "jpg");
        when(imageRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(List.of());

        service.upload(VENDOR_EMAIL, PRODUCT_ID, jpegUpload("rose.jpg"), false);
        service.upload(VENDOR_EMAIL, PRODUCT_ID, jpegUpload("rose.jpg"), false);

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(storageService, org.mockito.Mockito.times(2)).store(keys.capture(), any());
        assertThat(keys.getAllValues()).hasSize(2);
        assertThat(keys.getAllValues().get(0)).isNotEqualTo(keys.getAllValues().get(1));
    }

    @Test
    void theExtensionComesFromTheSniffedFormatNotFromTheUploadedName() {
        // A WebP upload is stored as JPEG, so the object is a .jpg even though the
        // vendor's file was .webp.
        stubProcessed("stored".getBytes(StandardCharsets.UTF_8), "jpg");
        when(imageRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(List.of());

        service.upload(VENDOR_EMAIL, PRODUCT_ID, jpegUpload("bouquet.webp"), false);

        ArgumentCaptor<ProductImage> saved = ArgumentCaptor.forClass(ProductImage.class);
        verify(imageRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getStorageKey()).endsWith(".jpg");
        assertThat(saved.getValue().getMimeType()).isEqualTo("image/jpeg");
        assertThat(saved.getValue().getOriginalFilename()).isEqualTo("bouquet.webp");
    }

    @Test
    void theStoredSizeIsTheProcessedSizeNotTheUploadedSize() {
        when(imageProcessor.process(any()))
                .thenReturn(new ProcessedImage(new byte[50], "image/jpeg", "jpg", 10, 10));
        when(imageRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(List.of());

        service.upload(VENDOR_EMAIL, PRODUCT_ID, jpegUpload("big.jpg"), false);

        ArgumentCaptor<ProductImage> saved = ArgumentCaptor.forClass(ProductImage.class);
        verify(imageRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getFileSize()).isEqualTo(50L);
    }

    @Test
    void aTraversalFilenameIsReducedToABasenameForDisplayOnly() {
        stubProcessed("stored".getBytes(StandardCharsets.UTF_8), "jpg");
        when(imageRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(List.of());

        service.upload(VENDOR_EMAIL, PRODUCT_ID, jpegUpload("..\\..\\windows\\system32\\cmd.jpg"), false);

        ArgumentCaptor<ProductImage> saved = ArgumentCaptor.forClass(ProductImage.class);
        verify(imageRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getOriginalFilename()).isEqualTo("cmd.jpg");
    }

    // ------------------------------------------------------------------
    // The cover
    // ------------------------------------------------------------------

    @Test
    void theFirstImageOfAProductBecomesItsCover() {
        stubProcessed("stored".getBytes(StandardCharsets.UTF_8), "jpg");
        when(imageRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(List.of());

        service.upload(VENDOR_EMAIL, PRODUCT_ID, jpegUpload("rose.jpg"), false);

        ArgumentCaptor<ProductImage> saved = ArgumentCaptor.forClass(ProductImage.class);
        verify(imageRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().isPrimary()).isTrue();
        assertThat(saved.getValue().getSortOrder()).isZero();
    }

    @Test
    void aSecondUploadDoesNotStealTheCover() {
        stubProcessed("stored".getBytes(StandardCharsets.UTF_8), "jpg");
        ProductImage cover = existingImage(1L, 0, true);
        when(imageRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(List.of(cover));

        service.upload(VENDOR_EMAIL, PRODUCT_ID, jpegUpload("rose-2.jpg"), false);

        assertThat(cover.isPrimary()).isTrue();
        ArgumentCaptor<ProductImage> saved = ArgumentCaptor.forClass(ProductImage.class);
        verify(imageRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().isPrimary()).isFalse();
        assertThat(saved.getValue().getSortOrder()).isEqualTo(1);
    }

    @Test
    void anUploadThatAsksForTheCoverClearsThePreviousOne() {
        stubProcessed("stored".getBytes(StandardCharsets.UTF_8), "jpg");
        ProductImage cover = existingImage(1L, 0, true);
        when(imageRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(List.of(cover));

        service.upload(VENDOR_EMAIL, PRODUCT_ID, jpegUpload("rose-2.jpg"), true);

        assertThat(cover.isPrimary()).isFalse();
        ArgumentCaptor<ProductImage> saved = ArgumentCaptor.forClass(ProductImage.class);
        verify(imageRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().isPrimary()).isTrue();
    }

    @Test
    void settingTheCoverClearsTheOthersInTheSameTransaction() {
        ProductImage first = existingImage(1L, 0, true);
        ProductImage second = existingImage(2L, 1, false);
        when(imageRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(List.of(first, second));
        when(imageRepository.findByProductIdOrderBySortOrderAscIdAsc(PRODUCT_ID))
                .thenReturn(List.of(first, second));

        List<ProductImageResponse> response = service.setPrimary(VENDOR_EMAIL, PRODUCT_ID, 2L);

        assertThat(first.isPrimary()).isFalse();
        assertThat(second.isPrimary()).isTrue();
        verify(imageRepository).saveAndFlush(second);
        assertThat(response).hasSize(2);
    }

    @Test
    void settingTheCoverOfAnImageThatIsAlreadyTheCoverIsIdempotent() {
        ProductImage first = existingImage(1L, 0, true);
        ProductImage second = existingImage(2L, 1, false);
        when(imageRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(List.of(first, second));

        service.setPrimary(VENDOR_EMAIL, PRODUCT_ID, 1L);

        assertThat(first.isPrimary()).isTrue();
        assertThat(second.isPrimary()).isFalse();
    }

    // ------------------------------------------------------------------
    // Delete
    // ------------------------------------------------------------------

    @Test
    void deletingTheCoverPromotesTheNextImage() {
        ProductImage cover = existingImage(1L, 0, true);
        ProductImage second = existingImage(2L, 1, false);
        when(imageRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(List.of(cover, second));

        service.delete(VENDOR_EMAIL, PRODUCT_ID, 1L);

        verify(imageRepository).delete(cover);
        assertThat(second.isPrimary()).isTrue();
        verify(storageService).delete(cover.getStorageKey());
    }

    @Test
    void deletingANonCoverLeavesTheCoverAlone() {
        ProductImage cover = existingImage(1L, 0, true);
        ProductImage second = existingImage(2L, 1, false);
        when(imageRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(List.of(cover, second));

        service.delete(VENDOR_EMAIL, PRODUCT_ID, 2L);

        assertThat(cover.isPrimary()).isTrue();
        verify(imageRepository, never()).saveAndFlush(any(ProductImage.class));
        verify(storageService).delete(second.getStorageKey());
    }

    @Test
    void deletingTheLastImageLeavesTheProductWithNoCover() {
        ProductImage only = existingImage(1L, 0, true);
        when(imageRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(List.of(only));

        service.delete(VENDOR_EMAIL, PRODUCT_ID, 1L);

        verify(imageRepository).delete(only);
        assertThat(only.isPrimary()).isTrue();
    }

    @Test
    void aDeleteThatCannotRemoveTheObjectStillDeletesTheRow() {
        // Row first, object second: an orphaned file costs disk, a surviving row
        // pointing at nothing is a broken image on the storefront.
        ProductImage cover = existingImage(1L, 0, true);
        when(imageRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(List.of(cover));
        doThrow(new StorageException("bucket on fire")).when(storageService).delete(anyString());

        assertThatCode(() -> service.delete(VENDOR_EMAIL, PRODUCT_ID, 1L)).doesNotThrowAnyException();

        verify(imageRepository).delete(cover);
    }

    @Test
    void deletingAnImageThatIsNotTheProductsIsNotFound() {
        when(imageRepository.findByProductIdForUpdate(PRODUCT_ID))
                .thenReturn(List.of(existingImage(1L, 0, true)));

        assertThatThrownBy(() -> service.delete(VENDOR_EMAIL, PRODUCT_ID, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);

        verify(storageService, never()).delete(anyString());
    }

    // ------------------------------------------------------------------
    // Reorder
    // ------------------------------------------------------------------

    @Test
    void reorderRewritesSortOrderFromTheRequest() {
        ProductImage first = existingImage(1L, 0, true);
        ProductImage second = existingImage(2L, 1, false);
        ProductImage third = existingImage(3L, 2, false);
        when(imageRepository.findByProductIdForUpdate(PRODUCT_ID))
                .thenReturn(List.of(first, second, third));

        service.reorder(VENDOR_EMAIL, PRODUCT_ID, List.of(3L, 1L, 2L));

        assertThat(third.getSortOrder()).isZero();
        assertThat(first.getSortOrder()).isEqualTo(1);
        assertThat(second.getSortOrder()).isEqualTo(2);
        verify(imageRepository).saveAllAndFlush(List.of(first, second, third));
    }

    @Test
    void aPartialReorderIsRefused() {
        when(imageRepository.findByProductIdForUpdate(PRODUCT_ID))
                .thenReturn(List.of(existingImage(1L, 0, true), existingImage(2L, 1, false)));

        assertThatThrownBy(() -> service.reorder(VENDOR_EMAIL, PRODUCT_ID, List.of(2L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("every image");
        verify(imageRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void aReorderThatRepeatsAnIdIsRefused() {
        when(imageRepository.findByProductIdForUpdate(PRODUCT_ID))
                .thenReturn(List.of(existingImage(1L, 0, true), existingImage(2L, 1, false)));

        assertThatThrownBy(() -> service.reorder(VENDOR_EMAIL, PRODUCT_ID, List.of(1L, 1L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("repeat");
    }

    @Test
    void aReorderThatNamesAnImageOfAnotherProductIsRefused() {
        when(imageRepository.findByProductIdForUpdate(PRODUCT_ID))
                .thenReturn(List.of(existingImage(1L, 0, true), existingImage(2L, 1, false)));

        assertThatThrownBy(() -> service.reorder(VENDOR_EMAIL, PRODUCT_ID, List.of(1L, 77L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("another product");
    }

    @Test
    void aReorderOfAProductWithNoImagesIsANoOp() {
        // Empty is a permutation of empty. The HTTP layer still refuses an empty
        // reorder (@NotEmpty), because a reorder request naming no image is a
        // client mistake rather than an instruction.
        when(imageRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(List.of());
        when(imageRepository.findByProductIdOrderBySortOrderAscIdAsc(PRODUCT_ID)).thenReturn(List.of());

        assertThat(service.reorder(VENDOR_EMAIL, PRODUCT_ID, List.of())).isEmpty();
        verify(imageRepository).saveAllAndFlush(List.of());
    }

    // ------------------------------------------------------------------
    // Refusals, all of which happen before any write
    // ------------------------------------------------------------------

    @Test
    void anEmptyUploadIsRefusedAndNothingIsWritten() throws Exception {
        assertThatThrownBy(() -> service.upload(VENDOR_EMAIL, PRODUCT_ID,
                new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]), false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);

        verify(imageProcessor, never()).process(any());
        verify(storageService, never()).store(anyString(), any());
    }

    @Test
    void anOversizedUploadIsRefusedFromItsDeclaredLengthWithoutReadingIt() throws Exception {
        MultipartFile huge = org.mockito.Mockito.mock(MultipartFile.class);
        when(huge.isEmpty()).thenReturn(false);
        when(huge.getSize()).thenReturn(properties.getMaxFileSizeBytes() + 1);

        assertThatThrownBy(() -> service.upload(VENDOR_EMAIL, PRODUCT_ID, huge, false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE);

        // The point of checking the length first: an oversized body is never
        // buffered to find out how big it is.
        verify(huge, never()).getBytes();
        verify(storageService, never()).store(anyString(), any());
    }

    @Test
    void aMissingFilePartIsRefused() {
        assertThatThrownBy(() -> service.upload(VENDOR_EMAIL, PRODUCT_ID, null, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("required");
        verify(storageService, never()).store(anyString(), any());
    }

    @Test
    void anUnsupportedFormatFromThePipelineIsRefused() {
        when(imageProcessor.process(any())).thenThrow(
                BusinessException.unsupportedMediaType("Unsupported image format"));

        assertThatThrownBy(() -> service.upload(VENDOR_EMAIL, PRODUCT_ID, jpegUpload("x.jpg"), false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE);

        verify(storageService, never()).store(anyString(), any());
    }

    @Test
    void anUnreadableFileFromThePipelineIsRefused() {
        when(imageProcessor.process(any()))
                .thenThrow(BusinessException.badRequest("Uploaded file is not a readable image"));

        assertThatThrownBy(() -> service.upload(VENDOR_EMAIL, PRODUCT_ID, jpegUpload("x.jpg"), false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void aProductAtItsImageLimitIsRefusedBeforeAnythingIsWritten() {
        properties.setMaxImagesPerProduct(2);
        stubProcessed("stored".getBytes(StandardCharsets.UTF_8), "jpg");
        when(imageRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(
                List.of(existingImage(1L, 0, true), existingImage(2L, 1, false)));

        assertThatThrownBy(() -> service.upload(VENDOR_EMAIL, PRODUCT_ID, jpegUpload("third.jpg"), false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("maximum of 2 images");

        verify(storageService, never()).store(anyString(), any());
        verify(imageRepository, never()).saveAndFlush(any());
    }

    @Test
    void aForeignProductIsRefusedBeforeTheRowsAreLocked() {
        when(productService.requireOwnedProduct(VENDOR_EMAIL, PRODUCT_ID))
                .thenThrow(BusinessException.forbidden("Product does not belong to this vendor"));

        assertThatThrownBy(() -> service.upload(VENDOR_EMAIL, PRODUCT_ID, jpegUpload("x.jpg"), false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        // Locking a foreign vendor's rows on the way to a 403 would let any
        // florist stall that product's image set (D-24's ordering rule).
        verify(imageRepository, never()).findByProductIdForUpdate(anyLong());
        verify(storageService, never()).store(anyString(), any());
    }

    @Test
    void anUnknownProductIsRefusedAsNotFound() {
        when(productService.requireOwnedProduct(VENDOR_EMAIL, PRODUCT_ID))
                .thenThrow(BusinessException.notFound("Product not found"));

        assertThatThrownBy(() -> service.upload(VENDOR_EMAIL, PRODUCT_ID, jpegUpload("x.jpg"), false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    // ------------------------------------------------------------------
    // Failure handling and the invariant post-condition
    // ------------------------------------------------------------------

    @Test
    void aRowThatCannotBeWrittenLeavesNoFileBehind() {
        stubProcessed("stored".getBytes(StandardCharsets.UTF_8), "jpg");
        when(imageRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(List.of());
        when(imageRepository.saveAndFlush(any(ProductImage.class)))
                .thenThrow(new IllegalStateException("constraint violation"));

        assertThatThrownBy(() -> service.upload(VENDOR_EMAIL, PRODUCT_ID, jpegUpload("x.jpg"), false))
                .isInstanceOf(IllegalStateException.class);

        ArgumentCaptor<String> storedKey = ArgumentCaptor.forClass(String.class);
        verify(storageService).store(storedKey.capture(), any());
        verify(storageService).delete(storedKey.getValue());
    }

    @Test
    void aPostConditionFailureAlsoRemovesTheStoredObject() {
        stubProcessed("stored".getBytes(StandardCharsets.UTF_8), "jpg");
        when(imageRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(List.of());
        // Two primaries reported after the write: the invariant is violated, and
        // the request must fail rather than commit the row and the file.
        when(imageRepository.countByProductIdAndPrimaryIsTrue(PRODUCT_ID)).thenReturn(2L);
        when(imageRepository.countByProductId(PRODUCT_ID)).thenReturn(2L);

        assertThatThrownBy(() -> service.upload(VENDOR_EMAIL, PRODUCT_ID, jpegUpload("x.jpg"), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Primary image invariant");

        ArgumentCaptor<String> storedKey = ArgumentCaptor.forClass(String.class);
        verify(storageService).store(storedKey.capture(), any());
        verify(storageService).delete(storedKey.getValue());
    }

    @Test
    void aProductWithNoImagesHasNoPrimaryAndThatIsNotAViolation() {
        stubProcessed("stored".getBytes(StandardCharsets.UTF_8), "jpg");
        when(imageRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(List.of());
        when(imageRepository.countByProductIdAndPrimaryIsTrue(PRODUCT_ID)).thenReturn(0L);
        when(imageRepository.countByProductId(PRODUCT_ID)).thenReturn(0L);

        assertThatCode(() -> service.upload(VENDOR_EMAIL, PRODUCT_ID, jpegUpload("x.jpg"), false))
                .doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------
    // Filename sanitisation
    // ------------------------------------------------------------------

    @Test
    void sanitizeFilenameDropsEveryDirectoryComponent() {
        assertThat(ProductImageService.sanitizeFilename("rose.jpg")).isEqualTo("rose.jpg");
        assertThat(ProductImageService.sanitizeFilename("../../etc/passwd"))
                .isEqualTo("passwd");
        assertThat(ProductImageService.sanitizeFilename("..\\..\\windows\\cmd.jpg"))
                .isEqualTo("cmd.jpg");
        assertThat(ProductImageService.sanitizeFilename("/absolute/rose.jpg")).isEqualTo("rose.jpg");
    }

    @Test
    void sanitizeFilenameRejectsNamesThatCarryNoInformation() {
        assertThat(ProductImageService.sanitizeFilename(null)).isNull();
        assertThat(ProductImageService.sanitizeFilename("   ")).isNull();
        assertThat(ProductImageService.sanitizeFilename("..")).isNull();
        assertThat(ProductImageService.sanitizeFilename(".")).isNull();
        assertThat(ProductImageService.sanitizeFilename("../../..")).isNull();
    }

    @Test
    void sanitizeFilenameClampsToTheColumnLength() {
        assertThat(ProductImageService.sanitizeFilename("a".repeat(300))).hasSize(255);
    }

    @Test
    void sanitizeFilenameStripsControlCharacters() {
        assertThat(ProductImageService.sanitizeFilename("ro se\n.jpg")).isEqualTo("rose.jpg");
    }

    @Test
    void listRequiresOwnershipJustLikeEveryOtherRoute() {
        when(productService.requireOwnedProduct(VENDOR_EMAIL, PRODUCT_ID))
                .thenThrow(BusinessException.forbidden("Product does not belong to this vendor"));

        assertThatThrownBy(() -> service.list(VENDOR_EMAIL, PRODUCT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(imageRepository, never()).findByProductIdOrderBySortOrderAscIdAsc(anyLong());
    }
}