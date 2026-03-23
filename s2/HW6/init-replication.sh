#!/bin/bash
set -e

echo "host replication replicator 0.0.0.0/0 md5" >> "$PGDATA/pg_hba.conf"
echo "host all all 0.0.0.0/0 md5" >> "$PGDATA/pg_hba.conf"

psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" <<'EOSQL'
DO $$
BEGIN
   IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'replicator') THEN
      CREATE ROLE replicator WITH REPLICATION LOGIN PASSWORD 'pass';
   END IF;
END
$$;

DO $$
BEGIN
   IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'postgres_exporter') THEN
      CREATE ROLE postgres_exporter LOGIN PASSWORD 'secret';
   END IF;
END
$$;

GRANT pg_monitor TO postgres_exporter;
EOSQL