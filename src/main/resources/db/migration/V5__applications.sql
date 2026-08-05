create table applications (
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    id uuid not null,
    tenant_id uuid not null,
    process_id uuid not null,
    full_name varchar(255) not null,
    email varchar(255) not null,
    phone varchar(50) not null,
    university varchar(255) not null,
    course varchar(255) not null,
    current_term varchar(50) not null,
    linkedin_url varchar(255),
    portfolio_url varchar(255),
    status varchar(50) not null check (status in ('SUBMITTED','IN_REVIEW','ACCEPTED','REJECTED')),
    privacy_consent boolean not null,
    primary key (id),
    constraint fk_applications_selection_process foreign key (process_id) references selection_processes (id)
);

create index idx_applications_process_email on applications (process_id, email);
