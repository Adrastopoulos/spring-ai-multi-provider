package io.github.adrastopoulos.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Sample application wiring two OpenAI-compatible providers at once.
 *
 * <p>Run with {@code ./gradlew :samples:two-providers:bootRun}. The providers are declared
 * in {@code application.yaml}; no Java configuration is needed to create the beans.
 */
@SpringBootApplication
public class TwoProvidersApplication {

	public static void main(String[] args) {
		SpringApplication.run(TwoProvidersApplication.class, args);
	}

}
