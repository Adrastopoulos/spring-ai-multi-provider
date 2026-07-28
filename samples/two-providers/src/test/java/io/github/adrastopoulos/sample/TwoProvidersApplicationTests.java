package io.github.adrastopoulos.sample;

import org.junit.jupiter.api.Test;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the sample context loads with two distinct named providers, each resolvable by
 * qualifier. No network calls are made; only bean wiring and configuration are asserted.
 */
@SpringBootTest
class TwoProvidersApplicationTests {

	@Autowired
	private ApplicationContext context;

	@Autowired
	private ProviderRouter router;

	@Autowired
	@Qualifier("cerebras")
	private OpenAiChatModel cerebras;

	@Autowired
	@Qualifier("localLlama")
	private OpenAiChatModel localLlama;

	@Test
	void contextLoadsWithTwoDistinctNamedProviders() {
		assertThat(this.router).isNotNull();
		assertThat(this.context.getBeanNamesForType(OpenAiChatModel.class)).containsExactlyInAnyOrder("cerebras",
				"localLlama");
		assertThat(this.cerebras).isNotSameAs(this.localLlama);
	}

	@Test
	void eachProviderKeepsItsOwnConnectionSettings() {
		assertThat(options(this.cerebras).getBaseUrl()).isEqualTo("https://api.cerebras.ai/v1");
		assertThat(options(this.cerebras).getModel()).isEqualTo("llama-3.3-70b");

		assertThat(options(this.localLlama).getBaseUrl()).isEqualTo("http://localhost:8080/v1");
		assertThat(options(this.localLlama).getModel()).isEqualTo("qwen2.5-7b-instruct");
	}

	@Test
	void inheritsSharedSettingsFromTheReservedDefaultEntry() {
		// timeout and max-retries come from the `default` entry...
		assertThat(options(this.cerebras).getTimeout()).isEqualTo(java.time.Duration.ofSeconds(60));
		assertThat(options(this.localLlama).getTimeout()).isEqualTo(java.time.Duration.ofSeconds(60));

		// ...while temperature is inherited by one and overridden by the other.
		assertThat(options(this.cerebras).getTemperature()).isEqualTo(0.7);
		assertThat(options(this.localLlama).getTemperature()).isEqualTo(0.2);
	}

	@Test
	void reservedDefaultEntryIsNotItselfRegistered() {
		assertThat(this.context.containsBean("default")).isFalse();
	}

	private static OpenAiChatOptions options(OpenAiChatModel model) {
		return model.getOptions();
	}

}
