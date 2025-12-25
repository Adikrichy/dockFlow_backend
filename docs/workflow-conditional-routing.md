# Advanced Workflow: Conditional Routing

## Общее описание

Conditional routing позволяет документу возвращаться на предыдущие шаги при отклонении, вместо завершения всего workflow'а как отклоненного.

---

## Пример 1: Return to Previous Step

```xml
<workflow>
  <!-- Шаги -->
  <step order="1" roleName="Manager" roleLevel="60" action="review" parallel="false" description="Initial review"/>
  <step order="2" roleName="Director" roleLevel="80" action="approve" parallel="false" description="Director approval"/>
  <step order="3" roleName="CEO" roleLevel="100" action="sign" parallel="false" description="Final signature"/>
  
  <!-- Правила маршрутизации -->
  <!-- Если Manager отклонит, вернуться на шаг 1 (повторный review) -->
  <onReject stepOrder="1" targetStep="1" description="Return to manager for revision"/>
  
  <!-- Если Director отклонит, вернуться на шаг 1 (Manager пересмотрит) -->
  <onReject stepOrder="2" targetStep="1" description="Return to manager for significant revision"/>
  
  <!-- Если CEO отклонит, завершить workflow как rejected (targetStep="null" or omit) -->
  <onReject stepOrder="3" description="Final decision - workflow rejected"/>
</workflow>
```

**Flow:**
```
Manager reviews → (reject?) → Manager reviews again
    ↓ (approve)
Director approves → (reject?) → Manager reviews again
    ↓ (approve)
CEO signs → (reject?) → WORKFLOW REJECTED
    ↓ (approve)
WORKFLOW COMPLETED
```

---

## Пример 2: Different Return Points

```xml
<workflow>
  <step order="1" roleName="Manager" roleLevel="60" action="review"/>
  <step order="2" roleName="Senior" roleLevel="70" action="technical_check"/>
  <step order="3" roleName="Director" roleLevel="80" action="approve"/>
  <step order="4" roleName="CEO" roleLevel="100" action="sign"/>
  
  <!-- Разные точки возврата в зависимости от шага -->
  <onReject stepOrder="1" targetStep="1"/>
  <onReject stepOrder="2" targetStep="1"/> <!-- Вернуться к Manager -->
  <onReject stepOrder="3" targetStep="2"/> <!-- Вернуться к Senior для переанализа -->
  <onReject stepOrder="4"/> <!-- Завершить как rejected -->
</workflow>
```

---

## Пример 3: Approval Rules (onApprove)

```xml
<workflow>
  <step order="1" roleName="Manager" roleLevel="60" action="review"/>
  <step order="2" roleName="Director" roleLevel="80" action="approve"/>
  <step order="3" roleName="CEO" roleLevel="100" action="sign"/>
  
  <!-- Условия одобрения (может использоваться для skip шагов) -->
  <onApprove stepOrder="1" targetStep="3" condition="isLowValue"/> <!-- Skip director для малых сумм -->
  
  <!-- Стандартные reject правила -->
  <onReject stepOrder="1" targetStep="1"/>
  <onReject stepOrder="2" targetStep="1"/>
</workflow>
```

---

## API для создания template с conditional routing

```bash
POST /api/workflow/template

Content-Type: application/json
Authorization: Bearer {JWT}

{
  "name": "Contract Approval with Revisions",
  "description": "Allow multiple revision rounds",
  "companyId": 1,
  "workflowXml": "<workflow>
    <step order=\"1\" roleName=\"Manager\" roleLevel=\"60\" action=\"review\"/>
    <step order=\"2\" roleName=\"Director\" roleLevel=\"80\" action=\"approve\"/>
    <step order=\"3\" roleName=\"CEO\" roleLevel=\"100\" action=\"sign\"/>
    <onReject stepOrder=\"1\" targetStep=\"1\"/>
    <onReject stepOrder=\"2\" targetStep=\"1\"/>
  </workflow>"
}
```

Response:
```json
{
  "id": 5,
  "name": "Contract Approval with Revisions",
  "description": "Allow multiple revision rounds",
  "isActive": true,
  "createdAt": "2024-01-15T12:00:00"
}
```

