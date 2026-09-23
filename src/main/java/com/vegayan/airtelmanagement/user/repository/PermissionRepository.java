package com.vegayan.airtelmanagement.user.repository;

import com.vegayan.airtelmanagement.common.service.BaseService;

import java.util.List;

public class PermissionRepository extends BaseService {

    public List<String> findPermissionsByOlmId(String olmId) {

        String sql = """
            SELECT DISTINCT p.permission_code
            FROM users u
            JOIN user_roles ur ON u.id = ur.user_id
            JOIN role_permissions rp ON ur.role_id = rp.role_id
            JOIN permissions p ON rp.permission_id = p.id
            WHERE u.olm_id = ?
        """;

        return jdbcTemplateOne.queryForList(sql, String.class, olmId);
    }
}
