<script setup>
import { onBeforeUnmount, ref } from 'vue';

const flow1VehicleId = ref('EV-001');
const flow1StationId = ref('STATION-FAST-01');
// Parametro di sola simulazione/demo: forza il Tariff Provider a comportarsi come weekend
// (sconto notturno profondo) senza dover aspettare un vero sabato/domenica.
const flow1SimulateWeekend = ref(false);
const flow1Loading = ref(false);
const flow1Result = ref(null);
const flow1Error = ref('');
// Latenza totale percepita dal client. Serve a rendere tangibile l'effetto
// dell'orchestrazione parallela: e circa la durata della piu lenta delle quattro
// integrazioni, non la loro somma.
const flow1ElapsedMs = ref(null);

const simulationVehicleId = ref('EV-001');
// Parametro di sola simulazione/demo: forza il Tariff Provider a comportarsi come weekend
// (sconto notturno profondo) senza dover aspettare un vero sabato/domenica.
const simulationSimulateWeekend = ref(false);
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

// Formattatori usati per presentare i contributi dei due prosumer in modo leggibile,
// senza costringere chi guarda a interpretare il JSON grezzo.
const formatEur = (value) => (typeof value === 'number' ? `${value.toFixed(2)} €` : '—');
const formatHour = (hour) => (typeof hour === 'number' ? `${String(hour).padStart(2, '0')}:00` : '—');
const formatNumber = (value, unit) => (typeof value === 'number' ? `${value} ${unit}` : '—');

async function calculatePlan() {
  flow1Loading.value = true;
  flow1Error.value = '';
  flow1Result.value = null;
  flow1ElapsedMs.value = null;

  const query = new URLSearchParams({
    vehicleId: flow1VehicleId.value,
    stationId: flow1StationId.value,
    simulateWeekend: String(flow1SimulateWeekend.value)
  });

  const startedAt = performance.now();

  try {
    const response = await fetch(`http://localhost:9000/api/v1/optimize?${query.toString()}`);
    const data = await response.json();

    if (!response.ok) {
      throw new Error(data?.error || data?.detail || data?.message || 'Errore durante il calcolo del piano.');
    }

    flow1Result.value = data;
    flow1ElapsedMs.value = Math.round(performance.now() - startedAt);
  } catch (error) {
    flow1Error.value = error instanceof Error ? error.message : 'Errore imprevisto.';
  } finally {
    flow1Loading.value = false;
  }
}

