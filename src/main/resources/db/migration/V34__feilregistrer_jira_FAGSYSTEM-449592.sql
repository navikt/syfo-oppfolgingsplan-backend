UPDATE oppfolgingsplan
   SET feilregistrert = now(),
       feilregistrert_aarsak = 'Opprettet på feil person'
WHERE uuid IN ('17f65415-069d-485e-9af0-a005cc5be847', 'e56ff8f5-3e76-41d8-89f9-8c4b60321599');
