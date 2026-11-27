package com.flowerconnect.geo.domain;

import com.flowerconnect.domain.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * A proposed service area submitted during vendor registration when the
 * vendor's shop area is not yet a seeded {@code service_locations} row.
 *
 * A proposal is recorded in {@link Status#PENDING} and reviewed by an
 * administrator. Approval creates the real {@link com.flowerconnect.domain.ServiceLocation}
 * row (with administrator-supplied coordinates and timezone -- data the
 * registration form cannot and must not invent), at which point the area
 * appears in the customer "Set your delivery location" picker. Rejection
 * records a reason.
 *
 * PENDING proposals are intentionally never exposed by
 * {@code GET /api/v1/locations}, so a proposed area is not publicly
 * discoverable or deliverable until approved.
 */
@Entity
@Table(name = "service_location_proposals")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ServiceLocationProposal {

    public enum Status {
        PENDING, APPROVED, REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "city", length = 128, nullable = false)
    private String city;

    @Column(name = "area", length = 128, nullable = false)
    private String area;

    @Column(name = "pincode", length = 10, nullable = false)
    private String pincode;

    @Column(name = "address_line1", length = 255, nullable = false)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private Status status = Status.PENDING;

    @ManyToOne(optional = true)
    @JoinColumn(name = "proposed_by_user_id", foreignKey = @ForeignKey(name = "fk_service_location_proposals_proposer"))
    private User proposedByUser;

    @Column(name = "proposed_by_email", length = 255)
    private String proposedByEmail;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @ManyToOne(optional = true)
    @JoinColumn(name = "reviewed_by_user_id", foreignKey = @ForeignKey(name = "fk_service_location_proposals_reviewer"))
    private User reviewedByUser;
}
