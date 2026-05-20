#!/bin/bash

echo Creating a nomad dev environment:

docker compose build
docker compose up -d
docker compose exec minio sh /usr/local/bin/init-minio.sh
docker compose exec nomad sh /usr/local/bin/init-nomad.sh

echo Grab the NOMAD_TOKEN environment
