package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.DefinicionPresentacionIADTO;
import com.huellago.backend.services.PoliticaAutorizacionEditorialIAService;
import com.huellago.backend.services.PresentacionRecomendacionesIAService;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Decisión humana para uso universitario local de las 30 definiciones V1 revisadas.
 * No acredita publicación, validez científica ni permiso para PUBLICABLE. */
final class RegistroEditorialUniversitarioLocal implements PoliticaAutorizacionEditorialIAService.Registro {
    private static final Map<String, String> HUELLAS = Map.ofEntries(
            Map.entry("TR_PLANIFICAR_DESPLAZAMIENTOS|AGRUPACION", "68ac3e60bc94150cdad8734a486f71094eaca769b7fd2e000ef2280485513977"),
            Map.entry("TR_PLANIFICAR_DESPLAZAMIENTOS|NECESIDAD", "5a63fa0c2cafe7e0c728618efc6fb5ba4c7519bf66995ff84bc10e3d12b3dae8"),
            Map.entry("TR_PLANIFICAR_DESPLAZAMIENTOS|SIN_ENFOQUE", "3b650417e025042186bfa88eac1df05a0d67f417809c3bafa6438f65fe804440"),
            Map.entry("TR_EXPLORAR_ALTERNATIVAS_AUTO|DISPONIBILIDAD", "f05ba61bdcc6a259131fe9072ed5cc114a9b3c78ed68ee3ff4623c6272b1976c"),
            Map.entry("TR_EXPLORAR_ALTERNATIVAS_AUTO|VIABILIDAD", "466e3cc4d75558da1778cbc02f94c250b83b93718f7d82b88c45599aa1ec91ac"),
            Map.entry("TR_EXPLORAR_ALTERNATIVAS_AUTO|SIN_ENFOQUE", "997291bb63f49757d62b36a5016f19662df1421f4998ac1c9cac8cac5d380e4c"),
            Map.entry("TR_REVISAR_MOVILIDAD_ACTIVA|ORGANIZACION", "85d3f8c129992cde6698d292773279d75e85c1b2a3fd46ed531ebbf1413623cf"),
            Map.entry("TR_REVISAR_MOVILIDAD_ACTIVA|VIABILIDAD", "a6e222cab250c20104259ffbf64d742eecfc04f330f06cd993cfc81630aa6a36"),
            Map.entry("TR_REVISAR_MOVILIDAD_ACTIVA|SIN_ENFOQUE", "6c49160965208d3c79e31367a16c7993474edf45b3504012bddc0854adf0004a"),
            Map.entry("EN_REVISAR_USOS|COMPROBAR_NECESIDAD", "ed9f56940b272701d70b5579918abe82939c13f50c8f4fd7cbd810b0d8628139"),
            Map.entry("EN_REVISAR_USOS|IDENTIFICAR_USOS", "54f083cebf7393010c383d459d161b756c6c3ec36649f98042891c3753cc25ae"),
            Map.entry("EN_REVISAR_USOS|SIN_ENFOQUE", "f652f91fd9fe5327faf76d69943f57c26d341a12c558d5e47ffa5996148fba37"),
            Map.entry("EN_REVISAR_REGISTRO_HOGAR|ALCANCE", "477459df66140b166d5c504c4b69d427596e550ad1d9ece922a9cec91ba289fe"),
            Map.entry("EN_REVISAR_REGISTRO_HOGAR|PROCEDENCIA_REGISTRO", "4479aa99990228eae9651341ff121bd6b6b4fb5aeeaac5d1c9c05fcbc6d7ae39"),
            Map.entry("EN_REVISAR_REGISTRO_HOGAR|SIN_ENFOQUE", "e906f1aab844f6664c04235b5958adb894b9983affc8c17fa19348199a2844ca"),
            Map.entry("AL_PLANIFICAR_APROVECHAMIENTO|DISPONIBILIDAD", "36cbf9b312936517260db6f0259da6f959e7df5f7f9e9373035a20d071c08d0a"),
            Map.entry("AL_PLANIFICAR_APROVECHAMIENTO|PLANIFICACION", "2226c3742ad9f3294bfa35e4d1cede9e30c4ffcbcce18076dbe6c976a30ec413"),
            Map.entry("AL_PLANIFICAR_APROVECHAMIENTO|SIN_ENFOQUE", "bac673f8d3e8a18c719c1615586aeac1792ada5d7074831331430994595d5de6"),
            Map.entry("AL_REVISAR_PRESENTACION|DISPONIBILIDAD", "3a228c68261357eb7bf48e8595c38a7e0bbbde428923b7caef78111fa30b3512"),
            Map.entry("AL_REVISAR_PRESENTACION|PRESENTACION", "2eb32a15baddba02278b5b981cee790f5b7d7f7abd965386b01fb2a5f7c4dc1b"),
            Map.entry("AL_REVISAR_PRESENTACION|SIN_ENFOQUE", "1633aa0b146d698d7961cdc48114b3e3be54f7a5cb2ee8ead2e502a4383cfeff"),
            Map.entry("RE_INICIAR_SEPARACION|CONDICIONES_LOCALES", "c367dd2ebfee85d8af5325353759a390d5c64a604fe9bf190c56adb9fbc2b9c8"),
            Map.entry("RE_INICIAR_SEPARACION|SEPARACION", "6b3e97ba53a4148e7ffce1913fa475e184596c20d4cc2edd0653da794bb09fce"),
            Map.entry("RE_INICIAR_SEPARACION|SIN_ENFOQUE", "b9e960249df99574453e685be3539e7f9b0394c6197f227223ff7f284c24b3f2"),
            Map.entry("RE_REVISAR_ENTREGA|CONDICIONES_ENTREGA", "cac6f577532a4d850e29ec55eed0d9d50743d6328e6785136be616556802eae7"),
            Map.entry("RE_REVISAR_ENTREGA|MATERIALES_ADMITIDOS", "7a29e6adc561acfb24f701d9920ad2b06539c7fc0bb5ac51c57e135dabd53caf"),
            Map.entry("RE_REVISAR_ENTREGA|SIN_ENFOQUE", "2b6deec71e28175f669c3e60d710d1e6d7d04277aab9e1473942f4b7221081d9"),
            Map.entry("RE_REVISAR_REUTILIZACION|NECESIDAD", "e96055226559c25f0af4cbad9b61913304e7a519862c2f850079900a06a5984a"),
            Map.entry("RE_REVISAR_REUTILIZACION|REUTILIZACION_VIABLE", "4c4fe16da67464245a0245e33396a2745860bd5a3c2c69f95d04bea008502b09"),
            Map.entry("RE_REVISAR_REUTILIZACION|SIN_ENFOQUE", "d6de1703932a6f3ee0ecfc3cb01ceae7e3394926db857d14240f6e4fee9bfcc8")
    );

