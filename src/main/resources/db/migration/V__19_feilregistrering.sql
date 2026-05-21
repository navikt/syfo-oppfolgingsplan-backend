UPDATE oppfolgingsplan
SET feilregistrert = now(),
    feilregistrert_aarsak = 'Opprettet på feil person'
WHERE uuid = '4de6ad77-a615-4468-b647-f37b14a472cc';
