# Intelligence Layer Refactor Plan

Status: draft
Owner: LogDate engineering
Scope: :client:intelligence and consumers in :client:domain and feature modules

## Goals
- Standardize intelligence APIs across platforms with consistent error handling and graceful offline behavior.
- Improve caching for reliability, privacy, performance, and determinism across Android, iOS, and JVM.
- Increase structured output reliability for summarization, people extraction, narrative synthesis, and onboarding.
- Migrate to gpt-5.2 and add multi-provider support (OpenAI, Anthropic) with a unified provider model.
- Produce a roadmap for on-device intelligence on Android and desktop with graceful degradation elsewhere.

## Non-Goals (for this plan)
- No UI redesigns or feature expansions outside intelligence features.
- No backend service build-out; focus remains on client intelligence module.
- No hard dependency on a single provider beyond default configuration.

## Definitions
- Intelligence layer: :client:intelligence module and its consumption from domain/feature layers.
- Provider: an LLM vendor integration (OpenAI, Anthropic, future providers).
- Model: a named LLM version (e.g., gpt-5.2, gpt-5.2-mini, claude-3.5).
- Structured output: model response constrained to JSON schema or tool schema.
- Offline: no network available or provider unreachable.

## Current State Snapshot (from repo)
- Entry summarization uses OpenAI via GenerativeAIChatClient and caches to GenerativeAICache.
- People extraction uses unstructured newline parsing.
- Week narrative uses JSON parsing with lenient extraction from response.
- Android cache uses file-based storage; iOS and JVM cache are TODO/incomplete.
- Network checks exist in summarization use case but not universally.
- Default OpenAI model is gpt-4.1-mini-2025-04-14 in OpenAiClient.

## Guiding Principles
- Deterministic and predictable outputs when possible.
- Fail-safe, never fail-open: offline or provider failure returns a typed error and graceful fallback.
- Provider-agnostic design with clear capability and schema compatibility checks.
- Zero data loss: cached results should be stable and retrievable across app restarts.
- Privacy by design: minimize retention and scope of stored prompts and outputs.

## Plan Overview
Phase 1: Cleanup and API standardization across platforms.
Phase 2: Caching system improvements and storage parity across platforms.
Phase 3: Structured outputs and robust parsing/validation pipeline.
Phase 4: Provider abstraction and model migration to gpt-5.2 and Anthropic support.
Phase 5: On-device intelligence plan for Android and desktop, with graceful degradation.
Phase 6: Validation, QA, rollout, and documentation updates.

---

## Phase 1: Cleanup and API Standardization

### 1.1 API shape alignment
- [ ] Define a single request and response model for intelligence calls.
- [ ] Introduce a sealed result type: AIResult.Success, AIResult.Error, AIResult.Unavailable.
- [ ] Introduce a sealed error type: AIError.Network, AIError.Timeout, AIError.RateLimited, AIError.ProviderUnavailable, AIError.InvalidResponse, AIError.UnsupportedModel, AIError.Unknown.
- [ ] Ensure all public intelligence APIs return AIResult or a domain-specific wrapper that contains AIResult.
- [ ] Map existing null returns to AIResult.Error or AIResult.Unavailable.
- [ ] Introduce a unified request metadata object (requestId, cacheKey, timeout, priority).
- [ ] Add explicit language and locale fields where needed.
- [ ] Ensure input size constraints are checked before calling provider.
- [ ] Document API contracts and error mapping in :client:intelligence README.

### 1.2 Standardize GenerativeAIChatClient
- [ ] Replace GenerativeAIChatClient.submit(List<GenerativeAIChatMessage>) with submit(AIRequest).
- [ ] Support provider capabilities: structured output, tool calling, json_schema, response_format.
- [ ] Introduce AIRequest containing: systemPrompt, userPrompt, messages, model, responseSchema, temperature, maxTokens, timeoutMs, metadata.
- [ ] Add AIResponse containing: content, rawJson, tokensUsed, model, provider, finishReason, latencyMs, cached.
- [ ] Update all callers to use new request types.
- [ ] Add compatibility adapter for existing callers during migration (temporary).

