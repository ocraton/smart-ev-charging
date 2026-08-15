# Smart EV-Charging & Grid Optimizer

Study project for the final exam of the Service Oriented Software course. The educational goal is to design and implement, from scratch, a **Micro-SOA (Service-Oriented Architecture based on Microservices)** that applies the main cloud-native architectural patterns: dynamic service registration and discovery, an API Gateway as the single entry point, hybrid integration between **REST** and **SOAP** protocols, synchronous/parallel orchestration, and asynchronous communication based on polling.

The application domain simulates a smart electric vehicle charging management system, in which several independent services collaborate to compute optimal charging plans and simulate energy savings on the power grid.

## System Architecture

The system consists of the following eight modules, each containerized as its own Docker service:

- **`eureka-server`** — Service Registry based on Netflix Eureka. It is the first service to start and acts as the central registry with which all other microservices dynamically register, enabling service discovery and client-side load balancing.
- **`api-gateway`** — Built with Spring Cloud Gateway, it is the single entry point exposed to external clients. It handles routing of requests to the correct microservice (resolved dynamically via Eureka) and centralized management of CORS policies.
- **`tariff-provider`** *(REST Provider)* — Exposes energy tariff data applicable to charging via REST.
- **`vehicle-provider`** *(REST Provider)* — Exposes information about registered electric vehicles (battery status, capacity, etc.) via REST.
- **`station-provider`** *(SOAP Provider)* — Exposes charging station availability and status data via **SOAP/XML** (Apache CXF), demonstrating the integration of a strict-contract service (WSDL) within a predominantly REST architecture.
- **`charging-orchestrator`** *(Prosumer — FLOW-01)* — Orchestration service that, given a vehicle and a station, invokes the three providers (vehicle, tariff, station) **in parallel** using `CompletableFuture` and a dedicated executor, aggregating the responses into a single optimized charging recommendation returned synchronously to the client.
- **`energy-prosumer`** *(Prosumer — FLOW-02)* — Service implementing an **asynchronous ticket/polling** pattern: it accepts a grid-savings simulation request and immediately returns a ticket (HTTP 202 Accepted) with `PENDING` status, while processing continues in the background; the client periodically polls a status endpoint until completion.
- **`frontend-ui`** — User interface built with **Vue.js**, served by Nginx, that lets you visually test all three flows (parallel orchestration, asynchronous simulation, and a direct SOAP call to `station-provider`) by talking exclusively to the API Gateway.

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

This command builds the images for all eight services and starts the entire infrastructure in the background. Thanks to the `healthcheck` configured on `eureka-server`, dependent services (`api-gateway`, the providers, and the prosumers) wait until the Service Registry is actually ready before starting up and registering.

Services and ports exposed on the host:

| Service | Port | Description |
|---|---|---|
| `eureka-server` | 8761 | Service Registry dashboard |
| `api-gateway` | 9000 | Single entry point for the APIs |
| `tariff-provider` | 8081* | REST provider (direct access for testing/Swagger) |
| `vehicle-provider` | 8082 | REST provider (direct access for testing/Swagger) |
| `station-provider` | 8083 | SOAP provider (direct access for testing/WSDL) |
| `energy-prosumer` | 8084 | Asynchronous prosumer (FLOW-02) |
| `charging-orchestrator` | 8085 | Orchestrator prosumer (FLOW-01) |
| `frontend-ui` | **8080** | Application web interface |

\* `tariff-provider` uses a dynamic host port (Docker assigns a free one automatically) instead of a fixed 8081, so that it can be scaled with `docker compose up --scale tariff-provider=N` without port conflicts. Find the assigned port with `docker compose port tariff-provider 8081`.

## Usage and Testing

Once all containers are up and healthy, open your browser at:

```
http://localhost:8080
```

From the web interface you can test both main application flows without needing any additional tools (Postman, curl, etc.):

1. **FLOW-01 — Parallel orchestration:** select a vehicle and a charging station to invoke the optimization endpoint. The UI will display the aggregated recommendation, the result of three parallel calls to the providers (vehicle, tariff, and station).
2. **FLOW-02 — Asynchronous simulation:** start an energy savings simulation and observe how the interface immediately receives a request ticket, then automatically polls the status until the simulation completes (transitioning from `PENDING` to `COMPLETED`).
3. **FLOW-03 — Direct SOAP call:** query `station-provider` directly from the browser, bypassing both prosumers. The frontend builds a raw SOAP envelope, POSTs it to `/ws/station` on the Gateway, and parses the XML response client-side — showing that the Gateway routes SOAP traffic exactly like REST traffic, based only on the request path, with no protocol awareness.

All requests generated by the frontend pass exclusively through the `api-gateway` (port 9000), which routes them to the correct microservice by resolving it via Eureka.

## Implemented Patterns

This project made it possible to tackle and solve the following technical challenges, typical of a service-oriented architecture in a cloud-native environment:

- **Hybrid REST/SOAP topology:** seamless integration of a SOAP contract-based service (`station-provider`, based on WSDL and Apache CXF) within a predominantly REST ecosystem, showing how a Gateway and an orchestrator can abstract away protocol differences from end clients.
- **Dynamic Service Discovery:** elimination of hardcoded addresses between services thanks to Netflix Eureka, with runtime resolution via client-side load balancing (`lb://`) in both the Gateway and the Prosumer services.
- **Non-blocking asynchronous polling:** implementation of the *fire-and-poll* pattern in `energy-prosumer`, where the client receives an immediate response (202 Accepted) with a reference ticket, decoupling the request from the actual completion of server-side processing.
- **Parallel service orchestration:** in `charging-orchestrator`, use of `CompletableFuture` and a dedicated executor to simultaneously invoke multiple downstream providers, reducing the overall latency perceived by the client compared to sequential invocation.
- **Unified Gateway and centralized CORS:** a single entry point (`api-gateway`) simplifies exposing services to the frontend, centralizing cross-origin security policies.
