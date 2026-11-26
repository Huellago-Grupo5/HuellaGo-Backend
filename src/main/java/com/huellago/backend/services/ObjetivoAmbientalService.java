package com.huellago.backend.services;
import com.huellago.backend.dtos.*;
import java.util.List;
public interface ObjetivoAmbientalService {
    List<ObjetivoAmbientalDTO> listarDisponibles();
    List<ObjetivoAmbientalDTO> listarSeleccionados(String correo);
    List<ObjetivoAmbientalDTO> actualizarSeleccion(String correo, SeleccionObjetivosAmbientalesDTO seleccion);
}
