# Smart EV-Charging & Grid Optimizer

Study project for the final exam of the Service Oriented Software course. The educational goal is to design and implement, from scratch, a **Micro-SOA (Service-Oriented Architecture based on Microservices)** that applies the main cloud-native architectural patterns: dynamic service registration and discovery, an API Gateway as the single entry point, hybrid integration between **REST** and **SOAP** protocols, parallel orchestration with inter-prosumer coordination, and asynchronous communication based on polling.

The application domain simulates a smart electric vehicle charging management system, in which several independent services collaborate to compute optimal charging plans and simulate energy savings on the power grid.

## System Architecture

The system consists of the following eight modules, each containerized as its own Docker service:

- **`eureka-server`** — Service Registry based on Netflix Eureka. It is the first service to start and acts as the central registry with which all other microservices dynamically register, enabling service discovery and client-side load balancing.
- **`api-gateway`** — Built with Spring Cloud Gateway, it is the single entry point exposed to external clients. It handles routing of requests to the correct microservice (resolved dynamically via Eureka) and centralized management of CORS policies. Automatic discovery-based routing is deliberately **disabled**, so the public surface of the system is exactly the set of explicitly declared routes.
- **`tariff-provider`** *(REST Provider)* — Exposes hourly energy tariff data via REST. It is the highest fan-in component of the architecture, since it is consumed by both prosumers, and is the primary candidate for horizontal scaling.
- **`vehicle-provider`** *(REST Provider)* — Exposes information about registered electric vehicles (battery capacity, state of charge) via REST.
- **`station-provider`** *(SOAP Provider)* — Exposes charging station availability and status data via **SOAP/XML** (Apache CXF), demonstrating the integration of a strict-contract service (WSDL) within a predominantly REST architecture.
- **`charging-orchestrator`** *(Prosumer — FLOW-01)* — Orchestration service that, given a vehicle and a station, invokes **four** downstream services **in parallel** using `CompletableFuture` on a dedicated executor: the three providers plus the `energy-prosumer`. All four are synchronized on a single `allOf(...).join()` barrier before the results are aggregated into one charging recommendation, returned synchronously to the client.
- **`energy-prosumer`** *(Prosumer — FLOW-02 and cost estimation)* — Serves two complementary purposes. It implements an **asynchronous ticket/polling** pattern for long-running grid-savings simulations (HTTP 202 Accepted + `ticketId`), and it exposes a **synchronous cost-estimate endpoint** that is consumed by the `charging-orchestrator` during FLOW-01. The latter is what makes the two prosumers work in parallel and coordinate before the client is answered.
- **`frontend-ui`** — User interface built with **Vue.js**, served by Nginx, that lets you visually test all three flows by talking exclusively to the API Gateway.

### Inter-prosumer coordination

The two prosumers hold complementary halves of the decision and neither can produce the recommendation alone:

- `charging-orchestrator` gathers the **technical** constraints — state of charge from `vehicle-provider`, deliverable power from `station-provider` over SOAP, and the hourly price list from `tariff-provider`;
- `energy-prosumer` produces the **economic** assessment — how much energy is missing, what it costs to charge right now, and how much would be saved by waiting for the cheapest slot; to do so it consults `tariff-provider` and `vehicle-provider` itself.

Both run at the same time on the orchestrator's thread pool and meet at the synchronization barrier, where their contributions are merged into a single recommendation.

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

1. **FLOW-01 — Parallel orchestration with inter-prosumer coordination:** select a vehicle and a charging station. The UI displays the aggregated recommendation, produced by four parallel calls (three providers plus the other prosumer) joined on a synchronization barrier.
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

The new instances register themselves with Eureka under the same logical service-id, and callers pick them up on their next registry fetch (up to about 30 seconds). To observe the client-side load balancing in action:

```bash
docker compose logs -f tariff-provider
```

Each replica logs which instance served the request, so repeated calls to FLOW-01 visibly alternate between them. Note that a single FLOW-01 request produces **two** reads of the tariff service — one from the orchestrator and one from the energy prosumer — which is precisely why this provider is the first candidate for scaling.

To verify automatic reconvergence, stop one replica with `docker compose stop <container>` and keep issuing requests: after a brief window of transient failures, all traffic converges onto the surviving instance with no manual reconfiguration.

## Implemented Patterns

- **Hybrid REST/SOAP topology:** seamless integration of a SOAP contract-based service (`station-provider`, WSDL + Apache CXF) within a predominantly REST ecosystem, showing how a Gateway and an orchestrator abstract protocol differences away from end clients.
- **Dynamic Service Discovery:** elimination of hardcoded addresses thanks to Netflix Eureka, with runtime resolution via client-side load balancing (`lb://`) in both the Gateway and the prosumers.
- **Parallel service orchestration with a synchronization barrier:** `CompletableFuture` on a dedicated executor invokes four downstream services simultaneously, reducing perceived latency from the sum of the calls to the slowest one.
- **Inter-prosumer coordination:** the two prosumers work in parallel on complementary halves of the problem and synchronize before the client is answered.
- **Non-blocking asynchronous polling:** the *fire-and-poll* pattern in `energy-prosumer`, where the client gets an immediate 202 with a reference ticket. The artificial delay uses `CompletableFuture.delayedExecutor` rather than `Thread.sleep()`, so no pool thread is held while waiting.
- **Unified Gateway with an explicit route whitelist and centralized CORS.**
- **Graceful degradation:** explicit HTTP timeouts, downstream failures translated into a clean HTTP 503 by a `@RestControllerAdvice`, and asynchronous tickets moved to a terminal `FAILED` state instead of hanging forever.
