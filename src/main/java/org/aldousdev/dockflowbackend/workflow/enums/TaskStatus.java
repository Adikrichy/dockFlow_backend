package org.aldousdev.dockflowbackend.workflow.enums;

public enum TaskStatus {
    PENDING,      // Ожидает одобрения (в общем пуле)
    IN_PROGRESS,  // В процессе (взято конкретным пользователем)
    APPROVED,     // Одобрено
    REJECTED,     // Отклонено (может вернуть на предыдущий шаг)
    CANCELLED,    // Отменено (когда workflow вернулся на предыдущий шаг)
    OVERDUE       // Превышено время
}

