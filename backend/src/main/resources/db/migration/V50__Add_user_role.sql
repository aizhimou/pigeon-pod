ALTER TABLE user ADD COLUMN role TEXT DEFAULT 'user';
UPDATE user SET role = 'admin' WHERE id = 0;