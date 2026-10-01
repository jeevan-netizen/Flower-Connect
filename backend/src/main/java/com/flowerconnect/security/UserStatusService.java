package com.flowerconnect.security;

import com.flowerconnect.domain.AuditLog;
import com.flowerconnect.domain.User;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.mapper.AdminUserMapper;
import com.flowerconnect.repository.AuditLogRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.security.dto.AdminUserResponse;
import com.flowerconnect.security.jwt.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single owner of the "may this account's status change?" rule (plan task
 * 1.3, exercised by the admin API in task 2.8).
 *
 * <p>Phase 1 enforced status at login, refresh and in the JWT filter but never
 * wrote it; this service is the one place a status change happens, so the
 * refresh-token revocation that the plan requires cannot be bypassed by a caller
 * that updates the column directly.
 *
 * <p>Self-status change is refused because the acting admin is resolved from the
 * JWT subject and compared to the target row. This is an authorization rule
 * (the admin is not permitted to act on their own account), so it raises 403
 * {@code FORBIDDEN} rather than 409 — the same layer the vendor approval guard
 * and the {@code /api/v1/admin/**} role matcher report at. See docs/decisions.md
 * (D-14).
 *
 * <p>Every transition that leaves {@code ACTIVE} revokes all of the user's
 * refresh tokens in the same transaction, so a suspended or disabled account
 * cannot mint a new access token from an existing session. Reinstating to
 * {@code ACTIVE} revokes too: those tokens were already revoked on the way in, so
 * the call is a no-op and the rule stays unconditional rather than
 * status-dependent.
 *
 * <p>An admin may act on another administrator's account. Only the self-target
 * case is refused, so a rogue or compromised admin can still be suspended by a
 * peer. See docs/decisions.md (D-14).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserStatusService {

    public static final String ENTITY_TYPE = "USER";

    public static final String ACTION_SUSPENDED = "USER_SUSPENDED";
    public static final String ACTION_REACTIVATED = "USER_REACTIVATED";
    public static final String ACTION_DISABLED = "USER_DISABLED";

    public static final String MESSAGE_SELF_STATUS_CHANGE =
            "An administrator cannot change their own account status";
    public static final String MESSAGE_UNKNOWN_USER = "User not found";

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final AuditLogRepository auditLogRepository;
    private final AdminUserMapper adminUserMapper;

    /**
     * Moves a user to {@code newStatus}, revoking their sessions and writing one
     * {@code audit_log} row, all in the caller's transaction.
     *
     * @throws BusinessException {@code FORBIDDEN} if the target is the acting
     *                           admin, {@code NOT_FOUND} if no such user exists
     */
    @Transactional
    public AdminUserResponse changeStatus(String adminEmail, Long targetUserId,
                                         User.Status newStatus, String reason) {
        User actor = requireActor(adminEmail);
        User target = userRepository.findByIdWithRole(targetUserId)
                .orElseThrow(() -> BusinessException.notFound(MESSAGE_UNKNOWN_USER));

        if (target.getId().equals(actor.getId())) {
            throw BusinessException.forbidden(MESSAGE_SELF_STATUS_CHANGE);
        }

        target.setStatus(newStatus);
        userRepository.saveAndFlush(target);

        refreshTokenService.revokeAllRefreshTokensForUser(target.getId());

        auditLogRepository.save(AuditLog.builder()
                .actor(actor)
                .actionType(actionFor(newStatus))
                .entityType(ENTITY_TYPE)
                .entityId(target.getId())
                .reason(reason)
                .build());

        return adminUserMapper.toResponse(target);
    }

    private User requireActor(String adminEmail) {
        return userRepository.findByEmailWithRole(adminEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated admin not found"));
    }

    private static String actionFor(User.Status status) {
        return switch (status) {
            case SUSPENDED -> ACTION_SUSPENDED;
            case ACTIVE -> ACTION_REACTIVATED;
            case DISABLED -> ACTION_DISABLED;
        };
    }
}