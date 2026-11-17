package cl.barriodigital.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * QUÉ ES: punto de entrada del BFF (Backend For Frontend).
 *
 * QUÉ HACE: es la "puerta única" del backend. El API Gateway de AWS le envía todas las peticiones del
 * frontend; el BFF valida el JWT de Azure y reenvía cada petición al microservicio que corresponda
 * (catalog, requests, report, audit, rabbitadmin, kafkaadmin). No tiene base de datos propia.
 */
@SpringBootApplication
public class MsBarriodigitalBffApplication {

	public static void main(String[] args) {
		// Arranca Spring y el servidor web embebido (puerto 8080).
		SpringApplication.run(MsBarriodigitalBffApplication.class, args);
	}

}
