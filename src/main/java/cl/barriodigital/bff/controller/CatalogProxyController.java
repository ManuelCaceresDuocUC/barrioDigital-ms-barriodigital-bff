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
@RequestMapping("/api/catalog")
public class CatalogProxyController {

    @Value("${services.catalog.url}")
    private String catalogServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @RequestMapping(value = "/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
    public ResponseEntity<String> proxyCatalogRequest(@RequestBody(required = false) String body,
                                                      HttpMethod method,
                                                      HttpServletRequest request) {
        String path = request.getRequestURI();
        String query = request.getQueryString();
        String targetUrl = catalogServiceUrl + path + (query != null ? "?" + query : "");

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