// WebSocket Test Script for DockFlow Chat
// Используйте в браузере консоль (F12) или в Node.js с @stomp/stompjs

class ChatWebSocketTester {
    constructor(url = 'ws://localhost:8080/ws/chat') {
        this.url = url;
        this.client = null;
        this.subscription = null;
    }

    // Подключиться
    connect() {
        console.log('🔌 Connecting to:', this.url);
        
        this.client = new StompJs.Client({
            brokerURL: this.url,
            reconnectDelay: 5000,
            heartbeatIncoming: 4000,
            heartbeatOutgoing: 4000,
        });

        this.client.onConnect = () => {
            console.log('✅ Connected to WebSocket!');
            console.log('Готовы к отправке и получению сообщений');
        };

        this.client.onStompError = (frame) => {
            console.error('❌ STOMP error:', frame.body);
        };

        this.client.onDisconnect = () => {
            console.log('❌ Disconnected');
        };

        this.client.activate();
    }

    // Отключиться
    disconnect() {
        if (this.client) {
            this.client.deactivate();
            console.log('Disconnected');
        }
    }

    // Подписаться на канал
    subscribe(channelId) {
        if (!this.client?.connected) {
            console.error('Not connected. Call connect() first');
            return;
        }

        const destination = `/topic/channel/${channelId}`;
        console.log(`📨 Subscribing to ${destination}`);

        this.subscription = this.client.subscribe(destination, (message) => {
            const data = JSON.parse(message.body);
            console.log('📬 Message received:', data);
            console.log(`  From: ${data.senderName}`);
            console.log(`  Content: ${data.content}`);
            console.log(`  Time: ${new Date(data.timestamp).toLocaleString()}`);
        });

        console.log('✅ Subscribed!');
    }

    // Отписаться
    unsubscribe() {
        if (this.subscription) {
            this.subscription.unsubscribe();
            console.log('Unsubscribed');
        }
    }

    // Отправить сообщение
    sendMessage(channelId, content) {
        if (!this.client?.connected) {
            console.error('Not connected');
            return;
        }

        console.log(`📤 Sending message to channel ${channelId}`);
        
        this.client.publish({
            destination: `/app/chat/${channelId}`,
            body: JSON.stringify({
                content: content
            })
        });

        console.log('✅ Message sent!');
    }
}

// ========== ПРИМЕРЫ ИСПОЛЬЗОВАНИЯ ==========

/*
// 1. Создать тестер
const tester = new ChatWebSocketTester();

// 2. Подключиться
tester.connect();

// 3. Дождаться подключения (2 сек), потом подписаться
setTimeout(() => {
    tester.subscribe(1); // Подписываемся на канал 1
}, 2000);

// 4. Отправить сообщение через 3 сек
setTimeout(() => {
    tester.sendMessage(1, 'Hello from automated test!');
}, 3000);

// 5. Отправлять сообщения каждые 5 сек
setInterval(() => {
    tester.sendMessage(1, 'Auto message at ' + new Date().toLocaleTimeString());
}, 5000);

// 6. Отключиться через 1 минуту
setTimeout(() => {
    tester.disconnect();
}, 60000);
*/
