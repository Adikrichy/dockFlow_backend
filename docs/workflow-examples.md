# Workflow XML Examples

## Example 1: Simple Sequential Approval

```xml
<workflow>
  <step order="1" roleName="Manager" roleLevel="60" action="review"/>
  <step order="2" roleName="Director" roleLevel="80" action="approve"/>
  <step order="3" roleName="CEO" roleLevel="100" action="sign"/>
</workflow>
```

**Flow:**
1. Manager reviews document (role level 60+)
2. After approval → Director approves (role level 80+)
3. After approval → CEO signs (role level 100+)
4. Document signed and approved

---

## Example 2: Parallel Approval

```xml
<workflow>
  <step order="1" roleName="Manager" roleLevel="60" action="review" parallel="false"/>
  <step order="2" roleName="Director" roleLevel="80" action="approve" parallel="true"/>
  <step order="2" roleName="Accountant" roleLevel="70" action="verify" parallel="true"/>
  <step order="3" roleName="CEO" roleLevel="100" action="sign" parallel="false"/>
</workflow>
```

**Flow:**
1. Manager reviews (sequential)
2. Director and Accountant approve simultaneously (both order=2, parallel="true")
3. After both parallel tasks approve → CEO signs (sequential)

---

## Example 3: Conditional Routing

```xml
<workflow>
  <!-- Sequential steps -->
  <step order="1" roleName="Manager" roleLevel="60" action="review" parallel="false"/>
  <step order="2" roleName="Director" roleLevel="80" action="approve" parallel="false"/>
  <step order="3" roleName="CEO" roleLevel="100" action="sign" parallel="false"/>
  <step order="4" roleName="Legal" roleLevel="75" action="legal_review" parallel="false"/>
  <step order="5" roleName="Accountant" roleLevel="70" action="verify" parallel="false"/>

  <!-- Conditional routing rules -->
  <!-- Skip director for low-value documents (< 5,000) -->
  <onApprove stepOrder="1" condition="isLowValue" targetStep="3" description="Skip director for low-value documents"/>

  <!-- Normal flow for high-value documents -->
  <onApprove stepOrder="1" condition="!isLowValue" targetStep="2" description="Normal approval flow"/>

  <!-- Return to manager if director rejects -->
  <onReject stepOrder="2" targetStep="1" description="Return to manager for revision"/>

  <!-- Different paths based on document type -->
  <onApprove stepOrder="3" condition="isContract" targetStep="4" description="Legal review required for contracts"/>
  <onApprove stepOrder="3" condition="!isContract" targetStep="5" description="Accounting verification for other documents"/>
</workflow>
```

**Conditional Flow Examples:**

**Low-value Invoice (< 5,000):**
Manager → CEO → Accountant → Complete

**High-value Contract (> 5,000):**
Manager → Director → CEO → Legal → Complete

**Rejection Flow:**
Manager → Director (rejects) → Manager (revision) → Director → CEO → Complete

**Key Points:**
- Set `parallel="true"` for steps that should execute simultaneously
- All parallel tasks at the same `order` must be completed before moving to next step
- Tasks are assigned to all users with matching role and sufficient level

---

## Example 3: Complex Workflow with Multiple Reviewers

```xml
<workflow>
  <step order="1" roleName="Manager" roleLevel="60" action="review"/>
  <step order="2" roleName="Manager" roleLevel="60" action="secondary_review" parallel="true"/>
  <step order="2" roleName="Senior" roleLevel="75" action="technical_check" parallel="true"/>
  <step order="3" roleName="Director" roleLevel="80" action="approve"/>
  <step order="4" roleName="CEO" roleLevel="100" action="sign"/>
</workflow>
```

---

## Create Workflow Template via API

```bash
POST /api/workflow/template

Content-Type: application/json

{
  "name": "Standard Document Approval",
  "description": "Three-level approval for contracts",
  "companyId": 1,
  "workflowXml": "<workflow><step order=\"1\" roleName=\"Manager\" roleLevel=\"60\" action=\"review\"/><step order=\"2\" roleName=\"Director\" roleLevel=\"80\" action=\"approve\"/><step order=\"3\" roleName=\"CEO\" roleLevel=\"100\" action=\"sign\"/></workflow>"
}
```

---

## Start Workflow for Document

```bash
POST /api/workflow/1/start?documentId=42

Headers:
Authorization: Bearer <JWT_TOKEN>
```

Response:
```json
{
  "id": 100,
  "documentId": 42,
  "templateId": 1,
  "status": "IN_PROGRESS",
  "tasks": [
    {
      "id": 201,
      "stepOrder": 1,
      "requiredRoleName": "Manager",
      "requiredRoleLevel": 60,
      "status": "PENDING"
    }
  ],
  "createdAt": "2024-01-15T10:30:00"
}
```

