package cl.barriodigital.bff.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.sun.net.httpserver.HttpServer;

import cl.barriodigital.bff.config.SecurityConfig;
import cl.barriodigital.bff.config.ServicesProperties;
import cl.barriodigital.bff.config.ServicesProperties.Service;

/**
 * El BFF reenvía /api/rabbit/** al administrador de RabbitMQ. El microservicio se simula con el
 * servidor HTTP del JDK, así que no hace falta ningún servicio ni broker real.
 */
@SpringJUnitWebConfig(classes = RabbitProxyRoutingTest.TestConfig.class)
class RabbitProxyRoutingTest {

    private static final String TOKEN = "token-de-prueba";

    private static HttpServer downstream;

    // Lo que recibió el "microservicio" y lo que responde
    private static volatile String receivedMethod;
    private static volatile String receivedPath;
    private static volatile String receivedQuery;
    private static volatile String receivedAuthorization;
    private static volatile String receivedBody;
    private static volatile int responseStatus;
    private static volatile String responseBody;

    @BeforeAll
    static void startDownstream() throws IOException {
        downstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        downstream.createContext("/", exchange -> {
            receivedMethod = exchange.getRequestMethod();
            receivedPath = exchange.getRequestURI().getRawPath();
            receivedQuery = exchange.getRequestURI().getRawQuery();
            receivedAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            receivedBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        downstream.start();
    }

    @AfterAll
    static void stopDownstream() {
        downstream.stop(0);
    }

    @Configuration
    @EnableWebMvc
    @Import({ SecurityConfig.class, ApiProxyController.class })
    static class TestConfig {

        // Acepta solo el token de prueba: así el header Authorization pasa por el filtro Bearer real.
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                if (!TOKEN.equals(token)) {
                    throw new BadJwtException("token inválido");
                }
                return Jwt.withTokenValue(token).header("alg", "none").claim("sub", "usuario-de-prueba").build();
            };
        }

        @Bean
        RestClient proxyRestClient() {
            return RestClient.create();
        }

        @Bean
        ServicesProperties servicesProperties() {
            String url = "http://127.0.0.1:" + downstream.getAddress().getPort();
            return new ServicesProperties(null, null, null, null, new Service(url));
        }
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        receivedMethod = null;
        receivedPath = null;
        receivedQuery = null;
        receivedAuthorization = null;
        receivedBody = null;
        responseStatus = 200;
        responseBody = "[]";
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void reenviaLaRutaYElTokenAlAdministrador() throws Exception {
        responseBody = "[{\"name\":\"q.cmd.email\"}]";

        mockMvc.perform(get("/api/rabbit/queues")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"name\":\"q.cmd.email\"}]"));

        assertThat(receivedMethod).isEqualTo("GET");
        assertThat(receivedPath).isEqualTo("/api/rabbit/queues");
        assertThat(receivedAuthorization).isEqualTo("Bearer " + TOKEN);
    }

    @Test
    void reenviaElCuerpoJsonYDevuelveElEstadoDelServicio() throws Exception {
        responseStatus = 201;
        responseBody = "{\"name\":\"ex.prueba\"}";

        mockMvc.perform(post("/api/rabbit/exchanges")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ex.prueba\",\"type\":\"TOPIC\"}"))
                .andExpect(status().isCreated())
                .andExpect(content().json("{\"name\":\"ex.prueba\"}"));

        assertThat(receivedMethod).isEqualTo("POST");
        assertThat(receivedBody).isEqualTo("{\"name\":\"ex.prueba\",\"type\":\"TOPIC\"}");
    }

    @Test
    void conservaLosParametrosSinVolverAEscaparElNumeral() throws Exception {
        responseStatus = 204;
        responseBody = "";

        mockMvc.perform(delete(URI.create("/api/rabbit/bindings?exchange=ex.a&queue=q.a&routingKey=prueba.%23"))
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNoContent());

        assertThat(receivedMethod).isEqualTo("DELETE");
        assertThat(receivedPath).isEqualTo("/api/rabbit/bindings");
        assertThat(receivedQuery).isEqualTo("exchange=ex.a&queue=q.a&routingKey=prueba.%23");
    }

    @Test
    void devuelveTalCualLosErroresDelAdministrador() throws Exception {
        responseStatus = 403;
        responseBody = "{\"status\":403,\"message\":\"Se requiere el rol Admin para administrar RabbitMQ\"}";

        mockMvc.perform(get("/api/rabbit/queues")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(content().json(responseBody));
    }

    @Test
    void sinTokenElBffRechazaYNoLlamaAlAdministrador() throws Exception {
        mockMvc.perform(get("/api/rabbit/queues")).andExpect(status().isUnauthorized());

        assertThat(receivedMethod).isNull();
    }
}
