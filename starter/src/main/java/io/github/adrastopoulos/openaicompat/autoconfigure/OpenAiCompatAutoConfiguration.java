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
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuration registering one {@link OpenAiChatModel} bean per named provider
 * declared under {@code openai.compat.providers}.
 *
 * <p>Deliberately conditional so it stays inert unless asked for, and so it can coexist
 * with Spring AI's own {@code OpenAiChatAutoConfiguration}:
 *
 * <ul>
 * <li>{@link ConditionalOnClass} on {@link OpenAiChatModel} — no effect without the Spring
 * AI OpenAI module on the classpath.</li>
 * <li>{@link ConditionalOnProperty} on {@code openai.compat.enabled} and on at least one
 * configured provider — no providers configured means no beans and no behaviour change.</li>
 * </ul>
 *
 * <p>Coexistence is by construction rather than by backing off: this library uses its own
 * property namespace, and the registrar marks provider beans non-autowirable by type (see
 * {@link OpenAiCompatChatModelRegistrar}). An application can therefore run Spring AI's
 * single auto-configured {@code OpenAiChatModel} and several named providers side by side
 * without ambiguous-dependency failures.
 *
 * @author Adrastopoulos
 */
@AutoConfiguration
@ConditionalOnClass(OpenAiChatModel.class)
@EnableConfigurationProperties(OpenAiCompatProperties.class)
@ConditionalOnProperty(prefix = OpenAiCompatProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
		matchIfMissing = true)
@Import(OpenAiCompatChatModelRegistrar.class)
public class OpenAiCompatAutoConfiguration {

}
