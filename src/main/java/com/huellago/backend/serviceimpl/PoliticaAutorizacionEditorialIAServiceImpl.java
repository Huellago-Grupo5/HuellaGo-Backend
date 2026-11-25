package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.*;
import com.huellago.backend.services.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import static com.huellago.backend.services.PoliticaAutorizacionEditorialIAService.MotivoExclusion.*;

/** Sin registro Spring ni entrada HTTP. El constructor predeterminado siempre usa registro vacío.
 * Inyectar un Registro es una decisión confiable de composición, no una aprobación de estos borradores. */
public final class PoliticaAutorizacionEditorialIAServiceImpl implements PoliticaAutorizacionEditorialIAService {
    private final CatalogoAccionesAmbientalesService catalogo;
    private final PresentacionRecomendacionesIAService presentador;
    private final Registro registro;
    private record Clave(String accion, String version, String enfoque) { }

    public PoliticaAutorizacionEditorialIAServiceImpl() {
        this(new CatalogoAccionesAmbientalesServiceImpl(), new PresentacionRecomendacionesIAServiceImpl(), List::of);
    }
    public PoliticaAutorizacionEditorialIAServiceImpl(CatalogoAccionesAmbientalesService catalogo,
            PresentacionRecomendacionesIAService presentador, Registro registro) {
        this.catalogo = Objects.requireNonNull(catalogo);
        this.presentador = Objects.requireNonNull(presentador);
        this.registro = Objects.requireNonNull(registro);
    }

    @Override public Resultado evaluar(ContextoRecomendacionesIADTO contexto,
            Set<AccionAmbientalAdmisible.Referencia> realizadas) {
        ValidadorRecomendacionesIA.contexto(contexto);
        var compatibles = List.copyOf(catalogo.compatibles(contexto, Set.copyOf(realizadas)));
        var definiciones = List.copyOf(presentador.definicionesParaRevision());
        var permisos = registro.consultar();
        Map<AccionAmbientalAdmisible.Referencia, MotivoExclusion> excluidas = new LinkedHashMap<>();
        List<CatalogoAccionesAmbientalesService.Candidata> elegibles = new ArrayList<>();
        boolean registroValido = permisos != null;
        Set<Clave> claves = new HashSet<>();
        Set<AccionAmbientalAdmisible.Referencia> identidades = new HashSet<>();
        catalogo.listarBorradores().forEach(c -> identidades.add(new AccionAmbientalAdmisible.Referencia(c.definicion().id(), c.definicion().version())));
        if (registroValido) for (var p : permisos) {
            if (p == null || p.accionId() == null || p.versionAccion() == null || p.enfoque() == null
                    || p.versionPresentacion() == null || p.sha256() == null || !p.sha256().matches("[0-9a-f]{64}")
                    || !identidades.contains(new AccionAmbientalAdmisible.Referencia(p.accionId(), p.versionAccion()))
                    || !claves.add(new Clave(p.accionId(), p.versionAccion(), p.enfoque()))) {
                registroValido = false; break;
            }
        }
        // Instantánea inmutable del registro: ningún permiso se incorpora desde el contexto ambiental.
        if (registroValido) permisos = List.copyOf(permisos);
        for (var candidata : compatibles) {
            var a = candidata.definicion(); var ref = new AccionAmbientalAdmisible.Referencia(a.id(), a.version());
            var motivo = registroValido ? verificarFamilia(a, definiciones, permisos) : REGISTRO_INVALIDO;
            if (motivo == null) elegibles.add(candidata); else excluidas.put(ref, motivo);
        }
        return new Resultado(compatibles, elegibles, excluidas);
    }

    private MotivoExclusion verificarFamilia(AccionAmbientalAdmisible accion,
            List<DefinicionPresentacionIADTO> definiciones, List<Autorizacion> permisos) {
        var parametro = accion.parametros().get("enfoque");
        if (parametro == null || parametro.tipo() != RecomendacionFundamentadaIADTO.TipoValor.TEXTO
                || parametro.valoresPermitidos().size() != 2) return FAMILIA_NO_VERIFICABLE;
        Set<String> enfoques = new HashSet<>(Set.of("SIN_ENFOQUE"));
        for (var v : parametro.valoresPermitidos()) {
            if (!(v instanceof RecomendacionFundamentadaIADTO.Texto t) || !enfoques.add(t.valor())) return FAMILIA_NO_VERIFICABLE;
        }
        var familia = definiciones.stream().filter(d -> accion.id().equals(d.accionId()) && accion.version().equals(d.versionAccion())).toList();
        if (familia.size() != 3 || !enfoques.equals(familia.stream().map(DefinicionPresentacionIADTO::enfoque).collect(java.util.stream.Collectors.toSet())))
            return FAMILIA_NO_VERIFICABLE;
        var autorizaciones = permisos.stream().filter(p -> accion.id().equals(p.accionId()) && accion.version().equals(p.versionAccion())).toList();
        if (autorizaciones.isEmpty()) return SIN_AUTORIZACION;
        if (autorizaciones.size() != 3 || !enfoques.equals(autorizaciones.stream().map(Autorizacion::enfoque).collect(java.util.stream.Collectors.toSet())))
            return FAMILIA_INCOMPLETA;
        for (var d : familia) {
            var p = autorizaciones.stream().filter(v -> v.enfoque().equals(d.enfoque())).findFirst().orElseThrow();
            if (!p.versionPresentacion().equals(d.versionPresentacion())) return AUTORIZACION_OBSOLETA;
            if (!p.camposContextoPermitidos().equals(d.camposContextoPermitidos()) || !p.sha256().equals(huellaDefinicion(d)))
                return DEFINICION_NO_COINCIDE;
        }
        return null;
    }

    @Override public String huellaDefinicion(DefinicionPresentacionIADTO d) {
        Objects.requireNonNull(d);
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) {
                // Formato canónico V1: UTF-8 exacto, longitudes explícitas y conjunto de campos ordenado.
                for (String s : List.of("HUELLAGO_EDITORIAL_V1", d.accionId(), d.versionAccion(), d.enfoque(),
                        d.versionPresentacion(), d.titulo(), d.descripcion(), d.motivo())) escribir(out, s);
                out.writeInt(d.camposContextoPermitidos().size());
                for (String campo : new TreeSet<>(d.camposContextoPermitidos())) escribir(out, campo);
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("No se pudo calcular la huella editorial", e);
        }
    }
    private static void escribir(DataOutputStream out, String texto) throws IOException {
        byte[] bytes = texto.getBytes(StandardCharsets.UTF_8); out.writeInt(bytes.length); out.write(bytes);
    }
}
