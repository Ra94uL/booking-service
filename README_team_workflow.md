## Team Workflow

### Branch strategy

We use a feature-branch workflow with `main` as the only long-lived branch.

- `main` is protected and direct pushes are not allowed during normal development.
- All work is done on short-lived feature branches with descriptive names, for example:
  `feature/staging-deploy`, `feature/health-indicator`, or `feature/logging`.
- Changes are merged into `main` through pull requests.
- At least one other team member must review and approve the pull request.
- CI must pass before the pull request can be merged.

#### Why we chose this strategy

We chose a simple feature-branch / trunk-based approach because our team is small and the changes are relatively short-lived.

Using one main branch keeps the workflow simple while feature branches allow each team member to work independently.

Pull requests, code review and CI reduce the risk of broken code reaching `main`. This also makes it easier for the team to see what changed and discuss improvements before merging.

---

## From feature branch to production

Our deployment flow is:

1. A developer creates a feature branch from an up-to-date `main`.
2. The developer makes the change and pushes the branch to GitHub.
3. A pull request is opened against `main`.
4. GitHub Actions automatically runs the tests.
5. Another team member reviews and approves the pull request.
6. When CI is green, the pull request is merged into `main`.
7. A new uniquely tagged Docker image is built and pushed to Docker Hub.
8. The same image is automatically deployed to the staging environment.
9. GitHub Actions verifies the staging deployment using `/actuator/health`.
10. After staging has been verified, the same Docker image can be manually promoted to production using `workflow_dispatch`.
11. GitHub Actions verifies `/actuator/health` in production.

Example:

```text
Feature branch
      ↓
Pull Request
      ↓
Code review + CI
      ↓
Merge to main
      ↓
Build Docker image :29
      ↓
Docker Hub
      ↓
Staging :29
      ↓
Health check
      ↓
Manual workflow_dispatch
      ↓
Production :29
      ↓
Health check
```

The Docker image is built only once. Production reuses the same image that was already deployed and verified in staging.

---

## Environments

The service has two Railway environments.

### Staging

Service:

```text
booking-service-staging
```

Health endpoint:

```text
https://booking-service-staging26.up.railway.app/actuator/health
```

A merge to `main` automatically deploys the newly built Docker image to staging.

### Production

Service:

```text
booking-service
```

Application:

```text
https://booking-service-production-e569.up.railway.app/
```

Health endpoint:

```text
https://booking-service-production-e569.up.railway.app/actuator/health
```

Production deployment is manual. In GitHub Actions we use `workflow_dispatch` and enter the Docker image tag that has already been verified in staging.

---

## Docker image versioning

Every Docker image receives a unique tag using the GitHub Actions run number.

Example:

```text
r94g/booking-service:28
r94g/booking-service:29
```

We do not rely only on `latest`.

A unique image tag makes it possible to identify exactly which version is running in staging and production and also makes rollback easier.

---

## Rollback

If a production deployment causes a problem, we can redeploy a previously working Docker image without rebuilding the application.

Example:

Production currently runs:

```text
r94g/booking-service:29
```

If version `29` has a problem and version `28` was working correctly:

1. Open GitHub Actions.
2. Select the `ci` workflow.
3. Click `Run workflow`.
4. Select `main`.
5. Enter the previous working image tag:

```text
28
```

6. Run the workflow.
7. GitHub Actions deploys:

```text
r94g/booking-service:28
```

8. Verify that Railway production is using image `28`.
9. Verify that `/actuator/health` returns `UP`.

This rollback process was tested by redeploying a previous Docker image through the manual production workflow.

---

## Merge conflict

During the project we intentionally created and resolved a real merge conflict so that the team could practice handling conflicts.

One team member had an open feature branch that changed existing lines in `README.md`.

While that pull request was still open, the same line in `README.md` was changed differently on `main`.

After the change to `main` was pushed, GitHub detected that it could no longer automatically merge the pull request.

GitHub displayed conflict markers similar to:

```text
<<<<<<< feature/conflict_maker
# Conflict Kademina — Booking Conflict
=======
# Pensionat Kademina — Booking Microservice
>>>>>>> main
```

We resolved the conflict by:

1. Opening `Resolve conflicts` in the pull request.
2. Comparing both versions of the conflicting code.
3. Choosing the correct final version.
4. Removing the Git conflict markers.
5. Marking the conflict as resolved.
6. Committing the merge-conflict resolution.
7. Verifying that the pull request was mergeable again.
8. Merging the pull request into `main`.

