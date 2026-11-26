package com.huellago.backend.security;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.media.StringSchema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    public static final String JWT = "bearerAuth";

    @Bean
    public OpenAPI huellaGoOpenApi() {
        return new OpenAPI()
                .info(new Info().title("HuellaGo API").version("0.0.1-SNAPSHOT")
                        .description("API REST para el seguimiento de la huella de carbono, hábitos sostenibles, "
                                + "recomendaciones inteligentes, retos, comunidad y gestión institucional. "
                                + "Proyecto universitario; las magnitudes ambientales son estimaciones orientativas."))
                .components(new Components().addSecuritySchemes(JWT,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP)
                                .scheme("bearer").bearerFormat("JWT")));
    }

    @Bean
    public OpenApiCustomizer respuestasHttpDocumentadas() {
        return api -> api.getPaths().forEach((ruta, item) ->
                item.readOperationsMap().forEach((metodo, operacion) -> {
                    operacion.addParametersItem(new Parameter().in("header").name("Accept-Language")
                            .required(false).description("Idioma de los mensajes: es-419 (predeterminado) o en-US; también se aceptan es y en.")
                            .schema(new StringSchema()._default("es-419")).example("en-US"));
                    var respuestas = operacion.getResponses();
                    if (!ruta.equals("/auth/login") && !ruta.equals("/auth/register")
                            && !ruta.equals("/auth/recuperar-contrasena")
                            && !ruta.equals("/auth/restablecer-contrasena")) {
                        respuestas.addApiResponse("401", new ApiResponse().description("JWT ausente o inválido"));
                    }
                    if (ruta.equals("/auth/login")) {
                        respuestas.addApiResponse("401", new ApiResponse().description("Credenciales inválidas"));
                    }
                    if (ruta.equals("/usuarios/me/recomendaciones/generar")) {
                        respuestas.addApiResponse("409", new ApiResponse().description("Conflicto de contexto o recomendación pendiente"));
                        respuestas.addApiResponse("503", new ApiResponse().description("Ollama no disponible o inferencia fallida"));
                    }
                    if (ruta.matches("/usuarios/me/recomendaciones/\\{id}/(completar|convertir-en-reto)")) {
                        respuestas.addApiResponse("404", new ApiResponse().description("Asociación inexistente o ajena"));
                        respuestas.addApiResponse("409", new ApiResponse().description("Recomendación ya completada o convertida"));
                    }
                    boolean creado = metodo.name().equals("POST") && (
                            ruta.equals("/auth/register") || ruta.equals("/instituciones")
                                    || ruta.equals("/comunidad/publicaciones")
                                    || ruta.startsWith("/habitos/")
                                    || ruta.matches("/instituciones/\\{institucionId}/campanas")
                                    || ruta.matches("/instituciones/\\{id}/grupos")
                                    || ruta.matches("/usuarios/me/recomendaciones/\\{id}/convertir-en-reto")
                                    || ruta.matches("/retos/\\{id}/aceptar")
                                    || ruta.matches("/retos/colectivos/\\{id}/unirse"));
                    boolean sinContenido = metodo.name().equals("DELETE") && (
                            ruta.equals("/sesiones/actual") || ruta.equals("/instituciones/{id}/grupos/{grupoId}"))
                            || metodo.name().equals("POST") && ruta.equals("/auth/restablecer-contrasena");
                    if (creado || sinContenido) {
                        var exito = respuestas.remove("200");
                        respuestas.addApiResponse(creado ? "201" : "204", sinContenido
                                ? new ApiResponse().description("Operación completada sin contenido")
                                : exito == null ? new ApiResponse().description("Recurso creado")
                                        : exito.description("Recurso creado"));
                    }
                }));
    }
}
