-- Transbank removed: it was the last payment rail, so there is currently no way to actually
-- charge anyone. The generic payments table/history stays (a future Stripe integration will
-- likely reuse it), but the Transbank-specific columns and the Transbank Mall commerce-code
-- registration (which had no purpose outside Transbank's split-payment mechanism) are gone.

ALTER TABLE payments DROP COLUMN IF EXISTS provider;
ALTER TABLE payments DROP COLUMN IF EXISTS transbank_token;
ALTER TABLE payments DROP COLUMN IF EXISTS buy_order;

DROP TABLE IF EXISTS sitter_commerce_accounts;
