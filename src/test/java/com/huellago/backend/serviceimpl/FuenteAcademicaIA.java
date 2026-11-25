package com.huellago.backend.serviceimpl;

import org.springframework.jdbc.datasource.AbstractDataSource;
import java.sql.*;
import java.util.Properties;

/** Ninguna conexion se expone a JPA antes de confirmar base, usuario y privilegios. */
class FuenteAcademicaIA extends AbstractDataSource {
    @FunctionalInterface interface Abrir { Connection conectar(String url,Properties propiedades) throws SQLException; }
    private final String clave;
    private final Abrir abrir;
    FuenteAcademicaIA(String clave) { this(clave,DriverManager::getConnection); }
    FuenteAcademicaIA(String clave,Abrir abrir) {
        if (clave==null || clave.isBlank()) throw new IllegalArgumentException("Falta clave de PostgreSQL academico");
        this.clave=clave; this.abrir=java.util.Objects.requireNonNull(abrir);
    }
    @Override public Connection getConnection() throws SQLException {
        Connection c=null;
        try {
            Properties p=new Properties(); p.setProperty("user",AcademicoLocalIA.ROL); p.setProperty("password",clave);
            p.setProperty("connectTimeout","5"); p.setProperty("socketTimeout","25");
            p.setProperty("ApplicationName","HuellaGo-IA123-local-test");
            p.setProperty("options","-c search_path=public -c lock_timeout=8000 -c statement_timeout=20000");
            c=abrir.conectar(AcademicoLocalIA.URL,p);
            try(var s=c.createStatement(); var r=s.executeQuery("SELECT current_database(), current_user, session_user, "
                    +"EXISTS(SELECT 1 FROM pg_roles WHERE (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls) "
                    +"AND pg_has_role(current_user, oid, 'MEMBER'))")) {
                if(!r.next() || !AcademicoLocalIA.BASE.equals(r.getString(1)) || !AcademicoLocalIA.ROL.equals(r.getString(2))
                        || !AcademicoLocalIA.ROL.equals(r.getString(3)) || r.getBoolean(4))
                    throw new SQLException("Destino o privilegios IA123 rechazados","08004");
            }
            return c;
        } catch(SQLException e) {
            if(c!=null) try { c.close(); } catch(SQLException ignored) { }
            throw new SQLException("No se pudo verificar conexion exclusiva IA123",e.getSQLState(),e.getErrorCode());
        }
    }
    @Override public Connection getConnection(String usuario,String clave) throws SQLException {
        throw new SQLException("Sustitucion de credenciales prohibida","08004");
    }
    @Override public String toString() { return "FuenteAcademicaIA[credenciales omitidas]"; }
}
