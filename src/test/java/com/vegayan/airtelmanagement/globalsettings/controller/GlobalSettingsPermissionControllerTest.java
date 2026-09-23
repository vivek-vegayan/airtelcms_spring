package com.vegayan.airtelmanagement.globalsettings.controller;

import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.globalsettings.service.GlobalSettingsPermissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GlobalSettingsPermissionControllerTest {

    @Mock
    private GlobalSettingsPermissionService permissionService;

    @InjectMocks
    private GlobalSettingsPermissionController controller;

    @Test
    void disableRoleShouldCallServiceWithAuthenticatedUser() throws Exception {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("438");

        ApiResponse expected = ApiResponse.builder()
                .status("SUCCESS")
                .message("Role disabled")
                .build();
        when(permissionService.disableRole(438L, 7)).thenReturn(expected);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(post("/global-settings/permissions/disable-role")
                        .param("roleId", "7")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(permissionService).disableRole(438L, 7);
    }
}