async function pollSimulationStatus(ticketId) {
  try {
    const response = await fetch(`http://localhost:9000/api/simulations/status/${ticketId}`);

    if (response.status === 404) {
      throw new Error('Ticket non trovato: la simulazione potrebbe non essere mai stata avviata su questa istanza.');
    }

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
    } else if (status === 'FAILED') {
      simulationError.value = 'La simulazione non è riuscita: uno o più provider (tariff-provider/vehicle-provider) non hanno risposto correttamente.';
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
      body: JSON.stringify({
        vehicleId: simulationVehicleId.value,
        simulateWeekend: simulationSimulateWeekend.value
      })
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

    <section class="card info-card">
      <details class="info-details">
        <summary><h2>Guida ai parametri di simulazione</h2></summary>

        <p class="subtitle">Questi valori sono dati simulati in-memory (non una vera flotta/rete), pensati per dimostrare a comando i diversi rami di raccomandazione di FLOW-01. Lo stesso toggle "Simula tariffa weekend" è disponibile anche in FLOW-02 (agisce solo sui valori di tariffa nel risultato, non genera rami diversi).</p>

        <div class="info-grid">
          <div>
            <h3>Vehicle ID</h3>
            <ul>
              <li><code>EV-001</code> → batteria 50 kWh, SoC 60% (normale)</li>
              <li><code>EV-002</code> → batteria 50 kWh, SoC 20% (critico)</li>
              <li><code>EV-003</code> → batteria 50 kWh, SoC 90% (quasi carico)</li>
              <li>qualsiasi altro ID → default 60% (come EV-001)</li>
            </ul>
          </div>
          <div>
            <h3>Station ID</h3>
            <ul>
              <li>contiene <code>FAST</code> (es. <code>STATION-FAST-01</code>) → 150 kW</li>
              <li>qualsiasi altro (es. <code>STATION-SLOW-01</code>) → 22 kW</li>
            </ul>
          </div>
          <div>
            <h3>Simula tariffa weekend</h3>
            <ul>
              <li>disattivo → tariffa reale del giorno corrente</li>
              <li>attivo → forza lo sconto notturno profondo anche nei feriali</li>
            </ul>
          </div>
        </div>

        <div class="table-scroll">
          <table class="scenario-table">
            <thead>
              <tr>
                <th>Vehicle ID</th>
                <th>Station ID</th>
                <th>Weekend simulato</th>
                <th>Raccomandazione attesa</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td><code>EV-002</code></td>
                <td><code>STATION-FAST-01</code></td>
                <td>indifferente</td>
                <td>Ricarica immediata ad alta potenza: l'urgenza tecnica prevale, ma il costo della fretta viene quantificato</td>
              </tr>
              <tr>
                <td><code>EV-001</code></td>
                <td><code>STATION-SLOW-01</code></td>
                <td>indifferente</td>
                <td>Ricarica differita alla fascia minima: servono 20 kWh, il risparmio giustifica l'attesa</td>
              </tr>
              <tr>
                <td><code>EV-003</code></td>
                <td><code>STATION-SLOW-01</code></td>
                <td>no (default, feriale)</td>
                <td>Ricarica distribuita: mancano solo 5 kWh, differire farebbe risparmiare pochi centesimi</td>
              </tr>
              <tr>
                <td>qualsiasi</td>
                <td>qualsiasi</td>
                <td>sì, in fascia notturna</td>
                <td>Sei già nella fascia più economica: ricarica immediata</td>
              </tr>
            </tbody>
          </table>
        </div>
      </details>
    </section>

    <main class="grid-layout">
      <section class="card flow-card">
        <h2>FLOW-01 · Ottimizzazione Ricarica</h2>
        <p class="subtitle">Chiamata sincrona. L'orchestratore interroga in parallelo i tre provider e l'Energy Prosumer, poi sincronizza i contributi dei due prosumer in un'unica raccomandazione.</p>

        <label for="vehicleId">Vehicle ID</label>
        <input id="vehicleId" v-model="flow1VehicleId" type="text" />

        <label for="stationId">Station ID</label>
        <input id="stationId" v-model="flow1StationId" type="text" />

        <label class="toggle-row" for="flow1SimulateWeekend">
          <input id="flow1SimulateWeekend" v-model="flow1SimulateWeekend" type="checkbox" />
          Simula tariffa weekend (solo demo)
        </label>

        <button class="btn primary" :disabled="flow1Loading" @click="calculatePlan">
          {{ flow1Loading ? 'Calcolo in corso...' : 'Calcola Piano' }}
        </button>

        <p v-if="flow1Error" class="status error">{{ flow1Error }}</p>
        <p v-if="flow1Loading" class="status running">Caricamento risposta backend...</p>

        <div v-if="flow1Result" class="flow1-result">
          <!-- Rende visibile l'effetto dell'orchestrazione parallela: quattro integrazioni
               concorrenti, una latenza sola. -->
          <div
            class="parallel-badge"
            title="Le quattro integrazioni partono insieme su un thread pool dedicato: la durata totale e circa quella della piu lenta, non la somma."
          >
            4 integrazioni in parallelo · risposta completa in {{ flow1ElapsedMs }} ms
          </div>

          <!-- I due contributi sono presentati separatamente perche provengono da due prosumer
               distinti, ciascuno con la propria competenza. Il colore richiama quello usato per
               il servizio altrove nell'interfaccia. -->
          <div class="contrib-grid">
            <div class="contrib contrib-tech">
              <h3>Vincoli tecnici</h3>
              <p class="contrib-source">Charging Orchestrator<br />vehicle · tariff · station (SOAP)</p>
              <dl>
                <div><dt>Veicolo</dt><dd>{{ flow1Result.vehicleId }}</dd></div>
                <div><dt>Batteria</dt><dd>{{ formatNumber(flow1Result.batteryCapacity, 'kWh') }}</dd></div>
                <div><dt>Stato di carica</dt><dd>{{ formatNumber(flow1Result.currentSoC, '%') }}</dd></div>
                <div><dt>Colonnina</dt><dd>{{ flow1Result.stationId }}</dd></div>
                <div><dt>Potenza max</dt><dd>{{ formatNumber(flow1Result.maxPower, 'kW') }}</dd></div>
              </dl>
            </div>

            <div class="contrib contrib-econ">
              <h3>Valutazione economica</h3>
              <p class="contrib-source">Energy Prosumer<br />tariff · vehicle</p>
              <dl>
                <div><dt>Energia mancante</dt><dd>{{ formatNumber(flow1Result.energyNeededKwh, 'kWh') }}</dd></div>
                <div><dt>Costo adesso</dt><dd>{{ formatEur(flow1Result.estimatedCostNowEur) }}</dd></div>
                <div><dt>Fascia minima</dt><dd>{{ formatHour(flow1Result.cheapestHour) }}</dd></div>
                <div><dt>Costo in fascia minima</dt><dd>{{ formatEur(flow1Result.estimatedCostAtCheapestEur) }}</dd></div>
                <div class="highlight"><dt>Risparmio potenziale</dt><dd>{{ formatEur(flow1Result.potentialSavingsEur) }}</dd></div>
              </dl>
            </div>
          </div>

          <div class="synthesis">
            <span class="synthesis-label">Sintesi dopo la barriera di sincronizzazione</span>
            <p>{{ flow1Result.recommendation }}</p>
          </div>

          <details>
            <summary>Risposta JSON grezza</summary>
            <pre>{{ JSON.stringify(flow1Result, null, 2) }}</pre>
          </details>
        </div>
      </section>

      <section class="card flow-card">
        <h2>FLOW-02 · Simulazione con Polling</h2>
        <p class="subtitle">Pattern asincrono: HTTP 202 + monitoraggio stato ticket.</p>

        <label for="simulationVehicleId">Vehicle ID</label>
        <input id="simulationVehicleId" v-model="simulationVehicleId" type="text" />

        <label class="toggle-row" for="simulationSimulateWeekend">
          <input id="simulationSimulateWeekend" v-model="simulationSimulateWeekend" type="checkbox" />
          Simula tariffa weekend (solo demo)
        </label>

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
                completed: simulationStatus === 'COMPLETED',
                failed: simulationStatus === 'FAILED'
              }"
            >
              {{ simulationStatus }}
            </span>
          </p>
        </div>

        <p v-if="simulationError" class="status error">{{ simulationError }}</p>

        <div v-if="simulationResult?.result" class="contrib contrib-econ savings-result">
          <h3>Confronto tariffario e risparmio stimato</h3>
          <p class="contrib-source">Energy Prosumer<br />tariff · vehicle</p>
          <dl>
            <div><dt>Veicolo</dt><dd>{{ simulationResult.result.vehicleId }}</dd></div>
            <div><dt>Energia mancante al pieno</dt><dd>{{ formatNumber(simulationResult.result.energyNeededKwh, 'kWh') }}</dd></div>
            <div><dt>Tariffa di picco</dt><dd>{{ simulationResult.result.peakPricePerKwh }} €/kWh</dd></div>
            <div><dt>Tariffa minima ({{ formatHour(simulationResult.result.cheapestHour) }})</dt><dd>{{ simulationResult.result.offPeakPricePerKwh }} €/kWh</dd></div>
            <div class="highlight"><dt>Risparmio stimato</dt><dd>{{ formatEur(simulationResult.result.estimatedSavingsEur) }}</dd></div>
          </dl>
        </div>

        <details v-if="simulationResult">
          <summary>Risposta JSON grezza</summary>
          <pre>{{ JSON.stringify(simulationResult, null, 2) }}</pre>
        </details>
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
  /* Ogni card si dimensiona sul proprio contenuto: FLOW-01, una volta popolato,
     e sensibilmente piu alto degli altri e non deve trascinare con se le card vicine. */
  align-items: start;
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

.info-card {
  margin-bottom: 20px;
  background: rgba(255, 244, 214, 0.6);
  border-color: rgba(122, 93, 0, 0.18);
}

.info-details {
  margin-top: 0;
}

.info-details summary {
  margin: 0;
  cursor: pointer;
}

.info-details summary h2 {
  display: inline;
  margin: 0;
  font-size: 1.5rem;
  color: #1b263b;
}

.info-details p,
.info-details .info-grid,
.info-details .table-scroll {
  margin-top: 14px;
}

.info-grid {
  display: grid;
  gap: 16px;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
}

.info-grid h3 {
  margin: 0 0 6px;
  font-size: 0.95rem;
}

.info-grid ul {
  margin: 0;
  padding-left: 18px;
  font-size: 0.88rem;
  color: #33415c;
}

.info-grid li {
  margin-bottom: 4px;
}

code {
  background: rgba(27, 38, 59, 0.08);
  border-radius: 4px;
  padding: 1px 5px;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.85em;
}

.table-scroll {
  overflow-x: auto;
  margin-top: 16px;
}

.scenario-table {
  width: 100%;
  min-width: 480px;
  border-collapse: collapse;
  font-size: 0.85rem;
}

.scenario-table th,
.scenario-table td {
  text-align: left;
  padding: 8px 10px;
  border-bottom: 1px solid rgba(27, 38, 59, 0.12);
}

.scenario-table th {
  color: #33415c;
  font-weight: 700;
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

.toggle-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toggle-row input {
  width: auto;
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

.pill.failed {
  background: #ffd6d6;
  color: #9d0208;
}

/* ---- FLOW-01: presentazione dei contributi dei due prosumer ---- */

.parallel-badge {
  display: inline-block;
  margin-top: 14px;
  padding: 5px 11px;
  border-radius: 999px;
  background: rgba(0, 119, 182, 0.12);
  color: #005f73;
  font-size: 0.78rem;
  font-weight: 600;
  cursor: help;
}

.contrib-grid {
  display: grid;
  gap: 12px;
  margin-top: 12px;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
}

.contrib {
  padding: 12px;
  border: 1px solid rgba(27, 38, 59, 0.12);
  border-left-width: 3px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.6);
}

/* Il bordo colorato riprende il colore gia associato a ciascun servizio altrove
   nell'interfaccia: rosso per il Charging Orchestrator (pulsante di FLOW-01),
   blu per l'Energy Prosumer (pulsante di FLOW-02). */
.contrib-tech {
  border-left-color: #d90429;
}

.contrib-econ {
  border-left-color: #0077b6;
}

.contrib h3 {
  margin: 0 0 3px;
  font-size: 0.95rem;
}

.contrib-source {
  margin: 0 0 10px;
  font-size: 0.72rem;
  line-height: 1.35;
  color: #5a6b85;
}

.contrib dl {
  margin: 0;
  display: grid;
  gap: 6px;
}

.contrib dl > div {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
  align-items: baseline;
}

.contrib dt {
  font-size: 0.78rem;
  color: #3a506b;
}

.contrib dd {
  margin: 0;
  text-align: right;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.82rem;
  font-weight: 600;
}

.contrib dl > div.highlight {
  margin-top: 2px;
  padding-top: 7px;
  border-top: 1px dashed rgba(27, 38, 59, 0.18);
}

.contrib-econ .highlight dd {
  font-size: 0.95rem;
  color: #0077b6;
}

.savings-result {
  margin-top: 14px;
}

.synthesis {
  margin-top: 12px;
  padding: 12px;
  border: 1px solid rgba(27, 38, 59, 0.14);
  border-radius: 12px;
  background: rgba(27, 38, 59, 0.05);
}

.synthesis-label {
  display: block;
  margin-bottom: 5px;
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #5a6b85;
}

.synthesis p {
  margin: 0;
  font-size: 0.92rem;
  font-weight: 600;
  line-height: 1.45;
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