This demonstrated why conflicts can occur when two developers modify the same part of a file at the same time.

---

## Logging

The service uses application logging with appropriate log levels.

We use:

- `INFO` for normal important application events.
- `WARN` for unexpected situations that the application can still handle.
- `ERROR` when an operation fails.

For example, `BookingServiceImp` logs when a booking is created:

```text
Creating new booking for customerId=1, roomId=1,
checkIn=2026-10-10, checkOut=2026-10-11
```

and after a successful operation:

```text
Booking created successfully with ID=1
```

The logs can be viewed in Railway production and include timestamps, log levels and the class that generated the message.

Sensitive information such as passwords, database credentials, API tokens and other secrets must never be logged.

---

## Health checks

Spring Boot Actuator provides:

```text
/actuator/health
```

Railway uses this endpoint to verify that the deployed booking service is healthy.

The CI/CD pipeline also calls this endpoint after staging and production deployments.

A successful response contains:

```json
{
  "status": "UP"
}
```

This means that the service has started successfully and Spring Boot considers it healthy.

---

## Health indicator: CustomerServiceHealthIndicator

The service has its own health indicator, `CustomerServiceHealthIndicator`, which checks
a service that booking-service depends on: the customer service.

### CustomerServiceHealthIndicator

`CustomerServiceHealthIndicator` is a class that implements Spring Boot's `HealthIndicator`
and is registered as a Spring bean:

```java
@Component("customerService")
public class CustomerServiceHealthIndicator implements HealthIndicator
```

Its result is included in the overall status of `/actuator/health`, so the whole service
reports `DOWN` if the customer service is down.

### Why booking-service depends on customer-service

A booking only stores a `customerId`. The customer data itself lives in the customer service.

Booking-service calls the customer service when bookings are created or edited and when the
booking page lists customers. If the customer service is down, these features stop working,
which is why its status matters for booking-service.

### How RestClient is used

The indicator builds its own Spring `RestClient` with the base URL from `customer-service.url`
and a connect and read timeout of 2 seconds.

The timeouts make sure that a hanging customer service cannot make the health check hang.

### Which endpoint is checked

The indicator sends this request to the customer service:

```text
GET <CUSTOMER_SERVICE_URL>/actuator/health
```

### What causes UP

The customer service answers with a successful (2xx) response.

An `INFO` line is written to the log:

```text
Health check: customer-service is UP
```

### What causes DOWN

A `RestClientException` is thrown. For example:

- the customer service cannot be reached (connection refused, timeout or wrong URL)
- the customer service answers with an error status, such as 404 (no Actuator) or 503 (it reports itself as DOWN)

A `WARN` line is written to the log:

```text
Health check: customer-service is DOWN (ResourceAccessException)
```

### How CUSTOMER_SERVICE_URL is configured in Railway

The base URL is configured through:

```properties
customer-service.url=${CUSTOMER_SERVICE_URL:http://customer-service:8080}
```

The default value is only used locally.

In Railway, the variable `CUSTOMER_SERVICE_URL` is set under the booking-service's
**Variables** and points to the public URL of the deployed customer service.

Example:

```text
https://customer-service-production-2f0b.up.railway.app
```

The variable is configured separately in each Railway environment (staging and production)
and is not hard-coded in the source code.

### How the indicator appears in /actuator/health

The indicator is shown as its own component, `customerService`, because
`management.endpoint.health.show-details=always` is set in `application.properties`.

When the customer service is reachable:

```json
{
  "status": "UP",
  "components": {
    "customerService": {
      "status": "UP",
      "details": { "service": "customer-service" }
    }
  }
}
```

When the customer service is not reachable:

```json
{
  "status": "DOWN",
  "components": {
    "customerService": {
      "status": "DOWN",
      "details": {
        "service": "customer-service",
        "error": "ResourceAccessException"
      }
    }
  }
}
```
Other components, such as `db` and `diskSpace`, are listed in the same way.
The top-level `status` summarizes all components and is `DOWN` if any of them is `DOWN`. The `components` section shows which one it is.
The reason is also written to the booking-service log in Railway.

When the status is `DOWN`, the endpoint answers with HTTP 503, so the platform can see it.

