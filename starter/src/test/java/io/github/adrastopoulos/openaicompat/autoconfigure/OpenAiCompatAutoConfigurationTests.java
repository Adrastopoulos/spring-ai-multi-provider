package io.github.adrastopoulos.openaicompat.autoconfigure;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that named providers become distinct beans resolvable by qualifier, that the
 * reserved {@code default} entry is inherited rather than registered, and that the
 * auto-configuration stays inert when unconfigured.
 */
class OpenAiCompatAutoConfigurationTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(OpenAiCompatAutoConfiguration.class));

	@Test
	void registersOneDistinctBeanPerProvider() {
		this.runner
			.withPropertyValues("openai.compat.providers.cerebras.base-url=https://api.cerebras.ai/v1",
					"openai.compat.providers.cerebras.api-key=test-key",
					"openai.compat.providers.cerebras.model=llama-3.3-70b",
					"openai.compat.providers.groq.base-url=https://api.groq.com/openai/v1",
					"openai.compat.providers.groq.api-key=other-key",
					"openai.compat.providers.groq.model=llama-3.3-70b-versatile")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).hasBean("cerebras");
				assertThat(context).hasBean("groq");

				OpenAiChatModel cerebras = context.getBean("cerebras", OpenAiChatModel.class);
				OpenAiChatModel groq = context.getBean("groq", OpenAiChatModel.class);
				assertThat(cerebras).isNotSameAs(groq);

				assertThat(options(cerebras).getBaseUrl()).isEqualTo("https://api.cerebras.ai/v1");
				assertThat(options(cerebras).getModel()).isEqualTo("llama-3.3-70b");
				assertThat(options(groq).getBaseUrl()).isEqualTo("https://api.groq.com/openai/v1");
				assertThat(options(groq).getModel()).isEqualTo("llama-3.3-70b-versatile");
			});
	}

	@Test
	void resolvesProvidersByQualifier() {
		this.runner.withUserConfiguration(ConsumerConfiguration.class)
			.withPropertyValues("openai.compat.providers.cerebras.base-url=https://api.cerebras.ai/v1",
					"openai.compat.providers.cerebras.api-key=test-key",
					"openai.compat.providers.cerebras.model=llama-3.3-70b",
					"openai.compat.providers.groq.base-url=https://api.groq.com/openai/v1",
					"openai.compat.providers.groq.api-key=other-key",
					"openai.compat.providers.groq.model=llama-3.3-70b-versatile")
			.run(context -> {
				assertThat(context).hasNotFailed();
				Consumer consumer = context.getBean(Consumer.class);
				assertThat(options(consumer.cerebras()).getBaseUrl()).isEqualTo("https://api.cerebras.ai/v1");
				assertThat(options(consumer.groq()).getBaseUrl()).isEqualTo("https://api.groq.com/openai/v1");
			});
	}

	@Test
	void inheritsSettingsFromReservedDefaultEntryWithoutRegisteringIt() {
		this.runner
			.withPropertyValues("openai.compat.providers.default.api-key=shared-key",
					"openai.compat.providers.default.model=shared-model",
					"openai.compat.providers.default.timeout=45s",
					"openai.compat.providers.default.temperature=0.9",
					"openai.compat.providers.inherits.base-url=https://a.example.com/v1",
					"openai.compat.providers.overrides.base-url=https://b.example.com/v1",
					"openai.compat.providers.overrides.model=own-model",
					"openai.compat.providers.overrides.temperature=0.1")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).doesNotHaveBean("default");

				OpenAiChatOptions inherits = options(context.getBean("inherits", OpenAiChatModel.class));
				assertThat(inherits.getModel()).isEqualTo("shared-model");
				assertThat(inherits.getTemperature()).isEqualTo(0.9);
				assertThat(inherits.getTimeout()).isEqualTo(Duration.ofSeconds(45));

				OpenAiChatOptions overrides = options(context.getBean("overrides", OpenAiChatModel.class));
				assertThat(overrides.getModel()).isEqualTo("own-model");
				assertThat(overrides.getTemperature()).isEqualTo(0.1);
				// still inherited, since it was not overridden
				assertThat(overrides.getTimeout()).isEqualTo(Duration.ofSeconds(45));
			});
	}

	@Test
	void providerBeansDoNotMakeByTypeChatModelInjectionAmbiguous() {
		// Simulates Spring AI's own OpenAiChatAutoConfiguration being present: a single
		// ordinary OpenAiChatModel bean, injected by type as ChatClientAutoConfiguration
		// does. Without the fallback marking, two extra candidates would break this.
		this.runner.withUserConfiguration(SpringAiStyleSingleModelConfiguration.class, ByTypeConsumerConfiguration.class)
			.withPropertyValues("openai.compat.providers.cerebras.base-url=https://api.cerebras.ai/v1",
					"openai.compat.providers.cerebras.api-key=test-key",
					"openai.compat.providers.cerebras.model=llama-3.3-70b",
					"openai.compat.providers.groq.base-url=https://api.groq.com/openai/v1",
					"openai.compat.providers.groq.api-key=other-key",
					"openai.compat.providers.groq.model=llama-3.3-70b-versatile")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context.getBeanNamesForType(OpenAiChatModel.class)).hasSize(3);

				// By-type resolution unambiguously picks Spring AI's own bean...
				OpenAiChatModel byType = context.getBean(ByTypeConsumer.class).resolvedByType();
				assertThat(byType).isNotNull();
				assertThat(options(byType).getBaseUrl()).isEqualTo("https://api.openai.com/v1");

				// ...while the named providers stay reachable by qualifier.
				assertThat(options(context.getBean("groq", OpenAiChatModel.class)).getBaseUrl())
					.isEqualTo("https://api.groq.com/openai/v1");
			});
	}

	@Test
	void appliesCustomizersAfterProperties() {
		this.runner.withUserConfiguration(CustomizerConfiguration.class)
			.withPropertyValues("openai.compat.providers.cerebras.base-url=https://api.cerebras.ai/v1",
					"openai.compat.providers.cerebras.api-key=test-key",
					"openai.compat.providers.cerebras.model=llama-3.3-70b",
					"openai.compat.providers.groq.base-url=https://api.groq.com/openai/v1",
					"openai.compat.providers.groq.api-key=other-key",
					"openai.compat.providers.groq.model=llama-3.3-70b-versatile")
			.run(context -> {
				assertThat(context).hasNotFailed();
				// The customizer targets only "groq" and overrides the property value.
				assertThat(options(context.getBean("groq", OpenAiChatModel.class)).getModel())
					.isEqualTo("customized-model");
				assertThat(options(context.getBean("cerebras", OpenAiChatModel.class)).getModel())
					.isEqualTo("llama-3.3-70b");
			});
	}

	@Test
	void marksProviderPrimaryWhenRequested() {
		this.runner
			.withPropertyValues("openai.compat.providers.cerebras.base-url=https://api.cerebras.ai/v1",
					"openai.compat.providers.cerebras.api-key=test-key",
					"openai.compat.providers.cerebras.model=llama-3.3-70b",
					"openai.compat.providers.cerebras.primary=true",
					"openai.compat.providers.groq.base-url=https://api.groq.com/openai/v1",
					"openai.compat.providers.groq.api-key=other-key",
					"openai.compat.providers.groq.model=llama-3.3-70b-versatile")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(options(context.getBean(OpenAiChatModel.class)).getBaseUrl())
					.isEqualTo("https://api.cerebras.ai/v1");
			});
	}

	@Test
	void registersNothingWhenNoProvidersAreConfigured() {
		this.runner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBeanNamesForType(OpenAiChatModel.class)).isEmpty();
		});
	}

	@Test
	void backsOffWhenDisabled() {
		this.runner
			.withPropertyValues("openai.compat.enabled=false",
					"openai.compat.providers.cerebras.base-url=https://api.cerebras.ai/v1",
					"openai.compat.providers.cerebras.api-key=test-key",
					"openai.compat.providers.cerebras.model=llama-3.3-70b")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).doesNotHaveBean("cerebras");
			});
	}

	@Test
	void failsFastWhenProviderIsMissingRequiredSettings() {
		this.runner.withPropertyValues("openai.compat.providers.cerebras.api-key=test-key").run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).hasMessageContaining("openai.compat.providers.cerebras.base-url");
		});
	}

	private static OpenAiChatOptions options(OpenAiChatModel model) {
		return model.getOptions();
	}

	record Consumer(OpenAiChatModel cerebras, OpenAiChatModel groq) {
	}

	@Configuration(proxyBeanMethods = false)
	static class ConsumerConfiguration {

		@Bean
		Consumer consumer(@org.springframework.beans.factory.annotation.Qualifier("cerebras") OpenAiChatModel cerebras,
				@org.springframework.beans.factory.annotation.Qualifier("groq") OpenAiChatModel groq) {
			return new Consumer(cerebras, groq);
		}

	}

	record ByTypeConsumer(@org.jspecify.annotations.Nullable OpenAiChatModel resolvedByType) {
	}

	@Configuration(proxyBeanMethods = false)
	static class ByTypeConsumerConfiguration {

		@Bean
		ByTypeConsumer byTypeConsumer(
				org.springframework.beans.factory.ObjectProvider<OpenAiChatModel> chatModelProvider) {
			return new ByTypeConsumer(chatModelProvider.getIfAvailable());
		}

	}

	/**
	 * Stands in for Spring AI's own {@code OpenAiChatAutoConfiguration}: exactly one
	 * ordinary, non-fallback {@code OpenAiChatModel} bean.
	 */
	@Configuration(proxyBeanMethods = false)
	static class SpringAiStyleSingleModelConfiguration {

		@Bean
		OpenAiChatModel openAiChatModel() {
			return OpenAiChatModel.builder()
				.options(OpenAiChatOptions.builder()
					.baseUrl("https://api.openai.com/v1")
					.apiKey("spring-ai-key")
					.model("gpt-5")
					.build())
				.build();
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class CustomizerConfiguration {

		@Bean
		OpenAiCompatChatModelCustomizer groqCustomizer() {
			return (providerName, builder) -> {
				if ("groq".equals(providerName)) {
					builder.options(OpenAiChatOptions.builder()
						.baseUrl("https://api.groq.com/openai/v1")
						.apiKey("other-key")
						.model("customized-model")
						.build());
				}
			};
		}

	}

}
