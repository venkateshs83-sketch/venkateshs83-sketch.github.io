// server.js — main entrypoint: scheduled Tesla polling + API for the Android app
const express = require('express');
const cron = require('node-cron');
const fs = require('fs');
const path = require('path');

const tesla = require('./tesla');
const db = require('./db');

const app = express();
const PORT = process.env.PORT || 4000;

const API_KEY_FILE = path.join(__dirname, '.api-key');
if (!fs.existsSync(API_KEY_FILE)) {
  const key = require('crypto').randomBytes(24).toString('hex');
  fs.writeFileSync(API_KEY_FILE, key, { mode: 0o600 });
  console.log('Generated new API key, stored in .api-key');
}
const API_KEY = fs.readFileSync(API_KEY_FILE, 'utf8').trim();

function requireApiKey(req, res, next) {
  const provided = req.header('x-api-key');
  if (provided !== API_KEY) {
    return res.status(401).json({ error: 'unauthorized' });
  }
  next();
}

let vehicleVin = null;
async function getVin() {
  if (vehicleVin) return vehicleVin;
  const vehicles = await tesla.listVehicles();
  vehicleVin = vehicles[0].vin;
  return vehicleVin;
}

async function pollVehicle() {
  try {
    const vin = await getVin();
    const data = await tesla.getVehicleData(vin);

    const reading = {
      timestamp: new Date().toISOString(),
      battery_level: data.charge_state?.battery_level ?? null,
      charging_state: data.charge_state?.charging_state ?? null,
      charge_energy_added: data.charge_state?.charge_energy_added ?? null,
      odometer: data.vehicle_state?.odometer ?? null,
    };

    db.insertReading(reading);
    console.log(`[${reading.timestamp}] polled OK — battery ${reading.battery_level}%, ${reading.charging_state}`);
    return reading;
  } catch (err) {
    const msg = err.response?.data || err.message;
    console.log(`[${new Date().toISOString()}] poll skipped:`, msg);
    return null;
  }
}

function rollupToday() {
  const today = new Date().toISOString().slice(0, 10);
  const readings = db.getReadingsToday();
  if (readings.length === 0) return;

  const chargingReadings = readings.filter((r) => r.charging_state === 'Charging');
  const kwhValues = readings.map((r) => r.charge_energy_added).filter((v) => v != null);
  const odometers = readings.map((r) => r.odometer).filter((v) => v != null);
  const batteryReadings = readings.filter((r) => r.battery_level != null);

  const summary = {
    charging_minutes: chargingReadings.length * 15,
    kwh_added: kwhValues.length ? Math.max(...kwhValues) : 0,
    km_driven: odometers.length >= 2 ? (Math.max(...odometers) - Math.min(...odometers)) * 1.60934 : 0,
    start_odometer: odometers.length ? Math.min(...odometers) : null,
    end_odometer: odometers.length ? Math.max(...odometers) : null,
    start_battery: batteryReadings.length ? batteryReadings[0].battery_level : null,
    end_battery: batteryReadings.length ? batteryReadings[batteryReadings.length - 1].battery_level : null,
  };

  db.upsertDailySummary(today, summary);
  console.log(`[${today}] daily rollup updated:`, summary);
}

cron.schedule('*/15 * * * *', pollVehicle);
cron.schedule('0 * * * *', rollupToday);

app.get('/status', requireApiKey, async (req, res) => {
  const latest = db.getLatestReading();
  res.json({ latest });
});

app.get('/summary/today', requireApiKey, (req, res) => {
  const today = new Date().toISOString().slice(0, 10);
  const summary = db.getDailySummary(today) || { date: today, charging_minutes: 0, kwh_added: 0, km_driven: 0 };
  res.json(summary);
});

app.get('/summary/range', requireApiKey, (req, res) => {
  const days = parseInt(req.query.days || '7', 10);
  const summaries = db.getSummaryRange(days);
  res.json(summaries);
});

app.get('/summary/week', requireApiKey, (req, res) => {
  res.json(db.getSummaryAggregate(7));
});

app.get('/summary/month', requireApiKey, (req, res) => {
  res.json(db.getSummaryAggregate(30));
});

app.get('/poll-now', requireApiKey, async (req, res) => {
  const reading = await pollVehicle();
  res.json({ reading });
});

app.get('/health', (req, res) => res.json({ ok: true }));

app.listen(PORT, () => {
  console.log(`Tesla backend listening on port ${PORT}`);
  console.log(`API key (keep private): ${API_KEY}`);
});