### 1.3 Standardize use case APIs
- [ ] SummarizeJournalEntriesUseCase: return a domain result that wraps AIResult and error codes.
- [ ] ExtractPeopleUseCase: return AIResult with structured output of Person list and parsing metadata.
- [ ] WeekNarrativeSynthesizer: return AIResult<WeekNarrative> and include parse diagnostics.
- [ ] ProcessPersonalIntroductionUseCase: return AIResult<String> for LLM output and embed in Result.Success.
- [ ] Add consistent retry policy handling (no implicit retries inside use cases).
- [ ] Add standardized logging with correlation IDs for AI calls.

### 1.4 Network failure behavior
- [ ] Add a reusable network guard that checks NetworkAvailabilityMonitor before any remote request.
- [ ] Ensure PeopleExtractor and WeekNarrativeSynthesizer check network before calling provider.
- [ ] Define behavior for offline: use cached responses if available, otherwise return AIResult.Unavailable(Reason.NoNetwork).
- [ ] Ensure no exceptions leak to UI when network is unavailable.
- [ ] Add documented fallback messaging for onboarding flow when LLM is unavailable.
- [ ] Add tests for no-network scenarios across all use cases.

### 1.5 Clean up TODOs that block parity
- [ ] Replace TODO stubs in iOS AICacheLocalDataSource with real implementation.
- [ ] Implement JVM AICacheLocalDataSource or remove JVM target if not supported.
- [ ] Remove or replace TODOs in InMemoryAICacheLocalDataSource to avoid runtime failures.
- [ ] Address TODO in DefaultRewindGenerator or remove unused class from DI if not used.

### 1.6 Cross-platform interface parity
- [ ] Ensure cacheModule is consistent across Android, iOS, JVM.
- [ ] Ensure each platform can resolve AICacheLocalDataSource with correct path or storage.
- [ ] Define platform-specific cache root conventions in documentation.
- [ ] Ensure platform-specific modules expose the same dependencies and default behaviors.

### 1.7 Files to touch (initial list)
- [ ] client/intelligence/src/commonMain/kotlin/app/logdate/client/intelligence/generativeai/GenerativeAIChatClient.kt
- [ ] client/intelligence/src/commonMain/kotlin/app/logdate/client/intelligence/generativeai/GenerativeAIChatMessage.kt
- [ ] client/intelligence/src/commonMain/kotlin/app/logdate/client/intelligence/EntrySummarizer.kt
- [ ] client/intelligence/src/commonMain/kotlin/app/logdate/client/intelligence/entity/people/PeopleExtractor.kt
- [ ] client/intelligence/src/commonMain/kotlin/app/logdate/client/intelligence/narrative/WeekNarrativeSynthesizer.kt
- [ ] client/domain/src/commonMain/kotlin/app/logdate/client/domain/timeline/SummarizeJournalEntriesUseCase.kt
- [ ] client/domain/src/commonMain/kotlin/app/logdate/client/domain/entities/ExtractPeopleUseCase.kt
- [ ] client/domain/src/commonMain/kotlin/app/logdate/client/domain/onboarding/ProcessPersonalIntroductionUseCase.kt
- [ ] client/domain/src/commonMain/kotlin/app/logdate/client/domain/rewind/GenerateBasicRewindUseCase.kt
- [ ] client/intelligence/src/commonMain/kotlin/app/logdate/client/intelligence/di/IntelligenceModule.kt

---

## Phase 2: Caching System Improvements

### 2.1 Cache design goals
- [ ] Deterministic keys for identical inputs.
- [ ] Platform parity: Android, iOS, JVM all usable and tested.
- [ ] TTL support with per-feature defaults.
- [ ] Storage limits and eviction policy.
- [ ] Optional encryption at rest for cached AI outputs.
- [ ] Clear invalidation strategy for model/provider changes.

