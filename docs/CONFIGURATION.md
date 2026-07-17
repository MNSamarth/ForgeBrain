# Configuration Management

Status: Planning document — Phase 1
Scope: How ForgeBrain's backend is configured across environments, and how that maps onto the placeholder configuration classes in `backend/`. This document is about **system/infrastructure configuration** — project IDs, bucket names, service endpoints — which is a different kind of concern from the content-decision architecture in `brain/`, `renderer/`, `reviewer/`, `publishing/`, and `analytics/`, and deliberately lives here in `docs/` instead of alongside them.

## 1. Why Configuration Gets Its Own Document

Every pipeline stage described elsewhere in this repository eventually needs to call a real Google Cloud service — Vertex AI for generation, Cloud Storage for media, Firestore for state. None of those stages should know *how* to find or authenticate to those services; that knowledge belongs in one place, structured consistently, so an environment change (local development vs. staging vs. production) never requires touching pipeline logic.

## 2. Google Cloud, By Design

Per project direction, ForgeBrain uses Google Cloud wherever a capability is needed, specifically because the project has dedicated Google Cloud GenAI credits available:

| Concern | Google Cloud service | Used by |
| --- | --- | --- |
| Text generation (research, lesson, script drafting) | Vertex AI | Research, Lesson, Content Director, Script stages |
| Text-to-speech | Cloud Text-to-Speech (Google Cloud, adjacent to Vertex AI) | Voice Generation |
| Media storage (audio, video, assets) | Cloud Storage | Voice, Subtitles, Assets, Renderer, Publishing |
| Structured state (memory, topic tracking, pipeline runs) | Firestore | Memory, Topic Selection, `backend/entities` |
| Scheduled/recurring pipeline triggers | Cloud Scheduler | A future pipeline orchestration trigger (e.g. "attempt one new reel per day") |
| Application hosting | Cloud Run | The Spring Boot backend itself |

Every integration point is still expressed as an interface in `backend/` (see `backend/README.md`), consistent with `docs/ARCHITECTURE.md`'s "replaceable AI providers" principle — Google Cloud is the concrete Phase 1 choice, not a hard architectural dependency baked into the pipeline's data contracts.

## 3. Configuration Domains

Six configuration domains, each with a dedicated placeholder class in `backend/src/main/java/.../config/` (see `backend/README.md` for the package layout):

| Domain | Class | Holds |
| --- | --- | --- |
| Vertex AI | `VertexAiConfig` | GCP project ID, region, and per-stage model identifiers (research, lesson, script may reasonably use different models or prompt configurations). |
| Cloud Storage | `CloudStorageConfig` | Bucket names for media output, assets, and temporary render working files. |
| Firestore | `FirestoreConfig` | GCP project ID, database ID, and collection name prefixes. |
| Cloud Scheduler | `CloudSchedulerConfig` | Job name and cron expression for the pipeline's recurring trigger (not the trigger's implementation — see `docs/ARCHITECTURE.md` Section 10, scaling infrastructure is out of Phase 1 scope). |
| Cloud Run | `CloudRunConfig` | Values the running application needs to know about its own hosting environment (service name, port, concurrency hint) — not a deployment manifest. This project explicitly does not include Docker or Kubernetes configuration; Cloud Run is referenced here only as "where the already-built application happens to run," not as something this repository configures the deployment of. |
| General application | `ApplicationConfig` | Environment name (`local`, `staging`, `production`), active pipeline stage feature flags, and the current schema/spec version set this deployment targets. |

## 4. Principles

- **No credentials in code or in this repository, ever.** Every config class holds *structure* (field names, types, defaults for non-sensitive values) — never a real project ID, API key, or service account key. Real values are injected at runtime via environment variables or Google Secret Manager references, never committed.
- **Typed configuration over scattered lookups.** Each domain is one `@ConfigurationProperties`-style class with named, typed fields — not `@Value("${some.deeply.nested.property}")` calls scattered across services. A missing or misconfigured value should fail at application startup, not silently as a `null` deep inside a pipeline stage.
- **One config class per concern, not one giant config class.** Mirrors the same "one job per component" principle used throughout `brain/` and `renderer/` — `VertexAiConfig` doesn't know about bucket names, and `CloudStorageConfig` doesn't know about model identifiers.
- **Environment profiles, not environment `if` statements.** Spring Boot's profile mechanism (`application-local.yml`, `application-staging.yml`, `application-production.yml`) is the intended mechanism for varying non-sensitive defaults (e.g. a smaller/cheaper Vertex AI model for local development) — no pipeline or config code should branch on "which environment am I in."

## 5. Secrets Handling

Real credentials (service account keys, API tokens) are assumed to be provided via **Google Secret Manager**, referenced by name in environment-specific configuration, never stored as files or literal values in this repository. `backend/`'s config classes hold the *name* of a secret to look up, never the secret's value — this is a placeholder-level design decision now (Phase 1 has no real secrets to manage yet), stated explicitly so it isn't accidentally violated later.

## 6. What This Document Does Not Cover

Per this pass's explicit scope, this document does not include: Docker or container configuration, Kubernetes manifests, CI/CD pipeline definitions, actual Terraform/deployment infrastructure, or real API credentials of any kind. It describes what the *application* needs to know about its configuration surface — not how that surface gets deployed or provisioned. See `TODO.md` for these as explicitly out-of-scope, tracked future work.
