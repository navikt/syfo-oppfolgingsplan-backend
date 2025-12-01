-- Add index on sykmeldt_fnr for faster lookups since this column is frequently used in queries
CREATE INDEX IF NOT EXISTS oppfolgingsplan_sykmeldt_fnr_idx ON oppfolgingsplan (sykmeldt_fnr);
CREATE INDEX IF NOT EXISTS oppfolgingsplan_utkast_sykmeldt_fnr_idx ON oppfolgingsplan_utkast (sykmeldt_fnr);