### 2.2 Cache key strategy
- [ ] Define CacheKeyStrategy interface.
- [ ] Use stable hashing of normalized inputs (trimmed text, sanitized metadata).
- [ ] Include model, provider, and schema version in cache key.
- [ ] Include prompt version and template ID to avoid stale cache collisions.
- [ ] Store a human-readable key prefix for debugging.
- [ ] Provide cache key generation utilities for use cases.

### 2.3 Cache entry metadata
- [ ] Extend GenerativeAICacheEntry with model, provider, promptVersion, schemaVersion.
- [ ] Add ttlSeconds and expiresAt.
- [ ] Add sourceHash to detect mismatch between stored output and input.
- [ ] Add contentType (summary, people, narrative, onboarding).

### 2.4 Cache storage layers
- [ ] Define a two-tier cache: memory + persistent.
- [ ] Memory cache size limits and LRU eviction.
- [ ] Persistent cache file structure with per-entry metadata.
- [ ] Optional compression for large outputs.
- [ ] Ensure file IO is done on IO dispatcher.

### 2.5 Android implementation
- [ ] Keep file-based cache but add metadata header or JSON sidecar.
- [ ] Enforce maximum entries and total size limit.
- [ ] Implement TTL eviction on read and periodic cleanup on write.
- [ ] Provide a safe path sanitizer for cache file names.
- [ ] Add tests for Android cache read/write/expire.

### 2.6 iOS implementation
- [ ] Implement IOSAICacheLocalDataSource using NSFileManager or Kotlin/Native file APIs.
- [ ] Match Android behavior for TTL and metadata.
- [ ] Provide cache root path injection via DI.
- [ ] Add tests or integration test scaffolding for iOS cache.

### 2.7 JVM implementation
- [ ] Implement JVM AICacheLocalDataSource using a local temp or user cache directory.
- [ ] Ensure JVM module works in desktop scenarios.
- [ ] Provide configurable cache location and max size.
- [ ] Add basic JVM tests for read/write/eviction.

### 2.8 Cache invalidation rules
- [ ] Invalidate on model change (provider+model combo).
- [ ] Invalidate on prompt template version change.
- [ ] Invalidate on schema version change.
- [ ] Manual purge hook already exists; add test coverage for purge.
- [ ] Add per-feature invalidation toggles in config.

### 2.9 Error handling in cache
- [ ] Cache read errors return null and log; no crash.
- [ ] Cache write errors should not fail the AI request result.
- [ ] Use best-effort caching with explicit metrics when failures occur.

### 2.10 Cache tests
- [ ] Update FakeGenerativeAICache to reflect new metadata.
- [ ] Add tests for key stability across platforms.
- [ ] Add tests for TTL expiration.
- [ ] Add tests for eviction due to size limit.
- [ ] Add tests for corrupted cache entries.

---

## Phase 3: Structured Outputs and Reliability

### 3.1 Structured output strategy
- [ ] Define JSON schemas for each intelligence task.
- [ ] Use schema-first prompting with validation before parsing.
- [ ] Prefer provider-native structured output features when available.
- [ ] Include schema version in responses and cache keys.
- [ ] Fallback: best-effort parsing with strict validation and safe error paths.

### 3.2 Summarization output schema
- [ ] Define SummaryResponse schema: summaryText, lengthHint, tone, keyPoints.
- [ ] Update EntrySummarizer to request schema output.
- [ ] Add parser and validator for SummaryResponse.
- [ ] Provide a downgrade to plain text if schema parse fails.
- [ ] Update consuming use cases to use new structured output.

### 3.3 People extraction schema
- [ ] Define PeopleExtractionResponse schema: people[], confidence, ambiguousTokens.
- [ ] Require each person to include name and optional title/context.
- [ ] Update PeopleExtractor prompts to request JSON output.
- [ ] Add strict validation to filter empty or invalid names.
- [ ] Add fallback heuristics if JSON is invalid (line split).

