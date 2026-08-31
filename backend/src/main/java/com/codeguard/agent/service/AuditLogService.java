package com.codeguard.agent.service;

import com.codeguard.agent.api.AuditLogDto;
import com.codeguard.agent.domain.TenantDefaults;
import com.codeguard.agent.persistence.AuditLogEntity;
import com.codeguard.agent.persistence.AuditLogRepository;
import com.codeguard.agent.security.CurrentUserService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审计日志服务。
 *
 * 审计不是核心业务逻辑，所以写入失败不能影响用户提交 Review。
 * record 方法会吞掉审计写入异常，保证主流程优先成功。
 */
@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final CurrentUserService currentUserService;

    public AuditLogService(AuditLogRepository auditLogRepository, CurrentUserService currentUserService) {
        this.auditLogRepository = auditLogRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String resourceType, String resourceId, String summary) {
        try {
            auditLogRepository.save(AuditLogEntity.create(
                    currentUserService.organizationKey(),
                    currentUserService.username(),
                    currentUserService.role(),
                    action,
                    resourceType,
                    resourceId,
                    summary
            ));
        } catch (RuntimeException ignored) {
            // 审计写入失败不应该阻断审查主链路。
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAs(
            String organizationKey,
            String actorUsername,
            String actorRole,
            String action,
            String resourceType,
            String resourceId,
            String summary
    ) {
        try {
            auditLogRepository.save(AuditLogEntity.create(
                    organizationKey == null || organizationKey.isBlank()
                            ? TenantDefaults.DEFAULT_ORGANIZATION_KEY
                            : organizationKey.strip(),
                    actorUsername == null || actorUsername.isBlank() ? "unknown" : actorUsername.strip(),
                    actorRole == null || actorRole.isBlank() ? "UNKNOWN" : actorRole.strip(),
                    action,
                    resourceType,
                    resourceId,
                    summary
            ));
        } catch (RuntimeException ignored) {
            // 审计写入失败不应该阻断用户登录或业务操作。
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSystem(
            String organizationKey,
            String action,
            String resourceType,
            String resourceId,
            String summary
    ) {
        recordAs(organizationKey, "system", "SYSTEM", action, resourceType, resourceId, summary);
    }

    @Transactional(readOnly = true)
    public List<AuditLogDto> listRecent() {
        return auditLogRepository.findTop50ByOrganizationKeyOrderByCreatedAtDesc(currentUserService.organizationKey())
                .stream()
                .map(this::toDto)
                .toList();
    }

    private AuditLogDto toDto(AuditLogEntity entity) {
        return new AuditLogDto(
                entity.getId(),
                entity.getOrganizationKey(),
                entity.getActorUsername(),
                entity.getActorRole(),
                entity.getAction(),
                entity.getResourceType(),
                entity.getResourceId(),
                entity.getSummary(),
                entity.getCreatedAt()
        );
    }
}
