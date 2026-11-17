package cl.barriodigital.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * QUÉ ES: un "record" (clase inmutable) que enlaza el bloque "services:" de application.yaml.
 *
 * QUÉ HACE: guarda la URL base de cada microservicio al que el BFF puede reenviar peticiones.
 * Cada URL se puede sobrescribir con variables de entorno (ver application.yaml y el compose de infra).
 */
@ConfigurationProperties(prefix = "services")
public record ServicesProperties(Service catalog, Service requests, Service report, Service audit, Service rabbit,
                                 Service kafka) {

    // Cada servicio tiene solo una propiedad: su URL (services.catalog.url, etc.).
    public record Service(String url) { }

    /** Devuelve la URL base del servicio o null si el nombre no es válido o el servicio no está configurado. */
    public String baseUrlFor(String name) {
        // "name" es el segmento de la ruta tras /api/ (por ejemplo "catalog" en /api/catalog/procedures).
        return switch (name) {
            case "catalog" -> urlOf(catalog);
            case "requests" -> urlOf(requests);
            case "report" -> urlOf(report);
            case "audit" -> urlOf(audit);
            case "rabbit" -> urlOf(rabbit);
            case "kafka" -> urlOf(kafka);
            default -> null;   // Nombre desconocido: el controlador responderá 404.
        };
    }

    // Evita NullPointerException si el servicio no está configurado.
    private static String urlOf(Service service) {
        return service == null ? null : service.url();
    }
}
