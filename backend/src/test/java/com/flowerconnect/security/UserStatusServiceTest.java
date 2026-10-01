package com.flowerconnect.security;

import com.flowerconnect.domain.AuditLog;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.mapper.AdminUserMapper;
import com.flowerconnect.repository.AuditLogRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.security.dto.AdminUserResponse;
import com.flowerconnect.security.jwt.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit coverage for the account-status rule and audit trail of plan task 2.8.
 */
@ExtendWith(MockitoExtension.class)
class UserStatusServiceTest {

    private static final String ADMIN_EMAIL = "admin@test.com";

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private AdminUserMapper adminUserMapper;

    @InjectMocks
    private UserStatusService userStatusService;

    // --------------------------------------------------------------- transitions

    @Test
    void suspendingWritesTheStatusAndTheAuditRow() {
        User target = stubTarget(7L, User.Status.ACTIVE);
        stubActor();
        stubMapping();

        userStatusService.changeStatus(ADMIN_EMAIL, 7L, User.Status.SUSPENDED, "Chargeback fraud");

        assertEquals(User.Status.SUSPENDED, target.getStatus());
        verify(userRepository).saveAndFlush(target);

        AuditLog entry = captureAudit();
        assertEquals(UserStatusService.ACTION_SUSPENDED, entry.getActionType());
        assertEquals(UserStatusService.ENTITY_TYPE, entry.getEntityType());
        assertEquals(7L, entry.getEntityId());
        assertEquals("Chargeback fraud", entry.getReason());
        assertEquals(1L, entry.getActor().getId());
    }

    @Test
    void disablingWritesItsOwnActionType() {
        User target = stubTarget(7L, User.Status.SUSPENDED);
        stubActor();
        stubMapping();

        userStatusService.changeStatus(ADMIN_EMAIL, 7L, User.Status.DISABLED, "Terms violated");

        assertEquals(User.Status.DISABLED, target.getStatus());
        assertEquals(UserStatusService.ACTION_DISABLED, captureAudit().getActionType());
    }

    @Test
    void reactivatingWritesTheReactivationAction() {
        User target = stubTarget(7L, User.Status.SUSPENDED);
        stubActor();
        stubMapping();

        userStatusService.changeStatus(ADMIN_EMAIL, 7L, User.Status.ACTIVE, "Appeal upheld");

        assertEquals(User.Status.ACTIVE, target.getStatus());
        assertEquals(UserStatusService.ACTION_REACTIVATED, captureAudit().getActionType());
    }

    // ---------------------------------------------------------- refresh token rule

    @Test
    void suspensionRevokesTheTargetsRefreshTokens() {
        User target = stubTarget(7L, User.Status.ACTIVE);
        stubActor();
        stubMapping();

        userStatusService.changeStatus(ADMIN_EMAIL, 7L, User.Status.SUSPENDED, "Abuse");

        verify(refreshTokenService).revokeAllRefreshTokensForUser(7L);
    }

    @Test
    void disablingRevokesTheTargetsRefreshTokens() {
        User target = stubTarget(7L, User.Status.SUSPENDED);
        stubActor();
        stubMapping();

        userStatusService.changeStatus(ADMIN_EMAIL, 7L, User.Status.DISABLED, "Abuse");

        verify(refreshTokenService).revokeAllRefreshTokensForUser(7L);
    }

    @Test
    void reactivatingAlsoRevokesSoTheRuleIsUnconditional() {
        User target = stubTarget(7L, User.Status.SUSPENDED);
        stubActor();
        stubMapping();

        userStatusService.changeStatus(ADMIN_EMAIL, 7L, User.Status.ACTIVE, "Appeal upheld");

        verify(refreshTokenService).revokeAllRefreshTokensForUser(7L);
    }

    // --------------------------------------------------------------- refusals

    @Test
    void anAdminCannotChangeTheirOwnStatus() {
        User actor = user(1L, ADMIN_EMAIL, "ADMIN", User.Status.ACTIVE);
        when(userRepository.findByEmailWithRole(ADMIN_EMAIL)).thenReturn(Optional.of(actor));
        when(userRepository.findByIdWithRole(1L)).thenReturn(Optional.of(actor));

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> userStatusService.changeStatus(
                        ADMIN_EMAIL, 1L, User.Status.SUSPENDED, "Self"));

        assertEquals(ErrorCode.FORBIDDEN, thrown.getErrorCode());
        assertEquals(UserStatusService.MESSAGE_SELF_STATUS_CHANGE, thrown.getMessage());

        verify(userRepository, never()).saveAndFlush(any());
        verify(refreshTokenService, never()).revokeAllRefreshTokensForUser(any());
        verifyNoInteractions(auditLogRepository);
    }

    @Test
    void anUnknownTargetIsNotFound() {
        when(userRepository.findByEmailWithRole(ADMIN_EMAIL))
                .thenReturn(Optional.of(user(1L, ADMIN_EMAIL, "ADMIN", User.Status.ACTIVE)));
        when(userRepository.findByIdWithRole(999L)).thenReturn(Optional.empty());

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> userStatusService.changeStatus(
                        ADMIN_EMAIL, 999L, User.Status.SUSPENDED, "Nope"));

        assertEquals(ErrorCode.NOT_FOUND, thrown.getErrorCode());
        verifyNoInteractions(auditLogRepository);
    }

    @Test
    void anAdminMayChangeAnotherAdminsStatus() {
        User target = stubTarget(2L, User.Status.ACTIVE);
        when(userRepository.findByEmailWithRole(ADMIN_EMAIL))
                .thenReturn(Optional.of(user(1L, ADMIN_EMAIL, "ADMIN", User.Status.ACTIVE)));
        when(userRepository.findByIdWithRole(2L)).thenReturn(Optional.of(target));
        stubMapping();

        userStatusService.changeStatus(ADMIN_EMAIL, 2L, User.Status.SUSPENDED, "Peer review");

        assertEquals(User.Status.SUSPENDED, target.getStatus());
        verify(refreshTokenService).revokeAllRefreshTokensForUser(2L);
    }

    @Test
    void aMissingActorIsAFaultNotAClientError() {
        when(userRepository.findByEmailWithRole(ADMIN_EMAIL)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> userStatusService.changeStatus(
                        ADMIN_EMAIL, 7L, User.Status.SUSPENDED, "Nope"));
    }

    // ------------------------------------------------------------------ helpers

    private User stubTarget(Long id, User.Status status) {
        User target = user(id, "target@test.com", "CUSTOMER", status);
        when(userRepository.findByIdWithRole(id)).thenReturn(Optional.of(target));
        return target;
    }

    private void stubActor() {
        when(userRepository.findByEmailWithRole(ADMIN_EMAIL))
                .thenReturn(Optional.of(user(1L, ADMIN_EMAIL, "ADMIN", User.Status.ACTIVE)));
    }

    private void stubMapping() {
        when(adminUserMapper.toResponse(any())).thenReturn(AdminUserResponse.builder().build());
    }

    private AuditLog captureAudit() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        return captor.getValue();
    }

    private static User user(Long id, String email, String roleName, User.Status status) {
        return User.builder()
                .id(id)
                .email(email)
                .passwordHash("$2a$10$hash")
                .fullName("Test User")
                .phone("+919999999999")
                .role(Role.builder().id(99L).name(roleName).build())
                .status(status)
                .build();
    }
}