UPDATE oppfolgingsplan
SET feilregistrert = now(),
    feilregistrert_aarsak = 'Opprettet på feil person. Jira FAGSYSTEM-455944'
WHERE uuid IN ('f73dcff9-b0b0-4d29-bba7-6bb9be5d8ae7', 'b173d297-8a5f-4760-96bd-f19cc33c8f5e');
