package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.DefinicionPresentacionIADTO;
import com.huellago.backend.entities.UsuarioRecomendacionIA;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/** Traducciones de presentación pendientes de revisión humana. No autorizan generación ni publicación. */
@Component
public final class PresentacionesLocalizadasIA {
    // Huella del conjunto de 50 textos EN en revisión; no es una aprobación editorial.
    private static final String HUELLA_EN_REVISION = "23f098808f01146df4ad62b5fcd1cc6adc0d5f96f0cc7864e210b16c18781e6d";
    public record Textos(String titulo, String descripcion, String motivo) { }
    private record Clave(String accion, String version, String enfoque, String presentacion) { }
    private final Map<Clave, DefinicionPresentacionIADTO> originales;
    private final Map<Clave, DefinicionPresentacionIADTO> traducciones;

    public PresentacionesLocalizadasIA() {
        var fuente = new PresentacionRecomendacionesIAServiceImpl().definicionesParaRevision();
        Properties propiedades = new Properties();
        try (var recurso = Objects.requireNonNull(getClass().getResourceAsStream("/recomendaciones_en_US.properties"));
             var lector = new InputStreamReader(recurso, StandardCharsets.UTF_8)) {
            propiedades.load(lector);
        } catch (Exception e) { throw new IllegalStateException("No se pudo cargar el catálogo de traducciones IA", e); }
        if (!HUELLA_EN_REVISION.equals(huellaPropiedades(propiedades)))
            throw new IllegalStateException("La huella del catálogo inglés en revisión no coincide");
        Map<Clave, DefinicionPresentacionIADTO> es = new HashMap<>(), en = new HashMap<>();
        var politica = new PoliticaAutorizacionEditorialIAServiceImpl();
        for (var original : fuente) {
            var clave = clave(original);
            if (es.put(clave, original) != null) throw new IllegalStateException("Identidad editorial duplicada");
            String prefijo = original.accionId() + ".";
            var traducida = new DefinicionPresentacionIADTO(original.accionId(), original.versionAccion(),
                    original.enfoque(), original.versionPresentacion(),
                    obligatorio(propiedades, prefijo + "title"),
                    obligatorio(propiedades, prefijo + original.enfoque()),
                    obligatorio(propiedades, prefijo + "reason"), original.camposContextoPermitidos());
            if (!variables(original.descripcion()).equals(variables(traducida.descripcion()))
                    || !variables(original.motivo()).equals(variables(traducida.motivo()))
                    || !variables(original.titulo()).equals(variables(traducida.titulo()))
                    || traducida.titulo().length() > 160 || traducida.descripcion().length() > 1000
                    || traducida.motivo().length() > 1000)
                throw new IllegalStateException("Variables o límites de la traducción IA no coinciden: " + clave);
            // Calcula una huella canónica propia del idioma. La huella española y su permiso permanecen intactos.
            politica.huellaDefinicion(traducida);
            en.put(clave, traducida);
        }
        if (es.size() != 30 || en.size() != 30 || es.keySet().stream().map(Clave::accion).distinct().count() != 10
                || propiedades.size() != 50)
            throw new IllegalStateException("El catálogo inglés no contiene las 30 variantes exactas");
        originales = Map.copyOf(es);
        traducciones = Map.copyOf(en);
    }

    public List<DefinicionPresentacionIADTO> definicionesIngles() {
        return traducciones.values().stream().sorted(Comparator.comparing(DefinicionPresentacionIADTO::accionId)
                .thenComparing(DefinicionPresentacionIADTO::enfoque)).toList();
    }

    public Optional<Textos> localizar(UsuarioRecomendacionIA a) {
        if (a == null || a.getRecomendacionIA() == null) return Optional.empty();
        return localizar(a.getRecomendacionIA().getAccionId(), a.getRecomendacionIA().getVersionAccion(),
                a.getEnfoque(), a.getVersionPresentacion(), a.getTituloInstantanea(),
                a.getDescripcionInstantanea(), a.getMotivoInstantanea());
    }

