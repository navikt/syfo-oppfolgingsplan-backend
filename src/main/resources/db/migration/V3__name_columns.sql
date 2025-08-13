ALTER TABLE oppfolgingsplan
    ADD COLUMN sykmeldt_full_name VARCHAR(255) NOT NULL,
    ADD COLUMN organisasjonsnavn VARCHAR(255),
    ADD COLUMN narmeste_leder_full_name VARCHAR(255);


ALTER TABLE oppfolgingsplan
    RENAME COLUMN orgnummer TO organisasjonsnummer;

ALTER TABLE oppfolgingsplan_utkast
    RENAME COLUMN orgnummer TO organisasjonsnummer;
