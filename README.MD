# DocFlow Backend

**Intelligent Document Workflow Automation System**

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.0-green.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-13+-blue.svg)](https://www.postgresql.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**Development Date: December 26, 2025**

---

## 🎯 **Project Overview**

DocFlow is a comprehensive **Document Workflow Automation System** designed for enterprises to manage complex document approval processes. The system provides intelligent routing, version control, real-time collaboration, and comprehensive audit trails.

### ✨ **Key Features (Backend Completed)**

- 🔐 **Advanced Authentication** - JWT with refresh tokens, rate limiting, security audit
- 📋 **Intelligent Workflow Engine** - XML-based conditional routing, parallel approvals
- 📄 **Document Version Control** - SHA-256 hashing, watermarks, digital signatures
- 💬 **Real-time Collaboration** - WebSocket chat and notifications
- 🏢 **Multi-tenant Architecture** - Company-level data isolation
- 📊 **Comprehensive Audit** - Full activity logging and reporting
- 🔍 **API Documentation** - Swagger/OpenAPI integration

---

## 🏗️ **Architecture & Technologies**

### **Backend Stack**
- **Java 21** - Latest LTS with modern language features
- **Spring Boot 4.0** - Production-ready framework
- **Spring Security** - Authentication & authorization
- **Spring Data JPA** - Database access layer
- **PostgreSQL** - Primary database
- **WebSocket (STOMP)** - Real-time communication
- **JWT Tokens** - Secure authentication
- **iText PDF** - Document processing
- **Flyway** - Database migrations

### **Key Design Patterns**
- **Clean Architecture** - Clear separation of concerns
- **DTO Pattern** - API contract isolation
- **Repository Pattern** - Data access abstraction
- **Service Layer** - Business logic encapsulation
- **Observer Pattern** - Event-driven notifications

---

## 🔐 **Authentication & Security**

### **JWT Token Management**
```javascript
// Access Token: 1 hour validity
// Refresh Token: 7 days validity
// HttpOnly cookies for XSS protection
```

### **API Endpoints**

#### **Authentication**
```http
POST /api/register
POST /api/auth/login
POST /api/auth/logout
POST /api/auth/refresh
POST /api/auth/verify-email
```

#### **Company Management**
```http
POST /api/company/create
GET  /api/company/getAllRoles
POST /api/company/{id}/enter
POST /api/company/exit
```

### **Security Features**
- ✅ **HttpOnly Cookies** - XSS protection
- ✅ **Rate Limiting** - Brute force protection
- ✅ **Security Audit** - Comprehensive logging
- ✅ **Refresh Tokens** - Seamless authentication
- ✅ **Company Isolation** - Multi-tenant security

---

## 📋 **Workflow Engine**

### **XML-Based Workflow Definition**
```xml
<workflow>
  <!-- Sequential steps -->
  <step order="1" roleName="Manager" roleLevel="60" action="review" parallel="false"/>
  <step order="2" roleName="Director" roleLevel="80" action="approve" parallel="false"/>

  <!-- Parallel approvals -->
  <step order="3" roleName="Lawyer" roleLevel="70" action="review" parallel="true"/>
  <step order="3" roleName="Accountant" roleLevel="65" action="verify" parallel="true"/>

  <!-- Conditional routing -->
  <onApprove stepOrder="1" condition="isLowValue" targetStep="4" description="Skip director"/>
  <onApprove stepOrder="1" condition="!isLowValue" targetStep="2" description="Normal flow"/>
  <onReject stepOrder="2" targetStep="1" description="Return to manager"/>
</workflow>
```

### **Workflow API Endpoints**
```http
# Template Management
POST /api/workflow/template
GET  /api/workflow/company/{companyId}/templates
GET  /api/workflow/template/{templateId}

# Workflow Execution
POST /api/workflow/{templateId}/start?documentId={docId}
GET  /api/workflow/instance/{workflowId}
GET  /api/workflow/my-tasks
POST /api/workflow/task/{taskId}/approve
POST /api/workflow/task/{taskId}/reject

# Bulk Operations
POST /api/workflow/tasks/bulk-approve
POST /api/workflow/tasks/bulk-reject

# Audit & History
GET /api/workflow/instance/{workflowId}/audit
GET /api/workflow/document/{docId}/tasks
```

### **Workflow Features**
- ✅ **XML Configuration** - Declarative workflow definition
- ✅ **Conditional Routing** - Dynamic path selection
- ✅ **Parallel Approvals** - Simultaneous task execution
- ✅ **Role-based Access** - Level-based permissions
- ✅ **Real-time Updates** - WebSocket notifications
- ✅ **Bulk Operations** - Mass approve/reject
- ✅ **Audit Trail** - Complete activity history

---

## 📄 **Document Management**

### **Document API Endpoints**
```http
# Core Operations
POST /api/documents/upload
GET  /api/documents/{id}

# Version Control
POST /api/documents/{id}/versions
GET  /api/documents/{id}/versions
POST /api/documents/{id}/versions/{version}/restore

# Document Processing
POST /api/documents/{id}/watermark
POST /api/documents/{id}/sign
```

### **Document Features**
- ✅ **PDF Upload** - Company-scoped storage
- ✅ **Version Control** - Complete change history
- ✅ **SHA-256 Hashing** - Duplicate prevention
- ✅ **Watermarks** - Security markings
- ✅ **Digital Signatures** - Electronic approval
- ✅ **File Integrity** - Content validation
- ✅ **Metadata Support** - Extensible properties

### **Document Data Model**
```json
{
  "id": 123,
  "originalFilename": "contract.pdf",
  "fileSize": 2048576,
  "contentType": "application/pdf",
  "uploadedAt": "2025-12-26T10:30:00Z",
  "uploadedBy": "John Doe",
  "company": "Tech Corp",
  "signed": false,
  "amount": 50000.00,
  "priority": "HIGH",
  "documentType": "CONTRACT",
  "status": "DRAFT",
  "currentVersion": 3,
  "versionCount": 5
}
```

---

## 💬 **Real-time Communication**

### **WebSocket Endpoints**
```javascript
// Workflow Events
/topic/workflow/company/{companyId}
/topic/workflow/instance/{instanceId}
/user/{userId}/workflow

// Chat System
/ws/chat
/topic/channel/{channelId}
```

### **Communication Features**
- ✅ **Workflow Notifications** - Real-time task updates
- ✅ **Chat System** - Team collaboration
- ✅ **Event Broadcasting** - Server-sent events
- ✅ **User-specific Channels** - Personal notifications
- ✅ **Company Channels** - Organization-wide updates

---

## 🗄️ **Database Schema**

### **Core Tables**
- `users` - User accounts and authentication
- `companies` - Organization management
- `memberships` - User-company relationships
- `documents` - Document metadata
- `document_versions` - Version control
- `workflow_templates` - Workflow definitions
- `workflow_instances` - Active workflows
- `tasks` - Approval tasks
- `routing_rules` - Conditional routing
- `security_audit_logs` - Security events

### **Key Relationships**
```
User (N) ──── (1) Company
User (1) ──── (N) Membership (N) ──── (1) Company
Document (1) ──── (N) DocumentVersion
WorkflowTemplate (1) ──── (N) WorkflowInstance (1) ──── (N) Task
WorkflowTemplate (1) ──── (N) RoutingRule
```

---

## 🚀 **API Documentation**

### **Swagger UI**
- **URL**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI Spec**: `http://localhost:8080/v3/api-docs`

### **Authentication Flow**
```javascript
// 1. Register/Login
POST /api/auth/login
{
  "email": "user@company.com",
  "password": "SecurePass123!"
}
// → JWT cookie set automatically

// 2. Use authenticated endpoints
GET /api/workflow/my-tasks
// → Cookie sent automatically

// 3. Refresh when needed
POST /api/auth/refresh
// → New JWT cookie set
```

### **Complete Workflow Example**
```javascript
// 1. Create Company
POST /api/company/create
{
  "name": "Tech Solutions Inc",
  "description": "Leading tech company"
}

// 2. Upload Document
POST /api/documents/upload
Content-Type: multipart/form-data
file: contract.pdf

// 3. Create Workflow Template
POST /api/workflow/template
{
  "name": "Contract Approval",
  "companyId": 1,
  "workflowXml": "<workflow>...</workflow>"
}

// 4. Start Workflow
POST /api/workflow/1/start?documentId=123

// 5. Check Tasks
GET /api/workflow/my-tasks

// 6. Approve/Reject Tasks
POST /api/workflow/task/456/approve
{
  "comment": "Approved with minor changes"
}
```

---

## 🔧 **Development Setup**

### **Prerequisites**
- Java 21+
- PostgreSQL 13+
- Maven 3.8+

### **Environment Variables**
```bash
# Database
DB_URL=jdbc:postgresql://localhost:5432/dockflow
DB_USERNAME=postgres
DB_PASSWORD=your_password

# Security
JWT_SECRET=your-256-bit-secret-here
JWT_EXPIRATION=3600000
JWT_REFRESH_EXPIRATION=604800000

# Email (optional)
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=app-specific-password
```

### **Running the Application**
```bash
# Clone repository
git clone <repository-url>
cd dockflow-backend

# Install dependencies
mvn clean install

# Run application
mvn spring-boot:run

# Access endpoints
# API: http://localhost:8080
# Swagger: http://localhost:8080/swagger-ui.html
# WebSocket Test: http://localhost:8080/static/chat-tester.html
```

### **Database Migrations**
```bash
# Migrations run automatically on startup
# Or run manually:
mvn flyway:migrate
```

---

## 📊 **API Response Examples**

### **Login Response**
```json
{
  "id": 123,
  "email": "user@company.com",
  "firstName": "John",
  "lastName": "Doe",
  "company": "Tech Corp",
  "role": "Manager",
  "lastLogin": "2025-12-26T10:30:00Z"
}
```

### **Workflow Task Response**
```json
{
  "id": 456,
  "workflowInstanceId": 789,
  "stepOrder": 2,
  "requiredRoleName": "Director",
  "requiredRoleLevel": 80,
  "status": "PENDING",
  "assignedAt": "2025-12-26T10:35:00Z",
  "document": {
    "id": 123,
    "filename": "contract.pdf",
    "amount": 50000.00,
    "priority": "HIGH"
  }
}
```

### **Document Version Response**
```json
{
  "id": 101,
  "documentId": 123,
  "versionNumber": 3,
  "originalFilename": "contract_v3.pdf",
  "fileSize": 2048576,
  "changeDescription": "Added watermark",
  "changeType": "WATERMARK",
  "createdBy": "John Doe",
  "createdAt": "2025-12-26T11:00:00Z",
  "isSigned": false,
  "hasWatermark": true,
  "isCurrent": true
}
```

---

## 🎯 **Frontend Integration Guide**

### **Authentication Integration**
```javascript
// Login
const login = async (email, password) => {
  const response = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
    credentials: 'include' // Important for cookies
  });

  if (response.ok) {
    // JWT cookie set automatically
    return await response.json();
  }
};

// All subsequent requests include cookies automatically
const getMyTasks = async () => {
  const response = await fetch('/api/workflow/my-tasks', {
    credentials: 'include'
  });
  return await response.json();
};
```

### **WebSocket Integration**
```javascript
// Connect to workflow events
const socket = new SockJS('/ws/chat');
const stompClient = Stomp.over(socket);

stompClient.connect({}, () => {
  // Subscribe to company workflow events
  stompClient.subscribe('/topic/workflow/company/1', (message) => {
    const event = JSON.parse(message.body);
    console.log('Workflow event:', event);
    // Update UI based on event type
  });

  // Subscribe to personal notifications
  stompClient.subscribe('/user/123/workflow', (message) => {
    const notification = JSON.parse(message.body);
    showNotification(notification);
  });
});
```

### **File Upload Integration**
```javascript
const uploadDocument = async (file) => {
  const formData = new FormData();
  formData.append('file', file);

  const response = await fetch('/api/documents/upload', {
    method: 'POST',
    body: formData,
    credentials: 'include'
  });

  return await response.json();
};
```

---

## 🧪 **Testing**

### **Available Test Suites**
- **Unit Tests** - Service layer testing
- **Integration Tests** - Full API testing
- **Workflow Tests** - Complex scenario validation
- **Security Tests** - Authentication & authorization

### **Running Tests**
```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=WorkflowEngineParallelTest

# Run with coverage
mvn test jacoco:report
```

---

## 📈 **Performance & Scalability**

### **Database Optimization**
- ✅ **Flyway Migrations** - Version-controlled schema
- ✅ **Performance Indexes** - Optimized queries
- ✅ **Connection Pooling** - HikariCP configuration
- ✅ **Lazy Loading** - Efficient data fetching

### **Caching Strategy**
- ✅ **JWT Token Caching** - Reduced database hits
- ✅ **User Data Caching** - Membership information
- ✅ **Company Data Caching** - Organization details

### **Monitoring**
- ✅ **Spring Boot Actuator** - Health checks
- ✅ **Audit Logging** - Security event tracking
- ✅ **Performance Metrics** - Request timing

---

## 🔒 **Security Best Practices**

- **HttpOnly Cookies** - XSS protection
- **Secure Flag** - HTTPS enforcement
- **Rate Limiting** - DDoS protection
- **Input Validation** - SQL injection prevention
- **Audit Trails** - Security monitoring
- **Company Isolation** - Data segregation
- **Token Rotation** - Credential refresh

---

## 🚀 **Deployment Ready**

### **Production Checklist**
- ✅ **Environment Configuration**
- ✅ **Database Migration Strategy**
- ✅ **SSL/TLS Configuration**
- ✅ **Monitoring Setup**
- ✅ **Backup Strategy**
- ✅ **Security Headers**
- ✅ **API Rate Limiting**

### **Docker Support**
```dockerfile
FROM openjdk:21-jdk-slim
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]
```

---

## 🤝 **Contributing**

### **Development Workflow**
1. Create feature branch
2. Write tests first (TDD)
3. Implement functionality
4. Update documentation
5. Create pull request

### **Code Standards**
- **Clean Code** principles
- **SOLID** design patterns
- **Comprehensive Testing**
- **API Documentation**
- **Security First** approach

---

## 📚 **Additional Resources**

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [JWT Best Practices](https://tools.ietf.org/html/rfc8725)
- [WebSocket STOMP](https://stomp.github.io/)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [iText PDF Guide](https://itextpdf.com/)

---

## 📞 **Support**

For questions or issues:
- **API Documentation**: `http://localhost:8080/swagger-ui.html`
- **WebSocket Testing**: `http://localhost:8080/static/chat-tester.html`
- **Postman Collection**: `DocFlow-API-Collection.postman_collection.json`

---

**🎉 Backend is production-ready for frontend integration!**