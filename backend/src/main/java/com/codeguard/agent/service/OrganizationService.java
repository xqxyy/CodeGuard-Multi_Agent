package com.codeguard.agent.service;

import com.codeguard.agent.api.OrganizationDto;
import com.codeguard.agent.domain.TenantDefaults;
import com.codeguard.agent.persistence.OrganizationEntity;
import com.codeguard.agent.persistence.OrganizationRepository;
import com.codeguard.agent.security.CurrentUserService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 组织服务。
 *
 * 当前项目先做单组织演示，但代码按多组织方式设计，
 * 后续接入真实用户中心时不用改审查主流程。
 */
@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final CurrentUserService currentUserService;

    public OrganizationService(
            OrganizationRepository organizationRepository,
            CurrentUserService currentUserService
    ) {
        this.organizationRepository = organizationRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public OrganizationEntity ensureCurrentOrganization() {
        return ensureOrganization(currentUserService.organizationKey());
    }

    @Transactional
    public OrganizationEntity ensureOrganization(String organizationKey) {
        String key = organizationKey == null || organizationKey.isBlank()
                ? TenantDefaults.DEFAULT_ORGANIZATION_KEY
                : organizationKey.strip();

        return organizationRepository.findByOrganizationKey(key)
                .orElseGet(() -> organizationRepository.save(OrganizationEntity.create(
                        key,
                        TenantDefaults.DEFAULT_ORGANIZATION_NAME,
                        "本地演示组织，用于展示多租户、策略和审计能力。",
                        "ENTERPRISE_DEMO"
                )));
    }

    @Transactional
    public OrganizationDto current() {
        return toDto(ensureOrganization(currentUserService.organizationKey()));
    }

    @Transactional(readOnly = true)
    public List<OrganizationDto> list() {
        return organizationRepository.findAllByOrderByCreatedAtAsc()
                .stream()
                .map(this::toDto)
                .toList();
    }

    private OrganizationDto toDto(OrganizationEntity entity) {
        return new OrganizationDto(
                entity.getId(),
                entity.getOrganizationKey(),
                entity.getName(),
                entity.getDescription(),
                entity.getPlan(),
                entity.getCreatedAt()
        );
    }
}
