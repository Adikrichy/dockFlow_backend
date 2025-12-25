# DocFlow API - Postman Testing Guide

## 📋 Introduction

This guide will help you test the entire DocFlow workflow system using the Postman collection. The collection covers authentication, company management, workflow templates, task management, and audit tracking.

---

## 🚀 Quick Start

### Import the Collection
1. Open Postman
2. Click **Import** (top-left)
3. Select **DocFlow-API-Collection.postman_collection.json**
4. The collection will be added to your workspace

### Prerequisites
- Backend running on `http://localhost:8080`
- PostgreSQL running on `localhost:5432` with database `dockFlow`
- Email configured (optional - check logs if emails fail)

---

## 📝 Testing Workflow

### Step 1: Register a User

**Endpoint:** `POST /api/register`

```json
{
  "email": "manager@company.com",
  "password": "SecurePass123!",
  "firstName": "John",
  "lastName": "Manager"
}
```

**Expected Response:** 
- Status: `201 Created`
- Returns user registration details with verification code sent to email

**Note:** Check application logs for verification code (since email might not be configured)

---

### Step 2: Verify Email

**Endpoint:** `POST /api/auth/verify-email`

```
Query Parameters:
- email: manager@company.com
- verificationCode: 123456  (from logs or email)
```

**Expected Response:**
- Status: `200 OK`
- Message: "Email verified"

---

### Step 3: Login

**Endpoint:** `POST /api/auth/login`

```json
{
  "email": "manager@company.com",
  "password": "SecurePass123!"
}
```

**Expected Response:**
- Status: `200 OK`
- Returns JWT token (automatically stored in HttpOnly cookie)
- User email in response

**Important:** The JWT token is stored in an HttpOnly cookie. Postman will automatically send it with subsequent requests.

---

### Step 4: Create a Company

**Endpoint:** `POST /api/company/create`

```json
{
  "name": "Acme Corporation",
  "description": "Leading document management company",
  "industry": "Technology"
}
```

**Expected Response:**
- Status: `200 OK`
- Returns company details
- Your user becomes CEO

**Save the company ID** from the response (e.g., `1`)

---

### Step 5: Create Workflow Template

**Endpoint:** `POST /api/workflow/template`

```json
{
  "name": "Document Approval Workflow",
  "description": "Three-step approval process for documents",
  "workflowXml": "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<workflow>\n  <step order=\"1\" roleName=\"Manager\" roleLevel=\"60\" action=\"review\" parallel=\"false\" description=\"Initial review\"/>\n  <step order=\"2\" roleName=\"Director\" roleLevel=\"80\" action=\"approve\" parallel=\"false\" description=\"Director approval\"/>\n  <step order=\"3\" roleName=\"CEO\" roleLevel=\"100\" action=\"sign\" parallel=\"false\" description=\"Final signature\"/>\n  <onReject stepOrder=\"1\" targetStep=\"1\" description=\"Return to manager\"/>\n  <onReject stepOrder=\"2\" targetStep=\"1\" description=\"Return to manager if director rejects\"/>\n  <onReject stepOrder=\"3\" description=\"Complete as rejected\"/>\n</workflow>"
}
```

**Expected Response:**
- Status: `201 Created`
- Returns template with ID and parsed steps

**Save the template ID** from the response (e.g., `1`)

---

### Step 6: Get Company Templates

**Endpoint:** `GET /api/workflow/company/1/templates`

**Expected Response:**
- Status: `200 OK`
- Returns list of all company workflow templates

---

### Step 7: Create Multiple Users for Testing

To test the complete workflow, create additional users with different roles:

**Register Manager User:**
```json
{
  "email": "manager@company.com",
  "password": "SecurePass123!",
  "firstName": "Jane",
  "lastName": "Director"
}
```

**Register Director User:**
```json
{
  "email": "director@company.com",
  "password": "SecurePass123!",
  "firstName": "Bob",
  "lastName": "Executive"
}
```

**Register CEO User (if different from admin):**
```json
{
  "email": "ceo@company.com",
  "password": "SecurePass123!",
  "firstName": "Alice",
  "lastName": "CEO"
}
```

Then verify their emails and log them in.

---

### Step 8: Start a Workflow

**Endpoint:** `POST /api/workflow/1/start?documentId=1`

**Expected Response:**
- Status: `201 Created`
- Returns workflow instance with initial tasks created for step 1

**Save the workflow instance ID** from the response (e.g., `1`)

---

### Step 9: Get Pending Tasks

**Endpoint:** `GET /api/workflow/my-tasks`

**Expected Response:**
- Status: `200 OK`
- Returns list of tasks assigned to current user

---

### Step 10: Approve a Task

**Endpoint:** `POST /api/workflow/task/1/approve`

```json
{
  "comment": "Document looks good. Approved for next stage."
}
```

**Expected Response:**
- Status: `200 OK`
- Task marked as APPROVED
- Next step tasks are created if all tasks in current step approved
- Email notifications sent (check logs)

---

### Step 11: Reject a Task (Testing Conditional Routing)

**Endpoint:** `POST /api/workflow/task/1/reject`

```json
{
  "comment": "Document needs corrections. Please revise and resubmit."
}
```

**Expected Response:**
- Status: `200 OK`
- Task marked as REJECTED
- Routing rule applied (returns to target step or completes as rejected)
- Previous step tasks are reset to PENDING if returning to earlier step
- Email notifications sent

---

### Step 12: View Workflow Audit Log

**Endpoint:** `GET /api/workflow/instance/1/audit`

