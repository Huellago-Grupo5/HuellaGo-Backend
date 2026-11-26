package com.huellago.backend.serviceimpl;

import com.huellago.backend.entities.HuellaCarbono;
import com.huellago.backend.entities.Usuario;
import com.huellago.backend.repositories.HuellaCarbonoRepository;
import com.huellago.backend.services.ReporteAmbientalService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ReporteAmbientalServiceImpl implements ReporteAmbientalService {

    private static final String UNIDAD = "kg CO2e";
    private static final String PERIODO = "semanal";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Color VERDE = new Color(25, 105, 78);
    private static final Color VERDE_CLARO = new Color(232, 244, 237);
    private static final Color GRIS = new Color(70, 78, 76);

    @Autowired
    HuellaCarbonoRepository huellaCarbonoRepository;

    @Override
    public byte[] generarReporte(Usuario usuario) {
        List<HuellaCarbono> historial = huellaCarbonoRepository
                .findByUsuario_IdOrderByFechaCalculoDescIdDesc(usuario.getId());
        if (historial.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "El usuario no tiene huellas de carbono registradas para exportar");
        }

        try {
            return crearPdf(usuario, historial, LocalDateTime.now());
        } catch (IOException | RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo generar el reporte ambiental", exception);
        }
    }

    byte[] crearPdf(Usuario usuario, List<HuellaCarbono> historial,
                    LocalDateTime fechaGeneracion) throws IOException {
        HuellaCarbono ultima = historial.getFirst();
        validarHuella(ultima);

        try (PDDocument documento = new PDDocument();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            PDDocumentInformation informacion = new PDDocumentInformation();
            informacion.setTitle("Reporte ambiental HuellaGo");
            informacion.setAuthor("HuellaGo");
            informacion.setSubject("Resumen de huella de carbono y progreso");
            documento.setDocumentInformation(informacion);

            PDPage pagina = new PDPage(PDRectangle.A4);
            documento.addPage(pagina);
            PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont negrita = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            try (PDPageContentStream contenido = new PDPageContentStream(documento, pagina)) {
                float ancho = pagina.getMediaBox().getWidth();
                colorRelleno(contenido, VERDE);
                contenido.addRect(0, 742, ancho, 100);
                contenido.fill();
                escribir(contenido, negrita, 22, Color.WHITE, 55, 795,
                        "Reporte ambiental HuellaGo");
                escribir(contenido, regular, 10, Color.WHITE, 55, 770,
                        "Generado el " + FECHA.format(fechaGeneracion));

                float y = 715;
                y = tituloSeccion(contenido, negrita, "Usuario", y);
                y = dato(contenido, regular, negrita, "Nombre", nombreUsuario(usuario), y);
                y = dato(contenido, regular, negrita, "Identificador", usuario.getId().toString(), y);

                y -= 10;
                y = tituloSeccion(contenido, negrita, "Resumen de la huella más reciente", y);
                y = dato(contenido, regular, negrita, "Huella total", numero(ultima.getCo2Total()) + " " + UNIDAD, y);
                y = dato(contenido, regular, negrita, "Periodo", PERIODO, y);
                y = dato(contenido, regular, negrita, "Fecha del cálculo", FECHA.format(ultima.getFechaCalculo()), y);

                y -= 10;
                y = tituloSeccion(contenido, negrita, "Desglose de emisiones", y);
                y = filaEmision(contenido, regular, negrita, "Transporte", ultima.getCo2Transporte(), y);
                y = filaEmision(contenido, regular, negrita, "Energía", ultima.getCo2Energia(), y);
                y = filaEmision(contenido, regular, negrita, "Alimentación", ultima.getCo2Alimentacion(), y);
                y = filaEmision(contenido, regular, negrita, "Residuos", ultima.getCo2Residuos(), y);

                y -= 12;
                y = tituloSeccion(contenido, negrita, "Progreso", y);
                colorRelleno(contenido, VERDE_CLARO);
                contenido.addRect(55, y - 62, ancho - 110, 72);
                contenido.fill();
                List<String> lineas = ajustarTexto(mensajeProgreso(historial), regular, 10, ancho - 140);
                float textoY = y - 12;
                for (String linea : lineas) {
                    escribir(contenido, regular, 10, GRIS, 70, textoY, linea);
                    textoY -= 15;
                }

                escribir(contenido, regular, 8, GRIS, 55, 35,
                        "Reporte generado con los datos registrados en HuellaGo.");
                escribir(contenido, regular, 8, GRIS, ancho - 95, 35, "Página 1");
            }

            documento.save(salida);
            byte[] pdf = salida.toByteArray();
            if (pdf.length == 0) {
                throw new IOException("El PDF generado está vacío");
            }
            return pdf;
        }
    }

    private float tituloSeccion(PDPageContentStream contenido, PDFont fuente,
                                String titulo, float y) throws IOException {
        escribir(contenido, fuente, 13, VERDE, 55, y, titulo);
        contenido.setStrokingColor(VERDE.getRed() / 255f,
                VERDE.getGreen() / 255f, VERDE.getBlue() / 255f);
        contenido.setLineWidth(0.8f);
        contenido.moveTo(55, y - 7);
        contenido.lineTo(540, y - 7);
        contenido.stroke();
        return y - 27;
    }

    private float dato(PDPageContentStream contenido, PDFont regular, PDFont negrita,
                       String etiqueta, String valor, float y) throws IOException {
        escribir(contenido, negrita, 10, GRIS, 65, y, etiqueta + ":");
        escribir(contenido, regular, 10, Color.DARK_GRAY, 175, y, valor);
        return y - 18;
    }

    private float filaEmision(PDPageContentStream contenido, PDFont regular, PDFont negrita,
                              String categoria, BigDecimal valor, float y) throws IOException {
        escribir(contenido, negrita, 10, GRIS, 65, y, categoria);
        escribir(contenido, regular, 10, Color.DARK_GRAY, 380, y,
                numero(valor) + " " + UNIDAD);
        return y - 18;
    }

    private void escribir(PDPageContentStream contenido, PDFont fuente, float tamano,
                          Color color, float x, float y, String texto) throws IOException {
        contenido.beginText();
        contenido.setFont(fuente, tamano);
        colorRelleno(contenido, color);
        contenido.newLineAtOffset(x, y);
        contenido.showText(texto);
        contenido.endText();
    }

    private void colorRelleno(PDPageContentStream contenido, Color color) throws IOException {
        contenido.setNonStrokingColor(color.getRed() / 255f,
                color.getGreen() / 255f, color.getBlue() / 255f);
    }

    private List<String> ajustarTexto(String texto, PDFont fuente, float tamano,
                                      float anchoMaximo) throws IOException {
        List<String> lineas = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        for (String palabra : texto.split(" ")) {
            String candidato = actual.isEmpty() ? palabra : actual + " " + palabra;
            float ancho = fuente.getStringWidth(candidato) / 1000 * tamano;
            if (ancho > anchoMaximo && !actual.isEmpty()) {
                lineas.add(actual.toString());
                actual = new StringBuilder(palabra);
            } else {
                actual = new StringBuilder(candidato);
            }
        }
        if (!actual.isEmpty()) {
            lineas.add(actual.toString());
        }
        return lineas;
    }

    private String mensajeProgreso(List<HuellaCarbono> historial) {
        if (historial.size() < 2) {
            return "Todavía no hay suficiente historial para determinar el progreso. "
                    + "Se necesitan al menos dos cálculos de huella de carbono.";
        }

        BigDecimal actual = historial.get(0).getCo2Total();
        BigDecimal anterior = historial.get(1).getCo2Total();
        if (actual == null || anterior == null) {
            throw new IllegalStateException("Huella sin total");
        }

        int comparacion = actual.compareTo(anterior);
        if (comparacion == 0) {
            return "Las emisiones se mantuvieron sin cambios respecto al cálculo anterior ("
                    + numero(anterior) + " " + UNIDAD + ").";
        }

        BigDecimal diferencia = actual.subtract(anterior).abs();
        String direccion = comparacion < 0 ? "disminuyeron" : "aumentaron";
        String mensaje = "Las emisiones " + direccion + " en " + numero(diferencia) + " " + UNIDAD;
        if (anterior.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal porcentaje = diferencia.multiply(BigDecimal.valueOf(100))
                    .divide(anterior, 2, RoundingMode.HALF_UP);
            mensaje += " (" + numero(porcentaje) + " %)";
        }
        return mensaje + " respecto al cálculo anterior.";
    }

    private void validarHuella(HuellaCarbono huella) {
        if (huella.getCo2Total() == null || huella.getCo2Transporte() == null
                || huella.getCo2Energia() == null || huella.getCo2Alimentacion() == null
                || huella.getCo2Residuos() == null || huella.getFechaCalculo() == null) {
            throw new IllegalStateException("Huella incompleta");
        }
    }

    private String nombreUsuario(Usuario usuario) {
        String nombres = usuario.getNombres() == null ? "" : usuario.getNombres().trim();
        String apellidos = usuario.getApellidos() == null ? "" : usuario.getApellidos().trim();
        String nombre = (nombres + " " + apellidos).trim();
        return nombre.isEmpty() ? "Usuario HuellaGo" : nombre;
    }

    private String numero(BigDecimal valor) {
        DecimalFormatSymbols simbolos = DecimalFormatSymbols.getInstance(new Locale("es", "PE"));
        DecimalFormat formato = new DecimalFormat("0.00", simbolos);
        formato.setRoundingMode(RoundingMode.HALF_UP);
        return formato.format(valor);
    }
}
