# Prior art: multiple named OpenAI-compatible `ChatModel` beans in Spring AI

**Research date:** 2026-07-27
**Spring AI version reviewed:** 2.0.0 (GA, released 2026-06-12)
**Spring Boot baseline for that version:** 4.1.0 (`<spring-boot.version>4.1.0</spring-boot.version>` in the 2.0.0 root `pom.xml`)

## Question

Spring AI ships `spring-ai-starter-model-openai`, which auto-configures exactly **one**
`OpenAiChatModel` bean and lets you repoint it at any OpenAI-shaped endpoint via
`spring.ai.openai.base-url`. That covers the single-provider case completely.

The question this document answers: **can current Spring AI auto-configure SEVERAL named
OpenAI-compatible providers at once** — Cerebras + Groq + Together + vLLM + local
llama.cpp, each as its own bean, injectable by `@Qualifier`? If yes, no library should be
written.

## Verdict

**The gap is real as of Spring AI 2.0.0.** There is no declarative, auto-configured
multi-instance mechanism. The tracking issues are open, the one implementation PR was
closed unmerged, and the shipped 2.0.0 source contains no multi-instance code path.

This was verified three ways: reading the issue/PR history, reading the released 2.0.0
source, and checking the upstream Spring Boot blocker.

---

## 1. Upstream issue and PR history

### spring-ai#2221 — "Support multiple models like DeepSeek and OpenAI at the same time in one application"
<https://github.com/spring-projects/spring-ai/issues/2221>
Opened 2025-02-12 by `@sp213` · **closed** 2025-05-09 · last activity 2025-05-16

The original report. Mark Pollack (Spring AI lead) confirmed the gap on 2025-04-17:

> "I agree this is a gap. The issue in the example linked to by @FakeTrader is that the
> underlying chatmodels in this use case need to point to different openai api compatible
> URLs. I am looking to fix this for RC1 at least for OpenAI by letting users define
> multiple client endpoints in application.yml via a map."

He sketched a config shape essentially identical to the one this prototype implements:

```yaml
spring:
  ai:
    openai:
      models:
        enabled: true
        instances:
          gpt4:
            apiKey: "..."
            baseUrl: "https://api.openai.com"
          llama:
            apiKey: "..."
            baseUrl: "https://your-custom-endpoint.com"
```

...accessed through a proposed `OpenAiChatModelRegistry`. **This declarative design was
never implemented.** The issue was closed on 2025-05-09 in favour of the programmatic
`mutate()` approach with the explicit note:

> "See #3037. Closing this issue for now. We should revisit a more comprehensive
> declarative solution post GA in another issue."

Pollack also flagged a design caveat worth respecting: providers that claim OpenAI
compatibility but add extra JSON fields "pollute the openai implementation", which is why
DeepSeek got its own dedicated module rather than riding on the OpenAI one.

### spring-ai#3037 — "feat: add mutate functionality for OpenAiApi and OpenAiChatModel"
<https://github.com/spring-projects/spring-ai/pull/3037>
Opened 2025-05-07 · **CLOSED, NOT MERGED** (verified via GitHub API: `"merged": false`)

The WIP PR that #2221 was closed in favour of. It added `mutate()` so users could derive
extra models by hand, and its integration test (`MultiOpenAiClientIT`) is precisely the
Groq + OpenAI two-provider scenario. Note this was still **programmatic, not
auto-configured** — the user writes the wiring code. And it never landed.

### spring-ai#2610 — "Supporting multiple LLMs in auto-config"
<https://github.com/spring-projects/spring-ai/issues/2610>
Opened 2025-03-31 by `@ddobrin` (Google) · **STILL OPEN** · last activity 2026-06-25

Same request generalised beyond OpenAI to Vertex/Gemini. Explicitly names the workaround
as "not using auto-config and manually configuring each model in use". Thomas Vitale
diagnosed the root cause on 2025-04-05:

> "I'm afraid it's up to the Spring Boot project to support registering multiple beans of
> the same type via auto-configuration, a capability that so far has not been supported by
> Spring Boot."

Pollack responded 2025-06-06: *"Need an epic to research and discuss."*

### spring-ai#3518 — "Auto-Configuration for Multiple Beans of the Same Type" (the epic)
<https://github.com/spring-projects/spring-ai/issues/3518>
Opened 2025-06-12 by `@ThomasVitale` · **STILL OPEN** · label `status: waiting-for-triage`

The tracking epic created off #2610. States the current behaviour flatly:

> "Spring Boot doesn't support multiple bean registration and there's no plan to add this
> capability, so Spring AI doesn't provide this capability for `ChatClient` and `ChatModel`."

The requested API is exactly the `@Qualifier("instance1") ChatModel` shape.

On 2025-08-03 Vitale reported a **working POC** using Spring Framework 7's programmatic
bean registration, and recorded the two implementation constraints that matter:

