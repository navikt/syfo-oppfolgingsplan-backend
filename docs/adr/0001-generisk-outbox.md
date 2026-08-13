# ADR 0001: Én generisk outbox for planlagte varsler

- **Status:** Akseptert
- **Dato:** 2026-08-12

## Kontekst

Tjenesten skal etter hvert levere minst tre ulike varsler:

1. varsel til den sykmeldte når en oppfølgingsplan opprettes
2. frivillig påminnelse til arbeidsgiver etter fire uker
3. frivillig evalueringsvarsel ved planens evalueringsdato

De to planlagte varslene må revalidere mottaker, sykefravær og planstatus når tidspunktet nås.
Dette er domeneregler og skal ikke bygges inn i den tekniske leveringsmekanismen.

## Beslutning

Vi bruker én outbox-tabell og én prosessor. Meldingstypene er et lukket Kotlin-enum i applikasjonen,
slik at skrivefeil og meldinger uten en kjent handler avvises ved kompilering. Hver enumverdi har en
eksplisitt, stabil databaseverdi som lagres som `TEXT`. Nye varseltyper krever derfor en bevisst
kodeendring, men ingen databaseendring. Databaseverdier skal ikke gjenbrukes eller endres etter at
de er tatt i bruk.

En domenetransaksjon oppretter outbox-raden sammen med tilstanden som utløser meldingen.
`UNIQUE (message_type, dedup_key)` gjør kommandoen idempotent. `scheduled_at` uttrykker når
meldingen tidligst kan vurderes; umiddelbare meldinger bruker nåtid.

PostgreSQL kan svare med en serialization failure når to `REPEATABLE READ`-transaksjoner oppretter
samme dedup-nøkkel helt samtidig. Enqueue skal derfor ligge i en ren databasetransaksjon med
automatisk replay (`exposedTransaction(maxAttempts > 1)`). Blokken må ikke inneholde eksterne
sideeffekter, siden hele domenetransaksjonen kan kjøres på nytt.

Vanlig enqueue endrer aldri en terminal rad. Dersom en bruker eksplisitt slår på et planlagt
varsel igjen etter at det ble kansellert, kan adapteren eksplisitt reaktivere akkurat den
`CANCELLED`-raden med oppdatert referanse, payload og tidspunkt. En `SENT`-rad kan aldri
reaktiveres.

Outbox-raden inneholder en typeuavhengig `external_ref` og et lite JSON-payload. Dedup-nøkkel og
referanse skal bruke ugjennomsiktige ID-er; payload skal begrenses til det handleren faktisk trenger.
Fødselsnummer, organisasjonsnummer, navn, fritekst og varselinnhold skal aldri lagres her. UUID-er
som kan kobles tilbake til en person eller virksomhet er pseudonyme personopplysninger, ikke
anonyme data, og må behandles deretter. Handleren bruker referansen til å hente fersk domenetilstand
når meldingen behandles.

Hver handler returnerer ett av tre domeneutfall:

- `Sent`: leveringen er bekreftet og raden blir terminal.
- `Cancelled(reason)`: fersk domenetilstand viser at varselet ikke lenger skal sendes. En
  lavkardinal årsak lagres for drift og produktanalyse uten å lagre sensitive detaljer.
- `Deferred(until)`: domenet kan ikke avgjøre eller sende ennå. Raden flyttes til det oppgitte
  tidspunktet uten at dette regnes som en teknisk feil.

Exceptions behandles som tekniske feil. Handlerens retry-policy beregner neste forsøk. Standard er
eksponentiell ventetid fra ett minutt til maksimalt én time. Kjernen har ingen vilkårlig grense som
permanent kaster en melding etter et kort driftsavbrudd; domenet må eksplisitt velge `Cancelled`.
Antall forsøk og siste forsøk lagres for metrikk, varsling og feilsøking.

Prosessoren kjører på alle podder uten leader election. Den låser én klar rad med
`FOR UPDATE SKIP LOCKED`, kjører handleren og oppdaterer status i samme transaksjon. PostgreSQL
koordinerer dermed replikaene: en pod hopper over rader en annen pod har låst og kan behandle neste
rad uten å vente. Dette gir umiddelbar failover og lar kapasiteten skalere med antall podder uten en
separat elector-avhengighet.

Handleren kjører mens databaselåsen holdes. Denne enkle modellen forutsetter at eksternt arbeid er
kort og eksplisitt tidsavgrenset. Dersom en fremtidig handler trenger langsomme oppslag eller lange
batcher, skal vi vurdere en eksplisitt claim/lease-modell i stedet for å holde transaksjonen åpen.

Leveringen er *at least once*: krasj etter broker-ACK, men før database-commit kan gi en ny
publisering. Adapteren må derfor bruke outbox-radens stabile UUID som mottakerens dedupliserings-ID.

## Avgrensning

Denne kjernen registrerer ingen handler eller bakgrunnsoppgave alene. Oppfølgingsplanvarsel,
fireukersvarsel og evalueringsvarsel landes som separate adaptere med egne relevansregler og
kontrakter. Dermed kan fireukers- og evalueringsflyten utvikles uavhengig uten å etablere nye
outbox-varianter.

Før første adapter aktiveres må den dokumentere hvor lenge samme domenekommando realistisk kan
oppstå på nytt. Det intervallet bestemmer hvor lenge terminale rader må beholdes for idempotens, og
det skal innføres en cleanup som sletter dem etterpå. Kjernen aktiveres ikke før slik retention er
valgt; vi setter ikke en tilfeldig global TTL som kan åpne for duplikatvarsler.

## Konsekvenser

- Kafka-/brokerfeil og domeneutsettelse har forskjellige, eksplisitte mekanismer.
- En poison message blokkerer ikke nyere meldinger; den får nytt `scheduled_at` før batchen går
  videre.
- Varsler som ikke lenger skal sendes må kanselleres av handleren med en eksplisitt årsak basert på
  fersk domenetilstand.
- Operasjonelle alarmer må følge alder og antall forsøk for `READY`-rader.
