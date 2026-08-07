# ADR 0001: Generisk outbox for utgående meldinger

- **Status**: Akseptert
- **Dato**: 2026-08-04

## Beslutning

Bestilling av påminnelse og oppretting av en outbox-rad skjer i samme databasetransaksjon. Outbox
er generisk og består av `message_type`, `dedup_key`, `external_ref`, et ikke-PII `payload`,
et `scheduled_at`-tidspunkt og
en enkel status: `KLAR`, `SENDT` eller `IKKE_RELEVANT`.

Tasken kjører hvert minutt. Den plukker den tidligst planlagte `KLAR`-raden der `scheduled_at`
har passert, med `FOR UPDATE SKIP LOCKED`,
vurderer relevans og sender meldingen før status oppdateres og transaksjonen committes. Hver rad
behandles i egen transaksjon. Feil ruller transaksjonen tilbake, slik at raden fortsatt er `KLAR`
og prøves igjen neste minutt. Dette gir at-least-once-levering; mottakeren dedupliserer på det
stabile `eventId` i varseldata.

`UNIQUE (message_type, dedup_key)` hindrer at samme melding opprettes mer enn én gang, også etter
at den er sendt eller blitt irrelevant.

Outbox-raden har ingen fremmednøkkel til påminnelsen. Påminnelse-UUID-en lagres som en
typeuavhengig `external_ref`, som handleren bruker for å slå opp påminnelsen og hente fnr og
orgnr når meldingen vurderes og sendes. `payload` inneholder bare `forlopFom`.

Når utsendingsflagget er av, returnerer handleren `Utsatt`. Raden blir stående `KLAR` uten
statusendring, og tasken prøver den igjen neste minutt. Påminnelseutsendelse er foreløpig kun
aktivert i dev/test.

## Konsekvenser

- En krasj etter Kafka-ack og før databasecommit kan gi duplikatlevering.
- Direkte avbrytelse er ikke innført. Deaktivering setter `bestilt=false`; raden blir
  `IKKE_RELEVANT` når handleren plukker den opp.
- Påminnelsen opprettes med en gang ved bestilling. Planlagt utsending kommer senere;
  `scheduled_at` settes til starten av døgnet i norsk tid, `PAAMINNELSE_ETTER_DAGER` etter
  forløpets startdato.
