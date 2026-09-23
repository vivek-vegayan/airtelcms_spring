package com.vegayan.airtelmanagement.common.security.permission;

import com.vegayan.airtelmanagement.common.service.BaseService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserPermissionService extends BaseService {

    public List<String> getPermissionsByUserId(Long userId) {
        String sql = """
                    SELECT DISTINCT p.code
                    FROM CHM_USER u
                    JOIN CHM_USER_ROLES ur ON u.id = ur.user_id
                    JOIN CHM_ROLES_PERMISSIONS rp ON ur.role_id = rp.role_id
                    JOIN CHM_PERMISSIONS p ON rp.permission_id = p.id
                    WHERE u.id = ?
                """;
        List<String> permissions = jdbcTemplateOne.query(
                sql,
                (rs, rowNum) -> rs.getString("code"),
                userId   // ✅ varargs (modern approach)
        );

        LOGGER.info("Permissions for user [{}]: {}", userId, permissions);
        return permissions;
    }

    public List<String> getPermissionsByUserIdV1(Long userId) {

        String sql = """
        SELECT DISTINCT
            CONCAT(m.module_code, '_', sm.sub_module_code, '_', p.permission_code) AS permission_code
        FROM USER_ROLE_MAP ur
        JOIN ROLE_MASTER r ON ur.role_id = r.role_id
        JOIN ROLE_PERMISSION_MAP rpm ON r.role_id = rpm.role_id
        JOIN SUB_MODULE sm ON rpm.sub_module_id = sm.sub_module_id
        JOIN MODULE m ON sm.module_id = m.module_id
        JOIN PERMISSION p ON rpm.permission_id = p.permission_id
        WHERE ur.user_id = ?
        AND (ur.effective_to IS NULL OR ur.effective_to >= CURDATE())
    """;

        List<String> permissions = jdbcTemplateOne.queryForList(
                sql,
                String.class,
                userId
        );

        LOGGER.info("Permissions for user V1 [{}]: {}", userId, permissions);

        return permissions;
    }

}



