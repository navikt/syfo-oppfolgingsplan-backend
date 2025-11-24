ALTER TABLE oppfolgingsplan_utkast
    DROP COLUMN evalueringsdato;

ALTER TABLE oppfolgingsplan_utkast
    ALTER COLUMN content SET NOT NULL;