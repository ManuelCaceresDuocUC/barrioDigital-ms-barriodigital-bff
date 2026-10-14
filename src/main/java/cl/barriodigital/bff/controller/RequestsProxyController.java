package cl.barriodigital.bff.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/requests")
public class RequestsProxyController {

    @Value("${services.requests.url}")
    private String requestsServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @RequestMapping(value = "/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
    public ResponseEntity<String> proxyRequestsRequest(@RequestBody(required = false) String body,
                                                       HttpMethod method,
                                                       HttpServletRequest request) {
        String path = request.getRequestURI(); // Ej: "/api/requests/41/status"
        String query = request.getQueryString();

        // 1. Limpiar barras diagonales al final de la URL base
        String baseUrl = requestsServiceUrl.trim();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        // 2. Prevenir la duplicación de "/api/requests" si la propiedad ya la incluye
        if (baseUrl.endsWith("/api/requests") && path.startsWith("/api/requests")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - "/api/requests".length());
        }

        // 3. Construir la URL limpia hacia el microservicio
        String targetUrl = baseUrl + path + (query != null ? "?" + query : "");

        HttpHeaders headers = new HttpHeaders();
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null) {
            headers.set(HttpHeaders.AUTHORIZATION, authHeader);
        }
        if (request.getContentType() != null) {
            headers.set(HttpHeaders.CONTENT_TYPE, request.getContentType());
        }

        HttpEntity<String> httpEntity = new HttpEntity<>(body, headers);
        return restTemplate.exchange(targetUrl, method, httpEntity, String.class);
    }
}