# Smart EV-Charging & Grid Optimizer

Study project for the final exam of the Service Oriented Software course — a Micro-SOA application for optimizing electric vehicle charging. For the architectural description, design rationale, and interaction diagrams, see `project-report-en.docx` at the root of the repository. This file only covers how to set up, run, and test the system.

## Prerequisites

To run the entire system you only need:

- **Docker**
- **Docker Compose**

There is no need to install Java, Maven, Node.js, or any other build tooling on the host machine: every backend service is built through a multi-stage Maven build inside its own container, and the frontend is built with Node.js inside its own Nginx container.

## Installation and Startup

From the root of the repository, run:

```bash
docker compose up -d --build
```

This command builds the images for all eight services and starts the entire infrastructure in the background. Thanks to the `healthcheck` configured on `eureka-server`, dependent services wait until the Service Registry is actually ready before starting up and registering.

After startup, allow up to about 30 seconds for every service to register and for the callers to refresh their local registry cache. Until then, a request may briefly fail with HTTP 503.

### Ports

| Service | Host port | Description |
|---|---|---|
| `frontend-ui` | **8080** | Application web interface |
| `api-gateway` | 9000 | Single entry point for the APIs |
| `eureka-server` | 8761 | Service Registry dashboard |
| `energy-prosumer` | 8084 | Asynchronous prosumer (FLOW-02) |
| `tariff-provider` | dynamic* | REST provider |
| `vehicle-provider` | dynamic* | REST provider |
| `station-provider` | dynamic* | SOAP provider |
| `charging-orchestrator` | dynamic* | Orchestrator prosumer (FLOW-01) |

\* These four services are stateless and support multiple instances, so they declare neither a fixed `container_name` nor a fixed host port — both must be unique in Docker and would make `--scale` fail. Docker assigns a free host port to each replica; find it with:

```bash
docker compose port tariff-provider 8081
docker compose port charging-orchestrator 8085
```

Internal service-to-service traffic is unaffected: it flows over the Docker bridge network on the container ports, resolved through Eureka.

`energy-prosumer` keeps a fixed port for a different reason: it is the only **stateful** service (FLOW-02 tickets live in an in-process `ConcurrentHashMap`), so it cannot be replicated without an external shared store. This is a deliberate, documented design choice.

## Usage and Testing

Once all containers are up, open your browser at:

```
http://localhost:8080
```

From the web interface you can exercise all three flows without any additional tooling:

1. **FLOW-01 — Parallel orchestration with inter-prosumer coordination:** select a vehicle and a charging station. The result panel attributes each value to the service that produced it — technical constraints gathered by `charging-orchestrator` on one side, economic assessment produced by `energy-prosumer` on the other — with the final recommendation shown below as the synthesis reached after the synchronization barrier. A badge reports the number of concurrent integrations and the total round-trip time, which stays in the tens of milliseconds despite four network hops. The raw JSON remains available in a collapsible section.
2. **FLOW-02 — Asynchronous simulation:** start an energy savings simulation and watch the interface receive a ticket immediately (HTTP 202), then poll the status endpoint until the simulation reaches `COMPLETED`.
3. **FLOW-03 — Direct SOAP call:** query `station-provider` directly, bypassing both prosumers. The frontend builds a raw SOAP envelope, POSTs it to `/ws/station` on the Gateway, and parses the XML response client-side — showing that the Gateway routes SOAP traffic exactly like REST traffic, based only on the request path.

### Demo data

All business data is simulated in memory. These values let you reach every recommendation branch on demand:

| Vehicle | Battery | SoC | Purpose |
|---|---|---|---|
| `EV-001` | 50 kWh | 60 % | nominal case: deferring the charge is worth it |
| `EV-002` | 50 kWh | 20 % | critical case: triggers urgent high-power charging |
| `EV-003` | 50 kWh | 90 % | nearly full: the absolute saving is too small to justify waiting |

Station IDs containing `FAST` deliver 150 kW, any other ID delivers 22 kW. The **"simulate weekend tariff"** toggle forces `tariff-provider` to apply the deep overnight discount (0.15 €/kWh instead of 0.28 €/kWh) regardless of the actual day, so both tariff scenarios can be demonstrated on demand.

### Testing from the command line

```bash
# FLOW-01 — parallel orchestration
curl "http://localhost:9000/api/v1/optimize?vehicleId=EV-001&stationId=STATION-FAST-01&simulateWeekend=true"

# FLOW-02 — start the simulation, then poll with the returned ticketId
curl -X POST http://localhost:9000/api/simulations/grid-savings \
  -H 'Content-Type: application/json' \
  -d '{"vehicleId":"EV-001","simulateWeekend":true}'
curl http://localhost:9000/api/simulations/status/<ticketId>

# Energy Prosumer cost estimate (the endpoint the orchestrator consumes internally)
curl "http://localhost:9000/api/v1/energy/cost-estimate?vehicleId=EV-001"

# FLOW-03 — direct SOAP call through the Gateway
curl -X POST http://localhost:9000/ws/station \
  -H 'Content-Type: text/xml;charset=UTF-8' \
  -d '<?xml version="1.0" encoding="UTF-8"?>
      <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                        xmlns:sta="http://soap.smartcharging.com/station">
        <soapenv:Header/>
        <soapenv:Body>
          <sta:GetStationStatus><sta:stationId>STATION-FAST-01</sta:stationId></sta:GetStationStatus>
        </soapenv:Body>
      </soapenv:Envelope>'
```

## API Documentation

Every REST service documents its contract with SpringDoc OpenAPI 3; the SOAP service publishes a WSDL generated by Apache CXF. Because most services use dynamic host ports, resolve the port first with `docker compose port <service> <container-port>`, then open:

| Service | Documentation | URL |
|---|---|---|
| `charging-orchestrator` | Swagger UI | `http://localhost:$(docker compose port charging-orchestrator 8085 \| cut -d: -f2)/swagger-ui.html` |
| `energy-prosumer` | Swagger UI | `http://localhost:8084/swagger-ui.html` |
| `tariff-provider` | Swagger UI | `http://localhost:$(docker compose port tariff-provider 8081 \| cut -d: -f2)/swagger-ui.html` |
| `vehicle-provider` | Swagger UI | `http://localhost:$(docker compose port vehicle-provider 8082 \| cut -d: -f2)/swagger-ui.html` |
| `station-provider` | WSDL | `http://localhost:$(docker compose port station-provider 8083 \| cut -d: -f2)/ws/station?wsdl` |

The raw OpenAPI 3 specification of each REST service is available at `/v3/api-docs`.

## Running Multiple Instances

All stateless services support horizontal scaling. For example:

```bash
docker compose up -d --scale tariff-provider=2 --scale charging-orchestrator=2
```

The new instances register with Eureka under the same logical service-id, and callers pick them up on their next registry fetch (up to about 30 seconds). To observe the client-side load balancing in action:

```bash
docker compose logs -f tariff-provider
```

Each replica logs which instance served the request, so repeated calls to FLOW-01 visibly alternate between them.

To verify automatic reconvergence, stop a single replica with `docker stop <container-name>` — for example `docker stop final-test-tariff-provider-2`, since `docker compose stop tariff-provider` would stop *every* replica of that service — and keep issuing requests until traffic converges back onto the surviving instance. For the measured timings and the rationale behind these scenarios, see `project-report-en.docx`.
