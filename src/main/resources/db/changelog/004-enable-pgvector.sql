--liquibase formatted sql

--changeset datacatalog:006-enable-pgvector
-- Requires an image that ships the extension (pgvector/pgvector:pg16 in compose and tests).
create extension if not exists vector;
--rollback drop extension vector;
