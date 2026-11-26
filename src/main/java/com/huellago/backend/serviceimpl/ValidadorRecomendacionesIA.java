package com.huellago.backend.serviceimpl;

import com.huellago.backend.dtos.ContextoRecomendacionesIADTO;
import com.huellago.backend.dtos.RecomendacionGeneradaIADTO;
import com.huellago.backend.exceptions.OllamaClientException;
import tools.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.*;
import static com.huellago.backend.exceptions.OllamaClientException.Tipo.*;

/** Solo validación y selección de datos; no construye acciones ni recomendaciones alternativas. */
final class ValidadorRecomendacionesIA {
    private ValidadorRecomendacionesIA() { }

    static Map<String, Object> contexto(ContextoRecomendacionesIADTO c) {
        if (c == null) throw entrada();
        Map<String, Object> datos = new LinkedHashMap<>();
        if (c.transporte() != null) {
            var t = c.transporte();
            if (t.kmSemana() == null || t.kmSemana().signum() <= 0 || t.diasSemana() == null
                    || t.diasSemana() < 1 || t.diasSemana() > 7) throw entrada();
            datos.put("Transporte", Map.of("medio", opcion(t.medio(), "Bus", "Auto", "Moto", "Bicicleta", "Caminando"),
                    "kmSemana", t.kmSemana(), "diasSemana", t.diasSemana()));
        }
        if (c.energia() != null) {
            var e = c.energia();
            if (e.personas() == null || e.personas() < 1) throw entrada();
            String fuente = opcion(e.fuente(), "Electricidad", "Gas", "Solar");
            Map<String, Object> energia = new LinkedHashMap<>();
            energia.put("vivienda", opcion(e.vivienda(), "Casa", "Departamento"));
            energia.put("personas", e.personas()); energia.put("fuente", fuente);
            if (e.consumoKwhDiario() != null) {
                if (e.consumoKwhDiario().compareTo(BigDecimal.ZERO) < 0 || !"Electricidad".equals(fuente)) throw entrada();
                energia.put("consumoKwhDiario", e.consumoKwhDiario());
            }
            datos.put("Energía", energia);
        }
        if (c.alimentacion() != null)
            datos.put("Alimentación", Map.of("tipo", opcion(c.alimentacion().tipo(), "Vegana", "Vegetariana", "Mixta")));
        if (c.residuos() != null) {
            var r = c.residuos();
            if (r.reciclas() == null) throw entrada();
            datos.put("Residuos", Map.of("plasticos", opcion(r.plasticos(), "Bajo", "Medio", "Alto"), "reciclas", r.reciclas()));
        }
        if (datos.isEmpty()) throw entrada();
        return datos;
    }

    private static String opcion(String valor, String... opciones) {
        if (valor == null) throw entrada();
        return Arrays.stream(opciones).filter(o -> o.equalsIgnoreCase(valor.strip())).findFirst().orElseThrow(ValidadorRecomendacionesIA::entrada);
    }

    static Map<String, Object> esquema(Set<String> categorias) {
        var propiedades = Map.of("categoria", Map.of("type", "string", "enum", List.copyOf(categorias)),
                "titulo", textoEsquema(160), "descripcion", textoEsquema(1000), "motivo", textoEsquema(1000));
        var item = Map.of("type", "object", "additionalProperties", false,
                "required", List.of("categoria", "titulo", "descripcion", "motivo"), "properties", propiedades);
        var lista = Map.of("type", "array", "minItems", categorias.size(), "maxItems", categorias.size(), "items", item);
        return Map.of("type", "object", "additionalProperties", false, "required", List.of("recomendaciones"),
                "properties", Map.of("recomendaciones", lista));
    }

    private static Map<String, Object> textoEsquema(int limite) {
        return Map.of("type", "string", "minLength", 1, "maxLength", limite);
    }

    static List<RecomendacionGeneradaIADTO> respuesta(JsonNode raiz, Set<String> categorias) {
        if (raiz == null || !raiz.isObject() || raiz.size() != 1 || !raiz.has("recomendaciones")) throw respuestaInvalida();
        var lista = raiz.get("recomendaciones");
        if (!lista.isArray() || lista.size() != categorias.size()) throw respuestaInvalida();
        Map<String, RecomendacionGeneradaIADTO> porCategoria = new HashMap<>();
        for (JsonNode r : lista) {
            if (!r.isObject() || r.size() != 4) throw respuestaInvalida();
            String categoria = texto(r.get("categoria"), 32);
            if (!categorias.contains(categoria) || porCategoria.containsKey(categoria)) throw respuestaInvalida();
            porCategoria.put(categoria, new RecomendacionGeneradaIADTO(categoria, texto(r.get("titulo"), 160),
                    texto(r.get("descripcion"), 1000), texto(r.get("motivo"), 1000)));
        }
        return categorias.stream().map(porCategoria::get).toList();
    }

    static String texto(JsonNode nodo, int limite) {
        if (nodo == null || !nodo.isString()) throw respuestaInvalida();
        String texto = nodo.asString();
        if (texto.isBlank() || texto.length() > limite
                || texto.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t'))
            throw respuestaInvalida();
        return texto.strip();
    }

    static OllamaClientException respuestaInvalida() {
        return new OllamaClientException(RESPUESTA_INVALIDA, "Ollama devolvió una respuesta inválida o incompleta");
    }

    static OllamaClientException entrada() {
        return new OllamaClientException(CONTEXTO_INVALIDO, "El contexto ambiental sintético está vacío o contiene datos inválidos");
    }
}
