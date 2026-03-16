# flip-backend

Backend for the Flip home remodeling app. Serves 3D furniture models and product metadata to the iOS client.

## Tech Stack

- **Kotlin 2.0.21** + **Ktor 2.3.12** (Netty engine)
- **Exposed 0.52** (SQL DSL, not DAO pattern)
- **PostgreSQL 16** (primary database)
- **HikariCP** (connection pooling)
- **Flyway** (database migrations)
- **Gradle 9.x** with `com.gradleup.shadow` for fat JAR (replaces `io.ktor.plugin` which is incompatible with Gradle 9)

## Project Structure

```
src/main/kotlin/com/flip/
├── Application.kt          # Entry point, wires everything together
├── DataSeeder.kt           # Copies seed USDZ files into storage on first startup
├── db/
│   └── Products.kt         # Exposed table definition
├── models/
│   ├── Category.kt         # Enum: CHAIR, TABLE, SOFA, FLOOR, TILE, LAMP, SHELF, BED, DESK, RUG, OTHER
│   └── ProductDto.kt       # Request/response DTOs + ErrorResponse
├── plugins/
│   ├── Database.kt         # HikariCP + Flyway + Exposed setup
│   ├── Routing.kt          # Installs routes
│   ├── Serialization.kt    # Kotlinx JSON content negotiation
│   └── StatusPages.kt      # Global error handling
├── routes/
│   └── ProductRoutes.kt    # All HTTP route handlers
├── services/
│   └── ProductService.kt   # Business logic, database queries
└── storage/
    ├── FileStorage.kt      # Interface (swap LocalFileStorage for S3 later)
    └── LocalFileStorage.kt # Stores files on local disk
```

## API

```
GET  /models?q=chair&category=CHAIR&page=0&size=20   # search + filter
GET  /models/{id}                                     # single product
POST /models                                          # create product
PUT  /models/{id}                                     # update metadata
PUT  /models/{id}/file                                # upload .usdz (binary or multipart)
GET  /models/{id}/file                                # download .usdz
```

## Database Schema

Table: `products`

| Column         | Type           | Notes                            |
|----------------|----------------|----------------------------------|
| id             | UUID           | Random, auto-generated           |
| company_name   | VARCHAR(255)   | e.g. "Bohus", "IKEA"             |
| category       | VARCHAR(50)    | Matches Category enum            |
| name           | VARCHAR(255)   | Product name                     |
| model_version  | INTEGER        | Bumped on each file re-upload    |
| price_nok      | DECIMAL(10,2)  |                                  |
| product_url    | TEXT           | Link to buy (affiliate later)    |
| width_cm       | DECIMAL(8,2)   |                                  |
| height_cm      | DECIMAL(8,2)   |                                  |
| depth_cm       | DECIMAL(8,2)   |                                  |
| model_key      | VARCHAR(500)   | Path in FileStorage              |
| created_at     | TIMESTAMPTZ    |                                  |
| updated_at     | TIMESTAMPTZ    |                                  |

## File Storage

- Files stored under `./uploads/` locally (configurable via `STORAGE_PATH` env var)
- Model key format: `models/{id}/v{version}.usdz`
- Interface designed for future S3 swap

## Configuration

All settings via environment variables (with defaults for local dev):

| Variable        | Default                                | Description               |
|-----------------|----------------------------------------|---------------------------|
| PORT            | 8080                                   |                           |
| DATABASE_URL    | jdbc:postgresql://localhost:5432/flip  |                           |
| DATABASE_USER   | flip                                   |                           |
| DATABASE_PASSWORD | flip                                 |                           |
| STORAGE_PATH    | ./uploads                              | Where .usdz files go      |
| SEED_MODEL_PATH | ../flip/Models/Noni_spisestol.usdz     | Source for seed USDZ file |

## Running Locally

```bash
docker-compose up postgres -d
./gradlew run
```

## Seed Data

Flyway migration `V2` inserts the Noni Spisestol (Bohus, 1599 NOK).
`DataSeeder` copies the USDZ file from `SEED_MODEL_PATH` into storage on first startup.

## Future Plans

- **Users** — authentication, user accounts
- **Projects** — save room layouts (project name + placed model positions)
- **S3 storage** — swap `LocalFileStorage` for S3-backed implementation
- **AI suggestions** — endpoint for AI-generated decor recommendations
- **Shopping cart / affiliate links** — `product_url` field already in place
