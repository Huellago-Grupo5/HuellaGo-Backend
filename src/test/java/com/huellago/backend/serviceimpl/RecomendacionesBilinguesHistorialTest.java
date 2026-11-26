package com.huellago.backend.serviceimpl;

import com.huellago.backend.entities.*;
import com.huellago.backend.repositories.UsuarioRecomendacionIARepository;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecomendacionesBilinguesHistorialTest {
    @AfterEach void limpiar() { LocaleContextHolder.resetLocaleContext(); }

    @Test void pendienteYCompletadaCambianIdiomaSinModificarInstantaneasNiLlamarAlModelo() {
        var definicion = new PresentacionRecomendacionesIAServiceImpl().definicionesParaRevision().stream()
                .filter(d -> d.accionId().equals("TR_PLANIFICAR_DESPLAZAMIENTOS")
                        && d.enfoque().equals("AGRUPACION")).findFirst().orElseThrow();
        Usuario usuario = new Usuario(); usuario.setId(70L);
        CategoriaHabito categoria = new CategoriaHabito(); categoria.setNombre("Transporte");
        RecomendacionIA canonica = new RecomendacionIA(); canonica.setId(3); canonica.setCategoria(categoria);
        canonica.setAccionId(definicion.accionId()); canonica.setVersionAccion(definicion.versionAccion());
        var asociacion = new UsuarioRecomendacionIA(); asociacion.setId(22); asociacion.setUsuario(usuario);
        asociacion.setRecomendacionIA(canonica); asociacion.setEnfoque(definicion.enfoque());
        asociacion.setVersionPresentacion(definicion.versionPresentacion());
        asociacion.setMetodoGeneracion("OLLAMA_SELECCION_PRESENTACION_CONTROLADA");
        asociacion.setTituloInstantanea(definicion.titulo());
        asociacion.setDescripcionInstantanea(definicion.descripcion());
        asociacion.setMotivoInstantanea(definicion.motivo().replace("{/transporte/medio}", "Auto")
                .replace("{/transporte/kmSemana}", "100").replace("{/transporte/diasSemana}", "5"));
        asociacion.setPrioridadInstantanea("MEDIA"); asociacion.setFechaGeneracion(LocalDateTime.now());
        var repositorio = mock(UsuarioRecomendacionIARepository.class);
        var servicio = new RecomendacionServiceImpl();
        ReflectionTestUtils.setField(servicio, "usuarioRecomendacionIARepository", repositorio);
        ReflectionTestUtils.setField(servicio, "traducciones", new PresentacionesLocalizadasIA());

        asociacion.setEstado("Pendiente");
        when(repositorio.findByUsuario_IdAndEstadoIgnoreCaseOrderByFechaCompletadoDescIdDesc(70L, "pendiente"))
                .thenReturn(List.of(asociacion));
        LocaleContextHolder.setLocale(Locale.US);
        var ingles = servicio.pendientes(usuario, null).getFirst();
        assertTrue(ingles.getTitulo().startsWith("Check how"));
        assertTrue(ingles.getMotivo().contains("100 km/week"));
        assertEquals("TR_PLANIFICAR_DESPLAZAMIENTOS", ingles.getAccionId());
        LocaleContextHolder.setLocale(Locale.forLanguageTag("es-419"));
        assertEquals(definicion.titulo(), servicio.pendientes(usuario, null).getFirst().getTitulo());
        assertEquals(definicion.titulo(), asociacion.getTituloInstantanea());

        asociacion.setEstado("completado"); asociacion.setFechaCompletado(LocalDateTime.now());
        when(repositorio.findByUsuario_IdAndEstadoIgnoreCaseOrderByFechaCompletadoDescIdDesc(70L, "completado"))
                .thenReturn(List.of(asociacion));
        LocaleContextHolder.setLocale(Locale.US);
        assertTrue(servicio.historialCompletadas(usuario).getFirst().titulo().startsWith("Check how"));
        LocaleContextHolder.setLocale(Locale.forLanguageTag("es-419"));
        assertEquals(definicion.titulo(), servicio.historialCompletadas(usuario).getFirst().titulo());
        verify(repositorio, times(4)).findByUsuario_IdAndEstadoIgnoreCaseOrderByFechaCompletadoDescIdDesc(anyLong(), anyString());
    }

    @Test void registroHeredadoSinIdentidadEditorialConservaSuTexto() {
        UsuarioRecomendacionIA antigua = new UsuarioRecomendacionIA();
        antigua.setMetodoGeneracion("OLLAMA_SELECCION_PRESENTACION_CONTROLADA");
        antigua.setTituloInstantanea("Texto heredado");
        assertTrue(new PresentacionesLocalizadasIA().localizar(antigua).isEmpty());
    }
}
