# Plan: tiltakspakker og eksperimentsegmentering

## Mål

Innføre en generisk løsning for tiltakspakker/eksperimenter der virksomheter kan segmenteres etter ulike regeltyper. Første eksperiment gjelder nye sykmeldinger for virksomheter med adresse i Troms eller Finnmark, men modellen skal støtte andre eksperimenter og regler senere.

## Premisser

- API-respons må være svært rask.
- API skal derfor lese ferdig segmenteringsresultat fra lokal PostgreSQL, ikke gjøre EREG- eller sykmeldingoppslag synkront.
- Hvis segmentering mangler ved API-kall, skal API returnere `UKJENT` eller tom liste og trigge asynkron segmentering. API skal ikke blokkere på EREG eller andre eksterne oppslag.
- Nye sykmeldinger fra Kafka-topic `teamsykmelding.syfo-sendt-sykmelding` er bare en trigger for å oppdage kandidatvirksomheter som kan segmenteres.
- Første regeltype er fylke på virksomhetsadresse fra EREG.
- Fylkesregelen bruker EREG forretningsadresse uten fallback til postadresse. Manglende forretningsadresse gir `KAN_IKKE_EVALUERE`.
- Første eksperiment skal randomisere 50 % inkludert og 50 % ekskludert etter at øvrige kriterier matcher.
- Randomiseringsenhet er arbeidsgiver/virksomhet (`organisasjonsnummer`): alle relevante sykmeldinger hos samme arbeidsgiver i eksperimentperioden skal følge samme gruppe.
- Relevante sykmeldinger for primæranalyse er sykmeldinger som starter etter at arbeidsgiveren er randomisert/tildelt gruppe. Pågående forløp fra før randomisering kan eventuelt analyseres separat som sekundæranalyse.
- Inkluderingsperiode, eksponeringsperiode og analyseperiode må skilles. Stopp i inkludering skal ikke automatisk skru av tiltaket for arbeidsgivere som allerede er i tiltaksgruppen.
- Senere eksperimenter kan bruke andre regeltyper.
- Sykmeldt-frontend opererer på sykmeldt-nivå, mens arbeidsgiver-/backend-apper ofte kan bruke orgnummer direkte.

## Anbefalt arkitektur

```text
Kafka-konsument
  -> mottar sykmelding som trigger
  -> finner virksomhet/orgnummer
  -> slår opp nødvendige kildedata, f.eks. EREG
  -> evaluerer aktivt regelsett
  -> lagrer resultat i tiltakspakke_virksomhet

API
  -> leser kun lokal DB
  -> returnerer tiltakspakker for orgnummer eller innlogget sykmeldt
```

## Datamodell

### tiltakspakke

Representerer et eksperiment eller en tiltakspakke.

```sql
CREATE TABLE tiltakspakke (
    id TEXT PRIMARY KEY,
    navn TEXT NOT NULL,
    beskrivelse TEXT,
    aktiv BOOLEAN DEFAULT TRUE NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT NOW() NOT NULL
);
```

### tiltakspakke_regelsett

Versjonerer reglene for en tiltakspakke. Ved utvidelse underveis opprettes ny versjon i stedet for å endre gammel versjon.

Periodene har ulike formål:

- `inkludering_starter_at`: når nye arbeidsgivere/forløp kan tas inn.
- `inkludering_slutter_at`: når nye arbeidsgivere/forløp ikke lenger skal tas inn.
- `eksponering_slutter_at`: når tiltaket skal slutte å vises for eksisterende tiltaksgruppe.
- `analyse_slutter_at`: hvor lenge utfall skal måles.

`inkludering_slutter_at` skal ikke brukes av API-et til å skjule tiltaket for arbeidsgivere som allerede er `INKLUDERT`. API-et må vurdere eksponering mot `eksponering_slutter_at`.

```sql
CREATE TABLE tiltakspakke_regelsett (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tiltakspakke_id TEXT NOT NULL REFERENCES tiltakspakke(id),
    versjon INTEGER NOT NULL,
    aktiv BOOLEAN DEFAULT TRUE NOT NULL,
    inkludering_starter_at TIMESTAMPTZ,
    inkludering_slutter_at TIMESTAMPTZ,
    eksponering_slutter_at TIMESTAMPTZ,
    analyse_slutter_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    UNIQUE (tiltakspakke_id, versjon)
);

CREATE UNIQUE INDEX idx_tiltakspakke_regelsett_aktiv
    ON tiltakspakke_regelsett (tiltakspakke_id)
    WHERE aktiv = TRUE;
```

### tiltakspakke_segmenteringsregel

