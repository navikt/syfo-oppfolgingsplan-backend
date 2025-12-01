# syfo-oppfolgingsplan-backend

Backend-tjeneste for oppfølgingsplaner i sykefraværsoppfølgingen. Tjenesten håndterer oppfølgingsplaner mellom sykmeldte
og deres arbeidsgivere, og lar veiledere i Nav hente planene.

## Forutsetninger

- **JDK 21** eller nyere
- **Docker** for lokal utvikling
- **Gradle** (wrapper inkludert)

## Lokal utvikling

- Kjør `./gradlew build` for å bygge prosjektet og sikre at alle avhengigheter er på plass.
- Start lokal Postgres, Texas og mock-oauth2-server med `docker compose up`.
- Start applikasjonen lokalt med `./gradlew run`.
- Trenger du et token for sikrede endepunkter, kjør `./fetch-token-for-local-dev.sh`. Auth-serveren returnerer alltid et
  token med claimene definert i `docker-compose.yml`.

## API-dokumentasjon (Swagger)

### Lokal utvikling

Swagger UI er tilgjengelig på:

- **Arbeidsgiver:** http://localhost:8080/swagger/arbeidsgiver
- **Sykmeldt:** http://localhost:8080/swagger/sykmeldt
- **Veileder:** http://localhost:8080/swagger/veileder

### Dev-miljø

- **Arbeidsgiver:** https://syfo-oppfolgingsplan-backend.intern.dev.nav.no/swagger/arbeidsgiver
- **Sykmeldt:** https://syfo-oppfolgingsplan-backend.intern.dev.nav.no/swagger/sykmeldt
- **Veileder:** https://syfo-oppfolgingsplan-backend.intern.dev.nav.no/swagger/veileder

OpenAPI-spesifikasjonene finnes i `src/main/resources/openapi/`.

## Docker compose

### Størrelse på containerplattform

For å kjøre Kafka++ må containerplattformen (Rancher Desktop, Colima osv.) få utvidede ressurser.

Forslag for Colima:

```
colima start --arch aarch64 --memory 8 --cpu 4
```

Vi har en `docker-compose.yml` for Postgres, Texas og fake authserver, og en `docker-compose.kafka.yml` for
Kafka-broker, schema registry og kafka-io.

Start begge med:

```
docker-compose \
  -f docker-compose.yml \
  -f docker-compose.kafka.yml \
  up \
  db authserver texas broker kafka-ui valkey \
  -d
```

Stopp dem med:

```
docker-compose \
  -f docker-compose.yml \
  -f docker-compose.kafka.yml \
  down
```

Du kan også kjøre pdf-generatoren lokalt ved å klone [syfooppdfgen-repoet](https://github.com/navikt/syfooppdfgen) og
følge instruksjonene der.

### Kafka-ui

Bruk http://localhost:9000 for å inspisere konsumere og topics, samt publisere eller lese meldinger.

### Ferdiglagde API-forespørsler

Det finnes HTTP-forespørsler for testing beskrevet i `src/test/http/README.md`.

## Fjern-debugging i nais-clusteret

Det er mulig å aktivere fjern-debugging for apper i nais-clusteret. Generell beskrivelse finnes i repoet `utvikling`
på https://github.com/navikt/utvikling/blob/main/docs/teknisk/Remote_debug_i_Intellij.md.

### Justeringer i deployment

I `nais/nais-dev.yaml` må du:

1. Legge til `JAVA_TOOL_OPTIONS` under `env`:

```
    - name: JAVA_TOOL_OPTIONS
      value: -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
```

2. Gjøre liveness-proben mer tilgivende for debugging ved å justere `periodSeconds` og/eller `failureThreshold`, f.eks.:

```
  liveness:
    path: /internal/is_alive
    initialDelay: 10
    timeout: 5
    periodSeconds: 60
    failureThreshold: 10
```

Opprett tunnel til port 5005 med:

```
kubectx dev-gcp
kubectl port-forward deployment/syfo-oppfolgingsplan-backend -n team-esyfo 5005:5005
```

## Autentisering i dev

Token for sykmeldt eller nærmeste leder:
https://tokenx-token-generator.intern.dev.nav.no/api/obo?aud=dev-gcp:team-esyfo:syfo-oppfolgingsplan-backend

Token for veileder:
https://azure-token-generator.intern.dev.nav.no/api/obo?aud=dev-gcp:team-esyfo:syfo-oppfolgingsplan-backend (krever
veileder-bruker fra Ida).