    private final List<PoliticaAutorizacionEditorialIAService.Autorizacion> autorizaciones;

    RegistroEditorialUniversitarioLocal(PresentacionRecomendacionesIAService presentador) {
        Objects.requireNonNull(presentador);
        var definiciones = List.copyOf(presentador.definicionesParaRevision());
        var politica = new PoliticaAutorizacionEditorialIAServiceImpl();
        if (HUELLAS.size() != 30 || definiciones.size() != 30
                || definiciones.stream().map(DefinicionPresentacionIADTO::accionId).distinct().count() != 10)
            throw new IllegalStateException("El catálogo editorial local no coincide con las 30 variantes aprobadas");
        var porClave = definiciones.stream().collect(Collectors.toMap(
                d -> d.accionId() + "|" + d.enfoque(), Function.identity(), (a, b) -> {
                    throw new IllegalStateException("Identidad editorial duplicada");
                }));
        if (!porClave.keySet().equals(HUELLAS.keySet()))
            throw new IllegalStateException("Las identidades editoriales locales cambiaron");
        var lista = new ArrayList<PoliticaAutorizacionEditorialIAService.Autorizacion>();
        for (var d : definiciones) {
            String esperada = HUELLAS.get(d.accionId() + "|" + d.enfoque());
            if (!"1".equals(d.versionAccion()) || !"1".equals(d.versionPresentacion())
                    || !esperada.equals(politica.huellaDefinicion(d)))
                throw new IllegalStateException("Una definición editorial local cambió: " + d.accionId() + " / " + d.enfoque());
            lista.add(new PoliticaAutorizacionEditorialIAService.Autorizacion(d.accionId(), d.versionAccion(),
                    d.enfoque(), d.versionPresentacion(), esperada, d.camposContextoPermitidos()));
        }
        autorizaciones = List.copyOf(lista);
    }

    @Override public List<PoliticaAutorizacionEditorialIAService.Autorizacion> consultar() {
        return autorizaciones;
    }
}
