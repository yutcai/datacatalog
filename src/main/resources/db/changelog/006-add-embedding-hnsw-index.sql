--liquibase formatted sql

--changeset datacatalog:008-add-embedding-hnsw-index
-- ANN index for the similarity query. vector_cosine_ops matches the <=> operator the
-- query orders by — with a different opclass the planner would ignore the index.
-- HNSW over IVFFlat: it builds incrementally and needs no training data, so it works
-- on a table that starts empty; IVFFlat's lists are only as good as the rows present
-- when the index is built. NULL embeddings are simply absent from the index.
create index idx_datasets_embedding_hnsw on datasets using hnsw (embedding vector_cosine_ops);
--rollback drop index idx_datasets_embedding_hnsw;
