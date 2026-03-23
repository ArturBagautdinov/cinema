#!/bin/bash
set -e

REPLICA_NAME=$1
PGDATA=/var/lib/postgresql/data
SLOT_NAME="${REPLICA_NAME}_slot"

echo "[$REPLICA_NAME] waiting for primary..."
until /usr/lib/postgresql/16/bin/pg_isready -h postgres-primary -p 5432; do
  sleep 2
done

if [ ! -s "$PGDATA/PG_VERSION" ]; then
  echo "[$REPLICA_NAME] cleaning data dir..."
  rm -rf "${PGDATA:?}"/*

  echo "[$REPLICA_NAME] taking base backup using slot $SLOT_NAME..."
  export PGPASSWORD=pass
  /usr/lib/postgresql/16/bin/pg_basebackup \
    -h postgres-primary \
    -p 5432 \
    -U replicator \
    -D "$PGDATA" \
    -P \
    -R \
    -S "$SLOT_NAME"

  echo "primary_conninfo = 'host=postgres-primary port=5432 user=replicator password=pass application_name=$REPLICA_NAME'" >> "$PGDATA/postgresql.auto.conf"
  echo "primary_slot_name = '$SLOT_NAME'" >> "$PGDATA/postgresql.auto.conf"
  echo "hot_standby = on" >> "$PGDATA/postgresql.auto.conf"

  chown -R postgres:postgres "$PGDATA"
  chmod 700 "$PGDATA"
fi

echo "[$REPLICA_NAME] starting postgres..."
exec su - postgres -c "/usr/lib/postgresql/16/bin/postgres -D $PGDATA"