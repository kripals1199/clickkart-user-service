# ClickKart User Service

Customer profile and shipping address book. Service #5 of the platform's 14, port **8085**.

Auth Service owns *identity* — credentials, roles, lockout state, the email and mobile number used
to sign in. This service owns everything about a person that is **not** a credential: their name,
date of birth, marketing consent, locale preferences, and their saved delivery addresses. The two
are joined only by `userPublicId`, which is Auth Service's `publicId` and also the JWT `sub` claim.
No foreign key spans the two databases, because they are two databases, each reachable only by its
own least-privilege role.

There is deliberately **no local copy of the customer's email or mobile number**. Duplicating them
would create a second source of truth that goes stale the moment someone changes their sign-in
address, and would widen the blast radius if this service's database were ever compromised.

---

## The security property this service exists to demonstrate

This is the first ClickKart service that is both customer-facing and holds personal data, so it is
the first one where getting authorization wrong is directly exploitable.

**The Gateway's `X-User-Id` and `X-User-Roles` headers are ignored.** The Gateway sets them after
its own JWT validation, and its Javadoc describes them as removing the need for downstream services
to re-validate. Trusting them here would make caller identity a client-supplied string: anything
able to open a socket to this service — another pod, a misconfigured ingress, a port-forward,
anyone on the cluster network — could send `X-User-Id: <someone-else>` with no token at all and
read or rewrite that customer's address book.

Instead, [`JwtAuthenticationFilter`](src/main/java/com/clickkart/user/jwt/JwtAuthenticationFilter.java)
independently runs three checks on every request:

1. **Signature and expiry**, against the shared HMAC secret.
2. **Revocation**, against the same `revoked:jti:<jti>` Redis keyspace Auth Service writes on
   logout — so logging out takes effect here immediately rather than at natural token expiry. If
   Redis is unreachable the request fails with 503; treating an outage as "not revoked" would
   silently restore access to every logged-out token.
3. **Presence of the `correlationId` claim** minted by Auth Service at login (Rule 13 — this
   service is a correlation-id receiver and never mints its own).

`JwtAuthenticationFilterTest` pins this behaviour, including the case where a request carries
spoofed Gateway headers and no token at all.

### Horizontal privilege escalation

Every self-service route is rooted at `/me` and takes no user id, so "read someone else's profile"
is not a request this API can express. The one id-bearing self-service path — `addressId` — is
always resolved *within* the caller's own profile by a single query
(`findByIdAndProfileUserPublicIdAndDeletedFalse`), which folds the ownership check into the lookup
so no caller can forget it.

A foreign or non-existent id both return **404, never 403**. A 403 would confirm the id exists and
belongs to someone, turning the endpoint into an oracle for enumerating other customers' rows.

---

## API

