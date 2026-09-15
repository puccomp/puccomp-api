-- Os 13 códigos atuais unidos por vírgula já ocupam ~200 dos 255 caracteres. O limite não foi
-- escolhido, veio do padrão do varchar, e cada permissão nova encurta a folga.
alter table personal_access_tokens alter column scopes type text;
