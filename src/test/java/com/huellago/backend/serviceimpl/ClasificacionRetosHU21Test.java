package com.huellago.backend.serviceimpl;

import com.huellago.backend.entities.*;
import com.huellago.backend.repositories.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClasificacionRetosHU21Test {
    @Mock RetoRepository retos;
    @Mock UsuarioRetoRepository participaciones;
    @InjectMocks RetoServiceImpl servicio;

    private Reto reto(int id, int dias) {
        var r=new Reto(); r.setId(id); r.setActivo(true);
        r.setFechaInicio(LocalDate.now()); r.setFechaFin(LocalDate.now().plusDays(dias));
        return r;
    }

    @Test void distingueDuracionesExactasYConservaHistoricosAmbiguos() {
        var diario=reto(1,0); var semanal=reto(2,6); var ambiguo=reto(3,7);
        when(retos.findByActivoTrueOrderByFechaInicioAsc()).thenReturn(List.of(diario,semanal,ambiguo));
        assertEquals(List.of("DIARIO","SEMANAL","SIN_CLASIFICACION"),servicio.listarRetosDisponibles(70L,null)
                .stream().map(x->x.getClasificacion()).toList());
        assertEquals(List.of(2),servicio.listarRetosDisponibles(70L,"semanal")
                .stream().map(x->x.getId()).toList());
        assertEquals(List.of(3),servicio.listarRetosDisponibles(70L,"SIN_CLASIFICACION")
                .stream().map(x->x.getId()).toList());
    }

    @Test void personalizadoSoloSeDevuelveAlPropietarioYColectivoUsaSuRuta() {
        var personal=reto(4,0); var origen=new UsuarioRecomendacionIA(); var propio=new Usuario(); propio.setId(70L);
        origen.setUsuario(propio); personal.setRecomendacionOrigen(origen);
        var p=new UsuarioReto(); p.setUsuario(propio); p.setReto(personal); p.setEstado("activo");
        when(participaciones.findByUsuario_IdAndEstadoIgnoreCaseOrderByFechaAceptacionDesc(70L,"activo"))
                .thenReturn(List.of(p));
        assertEquals(List.of(4),servicio.listarRetosDisponibles(70L,"PERSONALIZADO")
                .stream().map(x->x.getId()).toList());
        var ajeno=new Usuario(); ajeno.setId(71L); origen.setUsuario(ajeno);
        assertTrue(servicio.listarRetosDisponibles(70L,"PERSONALIZADO").isEmpty());
    }
}