### 3.4 Week narrative schema
- [ ] Define NarrativeResponse schema with themes, emotionalTone, storyBeats, overallNarrative.
- [ ] Require evidenceIds to match input IDs or be empty.
- [ ] Add schema-based parsing with error reporting.
- [ ] Update parseNarrativeResponse to use the schema validator first.
- [ ] Add tests for malformed and extra fields.

### 3.5 Onboarding response schema
- [ ] Define OnboardingResponse schema: greeting, reflection, encouragement.
- [ ] Update ProcessPersonalIntroductionUseCase to request schema output.
- [ ] Add fallback message generator when schema invalid or missing.

### 3.6 Schema utilities
- [ ] Define a SchemaRegistry with id and version fields.
- [ ] Define a JSON validator interface to check schema compliance.
- [ ] Add test fixtures for each schema.
- [ ] Add centralized error mapping for JSON parsing failures.

### 3.7 Provider-specific structured output handling
- [ ] OpenAI: use response_format json_schema or tools where appropriate.
- [ ] Anthropic: use tool use or JSON mode and parse from content.
- [ ] Add provider capability flags to select best structured output mode.
- [ ] Add fallback to unstructured text when provider lacks structured output.

### 3.8 Reliability improvements
- [ ] Add deterministic prompt templates with explicit instruction ordering.
- [ ] Add request-level seed when provider supports it for stable outputs.
- [ ] Add response length constraints and truncation checks.
- [ ] Add validation metrics on parse failure rate.
- [ ] Add retries only for parse errors when safe to do so.

---

## Phase 4: Provider Abstraction and Model Migration

### 4.1 Provider interface
- [ ] Define AIProvider interface with submit(AIRequest) and capabilities.
- [ ] Define ProviderCapabilities: structuredOutput, tools, jsonSchema, vision, maxTokens.
- [ ] Implement a ProviderRegistry and ProviderSelector by config.
- [ ] Add per-feature model selection with fallback chain.
- [ ] Add standardized error mapping across providers.

### 4.2 OpenAI provider updates
- [ ] Migrate default model to gpt-5.2 or gpt-5.2-mini for cost-sensitive flows.
- [ ] Add support for gpt-5.2-high for narrative synthesis when configured.
- [ ] Implement API request payload with new model names and response_format options.
- [ ] Track provider-specific rate-limit headers if available.
- [ ] Update docs/environment/setup.md with model configuration options.

### 4.3 Anthropic provider integration
- [ ] Implement AnthropicClient with API key configuration (e.g., ANTHROPIC_API_KEY).
- [ ] Map GenerativeAIChatMessage to Anthropic message format.
- [ ] Implement structured output strategy for Anthropic (tool use or JSON schema via instructions).
- [ ] Add error mapping for Anthropic-specific errors.
- [ ] Add tests for Anthropic request serialization and response parsing.

### 4.4 Provider configuration
- [ ] Define an IntelligenceConfig with defaultProvider and per-feature overrides.
- [ ] Allow runtime provider selection in app settings where appropriate.
- [ ] Provide environment variable or config file mapping for provider and model.
- [ ] Add provider availability check to fall back to secondary provider.
- [ ] Add feature-level capability checks before use.

### 4.5 Model selection strategy
- [ ] Summaries: default to gpt-5.2-mini or equivalent high-throughput model.
- [ ] People extraction: default to gpt-5.2-mini or Claude fast variant.
- [ ] Narrative synthesis: default to gpt-5.2 or Claude Sonnet class model.
- [ ] Onboarding response: default to gpt-5.2-mini for warm responses.
- [ ] Allow power users to override per feature if exposed in settings.

### 4.6 Graceful failure and fallback policies
- [ ] For each feature, define fallback priority: cache -> secondary provider -> offline response.
- [ ] Ensure no provider failure results in uncaught exception in UI.
- [ ] Surface clear status to UI layer for each failure mode.
- [ ] Record diagnostics for provider fallback usage.

---

## Phase 5: On-Device Intelligence Plan

