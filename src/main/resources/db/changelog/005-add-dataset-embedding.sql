--liquibase formatted sql

--changeset datacatalog:007-add-dataset-embedding
-- Nullable: a row may not be embedded yet. 384 dims = the target embedding model
-- (all-MiniLM-L6-v2); the dimension is fixed at schema time. The HNSW index comes
-- with the similarity query (spec step 7), not here.
alter table datasets add column embedding vector(384);
--rollback alter table datasets drop column embedding;
