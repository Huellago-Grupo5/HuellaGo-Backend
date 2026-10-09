package com.huellago.backend.controllers;

import com.huellago.backend.entities.Usuario;
import com.huellago.backend.security.UserSecurity;
import com.huellago.backend.services.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DashboardControllerTest {

    @Test
    void obtieneElDashboardDelUsuarioAutenticadoSinUsuarioIdEnLaRuta() throws Exception {
        DashboardService dashboardService = mock(DashboardService.class);
        DashboardController controller = new DashboardController();
        ReflectionTestUtils.setField(controller, "dashboardService", dashboardService);

        Usuario usuario = new Usuario();
        usuario.setId(7L);
        ResponseEntity<?> response = controller.obtenerDashboard(new UserSecurity(usuario));

        assertEquals(200, response.getStatusCode().value());
        verify(dashboardService).obtenerDashboard(7L);

        Method metodo = DashboardController.class.getDeclaredMethod(
                "obtenerDashboard", UserSecurity.class);
        GetMapping mapping = metodo.getAnnotation(GetMapping.class);
        assertTrue(mapping.value().length == 0);
    }
}
