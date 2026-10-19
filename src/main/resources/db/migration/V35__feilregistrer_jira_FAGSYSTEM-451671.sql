UPDATE oppfolgingsplan
SET feilregistrert = now(),
    feilregistrert_aarsak = 'FAGSYSTEM-451671: Opprettet på feil person'
WHERE uuid = '54f13022-ecdd-467d-a4fa-f67ede51bdc0';
