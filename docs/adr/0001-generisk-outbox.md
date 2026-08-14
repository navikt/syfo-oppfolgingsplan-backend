# ADR 0001: Felles claim/lease-outbox for planlagte varsler

- **Status:** Akseptert
- **Dato:** 2026-08-12
- **Oppdatert:** 2026-08-13

## Kontekst

Tjenesten skal etter hvert levere minst tre ulike varsler:

1. varsel til den sykmeldte når en oppfølgingsplan opprettes
2. frivillig påminnelse til arbeidsgiver etter fire uker
3. frivillig evalueringsvarsel ved planens evalueringsdato

De to planlagte varslene må revalidere mottaker, sykefravær og planstatus når tidspunktet nås.
Dette er domeneregler og skal ikke bygges inn i den tekniske leveringsmekanismen.

Flere apper på teamet bruker outbox. Budstikka har allerede en leaderless claim/lease-mekanisme
som committer claim før nettverksarbeid. Vi vil konvergere på den samme worker-protokollen uten å
tvinge Budstikkas leveringsdomene eller retrypolicy inn i denne appen. En eventuell felles lib
utsettes til flere apper har bevist et lite og stabilt felles interface.

## Beslutning

### Typede, immutable kommandoer

Vi bruker én outbox-tabell og én prosessor. Kjernen definerer det typede `OutboxMessageType`-
interfacet, mens hver adapter eier et lukket Kotlin-enum som implementerer det. Dermed kan ikke en
inaktiv kjerne reservere eller enqueue en meldingstype uten handler. Hver enumverdi har en
eksplisitt, stabil databaseverdi som lagres som `TEXT`. En ny enumverdi lander sammen med sin
adapter, handler og adapterspesifikke enqueue-funksjon. Databaseverdier er append-only og skal ikke
gjenbrukes eller endres etter at de er tatt i bruk.

En domenetransaksjon oppretter outbox-raden sammen med tilstanden som utløser meldingen. Enqueue kan
bruke callerens eksisterende JDBC-connection direkte; Exposed-adapteren delegerer til samme
implementasjon. Dermed krever ikke innføring av outbox at eksisterende domenepersistens skrives om.
`UNIQUE (message_type, dedup_key)` gjør kommandoen idempotent. `available_at` uttrykker når
kommandoen tidligst kan behandles; umiddelbare meldinger bruker nåtid.

Outbox-kommandoer er immutable. Dersom en bruker slår et varsel av og senere på igjen, oppretter
domenet en ny, ugjennomsiktig bestillingsgenerasjon og en ny outbox-kommando. Den gamle kommandoen
kan kanselleres, men reaktiveres eller muteres aldri. Dette unngår at en samtidig worker kan
overskrive en nyere brukerintensjon.

PostgreSQL kan svare med serialization failure når to `REPEATABLE READ`-transaksjoner oppretter
samme dedup-nøkkel samtidig. Adaptere hvor dette kan skje må derfor bruke en ren databasetransaksjon
med automatisk replay, for eksempel `exposedTransaction(maxAttempts > 1)`. Blokken må ikke
inneholde eksterne sideeffekter, siden hele domenetransaksjonen kan kjøres på nytt. Adaptere med en
garantert ny dedup-nøkkel kan beholde eksisterende transaksjonssemantikk.

Outbox-raden inneholder en typeuavhengig `external_ref` og et lite JSON-payload. Dedup-nøkkel og
referanse skal bruke ugjennomsiktige ID-er; payload skal begrenses til det handleren faktisk trenger.
Fødselsnummer, organisasjonsnummer, navn, fritekst og varselinnhold skal aldri lagres her. UUID-er
som kan kobles tilbake til en person eller virksomhet er pseudonyme personopplysninger, ikke
anonyme data, og må behandles deretter. Handleren bruker referansen til å hente fersk domenetilstand.

### Claim/lease-worker på alle replikaer

Prosessoren kjører på alle podder uten leader election:

1. En kort databasetransaksjon velger `READY`-rader hvor `available_at <= now`, samt utløpte
   `CLAIMED`-rader, med `FOR UPDATE SKIP LOCKED`.
2. Radene oppdateres til `CLAIMED` med `claim_token` og `lease_until`, og transaksjonen committes.
3. Handleren kjører uten å holde databasetransaksjon, connection eller radlås.
4. Resultatet persisteres i en ny, kort transaksjon som bare oppdaterer raden dersom både status og
   `claim_token` fortsatt matcher.