    public Optional<Textos> localizar(String accion, String version, String enfoque, String presentacion,
            String titulo, String descripcion, String motivo) {
        if (!"en".equals(LocaleContextHolder.getLocale().getLanguage()) || accion == null || version == null
                || presentacion == null || titulo == null || descripcion == null || motivo == null) return Optional.empty();
        var clave = new Clave(accion, version, enfoque == null ? "SIN_ENFOQUE" : enfoque, presentacion);
        var es = originales.get(clave);
        var en = traducciones.get(clave);
        if (es == null || en == null || !es.titulo().equals(titulo)) return Optional.empty();
        Map<String, String> valores = new HashMap<>();
        if (!extraer(es.motivo(), motivo, valores) || !extraer(es.descripcion(), descripcion, valores))
            return Optional.empty();
        if (!representar(es.motivo(), valores).equals(motivo)
                || !representar(es.descripcion(), valores).equals(descripcion)) return Optional.empty();
        Map<String, String> ingles = new HashMap<>(valores);
        String reciclaje = ingles.get("/residuos/reciclas");
        if ("sí".equals(reciclaje)) ingles.put("/residuos/reciclas", "yes");
        if ("no".equals(reciclaje)) ingles.put("/residuos/reciclas", "no");
        return Optional.of(new Textos(representar(en.titulo(), ingles), representar(en.descripcion(), ingles),
                representar(en.motivo(), ingles)));
    }

    private static boolean extraer(String plantilla, String contenido, Map<String, String> valores) {
        var tokens = Pattern.compile("\\{(/[^}]+)}").matcher(plantilla);
        var expresion = new StringBuilder("^");
        List<String> nombres = new ArrayList<>();
        int anterior = 0;
        while (tokens.find()) {
            expresion.append(Pattern.quote(plantilla.substring(anterior, tokens.start()))).append("(.+?)");
            nombres.add(tokens.group(1));
            anterior = tokens.end();
        }
        expresion.append(Pattern.quote(plantilla.substring(anterior))).append("$");
        var coincidencia = Pattern.compile(expresion.toString(), Pattern.DOTALL).matcher(contenido);
        if (!coincidencia.matches()) return false;
        for (int i = 0; i < nombres.size(); i++) {
            String previo = valores.putIfAbsent(nombres.get(i), coincidencia.group(i + 1));
            if (previo != null && !previo.equals(coincidencia.group(i + 1))) return false;
        }
        return true;
    }

    private static String representar(String plantilla, Map<String, String> valores) {
        String texto = plantilla;
        for (var valor : valores.entrySet()) texto = texto.replace("{" + valor.getKey() + "}", valor.getValue());
        return texto;
    }

    private static Set<String> variables(String texto) {
        var matcher = Pattern.compile("\\{(/[^}]+)}").matcher(texto);
        Set<String> campos = new HashSet<>();
        while (matcher.find()) campos.add(matcher.group(1));
        return campos;
    }

    private static Clave clave(DefinicionPresentacionIADTO d) {
        return new Clave(d.accionId(), d.versionAccion(), d.enfoque(), d.versionPresentacion());
    }

    private static String obligatorio(Properties p, String clave) {
        String valor = p.getProperty(clave);
        if (valor == null || valor.isBlank()) throw new IllegalStateException("Falta traducción IA: " + clave);
        return valor;
    }

    private static String huellaPropiedades(Properties propiedades) {
        try {
            var resumen = MessageDigest.getInstance("SHA-256");
            for (String clave : new TreeSet<>(propiedades.stringPropertyNames())) {
                resumen.update(clave.getBytes(StandardCharsets.UTF_8)); resumen.update((byte) 0);
                resumen.update(propiedades.getProperty(clave).getBytes(StandardCharsets.UTF_8)); resumen.update((byte) 0);
            }
            return HexFormat.of().formatHex(resumen.digest());
        } catch (Exception e) { throw new IllegalStateException("No se pudo verificar el catálogo inglés", e); }
    }
}
