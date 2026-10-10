package br.com.ateliedigital;

import org.springframework.boot.SpringApplication;

public class TestAtelieDigitalApplication {

	public static void main(String[] args) {
		SpringApplication.from(AtelieDigitalApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
