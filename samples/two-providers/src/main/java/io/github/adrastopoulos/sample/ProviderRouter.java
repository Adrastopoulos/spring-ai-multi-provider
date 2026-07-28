package io.github.adrastopoulos.sample;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Demonstrates the point of the prototype: two providers, declared only in
 * {@code application.yaml}, injected by the names they were configured under.
 */
@Component
public class ProviderRouter {

	private final ChatClient cerebras;

	private final ChatClient localLlama;

	public ProviderRouter(@Qualifier("cerebras") ChatModel cerebras, @Qualifier("localLlama") ChatModel localLlama) {
		this.cerebras = ChatClient.create(cerebras);
		this.localLlama = ChatClient.create(localLlama);
	}

	public String askCerebras(String question) {
		return this.cerebras.prompt(question).call().content();
	}

	public String askLocalLlama(String question) {
		return this.localLlama.prompt(question).call().content();
	}

}
