// tesla.js — handles Tesla auth (token refresh) and API calls
const fs = require('fs');
const path = require('path');
const axios = require('axios');

const TOKEN_FILE = path.join(__dirname, '.tesla-vehicle-token.json');
const CREDS_FILE = path.join(__dirname, '.env.tesla-creds');

function loadCreds() {
  const content = fs.readFileSync(CREDS_FILE, 'utf8');
  const creds = {};
  content.split('\n').forEach((line) => {
    const match = line.match(/export\s+(\w+)="?([^"\n]*)"?/);
    if (match) creds[match[1]] = match[2];
  });
  return creds;
}

function loadTokens() {
  return JSON.parse(fs.readFileSync(TOKEN_FILE, 'utf8'));
}

function saveTokens(tokens) {
  fs.writeFileSync(TOKEN_FILE, JSON.stringify(tokens, null, 2), { mode: 0o600 });
}

const AUTH_URL = 'https://auth.tesla.com/oauth2/v3/token';
const FLEET_API_BASE = 'https://fleet-api.prd.na.vn.cloud.tesla.com';

let cachedAccessToken = null;
let cachedExpiry = 0;

async function getAccessToken() {
  const now = Date.now();
  if (cachedAccessToken && now < cachedExpiry) {
    return cachedAccessToken;
  }

  const creds = loadCreds();
  const tokens = loadTokens();

  const resp = await axios.post(
    AUTH_URL,
    new URLSearchParams({
      grant_type: 'refresh_token',
      client_id: creds.TESLA_CLIENT_ID,
      refresh_token: tokens.refresh_token,
    }),
    { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } }
  );

  const newTokens = resp.data;
  saveTokens(newTokens);

  cachedAccessToken = newTokens.access_token;
  cachedExpiry = now + (newTokens.expires_in - 300) * 1000;

  return cachedAccessToken;
}

async function getVehicleData(vin) {
  const token = await getAccessToken();
  const resp = await axios.get(`${FLEET_API_BASE}/api/1/vehicles/${vin}/vehicle_data`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return resp.data.response;
}

async function listVehicles() {
  const token = await getAccessToken();
  const resp = await axios.get(`${FLEET_API_BASE}/api/1/vehicles`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return resp.data.response;
}

module.exports = { getAccessToken, getVehicleData, listVehicles };