Generisk regeldefinisjon. Kode eier tolkningen av `regel_type`; databasen lagrer regeldata og historikk.

```sql
CREATE TABLE tiltakspakke_segmenteringsregel (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    regelsett_id UUID NOT NULL REFERENCES tiltakspakke_regelsett(id),
    regel_type TEXT NOT NULL,
    kriterier JSONB NOT NULL,
    inkluderingsprosent INTEGER,
    randomisering_salt TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

CREATE INDEX idx_tiltakspakke_segmenteringsregel_regelsett
    ON tiltakspakke_segmenteringsregel (regelsett_id);
```

Eksempel:

```sql
regel_type = 'VIRKSOMHET_ADRESSE_FYLKE'
kriterier = '{"fylkesnummer": ["55", "56"]}'::jsonb
inkluderingsprosent = 50
randomisering_salt = 'nye-sykmeldinger-v1'
```

`inkluderingsprosent` og `randomisering_salt` er foreløpig forslag og må avklares. Hvis randomisering skal være felles for hele regelsettet i stedet for per regel, bør feltene flyttes til `tiltakspakke_regelsett`.

### tiltakspakke_virksomhet

Lagrer ferdig evaluert resultat per virksomhet og regelsettversjon.

```sql
CREATE TABLE tiltakspakke_virksomhet (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisasjonsnummer TEXT NOT NULL,
    tiltakspakke_id TEXT NOT NULL REFERENCES tiltakspakke(id),
    regelsett_id UUID NOT NULL REFERENCES tiltakspakke_regelsett(id),
    status TEXT NOT NULL,
    matched_regel_id UUID REFERENCES tiltakspakke_segmenteringsregel(id),
    begrunnelse TEXT NOT NULL,
    randomisering_verdi INTEGER,
    randomisert_at TIMESTAMPTZ,
    evaluerte_data JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    UNIQUE (organisasjonsnummer, tiltakspakke_id, regelsett_id)
);

CREATE INDEX idx_tiltakspakke_virksomhet_lookup
    ON tiltakspakke_virksomhet (organisasjonsnummer, tiltakspakke_id);

CREATE INDEX idx_tiltakspakke_virksomhet_status
    ON tiltakspakke_virksomhet (tiltakspakke_id, status);
```

Foreslåtte statusverdier:

- `INKLUDERT`
- `EKSKLUDERT`
- `UKJENT`
- `KAN_IKKE_EVALUERE`

`randomisering_verdi` lagrer bucket/verdi som ble brukt i evalueringen, slik at beslutningen kan forklares og reproduseres.

`evaluerte_data` skal kun inneholde minimale beslutningsdata, for eksempel:

```json
{
  "regel_type": "VIRKSOMHET_ADRESSE_FYLKE",
  "fylkesnummer": "55",
  "adresse_kilde": "EREG_FORRETNINGSADRESSE"
}
```

Ikke lagre full EREG-respons, virksomhetsnavn, full adresse, fnr eller sykmeldingsdata i `evaluerte_data`. Logg heller ikke slike data i segmenteringsflyten.

## Evalueringsalgoritme

Foreslått første versjon:

1. Finn aktiv tiltakspakke og aktivt regelsett.
2. Bruk innkommende sykmelding kun som trigger for å finne virksomhetens orgnummer.
3. Sjekk at tidspunktet er innenfor inkluderingsperioden før nye arbeidsgivere tas inn.
4. Hvis virksomheten allerede er evaluert for aktivt regelsett, returner lagret resultat.
5. Hent nødvendige kildedata for regeltypen, for eksempel forretningsadresse fra EREG.
6. Evaluer alle regler i aktivt regelsett.
7. Hvis nødvendige kildedata mangler, lagre `KAN_IKKE_EVALUERE` med begrunnelse, for eksempel `MANGLER_FORRETNINGSADRESSE`.
8. Hvis ingen regel matcher, lagre `EKSKLUDERT` med begrunnelse, for eksempel `REGEL_MATCHET_IKKE`.
9. Hvis en regel matcher og regelen har randomisering, beregn stabil randomiseringsverdi for `organisasjonsnummer + tiltakspakke_id + regelsett_id + randomisering_salt`.
10. Inkluder virksomheten hvis randomiseringsverdien er innenfor `inkluderingsprosent`, ellers lagre `EKSKLUDERT`.
11. Lagre `matched_regel_id`, `begrunnelse`, `randomisering_verdi` og minimale `evaluerte_data`.
12. Lagre tidspunktet arbeidsgiveren ble tildelt gruppe i `randomisert_at`.
13. Alle relevante sykmeldinger hos samme `organisasjonsnummer` i eksperimentperioden bruker denne lagrede virksomhetstildelingen.

