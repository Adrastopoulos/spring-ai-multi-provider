# spring-ai-multi-provider

Auto-configures several named OpenAI-compatible `ChatModel` beans from properties, so Cerebras, Groq,
vLLM and a local llama.cpp can all run in one Spring Boot application, each injectable by `@Qualifier`.
Built against Spring AI 2.0.0 on Spring Boot 4.1.0. Requires JDK 25.

**Status: research prototype, not a released library.** The property namespace is not stable and you should not depend on it in production.
Findings are meant to feed back into [spring-ai#3518](https://github.com/spring-projects/spring-ai/issues/3518) and [spring-ai#2610](https://github.com/spring-projects/spring-ai/issues/2610); if upstream ships this, the repo should be deleted, not maintained.

## The gap

Spring AI's `OpenAiChatAutoConfiguration` creates exactly one `OpenAiChatModel`, guarded by
`@ConditionalOnMissingBean`, from one flat set of connection properties. Running several providers
today means hand-writing a `@Bean` per provider, moving config out of `application.yaml` into Java.
If you only need one provider, use `spring-ai-starter-model-openai` and stop reading here.

## Configuration

```yaml
openai:
  compat:
    providers:
      cerebras:
        base-url: https://api.cerebras.ai/v1
        api-key: ${CEREBRAS_API_KEY}
        model: llama-3.3-70b
      localLlama:
        base-url: http://localhost:8080/v1
        api-key: ""          # explicit empty key selects no-auth, for local servers
        model: qwen2.5-7b-instruct
```

Each key under `providers` becomes an `OpenAiChatModel` bean registered under that exact name, so
`@Qualifier("localLlama") ChatModel` resolves. Missing `base-url` or `model` fails fast at startup.

## Running the sample

```shell
./gradlew build                          # compiles and runs all 13 tests
./gradlew :samples:two-providers:bootRun # two providers at once, no credentials needed
```

## Consuming it

Not published to Maven Central: add `includeBuild("/path/to/spring-ai-multi-provider")` to your
`settings.gradle.kts` and depend on `io.github.adrastopoulos:starter:0.1.0-SNAPSHOT`.

## Notes

- Prior art, with links and dates: [docs/prior-art.md](docs/prior-art.md). `starter/` holds the auto-configuration; `samples/two-providers/` is the runnable app.
- The `openai.compat` prefix sits outside the `spring.ai.*` namespace Spring AI owns, so this prototype cannot shadow official properties.
- A reserved `default` key under `providers` holds values inherited by every provider; no bean is registered for it.
- Provider beans register as `fallback` candidates, so Spring AI's own by-type `ChatModel` injection stays unambiguous while names and qualifiers still resolve.
- Built on Spring Framework 7's `BeanRegistrar`; registration runs before `@ConfigurationProperties` beans exist, so properties are bound manually with a `Binder`.
- `OpenAiCompatChatModelCustomizer` hands you Spring AI's `OpenAiChatModel.Builder` unwrapped, for anything the properties don't model.

## License

Apache 2.0.
