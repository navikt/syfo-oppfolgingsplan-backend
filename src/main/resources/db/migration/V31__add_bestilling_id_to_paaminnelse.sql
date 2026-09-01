ALTER TABLE paaminnelse
    ADD COLUMN bestilling_id UUID;

UPDATE paaminnelse
SET bestilling_id = uuid;

ALTER TABLE paaminnelse
    ALTER COLUMN bestilling_id SET DEFAULT gen_random_uuid(),
    ALTER COLUMN bestilling_id SET NOT NULL;
