-- A delivery proof can carry one uploaded file (a JPEG, PNG or PDF). The database holds only a reference:
-- file_key is a random id the server generated, never a name or path the courier chose, and the bytes live in
-- the proof storage directory. The reference is never returned by the API.
ALTER TABLE proof_of_delivery ADD COLUMN file_key VARCHAR(64);
ALTER TABLE proof_of_delivery ADD COLUMN file_name VARCHAR(120);
ALTER TABLE proof_of_delivery ADD COLUMN file_content_type VARCHAR(50);
ALTER TABLE proof_of_delivery ADD COLUMN file_size_bytes BIGINT;
