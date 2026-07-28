/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.adrastopoulos.openaicompat.autoconfigure;

import java.util.LinkedHashMap;
import java.util.Map;

import io.micrometer.observation.ObservationRegistry;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.BeanRegistrar;
import org.springframework.beans.factory.BeanRegistry;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Registers one {@link OpenAiChatModel} bean per entry under
 * {@code openai.compat.providers}, using each entry's key as the bean name.
 *
 * <p>This uses Spring Framework 7's programmatic bean registration, which is the first
 * primitive that makes property-driven registration of N beans of the same type feasible.
 * Two consequences of that API shape are worth noting, because they drove this design and
 * are the same ones reported on
 * <a href="https://github.com/spring-projects/spring-ai/issues/3518">spring-ai#3518</a>:
 *
 * <ol>
 * <li>Registration runs before {@code @ConfigurationProperties} beans exist, so properties
 * are bound manually from the {@link Environment} with a {@link Binder} rather than
 * injected.</li>
 * <li>{@link BeanRegistry.SupplierContext} is only available inside a single bean's
 * supplier, so collaborators such as the {@link ObservationRegistry} are looked up lazily
 * there, at instantiation time.</li>
 * </ol>
 *
 * <p>Beans are registered as <em>fallback</em> candidates. Spring AI's
 * {@code ChatClientAutoConfiguration} still injects a single {@code ChatModel} by type, and
 * the PR to make it back off when several exist
 * (<a href="https://github.com/spring-projects/spring-ai/pull/3429">spring-ai#3429</a>) is
 * unmerged. Registering N ordinary candidates would therefore make that injection ambiguous
 * and break any application that also uses Spring AI's own OpenAI starter. As fallbacks,
 * these beans stay fully resolvable by name and by {@code @Qualifier}, but yield to any
 * regular {@code ChatModel} bean during by-type resolution — so Spring AI's own
 * auto-configured model still wins unambiguously. A provider may opt into being the
 * {@code primary} by-type candidate instead.
 *
 * @author Adrastopoulos
 */
class OpenAiCompatChatModelRegistrar implements BeanRegistrar {

	@Override
	public void register(BeanRegistry registry, Environment environment) {
		OpenAiCompatProperties properties = Binder.get(environment)
			.bind(OpenAiCompatProperties.CONFIG_PREFIX, Bindable.of(OpenAiCompatProperties.class))
			.orElseGet(OpenAiCompatProperties::new);

		if (!properties.isEnabled()) {
			return;
		}

		Map<String, OpenAiCompatProperties.Provider> resolved = resolveProviders(properties);
		resolved.forEach((name, provider) -> registry.registerBean(name, OpenAiChatModel.class, spec -> {
			spec.description("OpenAI-compatible chat model for provider '%s'".formatted(name));
			// Resolvable by @Qualifier(name), but a fallback for by-type injection so
			// Spring AI's own single-ChatModel autoconfiguration keeps working alongside.
			if (provider.isPrimary()) {
				spec.primary();
			}
			else {
				spec.fallback();
			}
			spec.supplier(context -> buildChatModel(name, provider, context));
		}));
	}

	/**
	 * Applies the reserved {@code default} entry to every other provider and drops it from
	 * the result, since it is a template rather than a provider.
	 */
	private static Map<String, OpenAiCompatProperties.Provider> resolveProviders(OpenAiCompatProperties properties) {
		Map<String, OpenAiCompatProperties.Provider> providers = properties.getProviders();
		OpenAiCompatProperties.Provider defaults = providers.get(OpenAiCompatProperties.DEFAULT_PROVIDER_NAME);

		var resolved = new LinkedHashMap<String, OpenAiCompatProperties.Provider>();
		String primaryProvider = null;

		for (Map.Entry<String, OpenAiCompatProperties.Provider> entry : providers.entrySet()) {
			String name = entry.getKey();
			if (OpenAiCompatProperties.DEFAULT_PROVIDER_NAME.equals(name)) {
				continue;
			}
			OpenAiCompatProperties.Provider provider = entry.getValue().withDefaults(defaults);
			Assert.state(StringUtils.hasText(provider.getBaseUrl()),
					() -> "Provider '%s' must define %s.providers.%s.base-url"
						.formatted(name, OpenAiCompatProperties.CONFIG_PREFIX, name));
			Assert.state(StringUtils.hasText(provider.getModel()),
					() -> "Provider '%s' must define %s.providers.%s.model"
						.formatted(name, OpenAiCompatProperties.CONFIG_PREFIX, name));
			if (provider.isPrimary()) {
				Assert.state(primaryProvider == null, "Providers '%s' and '%s' are both marked primary; at most one may be"
					.formatted(primaryProvider, name));
				primaryProvider = name;
			}
			resolved.put(name, provider);
		}
		return resolved;
	}

	private static OpenAiChatModel buildChatModel(String name, OpenAiCompatProperties.Provider provider,
			BeanRegistry.SupplierContext context) {

		ObservationRegistry observationRegistry = context.beanProvider(ObservationRegistry.class)
			.getIfUnique(() -> ObservationRegistry.NOOP);

		OpenAiChatModel.Builder builder = OpenAiChatModel.builder()
			.options(toOptions(provider))
			.observationRegistry(observationRegistry);

		// Applied last so user customizations win over property-derived settings.
		context.beanProvider(OpenAiCompatChatModelCustomizer.class)
			.orderedStream()
			.forEach(customizer -> customizer.customize(name, builder));

		return builder.build();
	}

	/**
	 * Maps provider properties onto {@link OpenAiChatOptions}. As of Spring AI 2.0 the
	 * options object carries the connection settings, and
	 * {@code OpenAiChatModel.Builder.build()} constructs the sync and async
	 * {@code OpenAIClient}s from them when none are supplied explicitly.
	 */
	private static OpenAiChatOptions toOptions(OpenAiCompatProperties.Provider provider) {
		OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
			.baseUrl(provider.getBaseUrl())
			.model(provider.getModel());

		applyIfNonNull(provider.getApiKey(), options::apiKey);
		applyIfNonNull(provider.getOrganizationId(), options::organizationId);
		applyIfNonNull(provider.getTemperature(), options::temperature);
		applyIfNonNull(provider.getMaxTokens(), options::maxTokens);
		applyIfNonNull(provider.getTimeout(), options::timeout);
		applyIfNonNull(provider.getMaxRetries(), options::maxRetries);
		if (!provider.getCustomHeaders().isEmpty()) {
			options.customHeaders(provider.getCustomHeaders());
		}
		return options.build();
	}

	private static <T> void applyIfNonNull(@Nullable T value, java.util.function.Consumer<T> setter) {
		if (value != null) {
			setter.accept(value);
		}
	}

}
