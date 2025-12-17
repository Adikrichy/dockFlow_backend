package org.aldousdev.dockflowbackend.auth.entity;

import org.springframework.stereotype.Component;

@Component("roleGuard")
public class RoleGuard {
    public boolean hasLevel(CompanyRoleEntity role, int level) {
        return role.getLevel() >= level;
    }
}
