package com.huellago.backend.dtos;

/** Contenido interno validado; no contiene IDs, prioridad, puntos ni estados persistentes. */
public record RecomendacionGeneradaIADTO(String categoria, String titulo, String descripcion, String motivo) { }