### 5.1 Android on-device options
- [ ] Evaluate lightweight on-device models for summarization and extraction.
- [ ] Consider ML Kit or TensorFlow Lite for text classification and entity extraction.
- [ ] Consider ONNX Runtime or llama.cpp for local LLM inferences.
- [ ] Define memory and size budgets for on-device models.
- [ ] Define battery and latency targets for on-device inference.
- [ ] Define fallback when model not installed or device unsupported.

### 5.2 Desktop plan (JVM)
- [ ] Investigate ONNX Runtime or local LLM via llama.cpp for desktop.
- [ ] Define packaging strategy for model weights in desktop builds.
- [ ] Define GPU vs CPU acceleration detection and configuration.
- [ ] Define storage location and download updates for models.

### 5.3 On-device capability gating
- [ ] Add capability detection for on-device inference.
- [ ] Add user settings to enable or disable on-device intelligence.
- [ ] Provide model download status UI for supported platforms.
- [ ] Provide telemetry for on-device vs cloud usage.

### 5.4 On-device use cases
- [ ] Summaries: simple extractive summarization as baseline.
- [ ] People extraction: local NER model or rule-based baseline.
- [ ] Narrative synthesis: likely cloud-only in first iteration.
- [ ] Onboarding responses: local canned templates if offline.

### 5.5 Graceful degradation strategy
- [ ] If on-device available, use it when offline; otherwise use cloud providers.
- [ ] If no on-device and no network, return AIResult.Unavailable and fallback UI copy.
- [ ] Keep cached results accessible offline across platforms.
- [ ] Ensure UX messaging is consistent and non-blocking.

---

## Phase 6: Validation, QA, and Documentation

### 6.1 Tests and validation
- [ ] Add unit tests for new AIRequest and AIResponse data classes.
- [ ] Add unit tests for AIResult and AIError mapping.
- [ ] Add integration tests for each intelligence use case.
- [ ] Add offline tests to ensure no network calls are attempted.
- [ ] Add parsing tests for structured output responses.
- [ ] Add provider mock tests for OpenAI and Anthropic clients.

### 6.2 Performance validation
- [ ] Measure latency for each feature with new models.
- [ ] Compare cache hit rates before and after changes.
- [ ] Validate memory usage for new cache implementation.
- [ ] Validate model cost impact with gpt-5.2 defaults.

### 6.3 Documentation updates
- [ ] Update client/intelligence/README.md with new APIs, providers, and structured outputs.
- [ ] Update docs/environment/setup.md with provider configuration.
- [ ] Document cache behaviors and invalidation rules.
- [ ] Add a troubleshooting guide for provider failures and offline mode.

---

## Detailed Work Breakdown (Task List)

### API surface changes
- [ ] Create AIRequest data class in :client:intelligence.
- [ ] Create AIResponse data class in :client:intelligence.
- [ ] Create AIResult sealed class in :client:intelligence.
- [ ] Create AIError sealed class in :client:intelligence.
- [ ] Define AIProvider interface and ProviderCapabilities.
- [ ] Add ProviderRegistry with configuration loading.
- [ ] Define a DefaultProviderSelector with fallback chain.
- [ ] Add ModelId value class and standard naming for provider+model.
- [ ] Update GenerativeAIChatClient to be replaced by AIProvider or made internal.
- [ ] Create a CompatibilityGenerativeAIChatClient adapter for old call sites.
- [ ] Update EntrySummarizer to accept AIProvider and AIRequest builder.
- [ ] Update PeopleExtractor to accept AIProvider and AIRequest builder.
- [ ] Update WeekNarrativeSynthesizer to accept AIProvider and AIRequest builder.
- [ ] Update ProcessPersonalIntroductionUseCase to accept AIProvider.
- [ ] Update SummarizeJournalEntriesUseCase to surface AIResult.
- [ ] Update ExtractPeopleUseCase to surface AIResult.
- [ ] Update GenerateBasicRewindUseCase to handle AIResult errors.

