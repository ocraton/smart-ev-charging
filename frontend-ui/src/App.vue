<script setup>
import { onBeforeUnmount, ref } from 'vue';

const flow1VehicleId = ref('EV-001');
const flow1StationId = ref('STATION-FAST-01');
const flow1Loading = ref(false);
const flow1Result = ref(null);
const flow1Error = ref('');

const simulationVehicleId = ref('EV-001');
const simulationTicketId = ref('');
const simulationStatus = ref('IDLE');
const simulationResult = ref(null);
const simulationError = ref('');
const simulationLoading = ref(false);

const flow3StationId = ref('STATION-FAST-01');
const flow3Loading = ref(false);
const flow3Result = ref(null);
const flow3RawResponse = ref('');
const flow3Error = ref('');

let pollIntervalId = null;

function stopPolling() {
  if (pollIntervalId) {
    clearInterval(pollIntervalId);
    pollIntervalId = null;
  }
}

async function calculatePlan() {
  flow1Loading.value = true;
  flow1Error.value = '';
  flow1Result.value = null;

  const query = new URLSearchParams({
    vehicleId: flow1VehicleId.value,
    stationId: flow1StationId.value
  });

  try {
    const response = await fetch(`http://localhost:9000/api/v1/optimize?${query.toString()}`);
    const data = await response.json();

    if (!response.ok) {
      throw new Error(data?.detail || data?.message || 'Errore durante il calcolo del piano.');
    }

    flow1Result.value = data;
  } catch (error) {
    flow1Error.value = error instanceof Error ? error.message : 'Errore imprevisto.';
  } finally {
    flow1Loading.value = false;
  }
}

async function pollSimulationStatus(ticketId) {
  try {
    const response = await fetch(`http://localhost:9000/api/simulations/status/${ticketId}`);
    const data = await response.json();

    if (!response.ok) {
      throw new Error(data?.detail || data?.message || 'Errore nel polling dello stato.');
    }

    const status = data?.status ?? 'UNKNOWN';
    simulationStatus.value = status;

    if (status === 'COMPLETED') {
      simulationResult.value = data;
      simulationLoading.value = false;
      stopPolling();
    }
  } catch (error) {
    simulationError.value = error instanceof Error ? error.message : 'Errore imprevisto nel polling.';
    simulationLoading.value = false;
    stopPolling();
  }
}

async function startSimulation() {
  stopPolling();

  simulationError.value = '';
  simulationResult.value = null;
  simulationTicketId.value = '';
  simulationStatus.value = 'PENDING';
  simulationLoading.value = true;

  try {
    const response = await fetch('http://localhost:9000/api/simulations/grid-savings', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ vehicleId: simulationVehicleId.value })
    });

    const data = await response.json();

    if (response.status !== 202) {
      throw new Error(data?.detail || data?.message || 'Il backend non ha accettato la simulazione.');
    }

    const ticketId = data?.ticketId;

    if (!ticketId) {
      throw new Error('Ticket non presente nella risposta del backend.');
    }

    simulationTicketId.value = ticketId;
    simulationStatus.value = data?.status || 'PENDING';

    pollIntervalId = setInterval(() => {
      simulationStatus.value = 'RUNNING';
      pollSimulationStatus(ticketId);
    }, 2000);

    await pollSimulationStatus(ticketId);
  } catch (error) {
    simulationError.value = error instanceof Error ? error.message : 'Errore imprevisto in avvio simulazione.';
    simulationLoading.value = false;
    stopPolling();
  }
}

