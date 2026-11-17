package cl.barriodigital.bff.controller;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import cl.barriodigital.bff.config.ServicesProperties;
import jakarta.servlet.http.HttpServletRequest;

/**
 * QUÉ ES: el único controlador del BFF; un proxy inverso genérico.
 *
 * QUÉ HACE: recibe cualquier petición /api/<servicio>/..., deduce el microservicio destino por el primer
 * segmento de la ruta, reenvía la misma petición (método, ruta, query, cuerpo y token) y devuelve tal cual
 * la respuesta (incluidos los errores 4xx/5xx). Agregar un servicio nuevo = una entrada en ServicesProperties,
 * una URL en el yaml y su patrón en el @RequestMapping.
 */
@RestController
public class ApiProxyController {

    // Prefijo común de todas las rutas; se usa para extraer el nombre del servicio.
    private static final String API_PREFIX = "/api/";

    private final RestClient restClient;
    private final ServicesProperties services;

    // Inyección por constructor: Spring entrega el RestClient (ProxyConfig) y las URLs (ServicesProperties).
    public ApiProxyController(RestClient proxyRestClient, ServicesProperties services) {
        this.restClient = proxyRestClient;
        this.services = services;
    }

    // Un solo método atiende todas las rutas y todos los métodos HTTP permitidos.
    @RequestMapping(
            value = {"/api/catalog/**", "/api/requests/**", "/api/report/**", "/api/audit/**", "/api/rabbit/**",
                      "/api/kafka/**"},
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                      RequestMethod.PATCH, RequestMethod.DELETE})
    public ResponseEntity<byte[]> proxy(@RequestBody(required = false) byte[] body,
                                        HttpServletRequest request) {
        // Ejemplo: path = /api/catalog/procedures -> serviceName = "catalog".
        String path = request.getRequestURI();
        String serviceName = path.substring(API_PREFIX.length()).split("/", 2)[0];
        String baseUrl = services.baseUrlFor(serviceName);
        if (baseUrl == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        // URL destino = URL base del servicio + misma ruta + misma query string (?page=0&size=20...).
        String query = request.getQueryString();
        URI target = URI.create(baseUrl + path + (query != null ? "?" + query : ""));
        HttpMethod method = HttpMethod.valueOf(request.getMethod());

        // Se arma la petición saliente con el mismo método y algunas cabeceras (ver copyRequestHeaders).
        RestClient.RequestBodySpec spec = restClient.method(method)
                .uri(target)
                .headers(h -> copyRequestHeaders(request, h));
        // Solo se adjunta cuerpo si venía uno (GET y DELETE normalmente no traen).
        if (body != null && body.length > 0) {
            spec.body(body);
        }

        try {
            // onStatus vacío: los 4xx/5xx del microservicio se devuelven tal cual
            // (sin esto, RestClient lanzaría excepción y el cliente perdería el código real).
            ResponseEntity<byte[]> response = spec.retrieve()
                    .onStatus(status -> true, (req, res) -> { })
                    .toEntity(byte[].class);

            // Se devuelve el mismo código, algunas cabeceras y el cuerpo en bruto (bytes).
            return ResponseEntity.status(response.getStatusCode())
                    .headers(h -> copyResponseHeaders(response.getHeaders(), h))
                    .body(response.getBody());
        } catch (ResourceAccessException ex) {
            // No se pudo conectar o venció el tiempo: 504 si fue por tiempo, 502 si el servicio no responde.
            boolean timeout = ex.getCause() instanceof SocketTimeoutException
                    || ex.getCause() instanceof HttpTimeoutException;
            throw new ResponseStatusException(
                    timeout ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY,
                    "Servicio '" + serviceName + "' no disponible", ex);
        }
    }

    // Copia solo las cabeceras necesarias hacia el microservicio. Authorization lleva el JWT: así el
    // microservicio puede volver a validarlo y leer los roles.
    private void copyRequestHeaders(HttpServletRequest request, HttpHeaders target) {
        for (String name : new String[] {HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, HttpHeaders.ACCEPT}) {
            String value = request.getHeader(name);
            if (value != null) {
                target.set(name, value);
            }
        }
    }

    // Copia de vuelta solo el tipo de contenido y, si existe, el nombre de archivo (descarga de certificados).
    private void copyResponseHeaders(HttpHeaders source, HttpHeaders target) {
        for (String name : new String[] {HttpHeaders.CONTENT_TYPE, HttpHeaders.CONTENT_DISPOSITION}) {
            String value = source.getFirst(name);
            if (value != null) {
                target.set(name, value);
            }
        }
    }
}
