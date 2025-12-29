# DocFlow API - Quick Reference Card

## 🔐 Authentication Flow

```
1. POST /api/register                 → Create account
2. POST /api/auth/verify-email        → Verify email
3. POST /api/auth/login               → Get JWT + refresh tokens
4. [Use JWT token for all requests]
5. POST /api/auth/refresh             → Refresh expired JWT
6. POST /api/auth/logout              → Clear all tokens
```

### 🔄 Token Management

**Access Token (JWT):**
- Expires: 1 hour
- Stored: HttpOnly cookie
- Usage: All API requests

**Refresh Token:**
- Expires: 7 days
- Stored: Database
- Usage: Get new access token

```
POST /api/auth/refresh
Authorization: Bearer <refresh_token>
→ Returns new JWT in HttpOnly cookie
```

---

## 🏢 Company Setup

```
1. POST /api/company/create           → Create company (you become CEO)
2. POST /api/company/roles            → Create custom roles
3. GET  /api/company/getAllRoles      → View all roles
4. POST /api/company/{id}/enter       → Switch to company
```

---

## 📋 Workflow Template Creation

```
POST /api/workflow/template
{
  "name": "Document Approval",
  "description": "...",
  "workflowXml": "<?xml version=\"1.0\"?>
    <workflow>
      <step order=\"1\" roleName=\"Manager\" roleLevel=\"60\" 
            action=\"review\" parallel=\"false\"/>
      <step order=\"2\" roleName=\"Director\" roleLevel=\"80\" 
            action=\"approve\" parallel=\"false\"/>
      <onReject stepOrder=\"1\" targetStep=\"1\"/>
      <onReject stepOrder=\"2\" targetStep=\"1\"/>
    </workflow>"
}
```

---

## 🚀 Start Workflow & Complete Tasks

```
1. POST /api/workflow/{templateId}/start?documentId={docId}
   → Returns: workflow_instance_id with initial tasks

2. GET /api/workflow/my-tasks
   → See all pending tasks for current user

3. POST /api/workflow/task/{taskId}/approve
   {
     "comment": "Looks good. Approved."
   }
   → Task approved, next step created if all tasks approved

4. POST /api/workflow/task/{taskId}/reject
   {
     "comment": "Needs revision."
   }
   → Task rejected, routing rule applied
   → If routing exists, returns to target step
   → Otherwise, workflow marked as REJECTED
```

---

## 📊 View Status & History

```
GET /api/workflow/instance/{workflowId}
  → Current workflow status with all tasks

GET /api/workflow/document/{docId}/tasks
  → All tasks for a document

GET /api/workflow/instance/{workflowId}/audit
  → Complete audit trail (who, what, when, from where)
```

---

## 📌 Important IDs to Track

| ID | Source | Used For |
|----|--------|----------|
| `company_id` | POST /api/company/create | Templates, tasks |
| `template_id` | POST /api/workflow/template | Start workflow |
| `workflow_instance_id` | POST /api/workflow/{id}/start | Get status, audit |
| `task_id` | GET /api/workflow/my-tasks | Approve/reject |
| `document_id` | Your system | Start workflow |

---

## 🔑 Task Status Values

```
PENDING  → Waiting for approval
APPROVED → User approved
REJECTED → User rejected
COMPLETED → Workflow completed successfully
CANCELLED → Task cancelled (returned to earlier step)
```

---

## 🔄 Workflow Status Values

```
IN_PROGRESS → Workflow running
COMPLETED   → All steps finished
REJECTED    → Rejected and not returned
TIMEOUT     → Task exceeded timeout (future)
```

---

## ⚙️ Parallel Steps in XML

To execute tasks in parallel at the same step level:

```xml
<workflow>
  <step order="1" roleName="Manager" roleLevel="60" action="review" parallel="false"/>

  <!-- These 2 execute in parallel -->
  <step order="2" roleName="Lawyer" roleLevel="70" action="review" parallel="true"/>
  <step order="2" roleName="Accountant" roleLevel="65" action="check" parallel="true"/>

  <step order="3" roleName="CEO" roleLevel="100" action="sign" parallel="false"/>
</workflow>
```

All tasks at order=2 must be approved before moving to order=3.

---

## 🔀 Conditional Routing

```xml
<workflow>
  <!-- Steps -->
  <step order="1" roleName="Manager" roleLevel="60" action="review" parallel="false"/>
  <step order="2" roleName="Director" roleLevel="80" action="approve" parallel="false"/>
  <step order="3" roleName="CEO" roleLevel="100" action="sign" parallel="false"/>

  <!-- Conditional rules -->
  <onApprove stepOrder="1" condition="isLowValue" targetStep="3" description="Skip director"/>
  <onApprove stepOrder="1" condition="!isLowValue" targetStep="2" description="Normal flow"/>
  <onReject stepOrder="2" targetStep="1" description="Return to manager"/>
</workflow>
```

