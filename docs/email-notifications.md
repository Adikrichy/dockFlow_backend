# Email Notifications System

## Описание

Система автоматических email уведомлений о статусе workflow и tasks. Все письма отправляются асинхронно, чтобы не блокировать основной процесс.

---

## Типы уведомлений

### 1. Task Created (Задача создана)
**Когда:** Новая task создана и требуется одобрение
**Получает:** Пользователи с требуемой ролью
**Содержимое:** 
- Название документа
- Требуемая роль
- Ссылка на задачу
- Тип действия (review/approve/sign)

### 2. Task Approved (Задача одобрена)
**Когда:** Task одобрена пользователем
**Получает:** Инициатор workflow'а
**Содержимое:**
- Кто одобрил
- Название документа
- Прогресс выполнения (n из m шагов)
- Progress bar визуализация

### 3. Task Rejected (Задача отклонена)
**Когда:** Task отклонена
**Получает:** Инициатор workflow'а
**Содержимое:**
- Кто отклонил
- Название документа
- Информация о возврате (на какой шаг вернулось)
- Комментарий отклонения

### 4. Workflow Completed (Workflow завершен)
**Когда:** Все шаги одобрены успешно
**Получает:** Инициатор workflow'а
**Содержимое:**
- Успешный статус
- Название документа
- Кто инициировал
- Время завершения

### 5. Workflow Rejected (Workflow отклонен)
**Когда:** Workflow окончательно отклонен (последний шаг rejected без возврата)
**Получает:** Инициатор workflow'а
**Содержимое:**
- Статус отклонения
- Название документа
- Причина отклонения

### 6. Task Reminder (Напоминание о задаче)
**Когда:** Task ожидает одобрения > N дней (можно настроить)
**Получает:** Пользователи с требуемой ролью
**Содержимое:**
- Напоминание о pending task
- Ссылка на список задач
- Информация о документе

---

## Конфигурация

### application.properties

```properties
# SMTP Configuration
spring.mail.username=${MAIL_USERNAME:}
spring.mail.password=${MAIL_PASSWORD:}
spring.mail.host=${MAIL_HOST:smtp.gmail.com}
spring.mail.port=${MAIL_PORT:587}
spring.mail.from=${MAIL_FROM:noreply@dockflow.com}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
spring.mail.properties.mail.smtp.starttls.required=true
spring.mail.properties.mail.smtp.connectiontimeout=5000
spring.mail.properties.mail.smtp.timeout=5000
spring.mail.properties.mail.smtp.writetimeout=5000

# Async Thread Pool
spring.task.execution.pool.core-size=2
spring.task.execution.pool.max-size=5
spring.task.execution.pool.queue-capacity=100
```

### Environment Variables

```bash
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-specific-password
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_FROM=noreply@dockflow.com
```

### Gmail Setup

1. Включить 2FA на аккаунте
2. Создать App Password в Security settings
3. Использовать App Password как `MAIL_PASSWORD`

---

## Usage

### Автоматическая отправка

Email отправляются автоматически при следующих событиях:

```java
// WorkflowEngine - при создании task
workflowEngine.createTask(instance, stepOrder, step);
// → Automatically sends: TaskCreatedEmail

// WorkflowEngine - при одобрении
workflowEngine.approveTask(task, approvedBy, comment);
// → Automatically sends: TaskApprovedEmail

// WorkflowEngine - при отклонении
workflowEngine.rejectTask(task, rejectedBy, comment);
// → Automatically sends: TaskRejectedEmail

// WorkflowEngine - при завершении
workflowEngine.moveToNextStep(instance);
// → Automatically sends: WorkflowCompletedEmail
```

### Ручная отправка напоминания

```java
@Autowired
private EmailNotificationService emailNotificationService;

@Scheduled(cron = "0 9 * * MON") // Каждый понедельник в 9:00
public void sendPendingTaskReminders() {
    List<Task> pendingTasks = taskRepository.findByStatus(TaskStatus.PENDING);
    
    for (Task task : pendingTasks) {
        // Получить пользователя с требуемой ролью
        User user = getUserWithRole(task.getRequiredRoleName());
        
        // Отправить напоминание
        emailNotificationService.sendTaskReminderEmail(task, user);
    }
}
```

---

## Architecture

### Classes

- **EmailService** - Основной сервис отправки писем
  - `sendSimpleEmail()` - Простое текстовое письмо
  - `sendHtmlEmail()` - HTML письмо
  - `sendTaskCreatedEmail()` - Специализированное письмо о новой task
  - Другие специализированные методы

- **EmailNotificationService** - Бизнес-логика уведомлений
  - `@Async` методы для асинхронной отправки
  - Интеграция с workflow events
  - Форматирование и подготовка данных

- **DockFlowBackendApplication** - Главный класс с `@EnableAsync`

### Async Processing

```
1. Task создана → WorkflowEngine.createTask()
2. emailNotificationService.notifyTaskCreated() вызывается с @Async
3. Email отправляется в отдельном потоке (не блокирует)
4. Main thread продолжает обработку workflow'а
```

---

## Email Templates

HTML шаблоны включают:
- Профессиональный дизайн
- Информацию об статусе
- Ссылки для действий
- Брендирование DocFlow
- Поддержка русского языка (UTF-8)

---

## Troubleshooting

### Email не отправляется

1. Проверить конфиг SMTP
```bash
MAIL_USERNAME=correct@gmail.com
MAIL_PASSWORD=app-specific-password (не простой пароль!)
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
```

2. Проверить логи
```
grep "Failed to send email" logs/
```

3. Проверить, что `@EnableAsync` в DockFlowBackendApplication.java

### Gmail: Authentication failed

- Убедитесь, что используется App Password, не простой пароль
- Проверить, что 2FA включена
- Проверить, что App Password создана для "Mail"

### Emails отправляются, но медленно

- Увеличить thread pool size в application.properties
```properties
spring.task.execution.pool.core-size=5
spring.task.execution.pool.max-size=10
```

### HTML письма отображаются как текст

- Проверить, что `MimeMessageHelper` создается с `true` для HTML
```java
MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
```

---

## Future Enhancements

1. **Email Templates DB** - Сохранить шаблоны в БД для кастомизации
2. **Batch Sending** - Группировать письма для отправки (reduce SMTP calls)
3. **Retry Logic** - Повторная отправка при ошибке
4. **Email Analytics** - Отслеживание opens/clicks
5. **Scheduled Reminders** - Configurable reminder timing
6. **Multiple Recipients** - Отправка одного письма нескольким получателям
7. **Localization** - Письма на разных языках в зависимости от локали пользователя
