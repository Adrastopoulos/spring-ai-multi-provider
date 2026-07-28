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

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for named OpenAI-compatible chat providers.
 *
 * <p>Each entry under {@code openai.compat.providers} becomes its own
 * {@link org.springframework.ai.openai.OpenAiChatModel} bean, registered under the entry's
 * key so it can be injected with {@code @Qualifier("<name>")}.
 *
 * <pre>{@code
 * openai.compat.providers:
 *   default:  { api-key: ${SHARED_KEY} }          # inherited by the entries below
 *   cerebras: { base-url: https://api.cerebras.ai/v1, model: llama-3.3-70b }
 *   groq:     { base-url: https://api.groq.com/openai/v1, model: llama-3.3-70b-versatile }
 * }</pre>
 *
 * <p>The {@value #DEFAULT_PROVIDER_NAME} key is reserved: it does not produce a bean of its
 * own, and every field it sets is inherited by all other entries unless they override it.
 * This mirrors the convention used by {@code spring-cloud-openfeign}'s
 * {@code FeignClientProperties}, and is this prototype's answer to the open question raised
 * in <a href="https://github.com/spring-projects/spring-ai/issues/3518">spring-ai#3518</a>
 * about co-locating default and per-instance configuration.
 *
 * @author Adrastopoulos
 */
@ConfigurationProperties(OpenAiCompatProperties.CONFIG_PREFIX)
public class OpenAiCompatProperties {

	/**
	 * Property prefix for named OpenAI-compatible providers.
	 */
	public static final String CONFIG_PREFIX = "openai.compat";

	/**
	 * Reserved provider name whose settings are inherited by every other provider. No bean
	 * is registered for this key.
	 */
	public static final String DEFAULT_PROVIDER_NAME = "default";

	/**
	 * Whether to register a chat model bean per configured provider.
	 */
	private boolean enabled = true;

	/**
	 * Named OpenAI-compatible providers, keyed by the bean name they are registered under.
	 */
	private Map<String, Provider> providers = new LinkedHashMap<>();

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public Map<String, Provider> getProviders() {
		return this.providers;
	}

	public void setProviders(Map<String, Provider> providers) {
		this.providers = providers;
	}

	/**
	 * Connection and model settings for a single OpenAI-compatible provider.
	 */
	public static class Provider {

		/**
		 * Base URL of the provider's OpenAI-compatible API, including any version path
		 * segment the provider expects (for example {@code https://api.groq.com/openai/v1}).
		 */
		private @Nullable String baseUrl;

		/**
		 * API key sent as a bearer token. An explicit empty string selects Spring AI's
		 * no-auth mode, which suits local servers such as llama.cpp or vLLM.
		 */
		private @Nullable String apiKey;

		/**
		 * Default model id used for completions against this provider.
		 */
		private @Nullable String model;

		/**
		 * Sampling temperature applied by default to this provider.
		 */
		private @Nullable Double temperature;

		/**
		 * Maximum number of tokens to generate by default.
		 */
		private @Nullable Integer maxTokens;

		/**
		 * Organization id, for providers that honour it.
		 */
		private @Nullable String organizationId;

		/**
		 * Request timeout.
		 */
		private @Nullable Duration timeout;

		/**
		 * Maximum number of retries performed by the underlying OpenAI client.
		 */
		private @Nullable Integer maxRetries;

		/**
		 * Extra headers sent with every request to this provider.
		 */
		private Map<String, String> customHeaders = new LinkedHashMap<>();

		/**
		 * Whether this provider's bean should be the primary {@code OpenAiChatModel}
		 * candidate. At most one provider may set this.
		 */
		private boolean primary;

		public @Nullable String getBaseUrl() {
			return this.baseUrl;
		}

		public void setBaseUrl(@Nullable String baseUrl) {
			this.baseUrl = baseUrl;
		}

		public @Nullable String getApiKey() {
			return this.apiKey;
		}

		public void setApiKey(@Nullable String apiKey) {
			this.apiKey = apiKey;
		}

		public @Nullable String getModel() {
			return this.model;
		}

		public void setModel(@Nullable String model) {
			this.model = model;
		}

		public @Nullable Double getTemperature() {
			return this.temperature;
		}

		public void setTemperature(@Nullable Double temperature) {
			this.temperature = temperature;
		}

		public @Nullable Integer getMaxTokens() {
			return this.maxTokens;
		}

		public void setMaxTokens(@Nullable Integer maxTokens) {
			this.maxTokens = maxTokens;
		}

		public @Nullable String getOrganizationId() {
			return this.organizationId;
		}

		public void setOrganizationId(@Nullable String organizationId) {
			this.organizationId = organizationId;
		}

		public @Nullable Duration getTimeout() {
			return this.timeout;
		}

		public void setTimeout(@Nullable Duration timeout) {
			this.timeout = timeout;
		}

		public @Nullable Integer getMaxRetries() {
			return this.maxRetries;
		}

		public void setMaxRetries(@Nullable Integer maxRetries) {
			this.maxRetries = maxRetries;
		}

		public Map<String, String> getCustomHeaders() {
			return this.customHeaders;
		}

		public void setCustomHeaders(Map<String, String> customHeaders) {
			this.customHeaders = customHeaders;
		}

		public boolean isPrimary() {
			return this.primary;
		}

		public void setPrimary(boolean primary) {
			this.primary = primary;
		}

		/**
		 * Returns a copy of this provider with any unset field taken from {@code defaults}.
		 * Values already set on this instance always win.
		 * @param defaults the reserved {@code default} entry, may be {@code null}
		 * @return the merged provider
		 */
		Provider withDefaults(@Nullable Provider defaults) {
			if (defaults == null) {
				return this;
			}
			var merged = new Provider();
			merged.baseUrl = this.baseUrl != null ? this.baseUrl : defaults.baseUrl;
			// An explicit empty api-key is a meaningful no-auth signal, so only fall back
			// when this provider left the key entirely unset.
			merged.apiKey = this.apiKey != null ? this.apiKey : defaults.apiKey;
			merged.model = this.model != null ? this.model : defaults.model;
			merged.temperature = this.temperature != null ? this.temperature : defaults.temperature;
			merged.maxTokens = this.maxTokens != null ? this.maxTokens : defaults.maxTokens;
			merged.organizationId = this.organizationId != null ? this.organizationId : defaults.organizationId;
			merged.timeout = this.timeout != null ? this.timeout : defaults.timeout;
			merged.maxRetries = this.maxRetries != null ? this.maxRetries : defaults.maxRetries;
			merged.customHeaders = !this.customHeaders.isEmpty() ? this.customHeaders : defaults.customHeaders;
			merged.primary = this.primary;
			return merged;
		}

	}

}
