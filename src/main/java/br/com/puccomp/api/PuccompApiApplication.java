package br.com.puccomp.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@Modulithic(sharedModules = {"shared", "config"})
@SpringBootApplication
public class PuccompApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(PuccompApiApplication.class, args);
	}

}