Randomisering må være deterministisk etter at virksomheten er tildelt. Samme virksomhet må få samme resultat for samme regelsettversjon, og samme virksomhet kan ikke havne både i tiltaksgruppe og kontrollgruppe innenfor samme eksperimentperiode.

Sykmelding er ikke randomiseringsenhet og skal ikke gi ny tildeling per sykmelding. Den starter bare segmentering dersom virksomheten ikke allerede har gyldig tildeling for aktivt regelsett.

Primæranalysen bør bare inkludere sykmeldinger som starter etter `randomisert_at`, fordi tiltaket ikke kan påvirke starten på forløp som allerede var i gang før arbeidsgiveren ble tildelt gruppe.

API-et skal ikke automatisk fjerne tiltaket ved `inkludering_slutter_at`. Eksisterende inkluderte arbeidsgivere skal beholde tiltaket frem til `eksponering_slutter_at` eller til teamet avslutter eksponeringen kontrollert. `eksponering_slutter_at IS NULL` betyr at eksponering fortsetter.

Valgt førsteversjon: bruk deterministisk hash-bucket per orgnummer når Kafka oppdager en ny kandidatvirksomhet. API-et skal uansett bare lese lagret tildeling fra `tiltakspakke_virksomhet`.

Konsekvens: 50/50 er forventet fordeling over tid, men ikke garantert eksakt fordeling i små datamengder.

Eksempel på ønsket logikk:

```text
hvis fylke matcher 55 eller 56:
  bucket = stableHash(orgnummer + tiltakspakke + regelsett + salt) % 100
  hvis bucket < 50:
    status = INKLUDERT
    begrunnelse = REGEL_MATCHET_RANDOMISERT_INN
  ellers:
    status = EKSKLUDERT
    begrunnelse = REGEL_MATCHET_RANDOMISERT_UT
ellers:
  status = EKSKLUDERT
  begrunnelse = REGEL_MATCHET_IKKE
```

Må avklares før implementering:

- Avklart for første versjon: 50/50 gjelder blant virksomheter som matcher faglige regler, altså forretningsadresse i Troms eller Finnmark.
- Avklart for første versjon: fordelingen blir forventet 50/50 over tid, ikke eksakt 50/50.
- Avklart for første versjon: virksomheter oppdages løpende via Kafka.
- Skal eksisterende virksomheter beholde opprinnelig randomisering ved nytt regelsett, eller randomiseres på nytt?
- Skal kontrollgruppen returneres eksplisitt i API-et, eller skjules som `ikke med`?
- Skal randomisering ligge på regel, regelsett eller tiltakspakke?

## Første eksperiment: Troms og Finnmark

```sql
INSERT INTO tiltakspakke (id, navn, beskrivelse)
VALUES (
    'nye-sykmeldinger',
    'Nye sykmeldinger',
    'Eksperiment for nye sykmeldinger'
);

INSERT INTO tiltakspakke_regelsett (tiltakspakke_id, versjon)
VALUES ('nye-sykmeldinger', 1);

INSERT INTO tiltakspakke_segmenteringsregel (
    regelsett_id,
    regel_type,
    kriterier,
    inkluderingsprosent,
    randomisering_salt
)
SELECT
    id,
    'VIRKSOMHET_ADRESSE_FYLKE',
    '{"fylkesnummer": ["55", "56"]}'::jsonb,
    50,
    'nye-sykmeldinger-v1'
FROM tiltakspakke_regelsett
WHERE tiltakspakke_id = 'nye-sykmeldinger'
  AND versjon = 1;
```

## Utvide eksperimentet underveis

Ikke oppdater eksisterende regelsett. Opprett ny versjon:

```sql
UPDATE tiltakspakke_regelsett
SET aktiv = FALSE
WHERE tiltakspakke_id = 'nye-sykmeldinger'
  AND versjon = 1;

INSERT INTO tiltakspakke_regelsett (tiltakspakke_id, versjon)
VALUES ('nye-sykmeldinger', 2);

INSERT INTO tiltakspakke_segmenteringsregel (
    regelsett_id,
    regel_type,
    kriterier,
    inkluderingsprosent,
    randomisering_salt
)
SELECT
    id,
    'VIRKSOMHET_ADRESSE_FYLKE',
    '{"fylkesnummer": ["55", "56", "<nytt_fylkesnummer>"]}'::jsonb,
    50,
    'nye-sykmeldinger-v2'
FROM tiltakspakke_regelsett
WHERE tiltakspakke_id = 'nye-sykmeldinger'
  AND versjon = 2;
```