> - "The `BeanRegistry` API gives access to a `SupplierContext` object used to get access
>   to the dependant beans from the Spring context."
> - "Since the `SupplierContext` is only available within the context of a single bean
>   registration, it's not possible to use it for getting configuration properties beans.
>   Instead, the `Environment` instance provided to the `BeanRegistry` can be used to build
>   a `Binder` and bind the configuration properties from the environment."

He also flagged the open design problem this prototype takes a position on:

> "I haven't still figured out how to make co-exist in the same `@ConfigurationProperties`
> both a default instance config and a map of named instances without requiring extra
> nesting."

POC repo: <https://github.com/ThomasVitale/spring-ai-multiple-beans-demo> (Ollama-based,
2 stars, adapted from `spring-ai-autoconfigure-model-ollama`). Not part of Spring AI.

### spring-ai#3129 — "Improve docs working with multiple ChatModels"
<https://github.com/spring-projects/spring-ai/pull/3129> · closed, docs-only.
Confirms the sanctioned answer is currently *documentation about manual wiring*, not a feature.

### spring-ai#3429 — "ChatClientAutoConfiguration should back off if there are multiple ChatModels"
<https://github.com/spring-projects/spring-ai/pull/3429> · opened 2025-06-03, **still open, unmerged**

Directly relevant collision evidence: `ChatClientAutoConfiguration` injects a single
`ChatModel` by type, so introducing multiple `ChatModel` beans breaks it today. That PR
proposes making it back off. Since it has not merged, **any multi-bean library must avoid
making its provider beans ambiguous type-level injection candidates** — see the design
note below.

### spring-boot#15732 — "Provide support for auto-configuring multiple beans"
<https://github.com/spring-projects/spring-boot/issues/15732>
Opened **2019-01-17** · **STILL OPEN** · last activity 2025-12-10

The upstream root blocker, open for over seven years. This is why the Spring AI issues
stall: the framework primitive did not exist until Spring Framework 7's `BeanRegistrar`.

### spring-ai#3475
<https://github.com/spring-projects/spring-ai/issues/3475> — "How to add multi-model support
via Spring AI 1.0.0 custom configurations for Azure OpenAI?" · open. Further demand signal.

---

## 2. Verification against the released 2.0.0 source

Issue history can lag the code, so the 2.0.0 GA tarball was downloaded and inspected
directly.

**`spring-ai-autoconfigure-model-openai` contains no multi-instance support.** Full list of
its main-source classes:

```
AbstractOpenAiProperties, OpenAiAudioSpeechAutoConfiguration, OpenAiAudioSpeechProperties,
OpenAiAudioTranscriptionAutoConfiguration, OpenAiAudioTranscriptionProperties,
OpenAiAutoConfigurationUtil, OpenAiChatAutoConfiguration, OpenAiChatProperties,
OpenAiCommonProperties, OpenAiEmbeddingAutoConfiguration, OpenAiEmbeddingProperties,
OpenAiImageAutoConfiguration, OpenAiImageProperties, OpenAiModerationAutoConfiguration,
OpenAiModerationProperties
```

There is no registry, no `instances`/`models` map, no `BeanRegistrar`.

**`OpenAiChatAutoConfiguration` is strictly singular** — one bean, guarded by
`@ConditionalOnMissingBean`, resolving one flat set of connection properties:

```java
@AutoConfiguration
@EnableConfigurationProperties({ OpenAiCommonProperties.class, OpenAiChatProperties.class })
@ConditionalOnProperty(name = SpringAIModelProperties.CHAT_MODEL,
        havingValue = SpringAIModels.OPENAI, matchIfMissing = true)
public class OpenAiChatAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public OpenAiChatModel openAiChatModel(OpenAiCommonProperties commonProperties,
            OpenAiChatProperties chatProperties, ...) { ... }
}
```

`OpenAiAutoConfigurationUtil.resolveCommonProperties` merges exactly **two** scalar
property sources (`spring.ai.openai.*` and `spring.ai.openai.chat.*`). It has no concept of
an N-way named map.

**Grep results across the whole 2.0.0 tree:**

- `BeanRegistrar` — **zero** occurrences in any main source file.
- `Map<String, Instance>` / named-instance properties in `auto-configurations/` — none;
  the only `instances` hits are unrelated Javadoc prose and an OpenSearch host list.

So the `BeanRegistrar` capability that Spring Framework 7 added, and that Vitale
prototyped, has **not** been adopted by Spring AI 2.0.0.

**What 2.0.0 *did* change** (relevant to any implementation): the OpenAI module was
rebuilt on the official `openai-java` SDK. `OpenAiChatModel.Builder` now takes
`OpenAIClient` / `OpenAIClientAsync` rather than the old `OpenAiApi`, and client
construction is centralised in the public helper
`org.springframework.ai.openai.setup.OpenAiSetup.setupSyncClient(...)` /
`setupAsyncClient(...)`. Also note `OpenAiChatOptions` now carries connection fields
(`baseUrl`, `apiKey`, `organizationId`, `timeout`, `maxRetries`, `proxy`, `customHeaders`)
inherited from `AbstractOpenAiOptions.AbstractBuilder`, and `OpenAiChatModel.Builder.build()`
will lazily construct clients from those options if none are supplied. That is the seam a
multi-instance library should build on.

