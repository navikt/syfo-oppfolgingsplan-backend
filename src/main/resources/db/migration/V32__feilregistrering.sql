UPDATE oppfolgingsplan
SET feilregistrert = now(),
    feilregistrert_aarsak = 'Opprettet på feil person'
WHERE uuid = 'd7d18ab4-aa0e-4fbc-a5af-0518580cecdc';
