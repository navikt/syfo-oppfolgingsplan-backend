ALTER TABLE oppfolgingsplan
    RENAME COLUMN sluttdato TO evalueringsdato;

ALTER TABLE oppfolgingsplan_utkast
    RENAME COLUMN sluttdato TO evalueringsdato;
