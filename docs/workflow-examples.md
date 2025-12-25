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
  <step order="1" roleName="Manager" roleLevel="60" action="review"/>
  <step order="2" roleName="Director" roleLevel="80" action="approve" parallel="true"/>
  <step order="2" roleName="Accountant" roleLevel="70" action="verify" parallel="true"/>
  <step order="3" roleName="CEO" roleLevel="100" action="sign"/>
</workflow>
```

**Flow:**
1. Manager reviews
2. Director and Accountant approve simultaneously (both order=2)
3. After both approve → CEO signs

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
