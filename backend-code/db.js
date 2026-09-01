// db.js — SQLite storage for periodic readings and daily/weekly/monthly summaries
const path = require('path');
const Database = require('better-sqlite3');
const db = new Database(path.join(__dirname, 'data', 'tesla.db'));
db.exec(`
  CREATE TABLE IF NOT EXISTS readings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp TEXT NOT NULL,
    battery_level INTEGER,
    charging_state TEXT,
    charge_energy_added REAL,
    odometer REAL
  );
  CREATE TABLE IF NOT EXISTS daily_summary (
    date TEXT PRIMARY KEY,
    charging_minutes INTEGER DEFAULT 0,
    kwh_added REAL DEFAULT 0,
    km_driven REAL DEFAULT 0,
    start_odometer REAL,
    end_odometer REAL,
    start_battery INTEGER,
    end_battery INTEGER
  );
`);
// Migration-safe: add columns if this is an older existing database
try { db.exec('ALTER TABLE daily_summary ADD COLUMN start_battery INTEGER'); } catch (e) {}
try { db.exec('ALTER TABLE daily_summary ADD COLUMN end_battery INTEGER'); } catch (e) {}
function insertReading(reading) {
  const stmt = db.prepare(`
    INSERT INTO readings (timestamp, battery_level, charging_state, charge_energy_added, odometer)
    VALUES (?, ?, ?, ?, ?)
  `);
  stmt.run(
    reading.timestamp,
    reading.battery_level,
    reading.charging_state,
    reading.charge_energy_added,
    reading.odometer
  );
}
function getReadingsToday() {
  const today = new Date().toISOString().slice(0, 10);
  return db
    .prepare(`SELECT * FROM readings WHERE timestamp LIKE ? ORDER BY timestamp ASC`)
    .all(`${today}%`);
}
function getReadingsRange(days) {
  return db
    .prepare(`SELECT * FROM readings WHERE timestamp >= datetime('now', ?) ORDER BY timestamp ASC`)
    .all(`-${days} days`);
}
function getLatestReading() {
  return db.prepare(`SELECT * FROM readings ORDER BY timestamp DESC LIMIT 1`).get();
}
function upsertDailySummary(date, summary) {
  const stmt = db.prepare(`
    INSERT INTO daily_summary (date, charging_minutes, kwh_added, km_driven, start_odometer, end_odometer, start_battery, end_battery)
    VALUES (@date, @charging_minutes, @kwh_added, @km_driven, @start_odometer, @end_odometer, @start_battery, @end_battery)
    ON CONFLICT(date) DO UPDATE SET
      charging_minutes = excluded.charging_minutes,
      kwh_added = excluded.kwh_added,
      km_driven = excluded.km_driven,
      end_odometer = excluded.end_odometer,
      end_battery = excluded.end_battery
  `);
  stmt.run({ date, ...summary });
}
function getDailySummary(date) {
  return db.prepare(`SELECT * FROM daily_summary WHERE date = ?`).get(date);
}
function getSummaryRange(days) {
  return db
    .prepare(`SELECT * FROM daily_summary WHERE date >= date('now', ?) ORDER BY date ASC`)
    .all(`-${days} days`);
}
// Aggregates daily rows over N days into one summary (used for weekly view)
function getSummaryAggregate(days) {
  const rows = db
    .prepare(`SELECT * FROM daily_summary WHERE date >= date('now', ?) ORDER BY date ASC`)
    .all(`-${days} days`);
  if (rows.length === 0) {
    return { days_count: 0, total_km: 0, total_kwh: 0, start_battery: null, end_battery: null };
  }
  const totalKm = rows.reduce((sum, r) => sum + (r.km_driven || 0), 0);
  const totalKwh = rows.reduce((sum, r) => sum + (r.kwh_added || 0), 0);
  const firstWithBattery = rows.find((r) => r.start_battery != null);
  const lastWithBattery = [...rows].reverse().find((r) => r.end_battery != null);
  return {
    days_count: rows.length,
    total_km: totalKm,
    total_kwh: totalKwh,
    start_battery: firstWithBattery ? firstWithBattery.start_battery : null,
    end_battery: lastWithBattery ? lastWithBattery.end_battery : null,
  };
}
// All daily rows for the current calendar month (1st of month to today)
function getCalendarMonthDays() {
  return db
    .prepare(`SELECT * FROM daily_summary WHERE date >= date('now', 'start of month') ORDER BY date ASC`)
    .all();
}
// Aggregate for the current calendar month
function getCalendarMonthAggregate() {
  const rows = getCalendarMonthDays();
  if (rows.length === 0) {
    return { days_count: 0, total_km: 0, total_kwh: 0, start_battery: null, end_battery: null };
  }
  const totalKm = rows.reduce((sum, r) => sum + (r.km_driven || 0), 0);
  const totalKwh = rows.reduce((sum, r) => sum + (r.kwh_added || 0), 0);
  const firstWithBattery = rows.find((r) => r.start_battery != null);
  const lastWithBattery = [...rows].reverse().find((r) => r.end_battery != null);
  return {
    days_count: rows.length,
    total_km: totalKm,
    total_kwh: totalKwh,
    start_battery: firstWithBattery ? firstWithBattery.start_battery : null,
    end_battery: lastWithBattery ? lastWithBattery.end_battery : null,
  };
}
// Derives individual charging sessions by grouping consecutive "Charging" readings
function getChargingSessions(days) {
  const readings = db
    .prepare(`SELECT * FROM readings WHERE timestamp >= datetime('now', ?) ORDER BY timestamp ASC`)
    .all(`-${days} days`);

  const sessions = [];
  let current = null;

  for (const r of readings) {
    if (r.charging_state === 'Charging') {
      if (!current) {
        current = {
          start: r.timestamp,
          end: r.timestamp,
          startBattery: r.battery_level,
          endBattery: r.battery_level,
          maxEnergy: r.charge_energy_added || 0,
        };
      } else {
        current.end = r.timestamp;
        current.endBattery = r.battery_level;
        if ((r.charge_energy_added || 0) > current.maxEnergy) {
          current.maxEnergy = r.charge_energy_added;
        }
      }
    } else if (current) {
      sessions.push(current);
      current = null;
    }
  }
  if (current) sessions.push(current);

  return sessions.map((s) => ({
    date: s.start.slice(0, 10),
    start_time: s.start,
    end_time: s.end,
    kwh_added: s.maxEnergy,
    start_battery: s.startBattery,
    end_battery: s.endBattery,
  }));
}
module.exports = {
  insertReading,
  getReadingsToday,
  getReadingsRange,
  getLatestReading,
  upsertDailySummary,
  getDailySummary,
  getSummaryRange,
  getSummaryAggregate,
  getChargingSessions,
  getCalendarMonthDays,
  getCalendarMonthAggregate,
};