Når aktivt regelsett endres, må virksomheter som bare er vurdert mot gammel versjon reevalueres. I første versjon skjer nye vurderinger når Kafka oppdager virksomheten på nytt. Hvis teamet senere trenger rask re-evaluering av alle tidligere vurderte virksomheter ved regelendring, må det planlegges som en egen utvidelse.

## API-design

API-kontrakten bør kunne returnere eksplisitt status (`INKLUDERT`, `EKSKLUDERT`, `UKJENT`, `KAN_IKKE_EVALUERE`) til backend-konsumenter for sporbarhet og analyse. Frontend kan mappe `EKSKLUDERT`, `UKJENT` og `KAN_IKKE_EVALUERE` til standardopplevelse, og bare vise tiltak for `INKLUDERT`.

### Orgnummer-basert API

For arbeidsgiver-/backend-apper som kjenner orgnummer:

```http
GET /api/v1/tiltakspakker/virksomhet/{orgnummer}
```

API-et leser resultat for aktivt regelsett der eksponering ikke er avsluttet.

Hvis virksomheten mangler resultat for aktivt regelsett, skal API-et returnere `UKJENT` eller tom liste og trigge asynkron segmentering.

SQL:

```sql
SELECT
    tv.tiltakspakke_id,
    t.navn AS tiltakspakke_navn,
    tv.status,
    tv.begrunnelse,
    tv.matched_regel_id,
    tv.evaluerte_data,
    tr.versjon AS regelsett_versjon,
    tv.updated_at
FROM tiltakspakke_virksomhet tv
JOIN tiltakspakke t
    ON t.id = tv.tiltakspakke_id
JOIN tiltakspakke_regelsett tr
    ON tr.id = tv.regelsett_id
WHERE tv.organisasjonsnummer = ?
  AND t.aktiv = TRUE
  AND tr.aktiv = TRUE
  AND (tr.eksponering_slutter_at IS NULL OR tr.eksponering_slutter_at > NOW())
ORDER BY tv.tiltakspakke_id;
```

Hvis kalleren bare trenger inkluderte tiltakspakker kan dette brukes, men det bør ikke være eneste API-kontrakt fordi kontrollgruppen da skjules for backend-konsumenter og analyse:

```sql
SELECT
    tv.tiltakspakke_id,
    t.navn AS tiltakspakke_navn,
    tr.versjon AS regelsett_versjon,
    tv.updated_at
FROM tiltakspakke_virksomhet tv
JOIN tiltakspakke t
    ON t.id = tv.tiltakspakke_id
JOIN tiltakspakke_regelsett tr
    ON tr.id = tv.regelsett_id
WHERE tv.organisasjonsnummer = ?
  AND tv.status = 'INKLUDERT'
  AND t.aktiv = TRUE
  AND tr.aktiv = TRUE
  AND (tr.eksponering_slutter_at IS NULL OR tr.eksponering_slutter_at > NOW())
ORDER BY tv.tiltakspakke_id;
```

### Sykmeldt-basert API

For sykmeldt-frontend:

```http
GET /api/v1/sykmeldt/tiltakspakker
```

Backend bruker innlogget bruker fra TokenX, finner relevante orgnummer for sykmeldt fra lokal DB, og returnerer tiltakspakker for disse virksomhetene.

Hvis sykmeldt har flere arbeidsgivere, bør responsen enten være per orgnummer eller frontend må angi valgt virksomhet til et sykmeldt-sikret endpoint.

Hvis en relevant virksomhet mangler resultat for aktivt regelsett, skal API-et returnere `UKJENT` for den virksomheten eller utelate tiltakspakken fra en enkel flagg-respons, samtidig som asynkron segmentering trigges.

SQL for alle relevante virksomheter for innlogget sykmeldt:

```sql
WITH relevante_virksomheter AS (
    SELECT DISTINCT sp.organisasjonsnummer
    FROM sykmeldingsperiode sp
    WHERE sp.sykmeldt_fnr = ?
      AND sp.invalidated_at IS NULL
      AND sp.tom >= CURRENT_DATE
)
SELECT
    rv.organisasjonsnummer,
    tv.tiltakspakke_id,
    t.navn AS tiltakspakke_navn,
    tv.status,
    tv.begrunnelse,
    tv.matched_regel_id,
    tv.evaluerte_data,
    tr.versjon AS regelsett_versjon,
    tv.updated_at
FROM relevante_virksomheter rv
JOIN tiltakspakke_virksomhet tv
    ON tv.organisasjonsnummer = rv.organisasjonsnummer
JOIN tiltakspakke t
    ON t.id = tv.tiltakspakke_id
JOIN tiltakspakke_regelsett tr
    ON tr.id = tv.regelsett_id
WHERE t.aktiv = TRUE
  AND tr.aktiv = TRUE
  AND (tr.eksponering_slutter_at IS NULL OR tr.eksponering_slutter_at > NOW())
ORDER BY rv.organisasjonsnummer, tv.tiltakspakke_id;
```

