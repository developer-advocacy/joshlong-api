#!/usr/bin/env bash
export BW_SESSION=${BW_SESSION:-$(bw unlock --raw)}

DB_CREDS=`bw get item developer_advocacy-gke-postgresql-db--production`

USERNAME=$( echo $DB_CREDS |  jq -r '.fields[] | select(.name == "username") | .value' )
PASSWORD=$( echo $DB_CREDS |  jq -r '.fields[] | select(.name == "password") | .value' )
HOSTNAME=$( echo $DB_CREDS |  jq -r '.fields[] | select(.name == "hostname") | .value' )
DB=$( echo $DB_CREDS |  jq -r '.fields[] | select(.name == "database") | .value' )

echo $USERNAME
echo $DB 
echo $PASSWORD
echo $HOSTNAME

PGPASSWORD=$PASSWORD psql "sslmode=disable dbname=$DB user=$USERNAME hostaddr=${HOSTNAME}"