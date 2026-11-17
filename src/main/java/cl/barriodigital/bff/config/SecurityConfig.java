package cl.barriodigital.bff.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * QUÉ ES: configuración de Spring Security del BFF.
 *
 * QUÉ HACE: convierte al BFF en un "resource server" OAuth 2.0: toda petición debe traer un JWT
 * (cabecera Authorization: Bearer ...) emitido por Microsoft Entra ID. Spring descarga las claves públicas
 * del emisor (issuer-uri en application.yaml) y verifica firma, emisor y expiración del token.
 * Este servicio solo AUTENTICA; la autorización por rol la hace cada microservicio.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // La cadena de filtros define las reglas de seguridad HTTP.
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CSRF protege sesiones con cookies; aquí no hay sesión (API con token), se desactiva.
            .csrf(AbstractHttpConfigurer::disable)
            // STATELESS: el servidor no guarda sesión; cada petición se valida por su token.
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Regla explícita para el catálogo (redundante con la siguiente, pero deja constancia).
                .requestMatchers("/api/catalog/**").authenticated()
                // Cualquier otra ruta también exige estar autenticado (JWT válido).
                .anyRequest().authenticated()
            )
            // Activa la validación de JWT con la configuración por defecto (usa issuer-uri del yaml).
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }
}