5. Ved krasj eller cancellation blir raden stående `CLAIMED` til leasen utløper og en replika kan
   claime den på nytt.

Claim-tokenet er sterkere enn ren status-CAS: en worker som fullfører etter at leasen har utløpt kan
ikke overskrive en nyere claim. Eksterne effekter kan likevel skje mer enn én gang dersom en lease
utløper midt i et kall. Leveringen er derfor *at least once*, og adapteren må bruke outbox-radens
stabile UUID som mottakerens dedupliserings-ID.

En worker claimer maksimalt 25 rader per meldingstype. Den starter bare nye rader innenfor 80 prosent
av leaseperioden. Rader som ikke startes, beholder claimet til lease expiry. Tre sammenhengende feil
avbryter resten av batchen, slik at et systemisk nedstrømsavbrudd ikke hamres. En enkelt poison-rad
flyttes derimot fram i tid og blokkerer ikke nyere rader.

### Domeneutfall og teknisk retry

Hver handler returnerer ett av tre domeneutfall:

- `Sent`: leveringen er bekreftet og raden blir terminal.
- `Cancelled(reason)`: fersk domenetilstand viser at varselet ikke lenger skal sendes. Bare en
  generell, lavkardinal årsak lagres; detaljerte helse- og forretningsutfall skal ikke inn i
  outbox-raden.
- `Deferred(until)`: domenet kan ikke avgjøre eller sende ennå. Raden går tilbake til `READY` med
  nytt `available_at`, uten at dette regnes som en teknisk feil.

Exceptions behandles som tekniske feil. Raden går tilbake til `READY`, og handlerens retry-policy
beregner nytt `available_at`. Standard er eksponentiell ventetid med jitter fra omtrent ett minutt
til maksimalt én time. Antall tekniske feil og siste feiltidspunkt lagres for metrikk, varsling og
feilsøking. Kjernen har ingen vilkårlig grense som permanent kaster en melding etter et kort
driftsavbrudd; en eventuell poison-policy må innføres eksplisitt sammen med alarm og recovery.
En vellykket `Deferred` nullstiller den sammenhengende tekniske feilsekvensen.

### Rullerende deploy

En worker claimer bare meldingstyper den har en registrert handler for. En gammel pod ignorerer
dermed radene til en ny type. Databaseverdier er append-only. En ny kanselleringsårsak rulles ut i
to steg: først deployes lesestøtte til alle podder, deretter aktiveres skrivingen. Verdier fjernes
først etter en separat kontraksjonsdeploy hvor ingen persistente rader bruker dem.

## Avgrensning

Denne kjernen registrerer ingen handler eller bakgrunnsoppgave alene. Oppfølgingsplanvarsel,
fireukersvarsel og evalueringsvarsel landes som separate adaptere med egne relevansregler,
bestillingsgenerasjoner og kontrakter.

Før første adapter aktiveres må den dokumentere hvor lenge samme domenekommando realistisk kan
oppstå på nytt. Det intervallet bestemmer hvor lenge terminale rader må beholdes for idempotens, og
det skal innføres cleanup som sletter dem etterpå. Kjernen tilbyr én lederkoordinert, batchet
retention-task som appen konfigurerer per meldingstype. `completed_at` lagres for både `SENT` og
`CANCELLED`, slik at samme cleanup fungerer for umiddelbare og planlagte kommandoer. Kjernen
aktiveres ikke før slik retention er valgt.

Denne ADR-en standardiserer worker-protokollen, ikke en delt binæravhengighet. En framtidig lib skal
bare trekkes ut dersom claim/lease-, batch- og observability-koden kan deles med et lite interface.
Flyway-versjonering, appens meldingstyper, payload, domeneevaluering, retry-/poison-policy og retention
forblir app-eid.

## Konsekvenser

- Alle replikaer kan arbeide parallelt uten leader election.
- Nettverksarbeid holder ikke en databasetransaksjon eller connection åpen.
- Krasj og cancellation recoveres automatisk etter lease expiry.
- Stale workere kan ikke overskrive en nyere claim.
- Kafka-/brokerfeil og domeneutsettelse har forskjellige mekanismer.
- Kjernen eksponerer lavkardinale målinger for køalder, backlog, utløpte claims og tekniske feil.
- Adapteren må definere operative alarmer for sine meldingstyper før aktivering.
