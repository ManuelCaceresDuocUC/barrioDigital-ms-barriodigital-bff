package cl.barriodigital.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "services")
public record ServicesProperties(Service catalog, Service requests, Service report, Service audit, Service rabbit) {

    public record Service(String url) { }

    /** Devuelve la URL base del servicio o null si el nombre no es válido o el servicio no está configurado. */
    public String baseUrlFor(String name) {
        return switch (name) {
            case "catalog" -> urlOf(catalog);
            case "requests" -> urlOf(requests);
            case "report" -> urlOf(report);
            case "audit" -> urlOf(audit);
            case "rabbit" -> urlOf(rabbit);
            default -> null;
        };
    }

    private static String urlOf(Service service) {
        return service == null ? null : service.url();
    }
}
