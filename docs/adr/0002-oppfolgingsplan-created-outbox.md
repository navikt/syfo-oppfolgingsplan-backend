# ADR 0002: Opprettelsesvarsel bruker den generiske outboxen

- **Status:** Akseptert
- **Dato:** 2026-08-13

## Kontekst

Når arbeidsgiver oppretter en oppfølgingsplan, sender appen et Budstikka-varsel til den sykmeldte.
Den gamle flyten committet planen før Kafka-publisering. Et krasj eller en publiseringsfeil mellom
disse operasjonene kunne derfor etterlate en plan uten varsel.

## Beslutning

Planen og en `OPPFOLGINGSPLAN_CREATED`-kommando opprettes atomisk i den samme eksisterende
JDBC-transaksjonen. Planpersistensen beholder sin etablerte SQL og transaksjonssemantikk; den eneste
nye operasjonen er outbox-inserten før commit. Kommandoen har:

- planens UUID som `dedup_key` og `external_ref`
- det allerede lagrede `event_id` som outbox-UUID og Budstikka-dedupliserings-ID
- tomt JSON-payload; handleren henter mottakeren fra fersk domenetilstand
- planens opprettelsestidspunkt som `available_at`

Handleren avbryter leveringen dersom planen er skjult eller feilregistrert og lagrer
`SOURCE_NO_LONGER_ELIGIBLE`. En manglende plan bryter invarianten om atomisk plan/outbox-opprettelse;
det logges derfor som feil før kommandoen avsluttes med `SOURCE_NOT_FOUND`, fremfor å retryes for
alltid. Etter Kafka-ACK markerer workeren outbox-raden som `SENT`. Dersom databaseoppdateringen
feiler etter ACK, brukes samme event-ID ved retry, og Budstikka dedupliserer.

Alle replikaer kjører worker uten leader election. Kafka-kallet er tidsbegrenset i produsenten og er
kortere enn outbox-leasen.

## Rullerende deploy og rollback

Under utrulling kan gamle podder fremdeles opprette planer og publisere synkront, mens nye podder
oppretter plan og outbox-kommando atomisk. Vi innfører ikke en midlertidig reconciler eller indeks
for den gamle flyten: den ville gitt betydelig kode- og migreringskompleksitet for et kort
utrullingsvindu, og ville uansett ikke gitt en full rollback-mekanisme.

En full rollback til en pre-outbox-versjon kan ikke drenere allerede opprettede outbox-rader.
Rollback etter aktivering er derfor roll-forward eller krever at READY/CLAIMED-rader først dreneres
av en outbox-kompatibel versjon.

## Retention og observability

`SENT` og `CANCELLED` for denne meldingstypen slettes 90 dager etter `completed_at`. Kommandoen kan
bare oppstå i den atomiske planopprettelsen; det finnes ingen reconciler eller reaktivering som kan
opprette den på nytt. Terminal retention er derfor et app-eid operasjonelt og personvernmessig
vindu, ikke en forutsetning for idempotens. Den generiske, lederkoordinerte retention-tasken sletter
i små, avgrensede transaksjoner. Budstikka eier separat retention for sine egne inbox- og
delivery-tabeller.

Worker eksponerer lavkardinale målinger for antall leveringsklare rader, alder på eldste rad,
utløpte claims og uløste tekniske feil. Dev og prod varsler på vedvarende køalder, utløpte leases og
gjentatte feil. Payload, fødselsnummer og varseltekst logges eller tagges ikke.

`OPPFOLGINGSPLAN_CREATED` er den avgrensede identiteten for produsentbenet. For denne typen betyr
`outbox_enqueued_total` eligible. `outbox_terminal_total` skiller Kafka-acknowledged handlerutfall
fra lukkede avvisningsårsaker, og `outbox_created_to_terminal_latency_seconds` måler hele tiden fra
den atomiske plan-/outbox-opprettelsen til terminalutfallet er lagret, inkludert retry og backoff.
Målingene er bevisst avgrenset til denne umiddelbare meldingstypen; planlagte påminnelser har en
annen tidssemantikk og inngår ikke. `handler_acknowledged` beviser ikke behandling i Budstikka
eller levering til sluttbrukeren. Retryforsøk og terminalutfall holdes adskilt; eksisterende
`outbox_messages_total` beskriver forsøk som `retry_scheduled`, `processing_failed` og `claim_lost`.
Latenstimeren eksponerer foreløpig count, sum og max. Histogram-buckets og p95 kommer først når
behandlingsfristen er eksplisitt godkjent; Micrometers korte standardhistogram brukes ikke som en
skjult terskel.

Tellerne er operative signaler, ikke et regnskap: et prosesskrasj etter database-commit og før
metrikkinkrement kan gi undertelling. Avstemming av manglende eller dupliserte hendelser bruker
derfor outbox-/inbox-tilstand og stabil `event_id`, ikke likhet mellom Prometheus-tellere.
