# Testing Payment Portal API with Postman

## JWT Authentication Setup

This service uses JWT authentication. For development/testing, dummy users are available through the dev auth endpoint.

---

## Step 1: Get a JWT Token

### Endpoint: Login (Development Only)

**POST** `http://localhost:8080/api/v1/dev/auth/login`

**Headers:**
```
Content-Type: application/json
```

**Body (JSON):**
```json
{
  "email": "admin@test.com",
  "password": "admin123"
}
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "userId": "11111111-1111-1111-1111-111111111111",
  "email": "admin@test.com",
  "roles": ["ADMIN"]
}
```

### Available Test Users

| Email | Password | Roles | Use Case |
|-------|----------|-------|----------|
| `admin@test.com` | `admin123` | ADMIN | Create/update portals, manage submissions |
| `staff@test.com` | `staff123` | STAFF | View portals, approve/reject submissions |
| `student@test.com` | `student123` | STUDENT | View published portals, submit payments |
| `student2@test.com` | `student123` | STUDENT | Additional student for testing |

---

## Step 2: Use the Token in Requests

Once you have the token, use it in the `Authorization` header for all protected endpoints.

### Option A: Using Postman Authorization Tab
1. Go to the **Authorization** tab
2. Select **Type**: `Bearer Token`
3. Paste the token from the login response

### Option B: Using Headers Manually
Add this header to your requests:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

---

## Example API Calls

### 1. List All Portals (Any authenticated user)

**GET** `http://localhost:8080/api/v1/portals`

**Headers:**
```
Authorization: Bearer {your-token-here}
```

**Query Parameters (optional):**
- `month`: Filter by month (1-12)
- `year`: Filter by year
- `isPublished`: Filter by published status (true/false)
- `limit`: Results per page (default: 20, max: 100)
- `offset`: Page offset (default: 0)

**Example:**
```
GET http://localhost:8080/api/v1/portals?month=12&year=2025&limit=10
```

---

### 2. Create a Portal (Admin only)

**POST** `http://localhost:8080/api/v1/portals`

**Headers:**
```
Authorization: Bearer {admin-token}
Content-Type: application/json
```

**Body:**
```json
{
  "name": "january-2026-physics",
  "month": 1,
  "year": 2026
}
```

---

### 3. Get Portal by ID

**GET** `http://localhost:8080/api/v1/portals/{portalId}`

**Headers:**
```
Authorization: Bearer {your-token}
```

---

### 4. Update Portal (Admin only)

**PATCH** `http://localhost:8080/api/v1/portals/{portalId}`

**Headers:**
```
Authorization: Bearer {admin-token}
Content-Type: application/json
```

**Body:**
```json
{
  "visibility": "PUBLISHED"
}
```

---

### 5. Bulk Update Portal Visibility (Admin only)

**PATCH** `http://localhost:8080/api/v1/portals/bulk-visibility`

**Headers:**
```
Authorization: Bearer {admin-token}
Content-Type: application/json
```

**Body:**
```json
{
  "portalIds": [
    "portal-id-1",
    "portal-id-2"
  ],
  "isPublished": true
}
```

---

## Postman Collection Setup Tips

### Using Environment Variables

1. Create a Postman Environment called "Payment Portal Dev"
2. Add these variables:
   - `baseUrl`: `http://localhost:8080`
   - `token`: (leave empty, will be set by login request)
   - `userId`: (leave empty, will be set by login request)

### Auto-Save Token After Login

In the login request, go to the **Tests** tab and add:
```javascript
var jsonData = pm.response.json();
pm.environment.set("token", jsonData.token);
pm.environment.set("userId", jsonData.userId);
```

Then in other requests, use:
- URL: `{{baseUrl}}/api/v1/portals`
- Authorization: `Bearer {{token}}`

---

## Common Response Codes

- `200 OK`: Request successful
- `201 Created`: Resource created successfully
- `400 Bad Request`: Validation error or invalid data
- `401 Unauthorized`: Missing or invalid JWT token
- `403 Forbidden`: User doesn't have required role
- `404 Not Found`: Resource not found
- `500 Internal Server Error`: Server error

---

## Notes

⚠️ **Development Only**: The `/api/v1/dev/auth/*` endpoints are only available in development mode. They will not be present in production.

🔒 **Security**: In production, JWT tokens will be issued by the User Service and passed through the BFF. This dev endpoint is purely for testing the Payment Portal service in isolation.

📝 **Token Expiration**: Tokens expire after 24 hours. If you get 401 errors, request a new token.
