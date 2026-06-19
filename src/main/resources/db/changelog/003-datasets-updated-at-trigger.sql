--liquibase formatted sql

--changeset datacatalog:003-create-set-updated-at-fn splitStatements:false
-- Keep updated_at correct regardless of which client issues the UPDATE: the DB owns it.
create or replace function set_updated_at() returns trigger as $$
begin
    new.updated_at = now();
    return new;
end;
$$ language plpgsql;
--rollback drop function set_updated_at();

--changeset datacatalog:004-datasets-updated-at-trigger
create trigger datasets_set_updated_at
    before update on datasets
    for each row execute function set_updated_at();
--rollback drop trigger datasets_set_updated_at on datasets;
