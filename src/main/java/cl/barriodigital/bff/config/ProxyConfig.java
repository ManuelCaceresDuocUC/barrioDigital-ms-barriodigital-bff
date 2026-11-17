package cl.barriodigital.bff.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * QUÉ ES: configuración del cliente HTTP que usa el BFF para llamar a los microservicios.
 *
 * QUÉ HACE: crea un único RestClient (cliente HTTP moderno de Spring) con tiempos máximos de espera,
 * para que un microservicio caído o lento no deje colgado al BFF indefinidamente.
 */
@Configuration
// Registra ServicesProperties como bean para poder inyectarlo (lee el bloque "services:" del yaml).
@EnableConfigurationProperties(ServicesProperties.class)
public class ProxyConfig {

    @Bean
    RestClient proxyRestClient() {
        // Cliente HTTP del JDK: si no logra conectar en 3 segundos, falla.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        // Fábrica que adapta el cliente del JDK a Spring.
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        // Si ya conectó pero el servicio no responde en 30 segundos, falla (el controlador lo traduce a 504).
        factory.setReadTimeout(Duration.ofSeconds(30));
        return RestClient.builder().requestFactory(factory).build();
    }
}
