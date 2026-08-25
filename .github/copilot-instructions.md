# syfo-oppfolgingsplan-backend



## Team
- **Team**: team-esyfo, NAV IT
- **Org**: navikt

## Commands

```bash
./gradlew build   # Build + test + lint
./gradlew test    # Tests only
```

## NAV Principles
- **Team First**: Autonomous teams with circles of autonomy
- **Product Development**: Continuous development over ad hoc approaches
- **Essential Complexity**: Focus on essential, avoid accidental complexity
- **DORA Metrics**: Measure and improve team performance

## Platform & Auth
- **Platform**: NAIS (Kubernetes on GCP)
- **Auth**: Azure AD (internal users), TokenX (on-behalf-of token exchange), ID-porten (citizens), Maskinporten (machine-to-machine)
- **Observability**: Prometheus metrics, Grafana Loki logs, Tempo tracing (OpenTelemetry)

## Conventions
- English code and comments — Norwegian for user-facing text and domain terms (e.g. dialogmote, sykmelding, oppfolgingsplan)
- **Documentation lookup strategy** (prioritert rekkefølge):
  1. **Repo first**: Sjekk eksisterende kode og dokumentasjon i repoet
  2. **NAV-docs ved behov**: Slå opp aksel.nav.no (UI-komponenter, design tokens) og doc.nais.io (plattform, deploy, observability) når du lager eller endrer noe i disse domenene
  3. **Ekstern docs ved usikkerhet**: Bruk web search for eksterne biblioteker kun når du er usikker på API-korrekthet — ikke rutinemessig
- Check existing code patterns in the repository before writing new code
- Follow the ✅ Always / ⚠️ Ask First / 🚫 Never boundaries below

## Documentation and Working Notes

| Tier | Location | Purpose | Persists | Checked in |
|------|----------|---------|----------|------------|
| **Session** | `~/.copilot/session-state/` | Scratch work for one task | No | No |
| **Local notes** | `.local-notes/` | Plans, architecture drafts, research, AI reviews | Yes | No |
| **Permanent docs** | `docs/` | Finalized documentation (ADRs, API docs) | Yes | Yes |

**Defaults**: Planning/research/drafts → `.local-notes/`. Finalized docs → `docs/`. Task tracking → session state.

## Tech Stack
- **Language**: Kotlin
- **Framework**: Ktor
- **Build**: Gradle (Kotlin DSL)
- **Database**: PostgreSQL via HikariCP; Exposed JDBC DSL for new data access, with legacy JDBC migrated incrementally
- **Messaging**: Apache Kafka
- **Testing**: Kotest, MockK
- **Auth**: Les NAIS-manifestene i prosjektet for å finne hvilke auth-mekanismer som er konfigurert (mulige: Azure AD, TokenX, ID-porten, Maskinporten)

## Backend Patterns
- Check `build.gradle.kts` for actual dependencies before suggesting libraries
- Use Flyway for all database migrations — never modify existing migrations
- Use Exposed JDBC DSL for new database operations; migrate existing JDBC only when it is explicitly in scope
- Reuse the configured HikariCP data source and pass the Exposed database explicitly to transactions
- Keep Exposed database entry points suspend-based and dispatch blocking JDBC work through the shared transaction helper
- Parameterized raw SQL is an allowed escape hatch when a query cannot reasonably be expressed with the Exposed DSL
- Parameterized queries always — never string interpolation in SQL
- Follow the existing data access pattern in the repository (extension functions, repositories, etc.)
- Structured logging — check which pattern this repo uses (KotlinLogging, SLF4J, kv() fields, MDC)
- Follow existing code patterns in the repository

## Boundaries

### ✅ Always
- Run `./gradlew build` after changes
- Use Flyway for database migrations
- Add Prometheus metrics for business operations
- Validate JWT issuer, audience, and expiration

### ⚠️ Ask First
- Changing database schema or Kafka event schemas
- Modifying authentication configuration
- Adding new GCP resources

### 🚫 Never
- Skip database migration versioning
- Hardcode secrets or configuration values
- Use `!!` operator without null checks
- Bypass authentication checks
