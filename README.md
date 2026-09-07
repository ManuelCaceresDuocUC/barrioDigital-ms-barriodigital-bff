# Proyecto: BarrioDigital - BFF

**Componente:** Backend For Frontend (BFF) / API Gateway Interno
**Integrantes:** Manuel Cáceres Marín

## Descripción
Este componente actúa como intermediario seguro entre la aplicación frontend y los microservicios de dominio. Se sitúa detrás del AWS API Gateway y su responsabilidad principal es recibir las peticiones, validar el token JWT emitido por Azure AD y enrutar de manera segura las llamadas hacia los microservicios internos (requests, catalog, report, audit), validando los roles de usuario.

## Tecnologías a usar
* **Framework:** Spring Boot (Java)
* **Seguridad:** Spring Security (Validación JWT - OAuth2 Resource Server)
* **Arquitectura:** Patrón BFF (Backend For Frontend)