### Error handling standardization
- [ ] Map network unavailable to AIError.Network and AIResult.Unavailable.
- [ ] Map provider HTTP 429 to AIError.RateLimited.
- [ ] Map provider HTTP 401/403 to AIError.ProviderUnavailable or AIError.Unauthorized.
- [ ] Map parse failures to AIError.InvalidResponse.
- [ ] Map timeout to AIError.Timeout.
- [ ] Ensure all exceptions are caught and converted to AIError.Unknown.
- [ ] Add explicit error mapping tests for each provider.

### Network guard implementation
- [ ] Create a NetworkGuard helper in :client:intelligence.
- [ ] Ensure all AIRequest creation goes through NetworkGuard checks.
- [ ] Add a NetworkOverride for tests to simulate offline/online state.
- [ ] Add log entry when network is unavailable and cache used.
- [ ] Ensure no network request is sent when offline, even if caller forgets.

### Prompt and template management
- [ ] Create prompt templates with version numbers for each feature.
- [ ] Add PromptRegistry to manage templates and versions.
- [ ] Add tests to ensure prompt versions are included in cache keys.
- [ ] Add ability to override prompt templates for testing.
- [ ] Ensure prompts are short, deterministic, and aligned with schema.

### Cache improvements tasks
- [ ] Update GenerativeAICacheEntry to include metadata fields.
- [ ] Update GenerativeAICache interface to accept metadata and TTL.
- [ ] Implement MemoryGenerativeAICache as in-memory LRU.
- [ ] Update OfflineGenerativeAICache to compose memory + persistent layers.
- [ ] Add CacheKeyStrategy and default hashing implementation.
- [ ] Add cache migration for existing entries (if possible).
- [ ] Add cache purge on schema/model changes.
- [ ] Add structured cache file format (JSON).
- [ ] Add corruption detection and auto-repair (delete bad entries).

### Android cache tasks
- [ ] Implement per-entry JSON file with metadata and content.
- [ ] Add size-based eviction for Android cache directory.
- [ ] Add periodic cleanup task in background (optional).
- [ ] Add tests for Android cache storage.

### iOS cache tasks
- [ ] Implement iOS file storage with similar JSON format.
- [ ] Ensure file IO is done in background thread.
- [ ] Add iOS unit tests for get/set/clear if feasible.
- [ ] Document iOS cache directory location and policies.

### JVM cache tasks
- [ ] Implement JVM file storage in user cache directory.
- [ ] Add JVM tests for read/write/eviction.
- [ ] Add configuration to override cache path.

### Structured outputs tasks
- [ ] Define SummaryResponse schema and validator.
- [ ] Define PeopleExtractionResponse schema and validator.
- [ ] Define NarrativeResponse schema and validator.
- [ ] Define OnboardingResponse schema and validator.
- [ ] Implement SchemaRegistry with versions and IDs.
- [ ] Add structured output parsing to each feature.
- [ ] Add fallback parsing to each feature.
- [ ] Add tests for schema validation failure paths.

### Provider integration tasks
- [ ] Implement OpenAI provider using new AIRequest/AIResponse.
- [ ] Add gpt-5.2 model ids and default selections.
- [ ] Implement Anthropic provider and add configuration.
- [ ] Add provider selection logic and fallback chain.
- [ ] Add provider capability negotiation for structured outputs.
- [ ] Add request/response telemetry and debug logging.

### Model migration tasks
- [ ] Introduce model configuration constants with gpt-5.2 family.
- [ ] Update any hard-coded gpt-4.1-mini references.
- [ ] Add model override options for feature-level selection.
- [ ] Add tests to ensure configured model is used in requests.
- [ ] Add documentation for model choice and cost considerations.

### On-device plan tasks
- [ ] Research candidate models for summarization and NER.
- [ ] Evaluate performance on mid-tier Android devices.
- [ ] Evaluate licensing and distribution considerations.
- [ ] Prototype a minimal on-device summarizer on Android.
- [ ] Prototype a minimal on-device people extractor on Android.
- [ ] Define integration points for local model selection.
- [ ] Create a plan for desktop on-device support.
- [ ] Add documentation and roadmap for on-device adoption.

---

