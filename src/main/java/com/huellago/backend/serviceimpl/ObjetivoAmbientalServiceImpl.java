package com.huellago.backend.serviceimpl;
import com.huellago.backend.dtos.*;
import com.huellago.backend.entities.*;
import com.huellago.backend.repositories.*;
import com.huellago.backend.services.ObjetivoAmbientalService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.stream.Collectors;
@Service
public class ObjetivoAmbientalServiceImpl implements ObjetivoAmbientalService {
    private final ObjetivoAmbientalRepository objetivos;
    private final UsuarioObjetivoAmbientalRepository selecciones;
    private final UsuarioRepository usuarios;

    public ObjetivoAmbientalServiceImpl(ObjetivoAmbientalRepository objetivos, UsuarioObjetivoAmbientalRepository selecciones, UsuarioRepository usuarios) {
        this.objetivos = objetivos; this.selecciones = selecciones; this.usuarios = usuarios;
    }
    public List<ObjetivoAmbientalDTO> listarDisponibles() { return objetivos.findAll().stream().map(this::dto).toList(); }
    public List<ObjetivoAmbientalDTO> listarSeleccionados(String correo) {
        Usuario usuario = usuario(correo);
        return selecciones.findByUsuarioId(usuario.getId()).stream().map(UsuarioObjetivoAmbiental::getObjetivo).map(this::dto).toList();
    }
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public List<ObjetivoAmbientalDTO> actualizarSeleccion(String correo, SeleccionObjetivosAmbientalesDTO seleccion) {
        if (seleccion == null || seleccion.getObjetivoIds() == null || seleccion.getObjetivoIds().isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debes seleccionar al menos un objetivo ambiental.");
        Usuario usuario = usuario(correo);
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(seleccion.getObjetivoIds()));
        if (ids.stream().anyMatch(id -> id == null || id <= 0))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los identificadores de objetivos deben ser positivos");
        List<ObjetivoAmbiental> encontrados = objetivos.findAllById(ids);
        Map<Long, ObjetivoAmbiental> porId = encontrados.stream().collect(Collectors.toMap(ObjetivoAmbiental::getId, o -> o));
        if (encontrados.size() != ids.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Uno o más objetivos ambientales seleccionados no existen.");
        }

        Set<Long> seleccionDeseada = new LinkedHashSet<>(ids);
        // El bloqueo dura hasta commit/rollback y precede a la lectura de las asociaciones.
        usuarios.bloquearParaRecomendaciones(usuario.getId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario autenticado no encontrado"));
        List<UsuarioObjetivoAmbiental> actuales = selecciones.findByUsuarioId(usuario.getId());
        Set<Long> actualesIds = actuales.stream()
                .map(asociacion -> asociacion.getObjetivo().getId())
                .collect(Collectors.toSet());

        List<UsuarioObjetivoAmbiental> obsoletas = actuales.stream()
                .filter(asociacion -> !seleccionDeseada.contains(asociacion.getObjetivo().getId()))
                .toList();
        if (!obsoletas.isEmpty()) {
            selecciones.deleteAll(obsoletas);
            // Garantiza que las asociaciones obsoletas se eliminen antes de insertar nuevas.
            selecciones.flush();
        }

        List<UsuarioObjetivoAmbiental> nuevas = ids.stream()
                .filter(id -> !actualesIds.contains(id))
                .map(porId::get)
                .map(o -> new UsuarioObjetivoAmbiental(null, usuario, o))
                .toList();
        if (!nuevas.isEmpty()) {
            selecciones.saveAll(nuevas);
        }
        return ids.stream().map(porId::get).map(this::dto).toList();
    }
    private Usuario usuario(String correo) {
        Usuario u = usuarios.findByCorreo(correo);
        if (u == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario autenticado no encontrado");
        return u;
    }
    private ObjetivoAmbientalDTO dto(ObjetivoAmbiental o) { return new ObjetivoAmbientalDTO(o.getId(), o.getNombre(), o.getDescripcion()); }
}
