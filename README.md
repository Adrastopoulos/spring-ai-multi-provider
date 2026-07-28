# spring-ai-multi-provider

Auto-configure **several named OpenAI-compatible `ChatModel` beans** from properties, so
Cerebras, Groq, Together, vLLM, OpenRouter and a local llama.cpp can all run in one Spring
Boot application, each injectable by `@Qualifier`.

> **Status: research prototype, not a released library.**
> This exists to test the ergonomics of a config shape that Spring AI's maintainers sketched
> but never shipped, with the intent of feeding findings back to
> [spring-ai#3518](https://github.com/spring-projects/spring-ai/issues/3518) and
> [spring-ai#2610](https://github.com/spring-projects/spring-ai/issues/2610). It is not
> published to Maven Central, the property namespace is not stable, and you should not
> depend on it in production. If upstream ships this, **this repo should be deleted**, not
> maintained.

Built against **Spring AI 2.0.0** (GA, 2026-06-12) on **Spring Boot 4.1.0**.

## The gap

Spring AI already handles the single-provider case completely: `spring-ai-starter-model-openai`
auto-configures one `OpenAiChatModel` and `spring.ai.openai.base-url` repoints it at any
OpenAI-shaped endpoint. **If you only need one provider, use that and stop reading here.**

What is missing is the *N*-provider case. Spring AI 2.0.0's `OpenAiChatAutoConfiguration`
creates exactly one bean, guarded by `@ConditionalOnMissingBean`, from one flat set of
connection properties. To run several providers today you hand-write a `@Bean` per provider
— which works, but moves configuration out of `application.yaml` and into Java, and scales
in boilerplate with every provider added.

This is a known, open gap, not an oversight on the user's part:

- **[spring-ai#3518](https://github.com/spring-projects/spring-ai/issues/3518)** — the
  tracking epic, *open*, still `waiting-for-triage`.
- **[spring-ai#2610](https://github.com/spring-projects/spring-ai/issues/2610)** —
  "Supporting multiple LLMs in auto-config", *open* since 2025-03.
- **[spring-ai#2221](https://github.com/spring-projects/spring-ai/issues/2221)** — Spring
  AI's lead confirmed *"I agree this is a gap"* and proposed a map-based config, then closed
  the issue noting a declarative solution should be revisited post-GA.
- **[spring-ai#3037](https://github.com/spring-projects/spring-ai/pull/3037)** — the one
  implementation attempt, **closed unmerged**.
- **[spring-boot#15732](https://github.com/spring-projects/spring-boot/issues/15732)** — the
  upstream blocker (auto-configuring multiple beans of one type), open since **2019**.

Verified against the released 2.0.0 source: no `BeanRegistrar` usage, no named-instance
properties anywhere in the auto-configuration modules. Full evidence, with links and dates,
is in **[docs/prior-art.md](docs/prior-art.md)**.

## Property model

```yaml
openai:
  compat:
    providers:
      # Reserved key: a template, not a provider. No bean is registered for it.
      # Every field here is inherited by the providers below unless overridden.
      default:
        timeout: 60s
        max-retries: 2
        temperature: 0.7

      cerebras:
        base-url: https://api.cerebras.ai/v1
        api-key: ${CEREBRAS_API_KEY}
        model: llama-3.3-70b

      localLlama:
        base-url: http://localhost:8080/v1
        api-key: ""            # explicit empty key selects Spring AI's no-auth mode
        model: qwen2.5-7b-instruct
        temperature: 0.2
```

Each key under `providers` becomes an `OpenAiChatModel` bean **registered under that exact
name**:

```java
@Component
class ProviderRouter {

    private final ChatClient cerebras;
    private final ChatClient localLlama;

    ProviderRouter(@Qualifier("cerebras") ChatModel cerebras,
                   @Qualifier("localLlama") ChatModel localLlama) {
        this.cerebras = ChatClient.create(cerebras);
        this.localLlama = ChatClient.create(localLlama);
    }
}
```

### Supported keys

| Key | Notes |
|---|---|
| `base-url` | **Required.** Include the version path segment the provider expects. |
| `model` | **Required.** Default model id for this provider. |
| `api-key` | Bearer token. Explicit `""` selects no-auth, for local servers. |
| `temperature`, `max-tokens` | Default sampling options. |
| `organization-id`, `timeout`, `max-retries`, `custom-headers` | Connection settings. |
| `primary` | Makes this provider the primary by-type candidate. At most one. |
| `openai.compat.enabled` | Set `false` to disable registration entirely. |

Missing `base-url` or `model` fails fast at startup, naming the offending property.

### Prefix choice

`openai.compat` is deliberately vendor-neutral and outside the `spring.ai.*` namespace that
Spring AI owns, so this prototype cannot shadow or be confused with official properties.

## Coexistence with Spring AI's own OpenAI autoconfiguration

The prototype is designed to be additive, not disruptive:

- It uses its own property namespace, so it stays completely inert unless
  `openai.compat.providers` is populated.
- It is `@ConditionalOnClass(OpenAiChatModel.class)` and conditional on
  `openai.compat.enabled`.
- **Provider beans are registered as `fallback` candidates.** Spring AI's
  `ChatClientAutoConfiguration` still injects a single `ChatModel` *by type*, and the PR to
  make it back off when several exist
  ([spring-ai#3429](https://github.com/spring-projects/spring-ai/pull/3429)) is unmerged.
  Registering N ordinary candidates would make that injection ambiguous. As fallbacks, these
  beans remain fully resolvable by name and `@Qualifier`, but yield to any regular
  `ChatModel` bean during by-type resolution — so Spring AI's own auto-configured model still
  wins cleanly.

There is a test asserting exactly this: three `OpenAiChatModel` beans in one context, by-type
injection unambiguously resolving Spring AI's own, named providers still reachable by
qualifier.

## Advanced use: the builder stays exposed

The properties cover common settings only. Anything they don't model is reachable through
`OpenAiCompatChatModelCustomizer`, which hands you Spring AI's own
`OpenAiChatModel.Builder`, unwrapped:

```java
@Bean
OpenAiCompatChatModelCustomizer groqTuning() {
    return (providerName, builder) -> {
        if ("groq".equals(providerName)) {
            builder.httpClientBuilderCustomizer(http -> http.retryOnConnectionFailure(true));
        }
    };
}
```

Customizers run after property-derived settings, so they win. Nothing here hides or wraps
the builder — supplying a pre-built `OpenAIClient`, attaching OkHttp interceptors for OAuth2
token injection, or swapping the observation registry all remain available.

## How it works

Spring Framework 7's programmatic bean registration (`BeanRegistrar`) is the first primitive
that makes property-driven registration of N same-type beans feasible. Two consequences of
that API shaped this implementation, and are the same ones reported on #3518:

1. Registration runs *before* `@ConfigurationProperties` beans exist, so properties are bound
   manually from the `Environment` with a `Binder`.
2. `SupplierContext` is only available inside a single bean's supplier, so collaborators
   like `ObservationRegistry` are resolved lazily at instantiation time.

The reserved `default` key is this prototype's answer to the open question in #3518 about
co-locating default and per-instance config without extra nesting. The convention is
borrowed from `spring-cloud-openfeign`'s `FeignClientProperties`.

## Building and running

Requires JDK 25 (the Gradle toolchain enforces it); the Gradle wrapper provides Gradle 9.5.

```shell
./gradlew build                          # compiles and runs all 13 tests
./gradlew :samples:two-providers:bootRun # boots the two-provider sample
```

The sample in [`samples/two-providers`](samples/two-providers) wires a hosted provider and a
local one simultaneously. It starts without credentials — no network calls are made at
startup, and its tests assert bean wiring only.

## Layout

```
docs/prior-art.md   research: what upstream has tried, with links and dates
starter/            the auto-configuration
samples/two-providers/  runnable app with two providers configured at once
```

## License

Apache 2.0.