All endpoints require a valid access token, **including reads** — an address book is personal data,
not public catalog content. Reachable through the Gateway at the same paths.

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/users/me` | Fetch own profile, creating an empty one on first access |
| `PUT` | `/api/v1/users/me` | Replace own editable profile fields |
| `PUT` | `/api/v1/users/me/preferences` | Update marketing consent and locale |
| `GET` | `/api/v1/users/me/addresses` | List saved addresses, default first |
| `POST` | `/api/v1/users/me/addresses` | Save a new address |
| `GET` | `/api/v1/users/me/addresses/{addressId}` | Fetch one saved address |
| `PUT` | `/api/v1/users/me/addresses/{addressId}` | Replace a saved address |
| `DELETE` | `/api/v1/users/me/addresses/{addressId}` | Remove a saved address (soft delete) |
| `PUT` | `/api/v1/users/me/addresses/{addressId}/default` | Make this the default delivery address |
| `DELETE` | `/api/v1/users/me` | Erase own personal data (irreversible) |
| `GET` | `/api/v1/users/me/seller` | Fetch own seller business profile |
| `PUT` | `/api/v1/users/me/seller` | Create/update own seller profile (**ROLE_SELLER**) |
| `GET` | `/api/v1/users` | **ADMIN** — browse profiles, optional `?search=` |
| `GET` | `/api/v1/users/{userPublicId}` | **ADMIN** — fetch one customer's profile |
| `GET` | `/api/v1/users/sellers` | **ADMIN** — seller work queue, optional `?status=` |
| `PUT` | `/api/v1/users/{userPublicId}/seller/verification` | **ADMIN** — approve or reject a seller |
| `DELETE` | `/api/v1/users/{userPublicId}` | **ADMIN** — erase a customer's data on their behalf |

The admin endpoints are read-only by design. An operator may need to look a customer up for a
support case, but editing someone else's profile on their behalf is not a flow this platform has —
and the change would not be attributable to the customer who supposedly made it.

Swagger UI: `/swagger-ui.html`, and in the Gateway's aggregated dropdown as *User Service*.

### Internal API (service-to-service)

`/internal/v1/users/**` exists because Order Service must snapshot a shipping address at checkout,
where there is no customer JWT to act on — a retry, a queued step or a reconciliation job has no
token at all.

| Method | Path | Caller |
|---|---|---|
| `GET` | `/internal/v1/users/{userPublicId}` | resolve one profile |
| `POST` | `/internal/v1/users/lookup` | resolve up to 200 profiles in one call |
| `GET` | `/internal/v1/users/{userPublicId}/addresses/{addressId}` | Order — snapshot a shipping address |
| `GET` | `/internal/v1/users/{userPublicId}/addresses/default` | Cart/checkout — pre-fill |
| `GET` | `/internal/v1/users/{userPublicId}/seller` | Product — attribute a listing, gate on verification |

Authenticated by `X-Internal-Api-Key` (constant-time compared) plus the usual `X-Correlation-Id`.
**Not** by network position: these endpoints have no Gateway route and the k8s Service is ClusterIP,
but that is routing, not authorization — the same argument this service makes for ignoring
`X-User-Id`. Anything already inside the cluster can reach the port, so the secret is the gate.

The key is deliberately **not** `JWT_SECRET`: that one signs tokens and is held by three services,
whereas this authenticates callers. Sharing one value would mean anything able to validate a token
could also read any user's address.

Every operation is a **read**, so a leaked key is a disclosure problem rather than a tampering one.
Address lookups are still scoped by owner — passing a mismatched `(user, address)` pair returns 404,
so the ownership rule lives here rather than in each calling service. The whole surface is excluded
from the published OpenAPI spec.

---

## Behaviours worth knowing

**Profiles are created lazily.** A profile does not exist until the customer first touches `/me`.
The alternative — Auth Service calling this service during registration — would couple registration
to this service's availability and need a distributed transaction to stay consistent. A valid
access token is already proof the identity exists, so the profile is materialised the first time
it is actually needed. Two concurrent first-time requests are handled by the unique constraint on
`user_public_id`: the loser of the race re-reads the winner's row rather than failing.

**Addresses are soft-deleted.** An address is referenced by orders that have already shipped to it.
Physically removing the row would either break that reference or silently rewrite history for a
past order. Order Service does not exist yet, so nothing depends on this today — doing it now is
far cheaper than migrating a populated table later. The customer-facing effect is identical to a
delete: every read path filters `deleted = false`.

**Default-address invariants.** At most one default per customer, enforced by demoting all others
in a single bulk `UPDATE` before promoting the target rather than by an in-memory loop that two
concurrent requests could interleave. And a customer with at least one live address always has a
default: the first address saved is promoted automatically, deleting the current default promotes
the oldest survivor, and an update may promote but never silently demote.

**Audit details never carry personal data.** The trail records the *shape* of a change — which
fields are now set, how many addresses — not its content, because the audit trail is queryable by
operators and outlives the profile row itself. Marketing consent is the one exception: its actual
values are recorded, since proving what a customer consented to and when is the entire point of
auditing consent.

**The Audit Log Service is a required dependency.** If it is unreachable, the write fails and rolls
back rather than persisting a change with no audit entry. An unaudited write is a permanent,
undetectable gap in a tamper-evident chain; a failed request is visible and retryable.

**Sellers cannot verify themselves.** `verificationStatus` is absent from the request DTO entirely,
so only the ADMIN endpoint can move a seller out of `PENDING`. Changing business name or GSTIN
withdraws an existing verification — otherwise a seller could pass the check with one legitimate
registration and quietly swap in another, carrying the verified badge onto a business nobody looked
at. Changing contact details or pickup address does not, since that is not what was verified.

A rejection **requires a note**, so the seller is told what to fix rather than hitting a dead end.
The decision is audited against the ADMIN who made it, not the seller — the trail has to answer
"who approved this business".

**Erasure scrubs, it does not delete rows.** `DELETE /me` clears every personal field, withdraws
marketing consent, and overwrites each saved address rather than merely flagging it — a soft delete
alone would leave the full address recoverable in the table. The rows themselves survive, because
`userPublicId` is referenced by the append-only audit chain: deleting them would leave audit entries
pointing at nothing, and rewriting the chain to match is exactly what the chain exists to prevent.

This is also why Order Service must **snapshot** an address at checkout rather than hold a foreign
key to it. An order's record of where it shipped is subject to statutory retention and must survive
a later erasure request; this row must not. Two obligations on the same data, which only separate
copies can satisfy.

Erasure is irreversible: reads still succeed and report `erasedAt`, but every write returns 409.
That guard matters more than it looks — the self-service endpoints auto-provision on first access,
so without it the very next request would quietly repopulate a profile someone asked to empty.
Repeating the request is idempotent rather than an error.

**A seller account cannot erase itself.** A GSTIN and its trading history carry statutory retention
under Indian GST rules, and erasing the business identity would orphan whatever the seller has
listed. Self-service returns 409 explaining why; the ADMIN endpoint proceeds, on the basis that an
operator has already handled the judgement a self-service endpoint cannot make. The scrubbed GSTIN
is per-row (`ERASED-<id>`) because the column is unique — a fixed marker would collide with the next
erased seller and fail the erasure at exactly the wrong moment.

**A seller's pickup address must be one of their own.** They control that field, so an unchecked id
would let them read back another customer's address through their own seller profile. Deleting the
referenced address clears the reference, rather than leaving couriers pointed at a row the customer
believes they deleted.

---

## Running it

Needs Config Server, Eureka, Audit Log Service, Postgres and the shared revocation Redis. From the
platform repo:

```bash
docker compose -f docker-compose.dev-infra.yml -f docker-compose.app-tier.yml up -d user-service
```

Provision the database once first — see `scripts/provision-user-service-db.sql` in the platform
repo. The role is `clickkart_user_app` owning `clickkart_user`, never the `postgres` superuser.

`JWT_SECRET` must be **byte-identical** to Auth Service's and the Gateway's. A different value here
rejects every legitimate request, which looks like a broken token rather than a config mismatch.

### Tests

```bash
mvn verify
```

`verify`, not `test` — that is what enforces the jacoco coverage gate (currently a floor of 0.60
against measured 0.65; it should only ever ratchet upward).

> **Building on a JDK newer than 21:** the pom declares Lombok as an explicit annotation processor
> path. JDK 23 turned off implicit classpath processor discovery that JDK 21 only warns about, and
> without that declaration every Lombok-generated getter and logger silently vanishes, producing
> hundreds of "cannot find symbol" errors that look like broken source rather than a toolchain
> change. The deployed artifact is unaffected — Docker and CI both compile on JDK 21.

---

## Configuration

Per-environment properties live in `clickkart-config-repository` on the branch matching the active
profile (`dev`/`test`/`qa`/`prod`). Key settings:

| Property | Purpose |
|---|---|
| `user.jwt-secret` | Shared HMAC secret; must match Auth Service and the Gateway |
| `user.revocation-key-prefix` | Must match Auth Service's `auth.revocation-key-prefix` |
| `user.max-addresses-per-user` | Guard against unbounded address-book growth (default 20) |
| `user.trusted-proxy-cidrs` | Whose `X-Forwarded-For` to believe; empty means trust nothing |
| `user.allowed-origins` | CORS allow-list — defence in depth, this service is independently reachable |

Readiness (`/actuator/health/readiness`) covers `readinessState,db,redis`: the service can neither
authenticate a request without Redis nor serve one without the database, so an outage of either
should pull the instance out of the load balancer's pool.
