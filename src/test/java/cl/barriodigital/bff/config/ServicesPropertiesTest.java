package cl.barriodigital.bff.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import cl.barriodigital.bff.config.ServicesProperties.Service;

class ServicesPropertiesTest {

    private final ServicesProperties services = new ServicesProperties(
            new Service("http://catalog:8082"),
            new Service("http://requests:8081"),
            new Service("http://report:8084"),
            new Service("http://audit:8085"),
            new Service("http://rabbitadmin:8086"));

    @Test
    void resuelveLaUrlDeCadaServicio() {
        assertThat(services.baseUrlFor("catalog")).isEqualTo("http://catalog:8082");
        assertThat(services.baseUrlFor("requests")).isEqualTo("http://requests:8081");
        assertThat(services.baseUrlFor("report")).isEqualTo("http://report:8084");
        assertThat(services.baseUrlFor("audit")).isEqualTo("http://audit:8085");
    }

    @Test
    void resuelveElAdministradorDeRabbitMq() {
        assertThat(services.baseUrlFor("rabbit")).isEqualTo("http://rabbitadmin:8086");
    }

    @Test
    void unNombreDesconocidoNoTieneUrl() {
        assertThat(services.baseUrlFor("otro")).isNull();
    }

    @Test
    void unServicioSinConfigurarNoTieneUrlNiLanzaExcepcion() {
        ServicesProperties sinRabbit = new ServicesProperties(null, null, null, null, null);

        assertThat(sinRabbit.baseUrlFor("rabbit")).isNull();
        assertThat(sinRabbit.baseUrlFor("catalog")).isNull();
    }
}
