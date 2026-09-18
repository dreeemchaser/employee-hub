# Swagger / OpenAPI Guide

Interactive API documentation is available via SpringDoc OpenAPI.

## Accessing Swagger UI

| Method | URL |
|--------|-----|
| With Docker | http://localhost:8080/swagger-ui/index.html |
| Local dev | http://localhost:8080/swagger-ui/index.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| OpenAPI YAML | http://localhost:8080/v3/api-docs.yaml |

> `http://localhost:8080/swagger-ui.html` also works — SpringDoc redirects it to the canonical `/swagger-ui/index.html`.

## Authentication

All `/auth/**` endpoints are public (login, refresh, logout, forgot-password, reset-password), along with Swagger and `/actuator/health`. Every other endpoint requires a JWT access token.

1. Call `POST /auth/login` to obtain an `accessToken` (and a `refreshToken`)
2. Click the **Authorize** button (top right of Swagger UI)
3. Enter `Bearer <your-accessToken>` and click Authorize

## Testing Endpoints

### Login

1. Open http://localhost:8080/swagger-ui/index.html
2. Find `POST /auth/login` → click **Try it out**
3. Enter request body:
   ```json
   {
     "email": "admin@employeehub.com",
     "password": "Admin@1234"
   }
   ```
4. Execute — the response contains `accessToken` and `refreshToken`:
   ```json
   {
     "success": true,
     "data": { "accessToken": "eyJhbGci...", "refreshToken": "8xK3...opaque..." }
   }
   ```
5. Click **Authorize** and enter `Bearer <accessToken>`

> The access token is short-lived (~15 min). When it expires, call `POST /auth/refresh` with the `refreshToken` to get a new pair — there is no `/auth/register` endpoint; accounts are created via `POST /employees` (HR/admin) or seeded on first boot.

### Get All Employees

1. Find `GET /employees` → click **Try it out**
2. Click **Execute**

### Upload an Employee Photo

1. Find `POST /employees/{id}/photo` → click **Try it out**
2. Enter the employee `id` (UUID)
3. Select an image file
4. Click **Execute**

## Configuration

### Dependency (`pom.xml`)
```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.8.9</version>
</dependency>
```

### OpenAPI setup

Server URLs and metadata are configured in `config/OpenApiConfiguration.java` (registers both `localhost:8080` and the Docker `api:8080` server URLs). There is no `springdoc:` block in `application.yml` — SpringDoc defaults are used, so the UI lives at `/swagger-ui/index.html` and the spec at `/v3/api-docs`.

## Troubleshooting

**Swagger UI returns 404:**
- Confirm the API is running: `curl http://localhost:8080/actuator/health`
- Check API logs: `docker-compose logs api`
- Rebuild if needed: `docker-compose up --build`

**Endpoints not showing:**
- Swagger auto-scans on startup — no manual registration needed
- Rebuild after adding new controllers

**401 on protected endpoints:**
- Ensure you have clicked **Authorize** and entered a valid `Bearer <accessToken>`
- Access tokens expire after ~15 minutes — call `POST /auth/refresh` with your refresh token to get a fresh access token (no manual re-login needed)
