alter table selection_processes
    add column search_title text generated always as (lower(immutable_unaccent(title))) stored;

create index idx_selection_processes_search_title
    on selection_processes using gin (search_title gin_trgm_ops);
