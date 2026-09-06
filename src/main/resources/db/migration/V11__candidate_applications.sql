alter table candidacies rename to candidate_applications;
alter table candidate_applications rename constraint candidacies_pkey to candidate_applications_pkey;
alter index idx_candidacies_tenant rename to idx_candidate_applications_tenant;

alter table candidate_applications add column full_name varchar(255);
alter table candidate_applications add column email varchar(255);
alter table candidate_applications add column phone varchar(50);

update candidate_applications application
set full_name = candidate.full_name,
    email = candidate.email,
    phone = candidate.phone
from candidates candidate
where candidate.id = application.candidate_id
  and candidate.tenant_id = application.tenant_id;

alter table candidate_applications alter column full_name set not null;
alter table candidate_applications alter column email set not null;
alter table candidate_applications alter column phone set not null;

create table candidate_application_links (
    application_id uuid not null,
    link_order integer not null,
    url varchar(500) not null,
    primary key (application_id, link_order),
    constraint fk_candidate_application_links_application foreign key (application_id)
        references candidate_applications (id) on delete cascade
);

insert into candidate_application_links (application_id, link_order, url)
select application.id, link.link_order, link.url
from candidate_applications application
join candidate_links link on link.candidate_id = application.candidate_id;

drop index uq_candidacies_process_candidate;
alter table candidate_applications drop constraint fk_candidacies_candidate;
alter table candidate_applications drop constraint fk_candidacies_selection_process;
alter table candidate_applications drop column candidate_id;
alter table candidate_applications drop column status;

create unique index uq_candidate_applications_process_email
    on candidate_applications (process_id, lower(email));
create index idx_candidate_applications_tenant_email
    on candidate_applications (tenant_id, lower(email));

alter table selection_processes add constraint uq_selection_processes_tenant_id
    unique (tenant_id, id);
alter table candidate_applications add constraint fk_candidate_applications_selection_process
    foreign key (tenant_id, process_id) references selection_processes (tenant_id, id);

drop table candidate_links;
drop table candidates;

update selection_processes set status = 'CLOSED' where status = 'FINISHED';
alter table selection_processes drop constraint selection_processes_status_check;
alter table selection_processes add constraint selection_processes_status_check
    check (status in ('DRAFT', 'OPEN', 'CLOSED', 'CANCELLED'));
alter table selection_processes drop column opens_at;
alter table selection_processes drop column closes_at;
