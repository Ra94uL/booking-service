# # Pensionat Kademina — Booking Microservice

## Part of a conflict system

Pensionat Kademina is split into three independently deployable services:

| Service                             | Repo | Responsibility | Default port |
|-------------------------------------|---|---|---|
| **Blaaaa blablablabla** (this repo) | `pensionat-kademina-booking` | Rooms, bookings, UI, orchestrates the other two services | `8080` |
| Customer service                    | [`pensionat-kademina-customer-service`](https://github.com/gabbson42/pensionat-kademina-customer-service) | Customer records (CRUD REST API) | `8081` (host) / `8080` (container) |
| Rating service                      | [`pensionat-kademina-rating-service`](https://github.com/gabbson42/pensionat-kademina-rating-service) | Guest reviews/ratings, validated against real bookings | `8083` (host) / `8080` (container) |

The booking service is the hub: it renders the UI, owns rooms and bookings, and calls out to the customer service (to list/create/edit/delete customers) and the rating service (to list/submit reviews). It also exposes an internal API (`/api/bookings/check`) that the rating service calls to confirm a guest actually stayed in a room before letting them leave a review.

```
                 ┌────────────────────┐
   Browser  ───▶ │   booking-service    │ ───▶ REST ───▶ customer-service (customers)
  (Thymeleaf UI)  │  (this repo, :8080)  │ ───▶ REST ───▶ rating-service   (reviews)
                 └──────────┬─────────┘
                            │
                     GET /api/bookings/check
                     (called BY rating-service
                      to validate a review)
```

## Tech stack

- Java 25, Spring Boot (`spring-boot-starter-parent` 4.0.6)
- Spring MVC + Thymeleaf (server-rendered HTML views)
- Spring Data JPA + MySQL
- Bean Validation (`spring-boot-starter-validation`)
- springdoc-openapi (OpenAPI/Swagger UI for the REST endpoints)
- Lombok
- Spring's `RestClient` for calling the customer and rating services
- Maven (wrapper included), Docker, Kubernetes manifests (`k8s/`)

## Features

- **Rooms** — browse available rooms (`/Rooms`), seeded with sample single/double, small/large rooms on startup.
- **Bookings** (`/booking`) — create, edit, and delete bookings; search for available rooms by check-in/check-out date and number of guests, with double-booking prevention.
- **Customers** (`/customer`) — create, edit, and delete customers. This page proxies to the customer service and gracefully degrades (shows an error banner) if that service is unavailable.
- **Reviews** (`/reviews`) — view and submit guest reviews. Submission is proxied to the rating service, which rejects reviews from guests who never booked the room.
- **Internal booking-check API** — `GET /api/bookings/check?customerId={id}&roomId={id}` returns `{ "booked": true|false }`, used by the rating service to verify a booking exists before accepting a review.

## Domain model

- `Room` — `id`, `type` (`SINGLE`, `DOUBLE`), `size` (`SMALL`, `LARGE`), and its `Booking`s.
- `Booking` — `id`, `checkInDate`, `checkOutDate`, `numberOfGuests`, `extraBeds`, `customerId` (reference to a customer in the customer service), and the booked `Room`.

## Getting started

### Prerequisites
- Java 25
- MySQL 8 (or run everything via `docker-compose`, see below)
- Maven (or use the included `./mvnw`)

### Run locally with Maven
```bash
./mvnw spring-boot:run
```
By default the app expects a MySQL database configured via:
```properties
spring.datasource.url=jdbc:mysql://<host>:3306/booking_db
spring.datasource.username=springUser
spring.datasource.password=secretPassword
```
Override with the `MYSQL_HOST` environment variable, or point `spring.datasource.url` at your own instance.

It also needs to reach the other two services (defaults shown, overridable via env vars):
```properties
customer-service.url=${CUSTOMER_SERVICE_URL:http://customer-service:8080}
review-service.url=${REVIEW_SERVICE_URL:http://rating-service:8080}
```

### Run the whole system with Docker Compose
This repo contains the top-level `docker-compose.yml` that wires up all three services and their databases. Clone all three repos as sibling directories (`pensionat-kademina-booking/`, `pensionat-kademina-customer-service/`, `pensionat-kademina-rating-service/`) and run:
```bash
export MYSQL_ROOT_PASSWORD=changeme
export LOGIN_DB_USER=springUser
export LOGIN_DB_PASSWORD=secretPassword
docker compose up --build
```
This starts:
- `booking-db`, `customer-db`, `rating_db` — MySQL 8 instances
- `booking-service` on `localhost:8080`
- `customer-service` on `localhost:8081`
- `rating-service` on `localhost:8083`

Then open **http://localhost:8080** to use the app.

### Build & run just this service
```bash
./mvnw clean package
docker build -t pensionat-kademina-booking .
docker run -p 8080:8080 pensionat-kademina-booking
```

### Kubernetes
Manifests for the booking service and its database are in [`k8s/`](k8s):
```bash
kubectl apply -f k8s/booking-db.yaml
kubectl apply -f k8s/booking-service.yaml
```

## API docs

With the app running, interactive API docs (springdoc-openapi) are available at:
- `/swagger-ui.html` (or `/swagger-ui/index.html`)
- `/v3/api-docs`

## Tests

```bash
./mvnw test
```
Unit tests cover the booking, room, and customer services.

## Notes
- The three services share no code; they only communicate over REST, keeping each independently deployable and scalable.

## Team Workflow

### Branch strategy
We use a simple feature-branch workflow with `main` as the only long-lived branch.

- `main` is protected: no direct pushes. A pull request, at least 1 approval from another team member
and a green CI run are required before merging.
- All work is done on short-lived feature branches with descriptive names, e.g. `feature/booking-logging`.
- Branches are merged into `main` only through pull requests.

### From branch to production
1. Create a feature branch from an up-to-date `main`.
2. Push the branch and open a pull request against `main`.
3. CI runs automatically on the PR and must be green.
4. A team member reviews the PR and approves it.
5. The PR is merged into `main`.

### Deployed service
URL of deploy service here
Health check: URL of deploy service/actuator/health

### Merge conflict