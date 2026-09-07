-- Recuperação de senha é da Account, que é global: sem tenant_id aqui de propósito. Quem é membro
-- de duas EJs tem uma senha só, e o link precisa funcionar sem saber de qual EJ ele veio.
create table password_reset_tokens (
    id uuid primary key,
    account_id uuid not null references accounts (id),
    token_hash varchar(255) not null unique,
    expires_at timestamptz not null,
    used_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

-- Emitir um token novo invalida os anteriores da conta; a varredura é sempre por conta em aberto.
create index idx_password_reset_tokens_outstanding on password_reset_tokens (account_id)
    where used_at is null;