### 📋 Conditions

**Predefined:**
- `isHighValue` (>50k), `isLowValue` (≤5k)
- `isContract`, `isInvoice`
- `isUrgent` (HIGH/URGENT priority)

**Comparisons:**
- `amount > 10000`, `priority = HIGH`
- `type != CONTRACT`

**Negation:** `!isHighValue`

---

## 🔄 Bulk Operations

```bash
# Approve multiple tasks at once
POST /api/workflow/tasks/bulk-approve
{
  "taskIds": [201, 202, 203],
  "comment": "Bulk approval"
}

# Reject multiple tasks at once
POST /api/workflow/tasks/bulk-reject
{
  "taskIds": [201, 202],
  "comment": "Bulk rejection"
}
```

---

## 📧 Email Notifications (Auto-sent)

```
✓ Task created      → Assigned users notified
✓ Task approved     → Approver + next step users
✓ Task rejected     → Rejector + assigned users
✓ Workflow rejected → Initiator
✓ Workflow completed→ Initiator + managers
```

Check application logs if email not configured.

---

## 🔍 Role Levels

```
10-50   Regular Users
60      Manager (can review documents)
70      Team Lead (can approve)
80      Director (can approve higher level)
100     CEO/Executive (final approval)
```

Users can only approve if their role level >= task required level.

---

## 🔒 Security Features

### 🛡️ Rate Limiting
- **Auth endpoints**: 10 requests/minute
- **Other endpoints**: 100 requests/hour
- **IP-based tracking**
- **Automatic cleanup**

### 📊 Security Audit
**Events logged:**
- ✅ Login success/failure
- ✅ Token refresh
- ✅ Logout
- ✅ Rate limit violations
- ✅ Suspicious activity

---

## 📄 Document Management

### 📝 Version Control
```bash
# Upload new version
POST /api/documents/{id}/versions
file: newfile.pdf
changeDescription: "Updated contract terms"
changeType: "UPDATE"

# Get all versions
GET /api/documents/{id}/versions

# Add watermark
POST /api/documents/{id}/watermark
watermarkText: "CONFIDENTIAL"

# Sign document
POST /api/documents/{id}/sign
signatureText: "Approved"
signerName: "John Doe"

# Restore version
POST /api/documents/{id}/versions/{version}/restore
```

### 🔍 Document Features
- **SHA-256 hashing** - duplicate detection
- **Version history** - track all changes
- **Watermarks** - add security markings
- **Digital signatures** - electronic approval
- **PDF processing** - templates, compression
- **Integrity checks** - file validation

### 🔐 Token Security
- **HttpOnly cookies** (XSS protection)
- **Secure flag** (HTTPS only)
- **Refresh token rotation**
- **Automatic cleanup** of expired tokens

---

## 🚨 Error Codes

| Code | Meaning | Action |
|------|---------|--------|
| 401 | Token expired/invalid | Refresh token or login |
| 403 | Insufficient permissions | Check user role |
| 429 | Rate limit exceeded | Wait and retry |
| 400 | Invalid request | Check request format |

---

## 🐛 Common Errors

| Error | Cause | Fix |
|-------|-------|-----|
| 401 Unauthorized | Token expired | Login again |
| 403 Forbidden | Insufficient role | Assign higher role |
| 400 Bad Request | Invalid XML/data | Check format |
| 404 Not Found | Wrong ID | Get correct ID first |
| 409 Conflict | Task already completed | Already processed |

---

## 📍 Base URL

```
http://localhost:8080
```

---

## 🎯 Example Complete Workflow

```
1. Register user               → manager@company.com
2. Create company             → Acme Corp (company_id=1)
3. Create template            → 3-step approval (template_id=1)
4. Start workflow             → For document_id=1 (workflow_id=1)
5. Manager reviews            → Approve task 1
6. Director receives task 2   → Reject with comment
7. Routing rule applied       → Returns to step 1
8. Manager revises            → Approve task 1 again
9. Director receives task 2   → Approve
10. CEO receives task 3       → Approve/Sign
11. Workflow completed        → All users notified
12. View audit log            → See all 10+ actions with metadata
```

---

## 🔗 Swagger & OpenAPI

```
Swagger UI:  http://localhost:8080/swagger-ui.html
OpenAPI:     http://localhost:8080/v3/api-docs

Test endpoints directly from Swagger UI!
```

---

## 📝 Notes

- All timestamps in ISO 8601 format (UTC)
- Passwords: Min 8 chars, require uppercase, lowercase, number, special char
- JWT tokens expire after 24 hours
- Email must be verified before login
- Each user can have multiple roles in different companies

---

**Last Updated:** December 22, 2025