Caveat also worth recording: the `mutate()` API discussed in #2221/#3037 does **not** exist
on `OpenAiChatModel` in 2.0.0 (that PR never merged); the supported programmatic path is
`OpenAiChatModel.builder()`.

---

## 3. What Spring AI *does* support today

To be fair to the framework, these all work now and are genuinely sufficient for many apps:

| Capability | Mechanism | Limitation |
|---|---|---|
| One OpenAI-compatible provider | `spring.ai.openai.base-url` + `api-key` | Exactly one |
| Per-request model switch on one endpoint | `OpenAiChatOptions.builder().model(...)` in the `Prompt` | Same base URL/key |
| Several providers, hand-wired | `@Bean` per provider with `OpenAiChatModel.builder()` | Manual; no property-driven config; boilerplate scales with provider count |
| Distinct providers with divergent APIs | Dedicated modules (DeepSeek, Mistral, Anthropic, …) | Only for providers Spring AI has modelled |

**The supported answer to the multi-provider question today is "write a `@Bean` per
provider yourself."** That is the workaround named in #2610, documented via #3129, and
recommended in the #2221 comments. It works. It is just not auto-configuration, and the
config lives in Java rather than in `application.yaml`.

---

## 4. Conclusion and consequences for this repo

The gap is real and narrow:

1. Spring AI 2.0.0 auto-configures exactly one `OpenAiChatModel`.
2. The declarative multi-instance design was proposed by the project lead (#2221), never
   implemented, and explicitly deferred "post GA".
3. The tracking epic (#3518) and the general request (#2610) are both open and untriaged.
4. The upstream Spring Boot blocker (#15732) is open since 2019, but Spring Framework 7's
   `BeanRegistrar` now provides a viable primitive — proven by Vitale's POC, not yet adopted
   upstream.

**Therefore this repo implements a prototype**, deliberately scoped to the OpenAI-compatible
case, to test the ergonomics of the config shape that upstream sketched but never shipped.

Two design decisions fall directly out of the research:

- **Default-inheritance via a reserved map key.** #3518 records the unsolved problem of
  co-existing default config and named instances. This prototype adopts the
  `spring-cloud-openfeign` `FeignClientProperties` convention — a reserved `default` key
  whose values are inherited by every other entry. See
  <https://github.com/spring-cloud/spring-cloud-openfeign/blob/77b7963f9a5eb73af99b43e3159d8770b5120263/spring-cloud-openfeign-core/src/main/java/org/springframework/cloud/openfeign/FeignClientProperties.java#L54-L56>
- **Provider beans must not break `ChatClientAutoConfiguration`.** Because #3429 is still
  unmerged, `ChatClientAutoConfiguration` still injects a single `ChatModel` by type.
  Registering N plain `ChatModel` beans would make that injection ambiguous and break
  applications that also use Spring AI's own OpenAI starter. This prototype therefore
  registers provider beans as **non-autowirable by type** (`Spec::notAutowirable`), so they
  are reachable only by explicit qualifier/name and never compete in by-type resolution.
  This is what "must not collide with Spring AI's own OpenAI autoconfiguration" requires in
  practice.

Because the mechanism used here (`BeanRegistrar` + `Binder` off `Environment`) is the same
one Vitale's POC validated and the same one #3518 contemplates, findings from this
prototype are directly reportable upstream on #3518 / #2610.

---

## Sources

All links verified 2026-07-27; state and dates fetched via the GitHub API.

- spring-ai#2221 (closed 2025-05-09) — <https://github.com/spring-projects/spring-ai/issues/2221>
- spring-ai#3037 (closed unmerged 2025) — <https://github.com/spring-projects/spring-ai/pull/3037>
- spring-ai#2610 (open, upd. 2026-06-25) — <https://github.com/spring-projects/spring-ai/issues/2610>
- spring-ai#3518 (open, upd. 2025-08-03) — <https://github.com/spring-projects/spring-ai/issues/3518>
- spring-ai#3129 (closed, docs) — <https://github.com/spring-projects/spring-ai/pull/3129>
- spring-ai#3429 (open, unmerged) — <https://github.com/spring-projects/spring-ai/pull/3429>
- spring-ai#3475 (open) — <https://github.com/spring-projects/spring-ai/issues/3475>
- spring-boot#15732 (open since 2019-01-17) — <https://github.com/spring-projects/spring-boot/issues/15732>
- ThomasVitale/spring-ai-multiple-beans-demo — <https://github.com/ThomasVitale/spring-ai-multiple-beans-demo>
- Spring Framework 7 programmatic bean registration — <https://docs.spring.io/spring-framework/reference/7.0/core/beans/java/programmatic-bean-registration.html>
- Spring AI 2.0.0 release — <https://github.com/spring-projects/spring-ai/releases/tag/v2.0.0>
- Spring AI 2.0.0 source inspected from `https://github.com/spring-projects/spring-ai/archive/refs/tags/v2.0.0.tar.gz`
