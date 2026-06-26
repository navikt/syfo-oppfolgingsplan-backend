UPDATE oppfolgingsplan
SET feilregistrert = now(),
    feilregistrert_aarsak = 'Opprettet på feil person (FAGSYSTEM-436254)'
WHERE uuid = '1907dbad-c9e2-4cd6-a71c-e747f06cc00c';