## Acceptance Criteria
- All intelligence calls return AIResult with typed errors.
- All platforms have working cache implementations.
- Offline scenarios never crash and always degrade gracefully.
- Structured outputs pass validation in tests and handle malformed responses.
- OpenAI and Anthropic providers are both supported and selectable.
- Default models are gpt-5.2 family with configuration overrides.
- A documented on-device roadmap exists for Android and desktop.

## Risk Register
- Provider API changes may break structured outputs.
- Model upgrades may shift token usage and costs.
- On-device models may be too large for some devices.
- Cache migration may cause temporary misses or higher storage use.
- New abstraction layers may add latency if not optimized.

## Testing Matrix
- Unit tests: AIRequest, AIResponse, AIResult, AIError.
- Unit tests: cache key strategy, TTL, eviction.
- Integration tests: summarization, people extraction, narrative synthesis.
- Offline tests: no network -> cached or unavailable results.
- Provider tests: OpenAI and Anthropic request serialization.
- Structured output tests: parse success and failure.
- Platform tests: Android, iOS, JVM cache implementation parity.

## Rollout Plan
- Stage 1: Merge API and cache refactor behind feature flags.
- Stage 2: Enable structured outputs for summary and people extraction.
- Stage 3: Enable structured outputs for narrative and onboarding.
- Stage 4: Switch default model to gpt-5.2 family.
- Stage 5: Add Anthropic provider for selected flows.
- Stage 6: Expand on-device experiments on Android.

## Open Questions
- Which Anthropic model variants do we want to support initially?
- Do we want to expose provider/model selection to end users or keep internal?
- What cache size limits are acceptable per platform?
- What performance targets should be enforced for on-device inference?
- Should on-device models be optional downloads or bundled?

---

## Detailed Step-by-Step Plan (Ordered)

### Step 0: Repository preparation
- [ ] Confirm baseline tests pass for :client:intelligence.
- [ ] Document current model choices and environment variables.
- [ ] Add a temporary feature flag for new AI API usage.

### Step 1: Core API refactor
- [ ] Implement AIRequest/AIResponse data classes.
- [ ] Implement AIResult/AIError sealed classes.
- [ ] Add ProviderCapabilities.
- [ ] Implement AIProvider interface.
- [ ] Add ProviderRegistry and ProviderSelector.
- [ ] Update DI modules to expose AIProvider.
- [ ] Create compatibility adapter for old GenerativeAIChatClient usage.

### Step 2: Update features to use new API
- [ ] Update EntrySummarizer with AIRequest and AIResult.
- [ ] Update PeopleExtractor with AIRequest and AIResult.
- [ ] Update WeekNarrativeSynthesizer with AIRequest and AIResult.
- [ ] Update ProcessPersonalIntroductionUseCase to use AIProvider.
- [ ] Update SummarizeJournalEntriesUseCase to surface AIResult.
- [ ] Update ExtractPeopleUseCase to surface AIResult.
- [ ] Update GenerateBasicRewindUseCase to handle AIResult errors.

### Step 3: Add network guard
- [ ] Implement NetworkGuard helper.
- [ ] Ensure every AI call checks NetworkGuard before provider.
- [ ] Add tests for offline checks across use cases.

### Step 4: Cache refactor
- [ ] Implement CacheKeyStrategy and metadata changes.
- [ ] Implement new cache entry format and migration where feasible.
- [ ] Build memory cache with LRU eviction.
- [ ] Update OfflineGenerativeAICache to compose memory + persistent cache.
- [ ] Add TTL handling and expiration checks.
- [ ] Add per-feature cache config (TTL, size).

### Step 5: Platform cache parity
- [ ] Android: update file cache to new JSON format.
- [ ] iOS: implement IOSAICacheLocalDataSource.
- [ ] JVM: implement AICacheLocalDataSource.jvm.
- [ ] Add tests and verification for all platforms.

### Step 6: Structured output rollout
- [ ] Implement schema definitions.
- [ ] Update prompts to match schema.
- [ ] Implement parsing and validation.
- [ ] Add fallback logic for invalid schema responses.
- [ ] Add tests for each schema.

