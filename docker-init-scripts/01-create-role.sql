-- Create the cloudsqliamuser role if it doesn't exist
DO $$
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'cloudsqliamuser') THEN
            CREATE ROLE cloudsqliamuser WITH LOGIN;
        END IF;
    END
$$;
