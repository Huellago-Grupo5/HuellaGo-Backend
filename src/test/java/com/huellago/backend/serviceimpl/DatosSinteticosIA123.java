package com.huellago.backend.serviceimpl;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.io.Console;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Preparacion MANUAL futura, nunca bean ni inicializador. No ejecutada en PASO123.
 * No limpia automaticamente: conservar generacion hasta inspeccion y autorizacion posterior. */
public final class DatosSinteticosIA123 {
    private DatosSinteticosIA123() { }
    public static void main(String[] args) throws Exception {
        if(args.length!=1 || !"preparar".equals(args[0])) throw new IllegalArgumentException("Solo modo preparar explicito");
        var entorno=EntornoHttpIA123.validar(System.getenv());
        if(!"PREPARAR_USUARIO_SINTETICO_IA123".equals(System.getenv("HUELLAGO_IA123_CONFIRMAR_PREPARACION")))
            throw new IllegalStateException("Preparacion no confirmada");
        Path manifiesto=Path.of("target/paso123/ids-sinteticos.properties");
        if(Files.exists(manifiesto)) throw new IllegalStateException("Ya existe manifiesto; no sobrescribir ni regenerar");
        Console consola=System.console();
        if(consola==null) throw new IllegalStateException("Se requiere terminal interactiva para la clave sintetica");
        char[] clave=consola.readPassword("Clave NUEVA del usuario sintetico (minimo 12 caracteres): ");
        if(clave==null || clave.length<12) { if(clave!=null) Arrays.fill(clave,'\0'); throw new IllegalArgumentException("Clave sintetica insuficiente"); }
        String hash;
        try { hash=new BCryptPasswordEncoder().encode(new String(clave)); } finally { Arrays.fill(clave,'\0'); }
        boolean archivoCreado=false;
        try(var c=new FuenteHttpIA123(entorno).getConnection()) {
            c.setAutoCommit(false);
            try {
                try(var p=c.prepareStatement("SELECT count(*) FROM public.usuarios WHERE lower(correo)=?")) {
                    p.setString(1,EntornoHttpIA123.CORREO);
                    try(var r=p.executeQuery()) { r.next(); if(r.getLong(1)!=0) throw new IllegalStateException("Usuario sintetico ya existe; no sobrescribir"); }
                }
                long categoria; boolean categoriaCreada=false;
                try(var s=c.createStatement();var r=s.executeQuery("SELECT id FROM public.categorias_habito WHERE lower(nombre)='transporte'")) {
                    if(r.next()) { categoria=r.getLong(1); if(r.next()) throw new IllegalStateException("Categoria Transporte ambigua"); }
                    else categoria=0;
                }
                if(categoria==0) { categoria=insertar(c,"INSERT INTO public.categorias_habito(nombre,descripcion) VALUES ('Transporte','Fixture manual IA123') RETURNING id"); categoriaCreada=true; }
                long usuario=insertar(c,"INSERT INTO public.usuarios(nombres,apellidos,correo,contrasena,eco_puntos,nivel,notificaciones_activas,visibilidad_comunidad,fecha_creacion,fecha_actualizacion) "
                        +"VALUES ('Sintetico','IA123',?,?,0,1,false,false,localtimestamp,localtimestamp) RETURNING id",EntornoHttpIA123.CORREO,hash);
                List<Long> habitos=new ArrayList<>();
                String[] nombres={"medio","kmSemana","diasSemana"},valores={"0","100","5"},unidades={"Auto","km/semana","dias/semana"};
                for(int i=0;i<3;i++) habitos.add(insertar(c,"INSERT INTO public.habitos(nombre,valor,unidad,fecha_registro,fecha_actualizacion,usuario_id,categoria_id) "
                        +"VALUES (?,?,?,localtimestamp,localtimestamp,?,?) RETURNING id",nombres[i],new java.math.BigDecimal(valores[i]),unidades[i],usuario,categoria));
                String texto="# IDs sinteticos IA123. No contiene contrasena, hash ni JWT.\nusuarioId="+usuario+"\ncategoriaId="+categoria
                        +"\ncategoriaCreada="+categoriaCreada+"\nhabitos="+habitos.get(0)+","+habitos.get(1)+","+habitos.get(2)+"\n";
                Files.createDirectories(manifiesto.getParent()); Files.writeString(manifiesto,texto,StandardOpenOption.CREATE_NEW); archivoCreado=true;
                c.commit(); System.out.println("Fixture sintetico preparado; IDs en target/paso123/ids-sinteticos.properties. Conservar para verificar y limpiar.");
            } catch(Exception e) {
                try { c.rollback(); } catch(SQLException ignored) { }
                // No borrar manifiesto si el resultado del COMMIT es incierto: permite investigarlo.
                if(archivoCreado) System.err.println("Resultado no confirmado; conservar manifiesto y revisar antes de reintentar.");
                throw new IllegalStateException("Preparacion IA123 no confirmada; revisar sin reintento automatico");
            }
        }
    }
    private static long insertar(Connection c,String sql,Object... valores) throws SQLException {
        try(var p=c.prepareStatement(sql)) {
            for(int i=0;i<valores.length;i++) p.setObject(i+1,valores[i]);
            try(var r=p.executeQuery()) { if(!r.next()) throw new SQLException("INSERT no devolvio identidad"); return r.getLong(1); }
        }
    }
}
