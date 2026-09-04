#!/bin/bash
# PostgreSQL 컨테이너 최초 시작 시 자동 실행
# $POSTGRES_DB(novelcraft_vectors)에 pgvector 익스텐션 활성화
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE EXTENSION IF NOT EXISTS vector;
EOSQL
