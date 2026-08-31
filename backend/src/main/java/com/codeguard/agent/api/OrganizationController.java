package com.codeguard.agent.api;

import com.codeguard.agent.service.OrganizationService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 组织接口。
 *
 * 前端顶部的组织信息、后续多租户扩展，都从这里读取。
 */
@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping("/current")
    public OrganizationDto current() {
        return organizationService.current();
    }

    @GetMapping
    public List<OrganizationDto> list() {
        return organizationService.list();
    }
}