async function lookupStation() {
  flow3Loading.value = true;
  flow3Error.value = '';
  flow3Result.value = null;
  flow3RawResponse.value = '';

  const soapEnvelope = `<?xml version="1.0" encoding="UTF-8"?>
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:sta="http://soap.smartcharging.com/station">
   <soapenv:Header/>
   <soapenv:Body>
      <sta:GetStationStatus>
         <sta:stationId>${flow3StationId.value}</sta:stationId>
      </sta:GetStationStatus>
   </soapenv:Body>
</soapenv:Envelope>`;

  try {
    const response = await fetch('http://localhost:9000/ws/station', {
      method: 'POST',
      headers: {
        'Content-Type': 'text/xml;charset=UTF-8',
        SOAPAction: ''
      },
      body: soapEnvelope
    });

    const rawXml = await response.text();
    flow3RawResponse.value = rawXml;

    if (!response.ok) {
      throw new Error(`Il Station Provider ha risposto con status HTTP ${response.status}.`);
    }

    const xmlDoc = new DOMParser().parseFromString(rawXml, 'text/xml');

    if (xmlDoc.querySelector('parsererror')) {
      throw new Error('Risposta SOAP non valida: XML non parsabile.');
    }

    const findValue = (localName) => {
      const match = Array.from(xmlDoc.getElementsByTagName('*'))
        .find((node) => node.localName === localName);
      return match ? match.textContent : null;
    };

    const faultMessage = findValue('faultstring');
    if (faultMessage) {
      throw new Error(`SOAP Fault: ${faultMessage}`);
    }

    flow3Result.value = {
      stationId: findValue('stationId'),
      status: findValue('status'),
      maxPowerKw: findValue('maxPowerKw')
    };
  } catch (error) {
    flow3Error.value = error instanceof Error ? error.message : 'Errore imprevisto nella chiamata SOAP.';
  } finally {
    flow3Loading.value = false;
  }
}

onBeforeUnmount(() => {
  stopPolling();
});
</script>

<template>
  <div class="page-shell">
    <header class="hero">
      <h1>Smart EV-Charging Console</h1>
      <p>Control room per orchestrazione e simulazioni energetiche in tempo reale.</p>
    </header>

    <main class="grid-layout">
      <section class="card flow-card">
        <h2>FLOW-01 · Ottimizzazione Ricarica</h2>
        <p class="subtitle">Chiamata sincrona verso orchestratore parallelo.</p>

        <label for="vehicleId">Vehicle ID</label>
        <input id="vehicleId" v-model="flow1VehicleId" type="text" />

        <label for="stationId">Station ID</label>
        <input id="stationId" v-model="flow1StationId" type="text" />

        <button class="btn primary" :disabled="flow1Loading" @click="calculatePlan">
          {{ flow1Loading ? 'Calcolo in corso...' : 'Calcola Piano' }}
        </button>

        <p v-if="flow1Error" class="status error">{{ flow1Error }}</p>
        <p v-if="flow1Loading" class="status running">Caricamento risposta backend...</p>
        <pre v-if="flow1Result">{{ JSON.stringify(flow1Result, null, 2) }}</pre>
      </section>

      <section class="card flow-card">
        <h2>FLOW-02 · Simulazione con Polling</h2>
        <p class="subtitle">Pattern asincrono: HTTP 202 + monitoraggio stato ticket.</p>

        <label for="simulationVehicleId">Vehicle ID</label>
        <input id="simulationVehicleId" v-model="simulationVehicleId" type="text" />

        <button class="btn secondary" :disabled="simulationLoading" @click="startSimulation">
          {{ simulationLoading ? 'Simulazione in esecuzione...' : 'Avvia Simulazione Consumi' }}
        </button>

        <div class="status-panel">
          <p><strong>Ticket:</strong> {{ simulationTicketId || 'N/A' }}</p>
          <p>
            <strong>Stato:</strong>
            <span
              class="pill"
              :class="{
                pending: simulationStatus === 'PENDING',
                running: simulationStatus === 'RUNNING',
                completed: simulationStatus === 'COMPLETED'
              }"
            >
              {{ simulationStatus }}
            </span>
          </p>
        </div>

        <p v-if="simulationError" class="status error">{{ simulationError }}</p>
        <pre v-if="simulationResult">{{ JSON.stringify(simulationResult, null, 2) }}</pre>
      </section>

      <section class="card flow-card">
        <h2>FLOW-03 · Lookup Diretto Stazione (SOAP)</h2>
        <p class="subtitle">Bypassa il ChargingOrchestrator: il Gateway inoltra la richiesta direttamente allo Station Provider via SOAP/XML.</p>

        <label for="flow3StationId">Station ID</label>
        <input id="flow3StationId" v-model="flow3StationId" type="text" />

        <button class="btn primary" :disabled="flow3Loading" @click="lookupStation">
          {{ flow3Loading ? 'Interrogazione SOAP in corso...' : 'Interroga Stazione (SOAP)' }}
        </button>

        <p v-if="flow3Error" class="status error">{{ flow3Error }}</p>
        <pre v-if="flow3Result">{{ JSON.stringify(flow3Result, null, 2) }}</pre>
        <details v-if="flow3RawResponse">
          <summary>Risposta SOAP grezza (XML)</summary>
          <pre>{{ flow3RawResponse }}</pre>
        </details>
      </section>
    </main>
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Space+Grotesk:wght@400;500;700&family=IBM+Plex+Mono:wght@400;600&display=swap');