Hvis frontend bare trenger et samlet ja/nei-flagg per tiltakspakke kan backend mappe eksplisitt status til `er_med`. Backend bør likevel kunne hente full status for observability og analyse:

```sql
WITH relevante_virksomheter AS (
    SELECT DISTINCT sp.organisasjonsnummer
    FROM sykmeldingsperiode sp
    WHERE sp.sykmeldt_fnr = ?
      AND sp.invalidated_at IS NULL
      AND sp.tom >= CURRENT_DATE
)
SELECT
    tv.tiltakspakke_id,
    BOOL_OR(tv.status = 'INKLUDERT') AS er_med
FROM relevante_virksomheter rv
JOIN tiltakspakke_virksomhet tv
    ON tv.organisasjonsnummer = rv.organisasjonsnummer
JOIN tiltakspakke t
    ON t.id = tv.tiltakspakke_id
JOIN tiltakspakke_regelsett tr
    ON tr.id = tv.regelsett_id
WHERE t.aktiv = TRUE
  AND tr.aktiv = TRUE
  AND (tr.eksponering_slutter_at IS NULL OR tr.eksponering_slutter_at > NOW())
GROUP BY tv.tiltakspakke_id
ORDER BY tv.tiltakspakke_id;
```

## Viktige avklaringer

- Avklart: fylkesregelen bruker EREG forretningsadresse uten fallback til postadresse.
- Avklart: API bør kunne returnere eksplisitt status til backend-konsumenter. Frontend kan mappe kontrollgruppe og ukjent til standardopplevelse.
- Avklart: ukjent/manglende segmentering skal ikke tolkes som `false`; API returnerer `UKJENT` eller tom liste og trigger asynkron segmentering.
- Avklart: relevante sykmeldinger for primæranalyse er sykmeldinger som starter etter `randomisert_at`.
- Avklart: `eksponering_slutter_at IS NULL` betyr at eksponering fortsetter; `inkludering_slutter_at` skal ikke skru av tiltaket for eksisterende tiltaksgruppe.
- Avklart: `evaluerte_data` skal kun inneholde minimale beslutningsdata, ikke full EREG-respons, adresse, fnr eller sykmeldingsdata.
- Avklart for første versjon: randomisering skjer løpende ved første relevante sykmelding fra Kafka.
- Avklart for første versjon: fordeling er 50/50 blant virksomheter i Troms og Finnmark som matcher faglige regler. Fordelingen er forventet over tid, ikke garantert eksakt i små datamengder.
- Hvilke apper skal ha `accessPolicy.inbound` til API-et?
- Hvor lenge skal evaluerte segmenteringsdata beholdes?

## Grill-oppsummering

Planen er sterkest på API-hastighet og separasjon mellom trigger, tildeling og visning. De største risikoene er nå tydeligere:

1. Randomiseringsstrategi er låst for første versjon til Kafka-trigger og deterministisk hash-bucket per orgnummer. Risikoen er at 50/50 ikke blir eksakt i små datamengder.
2. Eksperimentperioder må modelleres riktig, ellers kan brukere falle tilbake til standard for tidlig eller bli eksponert lenger enn ønsket.
3. Analyse og brukeropplevelse må skille mellom randomisering, faktisk eksponering og utfall.
4. API må ikke skjule `EKSKLUDERT`/kontrollgruppe fra backend, ellers mister dere sporbarhet.
5. Dataminimering må håndheves aktivt, spesielt rundt `evaluerte_data`, logging og EREG-respons.

Anbefalt neste steg før implementering er å bekrefte at forventet 50/50 over tid er akseptabelt for første versjon.

## Implementeringsfaser

1. Lage Flyway-migreringer for tabellene.
2. Lage repository for tiltakspakke, aktivt regelsett og virksomhetsresultat.
3. Lage segmenteringstjeneste som evaluerer `regel_type`.
4. Lage EREG-klient eller gjenbruke eksisterende klient hvis tilgjengelig.
5. Koble segmentering på Kafka-flyt eller egen konsumentgruppe.
6. Lage raske lese-API-er for orgnummer og sykmeldt.
7. Legge til metrikker for evaluering, inkludering, ekskludering og feilsituasjoner.
8. Teste migreringer, repository, regel-evaluering, Kafka-prosessering og API.
