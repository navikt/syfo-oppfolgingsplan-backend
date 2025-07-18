ALTER TABLE oppfolgingsplan
    RENAME COLUMN sykemeldt_fnr TO sykmeldt_fnr;

ALTER TABLE oppfolgingsplan_utkast
    RENAME COLUMN sykemeldt_fnr TO sykmeldt_fnr;
