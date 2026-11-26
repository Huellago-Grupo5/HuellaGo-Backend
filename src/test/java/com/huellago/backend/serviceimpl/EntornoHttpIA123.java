package com.huellago.backend.serviceimpl;

import java.util.Map;

/** Preflight sin JDBC, carga de recursos ni secretos productivos. Solo test classpath. */
final class EntornoHttpIA123 {
    static final String URL="jdbc:postgresql://localhost:5432/huellagodb_ia_integracion_test";
    static final String BASE="huellagodb_ia_integracion_test", ROL="huellago_ia119_test";
    static final String CORREO="ia123@example.invalid";
    static final String ACTIVACION="HUELLAGO_IA123_ACTIVAR";
    static final String RUTA="/__test/ia/recomendaciones/generar", TOKEN="/__test/ia/token";
    static final String DIRECCION="127.0.0.1";
    static final int PUERTO=18081;
    private final String clave;
    private EntornoHttpIA123(String clave) { this.clave=clave; }
    static EntornoHttpIA123 validar(Map<String,String> variables) {
        if(!"true".equals(variables.get(ACTIVACION))) throw new IllegalStateException("Activacion exclusiva IA123 ausente");
        exigir(variables,"HUELLAGO_IA123_PG_URL",URL);
        exigir(variables,"HUELLAGO_IA123_PG_USUARIO",ROL);
        exigir(variables,"HUELLAGO_IA123_CONFIRMAR_BASE",BASE);
        String clave=variables.get("HUELLAGO_IA123_PG_CLAVE");
        if(clave==null || clave.isBlank()) throw new IllegalArgumentException("Falta clave exclusiva IA123 (no se imprime)");
        return new EntornoHttpIA123(clave);
    }
    private static void exigir(Map<String,String> v,String nombre,String esperada) {
        if(!esperada.equals(v.get(nombre))) throw new IllegalArgumentException("Configuracion IA123 rechazada: "+nombre);
    }
    String claveJdbc() { return clave; }
    static Map<String,Object> propiedadesJpa() {
        return Map.of("hibernate.hbm2ddl.auto","validate","hibernate.dialect","org.hibernate.dialect.PostgreSQLDialect",
                "hibernate.default_schema","public","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl",
                "hibernate.show_sql","false","hibernate.format_sql","false","hibernate.cache.use_second_level_cache","false");
    }
    @Override public String toString() { return "EntornoHttpIA123[exclusivo, secreto omitido]"; }
}
