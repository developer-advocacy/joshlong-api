#!/usr/bin/env bash

export BW_SESSION=${BW_SESSION:-$(bw unlock --raw)}

SQL_FILE=${SQL_FILE:-${HOME}/Desktop/elephantsql.sql}
GKE_SQL_CREDS=$( bw get item developer_advocacy-gke-postgresql-db--production )
DB_PW=$( echo $GKE_SQL_CREDS   | jq '.. | objects | select(.name=="password") '  | jq -r .value )
DB_HOST=$( echo $GKE_SQL_CREDS | jq '.. | objects | select(.name=="hostname") '  | jq -r .value )
DB_DB=$( echo $GKE_SQL_CREDS   | jq '.. | objects | select(.name=="database") '  | jq -r .value )
DB_USER=$( echo $GKE_SQL_CREDS | jq '.. | objects | select(.name=="username") '  | jq -r .value )

cat $SQL_FILE | PGPASSWORD=$DB_PW psql -U $DB_USER -h $DB_HOST $DB_DB