### Step 7: Provider support and model migration
- [ ] Implement OpenAI provider with gpt-5.2 model ids.
- [ ] Implement Anthropic provider and add configuration.
- [ ] Add provider selection logic with fallback chain.
- [ ] Update default model selection per feature.
- [ ] Update environment setup docs with provider settings.

### Step 8: On-device roadmap
- [ ] Document Android on-device architecture options.
- [ ] Document desktop on-device architecture options.
- [ ] Define milestones, MVP scope, and success criteria.
- [ ] Define fallback behavior when on-device not available.

---

## Provider Capability Matrix (Initial)
- OpenAI: structured output (json_schema), tools, vision (if needed), high token limits.
- Anthropic: structured output via tool use or JSON instructions, strong long-context.
- On-device: limited context, must use extractive or small models.

---

## Graceful Degradation Rules
- Rule 1: Use cached result if available and not expired.
- Rule 2: If cache miss and network unavailable, return AIResult.Unavailable.
- Rule 3: If primary provider fails, try secondary provider if configured.
- Rule 4: If structured output parse fails, use fallback parse or plain text.
- Rule 5: If all else fails, return user-friendly fallback message.

---

## Configuration Plan
- OPENAI_API_KEY for OpenAI provider.
- ANTHROPIC_API_KEY for Anthropic provider.
- INTELLIGENCE_DEFAULT_PROVIDER (openai|anthropic|ondevice).
- INTELLIGENCE_MODEL_SUMMARY, INTELLIGENCE_MODEL_PEOPLE, INTELLIGENCE_MODEL_NARRATIVE.
- INTELLIGENCE_CACHE_TTL_SECONDS per feature.
- INTELLIGENCE_CACHE_MAX_MB per platform.

---

## Migration Notes
- Keep compatibility adapter until all call sites migrated.
- Ship structured output schema changes behind a feature flag initially.
- Migrate cached entries by detecting old format and re-writing on read.
- Provide a one-time purge option in case of cache corruption.

---

## Work Packages

### WP1: API refactor package
- Deliverables: AIRequest, AIResponse, AIResult, AIError, AIProvider.
- Owner: core client.
- Dependencies: none.
- Risks: breaking API usage in domain module.

### WP2: Cache package
- Deliverables: platform cache parity, TTL, eviction.
- Owner: platform leads.
- Dependencies: WP1 for metadata updates.
- Risks: storage size or data migration bugs.

### WP3: Structured output package
- Deliverables: schema definitions and parsers.
- Owner: intelligence module.
- Dependencies: WP1.
- Risks: provider compatibility gaps.

### WP4: Provider package
- Deliverables: OpenAI and Anthropic providers with gpt-5.2 migration.
- Owner: intelligence module.
- Dependencies: WP1.
- Risks: API rate limits and model availability.

### WP5: On-device roadmap package
- Deliverables: documented plan and feasibility notes.
- Owner: platform leads.
- Dependencies: WP1 for integration points.
- Risks: device constraints.


---

## Appendix A: Proposed Schema Summaries

SummaryResponse schema fields:
- summaryText: string
- keyPoints: array of strings
- lengthHint: short|medium|long
- tone: descriptive string

PeopleExtractionResponse schema fields:
- people: array of { name: string, title: string?, context: string? }
- confidence: float
- ambiguousTokens: array of strings

NarrativeResponse schema fields:
- themes: array of strings
- emotionalTone: string
- storyBeats: array of { moment, context, emotionalWeight, evidenceIds[] }
- overallNarrative: string

OnboardingResponse schema fields:
- greeting: string
- reflection: string
- encouragement: string

---

## Appendix B: Example Fallback Messages
- Summary unavailable: "Summary is not available right now."
- People extraction unavailable: "People tags are unavailable offline."
- Narrative unavailable: "Your weekly story will be ready when you are online."
- Onboarding fallback: "Welcome to LogDate. Your profile is saved."

---

## Appendix C: Line Count
Line count: 666
