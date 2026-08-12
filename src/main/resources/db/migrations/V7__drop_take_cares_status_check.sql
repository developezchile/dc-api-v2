-- take_cares is shared with the original dc-api (Hibernate-managed) and, like payments before it
-- (see V6's payments_status_check fix), carries a stale CHECK constraint from that schema's
-- TakeCareStatus enum (ON_SITTER/COMPLETED/CANCELLED/LOOKING_FOR_SITTER) — it doesn't know about
-- WAITING_APPROVAL (added when payment approval was moved to gate the start of a sitting, before
-- the job begins, rather than happening after it's COMPLETED) or PAID (already unused before this
-- change). Undetected until now because nothing had ever tried to write a status outside that
-- original four. Dropped rather than replaced, consistent with V6's payments fix and every other
-- status-like column in this schema (pets.status, payouts.status): relies on the Java enum alone,
-- no DB-level CHECK.
ALTER TABLE take_cares DROP CONSTRAINT IF EXISTS take_cares_status_check;
