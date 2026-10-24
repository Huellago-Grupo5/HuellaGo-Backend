package com.huellago.backend.serviceimpl;

import com.huellago.backend.entities.HuellaCarbono;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.HuellaCarbonoRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

@ExtendWith(MockitoExtension.class)
class ReporteAmbientalServiceImplTest {

    @Mock
    HuellaCarbonoRepository huellaCarbonoRepository;

    @InjectMocks
    ReporteAmbientalServiceImpl service;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setId(22L);
        usuario.setNombres("Carlos");
        usuario.setApellidos("Quispe");
    }

    @Test
    void generaPdfValidoConResumenYProgreso() throws Exception {
        when(huellaCarbonoRepository.findByUsuario_IdOrderByFechaCalculoDescIdDesc(22L))
                .thenReturn(List.of(huella(10, 6, 2, 1, 1, 2),
                        huella(15, 8, 3, 2, 2, 1)));

        byte[] pdf = service.generarReporte(usuario);

        assertTrue(pdf.length > 500);
        assertEquals("%PDF", new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
        String texto = extraerTexto(pdf);
        assertTrue(texto.contains("Reporte ambiental HuellaGo"));
        assertTrue(texto.contains("Carlos Quispe"));
        assertTrue(texto.contains("10.00 kg CO2e"), texto);
        assertTrue(texto.contains("disminuyeron"));
        assertTrue(texto.contains("5.00 kg CO2e"));
    }

    @Test
    void informaHistorialInsuficienteCuandoSoloExisteUnaHuella() throws Exception {
        when(huellaCarbonoRepository.findByUsuario_IdOrderByFechaCalculoDescIdDesc(22L))
                .thenReturn(List.of(huella(10, 6, 2, 1, 1, 1)));

        String texto = extraerTexto(service.generarReporte(usuario));

        assertTrue(texto.contains("no hay suficiente historial para determinar el progreso"));
    }

    @Test
    void usuarioSinHuellasRecibe404Claro() {
        when(huellaCarbonoRepository.findByUsuario_IdOrderByFechaCalculoDescIdDesc(22L))
                .thenReturn(List.of());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.generarReporte(usuario));

        assertEquals(404, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("no tiene huellas"));
    }

    @Test
    void fallaDeGeneracionDevuelve500Controlado() throws Exception {
        when(huellaCarbonoRepository.findByUsuario_IdOrderByFechaCalculoDescIdDesc(22L))
                .thenReturn(List.of(huella(10, 6, 2, 1, 1, 1)));
        ReporteAmbientalServiceImpl servicioConFallo = spy(service);
        doThrow(new IOException("detalle interno"))
                .when(servicioConFallo).crearPdf(any(Usuario.class), anyList(), any(LocalDateTime.class));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> servicioConFallo.generarReporte(usuario));

        assertEquals(500, exception.getStatusCode().value());
        assertEquals("No se pudo generar el reporte ambiental", exception.getReason());
    }

    @Test
    void consultaSolamenteElHistorialDelUsuarioAutenticado() throws Exception {
        when(huellaCarbonoRepository.findByUsuario_IdOrderByFechaCalculoDescIdDesc(22L))
                .thenReturn(List.of(huella(10, 6, 2, 1, 1, 1)));

        String texto = extraerTexto(service.generarReporte(usuario));

        verify(huellaCarbonoRepository).findByUsuario_IdOrderByFechaCalculoDescIdDesc(22L);
        assertTrue(texto.contains("Identificador: 22"));
    }

    private String extraerTexto(byte[] pdf) throws Exception {
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            assertEquals(1, documento.getNumberOfPages());
            return new PDFTextStripper().getText(documento);
        }
    }

    private HuellaCarbono huella(int total, int transporte, int energia,
                                 int alimentacion, int residuos, int diasAtras) {
        HuellaCarbono huella = new HuellaCarbono();
        huella.setUsuario(usuario);
        huella.setCo2Total(BigDecimal.valueOf(total));
        huella.setCo2Transporte(BigDecimal.valueOf(transporte));
        huella.setCo2Energia(BigDecimal.valueOf(energia));
        huella.setCo2Alimentacion(BigDecimal.valueOf(alimentacion));
        huella.setCo2Residuos(BigDecimal.valueOf(residuos));
        huella.setFechaCalculo(LocalDateTime.now().minusDays(diasAtras));
        return huella;
    }
}
