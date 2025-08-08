ALTER TABLE oppfolgingsplan
    add COLUMN sykemeldt_full_name VARCHAR(255) NOT NULL DEFAULT '',
    add COLUMN org_name VARCHAR(255) NOT NULL DEFAULT '',
    add COLUMN narmeste_leder_full_name VARCHAR(255) NOT NULL DEFAULT '';
