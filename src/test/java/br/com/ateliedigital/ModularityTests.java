package br.com.ateliedigital;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Garante as fronteiras do monolito modular: cada modulo so acessa a API publica (pacote raiz) dos demais.
 */
class ModularityTests {

    @Test
    void verifiesModularStructure() {
        ApplicationModules.of(AtelieDigitalApplication.class).verify();
    }
}