---

## Database Schema Changes

### New Table: routing_rules

```sql
CREATE TABLE routing_rules (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    template_id BIGINT NOT NULL,
    step_order INT NOT NULL,
    routing_type VARCHAR(50) NOT NULL, -- 'ON_APPROVE', 'ON_REJECT', 'ON_TIMEOUT'
    target_step INT,                    -- NULL means complete workflow
    is_override_allowed BOOLEAN DEFAULT true,
    description VARCHAR(500),
    FOREIGN KEY (template_id) REFERENCES workflow_templates(id),
    INDEX idx_template (template_id),
    INDEX idx_step_order (step_order)
);
```

### Updated: workflow_templates

- Renamed: `stepsXml` → `workflowXml`
- Added: `company_id` (Long instead of relationship)
- Added: `is_active` (Boolean)
- Relationship: OneToMany to `routing_rules`

### Updated: tasks

- Added: `CANCELLED` status (when workflow returns to previous step)

---

## Testing Scenario

```bash
# 1. Create template with conditional routing
POST /api/workflow/template
→ returns template_id = 5

# 2. Start workflow for document
POST /api/workflow/5/start?documentId=42
→ returns workflow_instance_id = 100, task_id = 201 (Manager's task)

# 3. Manager rejects with comment
POST /api/workflow/task/201/reject
{
  "comment": "Need more details in section 3"
}
→ Task status: REJECTED
→ Workflow still IN_PROGRESS (returns to step 1)
→ New task 201 created for same Manager
→ WebSocket event: WORKFLOW_REJECTED + "Returned to step 1"

# 4. Manager reviews again and approves
POST /api/workflow/task/201/approve
{
  "comment": "Now looks good"
}
→ Task status: APPROVED
→ Workflow moves to step 2 (Director)
→ New task 202 created for Director

# 5. Director approves
POST /api/workflow/task/202/approve
{
  "comment": "Approved"
}
→ Workflow moves to step 3 (CEO)
→ New task 203 created for CEO

# 6. CEO approves
POST /api/workflow/task/203/approve
{
  "comment": "Signed"
}
→ Workflow status: COMPLETED
→ All tasks: APPROVED
→ WebSocket event: WORKFLOW_COMPLETED
```

---

## Enums

```java
// RoutingType
ON_APPROVE  // Правило для одобрения
ON_REJECT   // Правило для отклонения
ON_TIMEOUT  // Правило для истечения времени (future)

// TaskStatus (updated)
PENDING     // Ожидает одобрения
APPROVED    // Одобрено
REJECTED    // Отклонено
CANCELLED   // Отменено (когда workflow вернулся на предыдущий шаг)
OVERDUE     // Превышено время
```

---

## Implementation Details

### WorkflowEngine.rejectTask()

```
1. Mark task as REJECTED
2. Find RoutingRule for (stepOrder, ON_REJECT)
3. If rule exists:
   a. If targetStep is null → Complete workflow as REJECTED
   b. If targetStep > 0 → returnToStep(targetStep)
4. If no rule → Complete workflow as REJECTED
```

### WorkflowEngine.returnToStep()

```
1. Find all tasks >= targetStep
2. Mark them as CANCELLED
3. Find tasks at targetStep
4. Reset them to PENDING (remove completion info)
5. Set workflow status to IN_PROGRESS
```

---

## WebSocket Events (Enhanced)

```json
{
  "type": "WORKFLOW_REJECTED",
  "workflowInstanceId": 100,
  "reason": "Returned to step 1", // или просто rejection reason
  "timestamp": "2024-01-15T12:30:00"
}
```

---

## Future Enhancements

1. **Timeout Rules** - Auto-reject if not approved in X days
2. **Parallel Rejections** - Handle multiple parallel approvers rejecting
3. **Escalation** - Automatically escalate to higher role if time exceeded
4. **Delegation** - Allow task delegation to colleague
5. **Comments History** - Full audit trail with timestamps
6. **Conditional Logic** - Dynamic routing based on document properties
