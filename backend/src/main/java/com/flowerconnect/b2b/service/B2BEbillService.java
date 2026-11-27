package com.flowerconnect.b2b.service;

import com.flowerconnect.b2b.domain.B2BOrder;
import com.flowerconnect.b2b.domain.B2BOrderItem;
import com.flowerconnect.b2b.repository.B2BOrderItemRepository;
import com.flowerconnect.b2b.repository.B2BOrderRepository;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.repository.VendorProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * B2B e-bill service (Stage 2 of the B2B module).
 *
 * <p>Generates a PDF e-bill for a delivered B2B order. The bill number is
 * assigned exactly once when the order transitions to
 * {@code DELIVERED} in {@link B2BOrderService#deliver}; this service only
 * reads it. A download is refused unless the order is
 * {@code DELIVERED} and the caller is the buyer or the seller on the order.
 *
 * <p>All monetary amounts and item details come from the order snapshots
 * taken at placement; nothing is recomputed from the current catalog, and
 * no tax, GST or other fees are invented. The PDF is produced with Apache
 * PDFBox (Apache 2.0).
 *
 * <p>All timestamps come from the injected {@link Clock}; {@code
 * Instant.now()} is never called directly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class B2BEbillService {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final B2BOrderRepository orderRepository;
    private final B2BOrderItemRepository orderItemRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final Clock clock;

    /**
     * Generates the e-bill PDF for a delivered order.
     *
     * @param vendorEmail the authenticated caller (resolved from the JWT subject)
     * @param orderId     the B2B order id
     * @return the PDF document as bytes
     * @throws BusinessException with {@code E_BILL_NOT_AVAILABLE} (409) when
     *         the order is not {@code DELIVERED}, {@code FORBIDDEN} (403) when
     *         the caller is neither buyer nor seller, or {@code NOT_FOUND}
     *         (404) when the order does not exist
     */
    @Transactional(readOnly = true)
    public byte[] generateEbillPdf(String vendorEmail, Long orderId) {
        VendorProfile vendor = requireApprovedVendor(vendorEmail);
        B2BOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> BusinessException.notFound("B2B order not found"));

        if (!order.getBuyer().getId().equals(vendor.getId())
                && !order.getSeller().getId().equals(vendor.getId())) {
            throw BusinessException.forbidden(
                    "You are not a participant in this order");
        }
        if (order.getStatus() != B2BOrder.Status.DELIVERED) {
            throw new BusinessException(ErrorCode.E_BILL_NOT_AVAILABLE,
                    "E-bill is only available for delivered orders");
        }
        if (order.getBillNumber() == null) {
            // Defensive: a delivered order should always carry a bill number
            // (assigned in B2BOrderService.deliver). Generate it now if missing.
            order.setBillNumber(generateBillNumber(order.getId(), order.getDeliveredAt()));
            orderRepository.saveAndFlush(order);
        }

        List<B2BOrderItem> items = orderItemRepository.findByOrderId(order.getId());
        byte[] pdf = buildPdf(order, items);

        log.info("Generated e-bill {} for order {} requested by {}",
                order.getBillNumber(), orderId, vendorEmail);
        return pdf;
    }

    // ------------------------------------------------------------------
    // PDF building (Apache PDFBox)
    // ------------------------------------------------------------------

    private byte[] buildPdf(B2BOrder order, List<B2BOrderItem> items) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float margin = 50f;
                float width = page.getMediaBox().getWidth();
                float y = page.getMediaBox().getHeight() - margin;

                // Title
                content.beginText();
                content.setFont(PDType1Font.HELVETICA_BOLD, 18);
                content.newLineAtOffset(margin, y);
                content.showText("E-BILL");
                content.endText();
                y -= 30f;

                // Bill number, order number and bill date
                y = writeLine(content, margin, y, "Bill number:", order.getBillNumber(), 12, true);
                y = writeLine(content, margin, y, "Order number:", order.getOrderNo(), 12, true);
                y = writeLine(content, margin, y, "Bill date:",
                        formatDate(order.getDeliveredAt()), 12, true);
                y -= 10f;

                // Parties
                content.beginText();
                content.setFont(PDType1Font.HELVETICA_BOLD, 12);
                content.newLineAtOffset(margin, y);
                content.showText("Seller");
                content.endText();
                y -= 14f;
                y = writeLine(content, margin + 10f, y,
                        order.getSeller().getBusinessName(), null, 11, false);
                y = writeLine(content, margin + 10f, y,
                                addressLine(order.getSeller()), null, 11, false);
                y -= 10f;

                content.beginText();
                content.setFont(PDType1Font.HELVETICA_BOLD, 12);
                content.newLineAtOffset(margin, y);
                content.showText("Buyer");
                content.endText();
                y -= 14f;
                y = writeLine(content, margin + 10f, y,
                        order.getBuyer().getBusinessName(), null, 11, false);
                y = writeLine(content, margin + 10f, y,
                                addressLine(order.getBuyer()), null, 11, false);
                y -= 16f;

                // Item table header
                y = writeTableRow(content, margin, y,
                        new String[]{"Product", "Unit", "Qty", "Rate", "Total"},
                        true);
                y -= 2f;

                // Item rows
                for (B2BOrderItem item : items) {
                    y = writeTableRow(content, margin, y,
                            new String[]{
                                    item.getProductName(),
                                    item.getUnit(),
                                    String.valueOf(item.getQuantity()),
                                    formatMoney(item.getUnitPrice()),
                                    formatMoney(item.getLineTotal())
                            },
                            false);
                    y -= 2f;
                }

                // Grand total
                y -= 6f;
                content.beginText();
                content.setFont(PDType1Font.HELVETICA_BOLD, 12);
                content.newLineAtOffset(margin, y);
                content.showText("Grand total:");
                content.endText();
                content.beginText();
                content.setFont(PDType1Font.HELVETICA_BOLD, 12);
                content.newLineAtOffset(width - margin - stringWidth("INR " + formatMoney(order.getTotal()), PDType1Font.HELVETICA_BOLD, 12), y);
                content.showText("INR " + formatMoney(order.getTotal()));
                content.endText();
                y -= 20f;

                // Shipment reference (when present)
                if (order.getShipmentReference() != null && !order.getShipmentReference().isBlank()) {
                    y = writeLine(content, margin, y, "Shipment reference:",
                            order.getShipmentReference(), 11, false);
                    y -= 10f;
                }

                // Footer
                content.beginText();
                content.setFont(PDType1Font.HELVETICA_OBLIQUE, 9);
                content.newLineAtOffset(margin, y);
                content.showText("FlowerConnect B2B e-bill. Amounts are in INR and reflect the prices "
                        + "snapshotted at order placement.");
                content.endText();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "Failed to generate e-bill PDF: " + ex.getMessage());
        }
    }

    private float writeLine(PDPageContentStream content, float x, float y,
                            String label, String value, float fontSize, boolean bold) throws Exception {
        if (label != null) {
            content.beginText();
            content.setFont(bold ? PDType1Font.HELVETICA_BOLD : PDType1Font.HELVETICA, fontSize);
            content.newLineAtOffset(x, y);
            content.showText(label);
            content.endText();
        }
        if (value != null) {
            content.beginText();
            content.setFont(bold ? PDType1Font.HELVETICA_BOLD : PDType1Font.HELVETICA, fontSize);
            content.newLineAtOffset(x + 120f, y);
            content.showText(value);
            content.endText();
        }
        return y - 16f;
    }

    private float writeTableRow(PDPageContentStream content, float x, float y,
                                String[] cells, boolean header) throws Exception {
        float[] colX = {x, x + 220f, x + 300f, x + 350f, x + 410f};
        content.beginText();
        content.setFont(header ? PDType1Font.HELVETICA_BOLD : PDType1Font.HELVETICA, 10);
        for (int i = 0; i < cells.length; i++) {
            content.newLineAtOffset(colX[i], y);
            content.showText(cells[i]);
        }
        content.endText();
        return y - 16f;
    }

    private String formatDate(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATE_FORMATTER) : "-";
    }

    private String formatMoney(BigDecimal amount) {
        return amount != null ? amount.setScale(2, BigDecimal.ROUND_HALF_UP).toString() : "0.00";
    }

    private float stringWidth(String text, PDType1Font font, float fontSize) throws Exception {
        return font.getStringWidth(text) / 1000f * fontSize;
    }

    private String addressLine(VendorProfile vendor) {
        StringBuilder sb = new StringBuilder();
        if (vendor.getAddressLine1() != null) {
            sb.append(vendor.getAddressLine1());
        }
        if (vendor.getServiceLocation() != null) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(vendor.getServiceLocation().getArea());
            if (vendor.getServiceLocation().getCity() != null) {
                sb.append(", ").append(vendor.getServiceLocation().getCity());
            }
            if (vendor.getServiceLocation().getPincode() != null) {
                sb.append(" - ").append(vendor.getServiceLocation().getPincode());
            }
        }
        return sb.length() > 0 ? sb.toString() : "-";
    }

    private String generateBillNumber(Long orderId, LocalDateTime deliveredAt) {
        int year = deliveredAt.getYear();
        return String.format("EB-%d-%06d", year, orderId);
    }

    // ------------------------------------------------------------------
    // Lookups
    // ------------------------------------------------------------------

    private VendorProfile requireApprovedVendor(String email) {
        VendorProfile vendor = vendorProfileRepository.findByUserEmail(email)
                .orElseThrow(() -> BusinessException.notFound("Vendor profile not found"));
        if (vendor.getStatus() != VendorProfile.Status.APPROVED) {
            throw BusinessException.forbidden("Vendor profile is not APPROVED");
        }
        return vendor;
    }
}
