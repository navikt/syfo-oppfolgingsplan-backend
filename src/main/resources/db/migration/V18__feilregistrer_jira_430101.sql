UPDATE oppfolgingsplan
SET skjult_fra = now(),
    feilregistrert_aarsak = 'Opprettet på feil person'
WHERE uuid = '1f959ae4-cb57-4b8e-82bc-bdb6ab1ed436';
