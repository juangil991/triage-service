package com.vidacruz.triage.domain.service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

public class AdministrativeComplaintMatcher {

    private static final List<String> KEYWORDS = List.of(
            "cita", "agendar", "programar cita", "sistema de citas",
            "difficulties scheduling", "scheduling a medical appointment", "booking system",
            "tiempo de espera", "espera administrativa", "registro administrativo",
            "excessive waiting times", "administrative registration",
            "verificar seguro", "verificacion de seguro", "cobertura del seguro",
            "verifying their health insurance", "insurance coverage",
            "factura", "cobro", "cargo incorrecto",
            "incorrect charge", "medical bill",
            "expediente faltante", "historial medico", "registros medicos faltantes",
            "medical records are missing", "missing medical records",
            "actualizar datos", "corregir datos", "informacion personal",
            "inaccurate personal information", "medical profile",
            "autorizacion", "autorizar procedimiento",
            "obtaining authorization", "medical procedure",
            "atencion al cliente", "personal administrativo", "trato poco profesional",
            "unprofessional", "unhelpful treatment", "administrative staff",
            "confirmacion de cita", "confirmar cita",
            "did not receive confirmation", "appointment confirmation",
            "autorizacion de medicamento", "medicamento prescrito",
            "authorizing prescribed medication", "prescribed medication",
            "certificado medico", "documento administrativo", "emision de documentos",
            "medical certificates", "administrative documents",
            "factura duplicada", "cobro duplicado", "facturas duplicadas",
            "duplicate invoices", "same medical service",
            "fecha de cita", "fecha incorrecta de cita", "fecha confirmada",
            "appointment date displayed", "confirmed date",
            "actualizar seguro", "proveedor de seguro", "informacion de seguro",
            "update of their insurance provider", "insurance provider information",
            "documentos", "recoleccion de resultados", "renovacion de recetario", "vacunacion", "seguimiento",
            "seguimiento de queja", "queja previa", "reclamo sin respuesta",
            "previously submitted administrative complaint", "complaint has not received a response");

    public boolean matches(String complaint) {
        String normalized = normalize(complaint);
        return KEYWORDS.stream().anyMatch(normalized::contains);
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }
}
