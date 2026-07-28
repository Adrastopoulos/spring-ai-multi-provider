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

import org.springframework.ai.openai.OpenAiChatModel;

/**
 * Callback for customizing the {@link OpenAiChatModel.Builder} of a named provider before
 * the model is built.
 *
 * <p>Properties intentionally cover only the common connection settings. This hook exposes
 * the underlying Spring AI builder unchanged, so advanced users keep access to everything
 * the properties do not model — supplying a pre-built {@code OpenAIClient}, registering
 * OkHttp customizers for OAuth2 token injection, swapping the observation registry, and so
 * on. Nothing here hides or wraps the builder.
 *
 * <p>Customizers run for every provider; use the {@code providerName} argument to target
 * one:
 *
 * <pre>{@code
 * @Bean
 * OpenAiCompatChatModelCustomizer groqTimeouts() {
 *     return (providerName, builder) -> {
 *         if ("groq".equals(providerName)) {
 *             builder.httpClientBuilderCustomizer(http -> http.retryOnConnectionFailure(true));
 *         }
 *     };
 * }
 * }</pre>
 *
 * <p>Customizers are applied in {@code @Order} sequence, after this library has applied the
 * property-derived settings, so customizer changes take precedence.
 *
 * @author Adrastopoulos
 */
@FunctionalInterface
public interface OpenAiCompatChatModelCustomizer {

	/**
	 * Customize the builder for the given provider.
	 * @param providerName the configured provider name, which is also its bean name
	 * @param builder the builder about to be built, pre-populated from properties
	 */
	void customize(String providerName, OpenAiChatModel.Builder builder);

}
