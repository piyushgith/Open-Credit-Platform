# System Context

`my_docs/plan.md` §5 names this `system-context.png`; it's Mermaid here instead — GitHub renders
the fenced block below natively, and a portfolio repo doesn't need a separate image-generation step
for something this simple.

```mermaid
flowchart LR
    Analyst["Credit Officer / Maker / Checker / Admin<br/>(browser or curl, via JWT)"]
    Demo["scripts/development/demo.sh"]
    App["Open Credit Platform<br/>(single Spring Boot app)"]
    DB[("PostgreSQL<br/>schema-managed by Liquibase")]

    Analyst -->|REST + JWT| App
    Demo -->|REST + JWT| App
    App -->|JDBC| DB
```

One deployable, one database, no external services (no IdP, no message broker, no third-party credit
bureau integration) — see [ADR-001](../adr/ADR-001-modular-monolith.md) for why.