:global(*) {
  box-sizing: border-box;
}

:global(body) {
  margin: 0;
  font-family: 'Space Grotesk', sans-serif;
  background:
    radial-gradient(circle at 10% 10%, rgba(255, 183, 3, 0.25), transparent 25%),
    radial-gradient(circle at 90% 90%, rgba(0, 119, 182, 0.25), transparent 30%),
    linear-gradient(145deg, #f7f4ea 0%, #e7eef4 50%, #f3efe4 100%);
  color: #1b263b;
  min-height: 100vh;
}

.page-shell {
  max-width: 1200px;
  margin: 0 auto;
  padding: 32px 20px 40px;
}

.hero {
  margin-bottom: 24px;
}

.hero h1 {
  margin: 0;
  font-size: clamp(1.8rem, 2.5vw, 2.8rem);
  letter-spacing: 0.02em;
}

.hero p {
  margin: 8px 0 0;
  color: #33415c;
}

.grid-layout {
  display: grid;
  gap: 20px;
  grid-template-columns: repeat(2, minmax(280px, 1fr));
}

.flow-card:nth-child(3) {
  grid-column: 1 / -1;
}

.card {
  border: 1px solid rgba(27, 38, 59, 0.12);
  border-radius: 18px;
  padding: 20px;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(6px);
  box-shadow: 0 14px 36px rgba(27, 38, 59, 0.12);
  animation: rise-in 500ms ease both;
}

.flow-card:nth-child(2) {
  animation-delay: 120ms;
}

.subtitle {
  margin-top: 0;
  color: #3a506b;
}

label {
  display: block;
  margin-top: 12px;
  margin-bottom: 6px;
  font-size: 0.9rem;
  color: #33415c;
}

input {
  width: 100%;
  border: 1px solid #bcccdc;
  border-radius: 10px;
  padding: 10px 12px;
  font-family: inherit;
  font-size: 0.95rem;
}

.btn {
  margin-top: 16px;
  width: 100%;
  border: none;
  border-radius: 12px;
  padding: 11px 14px;
  color: #f8f9fa;
  font-weight: 700;
  cursor: pointer;
  transition: transform 180ms ease, opacity 180ms ease;
}

.btn:hover {
  transform: translateY(-1px);
}

.btn:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.primary {
  background: linear-gradient(90deg, #d90429, #ef233c);
}

.secondary {
  background: linear-gradient(90deg, #0077b6, #0096c7);
}

.status {
  margin-top: 14px;
}

.error {
  color: #9d0208;
}

.running {
  color: #005f73;
}

.status-panel {
  margin-top: 14px;
  background: rgba(255, 255, 255, 0.75);
  border: 1px dashed #adb5bd;
  border-radius: 12px;
  padding: 12px;
}

.pill {
  display: inline-block;
  padding: 2px 9px;
  border-radius: 999px;
  font-size: 0.8rem;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.pill.pending {
  background: #ffe8a1;
  color: #7a5d00;
}

.pill.running {
  background: #b3ecff;
  color: #005f73;
}

.pill.completed {
  background: #b7efc5;
  color: #2d6a4f;
}

pre {
  margin-top: 14px;
  padding: 12px;
  border-radius: 12px;
  overflow-x: auto;
  background: #1b263b;
  color: #f5f8ff;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.85rem;
}

details {
  margin-top: 14px;
}

details summary {
  cursor: pointer;
  font-size: 0.9rem;
  color: #33415c;
}

details pre {
  white-space: pre-wrap;
  word-break: break-word;
}

@keyframes rise-in {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 700px) {
  .page-shell {
    padding: 22px 14px 28px;
  }

  .card {
    padding: 16px;
  }

  .grid-layout {
    grid-template-columns: 1fr;
  }
}
</style>
