package com.eventbook.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
		loadDotEnv();
		SpringApplication.run(BackendApplication.class, args);
	}

	private static void loadDotEnv() {
		Path envFile = Path.of(".env");
		if (!Files.exists(envFile)) {
			return;
		}

		Properties props = new Properties();
		try (var reader = Files.newBufferedReader(envFile)) {
			props.load(reader);
		} catch (IOException e) {
			throw new IllegalStateException("Não foi possível ler o arquivo .env", e);
		}

		props.forEach((key, value) -> {
			String k = (String) key;
			if (System.getProperty(k) == null && System.getenv(k) == null) {
				System.setProperty(k, (String) value);
			}
		});
	}
}