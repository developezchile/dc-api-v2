-- Fintoc removed: owner bank-transfer payments and the Fintoc-transfer-based sitter payout ledger
-- are both gone. Transbank Webpay Plus Mall remains the only payment rail (it settles the
-- sitter's share directly via their commerce code at charge time, so it never needed a separate
-- payout step). A future payment provider (e.g. Stripe) will need its own payout mechanism design
-- — this does not attempt to anticipate that shape.

DROP TABLE IF EXISTS payouts;
DROP TABLE IF EXISTS sitter_bank_accounts;

ALTER TABLE payments DROP COLUMN IF EXISTS fintoc_payment_intent_id;
