--liquibase formatted sql

--changeset datacatalog:001-create-users
create table users (
    id          uuid primary key default gen_random_uuid(),
    username    text not null unique,
    created_at  timestamptz not null default now()
);
--rollback drop table users;

--changeset datacatalog:002-create-datasets
create table datasets (
    id                 uuid primary key default gen_random_uuid(),
    name               text not null,
    owner_id           uuid not null references users (id),
    team               text,
    description        text,
    tags               text[] not null default '{}',
    metadata           jsonb not null default '{}',
    latest_version_id  uuid,
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now()
);

create index idx_datasets_owner on datasets (owner_id);
-- GIN with the default jsonb_ops opclass: supports containment (@>) plus
-- key-existence operators, which the search endpoint will rely on
create index idx_datasets_metadata_gin on datasets using gin (metadata);
create index idx_datasets_tags_gin on datasets using gin (tags);
--rollback drop table datasets;

--changeset datacatalog:003-create-file-versions
create table file_versions (
    id              uuid primary key default gen_random_uuid(),
    dataset_id      uuid not null references datasets (id),
    version_number  integer not null,
    size_bytes      bigint,
    checksum        text,
    s3_key          text not null,
    state           text not null check (state in ('PENDING', 'ACTIVE')),
    created_at      timestamptz not null default now(),
    constraint uq_file_versions_dataset_version unique (dataset_id, version_number)
);

create index idx_file_versions_dataset on file_versions (dataset_id);
--rollback drop table file_versions;

--changeset datacatalog:004-datasets-latest-version-fk
-- added after file_versions exists because the two tables reference each other
alter table datasets
    add constraint fk_datasets_latest_version
    foreign key (latest_version_id) references file_versions (id);
--rollback alter table datasets drop constraint fk_datasets_latest_version;
