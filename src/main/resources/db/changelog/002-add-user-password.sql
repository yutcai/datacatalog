--liquibase formatted sql

--changeset datacatalog:005-add-user-password
-- Nullable on purpose: a user may authenticate without a local password
-- (e.g. federated/SSO identities added in a later phase). Password login
-- requires it; registration always sets it.
alter table users add column password_hash text;
--rollback alter table users drop column password_hash;
