ALTER TABLE oppfolgingsplan
    add COLUMN sykmeldt_full_name VARCHAR(255) NOT NULL,
    add COLUMN org_name VARCHAR(255),
    add COLUMN narmeste_leder_full_name VARCHAR(255);