**Expected Response:**
- Status: `200 OK`
- Returns complete audit trail with:
  - All actions performed
  - Who performed them
  - When (timestamps)
  - IP address
  - Additional metadata

---

### Step 13: Get Workflow Instance Details

**Endpoint:** `GET /api/workflow/instance/1`

**Expected Response:**
- Status: `200 OK`
- Returns complete workflow instance with:
  - All tasks
  - Current status
  - Document information
  - Template details
  - Routing rules

---

## 🔄 Complete Workflow Example

Here's a complete test scenario:

1. **Register 3 users** (Manager, Director, CEO)
2. **Create company** (CEO becomes admin)
3. **Create workflow template** with 3 steps
4. **Start workflow** for a document
5. **Manager approves task** → moves to Director
6. **Director rejects task** → returns to Manager (conditional routing)
7. **Manager revises and approves** → moves to Director again
8. **Director approves** → moves to CEO
9. **CEO signs** → workflow completes
10. **View audit log** to see complete history

---

## 📊 API Response Examples

### Successful Workflow Start
```json
{
  "id": 1,
  "document": {
    "id": 1,
    "title": "Sample Document",
    "company": {"id": 1}
  },
  "template": {"id": 1},
  "status": "IN_PROGRESS",
  "tasks": [
    {
      "id": 1,
      "stepOrder": 1,
      "requiredRoleName": "Manager",
      "requiredRoleLevel": 60,
      "status": "PENDING"
    }
  ]
}
```

### Workflow After Rejection with Routing
```json
{
  "status": "IN_PROGRESS",
  "tasks": [
    {
      "id": 1,
      "status": "PENDING",
      "stepOrder": 1,
      "resetAt": "2025-12-22T10:30:00"
    }
  ],
  "auditLog": [
    {
      "action": "TASK_REJECTED",
      "performedBy": "director@company.com",
      "comment": "Needs revision",
      "timestamp": "2025-12-22T10:25:00"
    },
    {
      "action": "ROUTING_RULE_APPLIED",
      "fromStep": 2,
      "toStep": 1,
      "timestamp": "2025-12-22T10:25:00"
    }
  ]
}
```

### Audit Log Entry
```json
{
  "id": 1,
  "action": "TASK_APPROVED",
  "performedBy": "manager@company.com",
  "email": "manager@company.com",
  "taskId": 1,
  "comment": "Looks good",
  "ipAddress": "127.0.0.1",
  "timestamp": "2025-12-22T10:20:00",
  "additionalData": {
    "stepOrder": 1,
    "workflowStatus": "IN_PROGRESS"
  }
}
```

---

## 🐛 Troubleshooting

### Issue: 401 Unauthorized
- **Cause:** JWT token not valid or expired
- **Solution:** Login again to get a new token

### Issue: 403 Forbidden
- **Cause:** User doesn't have required role level
- **Solution:** Assign higher role to user or use a different user

### Issue: Task not found (404)
- **Cause:** Using wrong task ID
- **Solution:** Get pending tasks first to find correct task ID

### Issue: Email not sent
- **Cause:** Email service not configured
- **Solution:** Check application.properties for SMTP settings; notifications are logged but won't send without config

### Issue: Workflow not starting
- **Cause:** Invalid XML or missing required attributes
- **Solution:** Check XML format matches expected schema

---

## 🔐 Security Notes

1. **JWT tokens** are stored in HttpOnly cookies (secure by default)
2. **Passwords** should be at least 8 characters with uppercase, lowercase, numbers, and special chars
3. **Role levels** control access:
   - 10-50: Regular users
   - 60-80: Managers and Directors
   - 100: CEO/Admin

---

## 📌 API Endpoints Summary

| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/api/register` | Register new user |
| POST | `/api/auth/verify-email` | Verify email |
| POST | `/api/auth/login` | Login user |
| POST | `/api/auth/logout` | Logout user |
| POST | `/api/company/create` | Create company |
| PATCH | `/api/company/{id}` | Update company |
| POST | `/api/company/{id}/enter` | Switch to company |
| POST | `/api/company/exit` | Leave company |
| POST | `/api/company/roles` | Create custom role |
| GET | `/api/company/getAllRoles` | Get all roles |
| POST | `/api/workflow/template` | Create workflow template |
| GET | `/api/workflow/company/{id}/templates` | Get company templates |
| GET | `/api/workflow/template/{id}` | Get template details |
| POST | `/api/workflow/{id}/start` | Start workflow |
| GET | `/api/workflow/instance/{id}` | Get workflow instance |
| GET | `/api/workflow/document/{id}/tasks` | Get document tasks |
| GET | `/api/workflow/my-tasks` | Get my pending tasks |
| POST | `/api/workflow/task/{id}/approve` | Approve task |
| POST | `/api/workflow/task/{id}/reject` | Reject task |
| GET | `/api/workflow/instance/{id}/audit` | Get audit log |

---

## 🌐 Additional Resources

- **Swagger UI:** `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON:** `http://localhost:8080/v3/api-docs`

---

## 💡 Tips for Testing

1. **Use Variables:** Set `company_id`, `template_id`, `workflow_instance_id` in Postman variables for easy switching
2. **Test with Multiple Users:** Switch between users to test role-based access
3. **Test Error Cases:** Try submitting invalid data to test error handling
4. **Monitor Logs:** Check application logs for detailed information about workflow processing
5. **Check Database:** Use PostgreSQL client to verify data is persisted correctly

---

Generated: December 22, 2025