---

## Approve Task

```bash
POST /api/workflow/task/201/approve

Content-Type: application/json

{
  "comment": "Document looks good, approved from my side"
}
```

---

## Reject Task

```bash
POST /api/workflow/task/201/reject

Content-Type: application/json

{
  "comment": "Need more information before approval"
}
```

---

## Get My Pending Tasks

```bash
GET /api/workflow/my-tasks

Headers:
Authorization: Bearer <JWT_TOKEN>
```

Response:
```json
[
  {
    "id": 201,
    "stepOrder": 1,
    "requiredRoleName": "Manager",
    "requiredRoleLevel": 60,
    "status": "PENDING",
    "assignedAt": "2024-01-15T10:30:00"
  },
  {
    "id": 202,
    "stepOrder": 1,
    "requiredRoleName": "Manager",
    "requiredRoleLevel": 60,
    "status": "PENDING",
    "assignedAt": "2024-01-15T11:00:00"
  }
]
```

---

## WebSocket Events

Subscribe to workflow events:

```javascript
const client = new StompClient('ws://localhost:8080/ws/chat');

// Subscribe to company workflow events
client.subscribe('/topic/workflow/company/1', (message) => {
  const event = JSON.parse(message.body);
  
  switch(event.type) {
    case 'WORKFLOW_STARTED':
      console.log('Workflow started:', event.workflowInstanceId);
      break;
    case 'TASK_CREATED':
      console.log('New task for role:', event.roleName);
      break;
    case 'TASK_APPROVED':
      console.log('Task approved by:', event.approvedBy);
      break;
    case 'TASK_REJECTED':
      console.log('Task rejected by:', event.rejectedBy);
      break;
    case 'WORKFLOW_COMPLETED':
      console.log('Workflow completed');
      break;
  }
});
```

---

## Bulk Operations

### Bulk Approve Multiple Tasks

```bash
POST /api/workflow/tasks/bulk-approve

Content-Type: application/json
Authorization: Bearer <JWT_TOKEN>

{
  "taskIds": [201, 202, 203],
  "comment": "Bulk approval of multiple tasks"
}
```

Response:
```json
{
  "totalTasks": 3,
  "successfulCount": 3,
  "successfulTaskIds": [201, 202, 203],
  "errors": []
}
```

### Bulk Reject Multiple Tasks

```bash
POST /api/workflow/tasks/bulk-reject

Content-Type: application/json
Authorization: Bearer <JWT_TOKEN>

{
  "taskIds": [201, 202],
  "comment": "Bulk rejection of multiple tasks"
}
```

---

## Available Conditions for Conditional Routing

### Predefined Conditions

| Condition | Description | Example |
|-----------|-------------|---------|
| `isHighValue` | Amount > 50,000 | High-value contracts |
| `isLowValue` | Amount ≤ 5,000 | Small invoices |
| `isMediumValue` | Amount 5,000-50,000 | Medium-value documents |
| `isContract` | Document type = CONTRACT | Legal documents |
| `isInvoice` | Document type = INVOICE | Financial documents |
| `isUrgent` | Priority = HIGH or URGENT | Time-sensitive documents |
| `isNormal` | Priority = NORMAL or MEDIUM | Standard documents |

### Comparison Conditions

| Syntax | Description | Example |
|--------|-------------|---------|
| `field > value` | Greater than | `amount > 10000` |
| `field >= value` | Greater or equal | `amount >= 50000` |
| `field < value` | Less than | `amount < 5000` |
| `field <= value` | Less or equal | `amount <= 1000` |
| `field = value` | Equal | `priority = HIGH` |
| `field != value` | Not equal | `type != CONTRACT` |

### Negation

Use `!` prefix to negate any condition:
- `!isHighValue` - not high value (amount ≤ 50,000)
- `!isContract` - not a contract (any type except CONTRACT)

### Supported Fields

- `amount` - Document amount (BigDecimal)
- `priority` - Document priority (LOW, NORMAL, HIGH, URGENT)
- `type` - Document type (CONTRACT, INVOICE, etc.)
- `status` - Document status (DRAFT, SUBMITTED, etc.)

---

## Role Level Reference

- **Worker: 10** - Basic employees
- **Manager: 60** - Department managers
- **Accountant: 70** - Financial reviewers
- **Senior: 75** - Senior specialists
- **Director: 80** - Department directors
- **CEO: 100** - Chief executive officer

Requirements:
- User must have role level >= required level to approve
- Example: Manager (60) can approve tasks requiring level 10-60
- CEO (100) can approve all tasks
