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

@RestController
public class ApiProxyController {

    private static final String API_PREFIX = "/api/";

    private final RestClient restClient;
    private final ServicesProperties services;

    public ApiProxyController(RestClient proxyRestClient, ServicesProperties services) {
        this.restClient = proxyRestClient;
        this.services = services;
    }

    @RequestMapping(
            value = {"/api/catalog/**", "/api/requests/**", "/api/report/**", "/api/audit/**"},
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                      RequestMethod.PATCH, RequestMethod.DELETE})
    public ResponseEntity<byte[]> proxy(@RequestBody(required = false) byte[] body,
                                        HttpServletRequest request) {
        String path = request.getRequestURI();
        String serviceName = path.substring(API_PREFIX.length()).split("/", 2)[0];
        String baseUrl = services.baseUrlFor(serviceName);
        if (baseUrl == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        String query = request.getQueryString();
        URI target = URI.create(baseUrl + path + (query != null ? "?" + query : ""));
        HttpMethod method = HttpMethod.valueOf(request.getMethod());

        RestClient.RequestBodySpec spec = restClient.method(method)
                .uri(target)
                .headers(h -> copyRequestHeaders(request, h));
        if (body != null && body.length > 0) {
            spec.body(body);
        }

        try {
            // onStatus vacío: los 4xx/5xx del microservicio se devuelven tal cual
            ResponseEntity<byte[]> response = spec.retrieve()
                    .onStatus(status -> true, (req, res) -> { })
                    .toEntity(byte[].class);

            return ResponseEntity.status(response.getStatusCode())
                    .headers(h -> copyResponseHeaders(response.getHeaders(), h))
                    .body(response.getBody());
        } catch (ResourceAccessException ex) {
            boolean timeout = ex.getCause() instanceof SocketTimeoutException
                    || ex.getCause() instanceof HttpTimeoutException;
            throw new ResponseStatusException(
                    timeout ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY,
                    "Servicio '" + serviceName + "' no disponible", ex);
        }
    }

    private void copyRequestHeaders(HttpServletRequest request, HttpHeaders target) {
        for (String name : new String[] {HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, HttpHeaders.ACCEPT}) {
            String value = request.getHeader(name);
            if (value != null) {
                target.set(name, value);
            }
        }
    }

    private void copyResponseHeaders(HttpHeaders source, HttpHeaders target) {
        for (String name : new String[] {HttpHeaders.CONTENT_TYPE, HttpHeaders.CONTENT_DISPOSITION}) {
            String value = source.getFirst(name);
            if (value != null) {
                target.set(name, value);
            }
        }
    }
}