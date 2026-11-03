CREATE TABLE IF NOT EXISTS grants(seq INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL CHECK(kind IN ('add','set')), seconds INTEGER NOT NULL CHECK(seconds BETWEEN 0 AND 86400), by TEXT NOT NULL, req_id TEXT NOT NULL UNIQUE, created_at INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS tv(id INTEGER PRIMARY KEY CHECK(id=1), last_seen INTEGER NOT NULL, report TEXT NOT NULL, acked_seq INTEGER, adb_bad INTEGER NOT NULL DEFAULT 0);
CREATE TABLE IF NOT EXISTS meta(k TEXT PRIMARY KEY, v TEXT NOT NULL);
INSERT OR IGNORE INTO meta VALUES ('db_id', lower(hex(randomblob(16)))), ('cap_s','3600'), ('enforce','false');
INSERT OR IGNORE INTO meta VALUES('adult_sched', '[{"days":"0123456","from":"00:00","to":"06:00"},{"days":"12345","from":"10:00","to":"16:00"},{"days":"0123456","from":"22:00","to":"24:00"}]');
CREATE TABLE IF NOT EXISTS events(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, who TEXT NOT NULL, kind TEXT NOT NULL, detail TEXT);
CREATE INDEX IF NOT EXISTS events_ts ON events(ts);
CREATE TABLE IF NOT EXISTS usage_daily(date TEXT NOT NULL, app TEXT NOT NULL CHECK(app IN ('youtube','netflix')), seconds INTEGER NOT NULL CHECK(seconds>=0), PRIMARY KEY(date,app));
