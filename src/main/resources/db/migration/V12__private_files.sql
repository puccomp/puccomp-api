create table stored_files (
    id uuid primary key,
    tenant_id uuid not null references tenants (id),
    filename varchar(120) not null,
    content_type varchar(100) not null,
    size bigint not null check (size > 0 and size <= 5242880),
    bucket varchar(255) not null,
    object_key varchar(255) not null,
    state varchar(20) not null check (state in ('PENDING', 'READY')),
    created_at timestamptz not null default now(),
    unique (tenant_id, id),
    unique (bucket, object_key)
);
create index idx_stored_files_pending on stored_files (created_at) where state = 'PENDING';

alter table candidate_applications add column cv_file_id uuid;
alter table candidate_applications add constraint fk_candidate_applications_cv
    foreign key (tenant_id, cv_file_id) references stored_files (tenant_id, id);
create unique index uq_candidate_applications_cv on candidate_applications (cv_file_id)
    where cv_file_id is not null;
