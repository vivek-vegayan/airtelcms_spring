package com.vegayan.airtelmanagement.globalsettings.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Maps to sp_get_module_dropdown() result:
 *   module_id    INT
 *   module_name  VARCHAR
 */
@Getter
@Setter
public class ModuleModel {
    private Integer moduleId;
    private String  moduleName;
}