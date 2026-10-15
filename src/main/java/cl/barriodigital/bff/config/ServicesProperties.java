package cl.barriodigital.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "services")
public record ServicesProperties(Service catalog, Service requests, Service report, Service audit) {

    public record Service(String url) { }

    /** Devuelve la URL base del servicio o null si el nombre no es válido. */
    public String baseUrlFor(String name) {
        return switch (name) {
            case "catalog" -> catalog.url();
            case "requests" -> requests.url();
            case "report" -> report.url();
            case "audit" -> audit.url();
            default -> null;
        };
    }
}