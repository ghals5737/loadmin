INSERT INTO users (id, name)
SELECT x, 'user-' || x FROM SYSTEM_RANGE(1, 20);
