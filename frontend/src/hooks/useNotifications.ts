import { useEffect, useState } from 'react';
import { useAuthStore } from '../store/authStore';
import { useNotificationStore } from '../store/notificationStore';
import toast from 'react-hot-toast';

interface UseNotificationsOptions {
    connect?: boolean;
}

export const useNotifications = ({ connect = true }: UseNotificationsOptions = {}) => {
    const { token } = useAuthStore();
    const { notificationsEnabled, setNotificationsEnabled } = useNotificationStore();
    const [permission, setPermission] = useState<NotificationPermission>('default');

    useEffect(() => {
        if ('Notification' in window) {
            const currentPermission = Notification.permission;
            setPermission(currentPermission);

            if (currentPermission !== 'granted' && notificationsEnabled) {
                setNotificationsEnabled(false);
            }
        }
    }, [notificationsEnabled, setNotificationsEnabled]);

    useEffect(() => {
        if (!connect || !token || !notificationsEnabled) {
            return;
        }

        let eventSource: EventSource | null = null;
        let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
        let shouldReconnect = true;

        const handleNotification = (rawMessage: string) => {
            const message = rawMessage?.trim() || 'Your coach has an update for you.';
            const isStreakAlert = /streak alert|don't break|streak/i.test(message);

            toast(message, {
                icon: isStreakAlert ? '!' : 'AI',
                duration: isStreakAlert ? 8000 : 5000,
                style: {
                    borderRadius: '10px',
                    background: isStreakAlert ? '#7c2d12' : '#1f2937',
                    color: '#fff',
                    border: isStreakAlert ? '1px solid #f97316' : 'none',
                }
            });

            if ('Notification' in window && Notification.permission === 'granted') {
                const notification = new Notification(isStreakAlert ? 'Coach Streak Alert' : 'AI Coach', {
                    body: message,
                    icon: '/vite.svg'
                });

                notification.onclick = () => {
                    window.focus();
                    window.location.href = '/coach';
                };
            }
        };

        const connectSse = () => {
            const url = `http://localhost:8080/api/notifications/subscribe?token=${token}`;
            eventSource = new EventSource(url);

            eventSource.addEventListener('notification', (event: MessageEvent) => {
                handleNotification(event.data);
            });

            eventSource.onerror = () => {
                eventSource?.close();
                if (!shouldReconnect || reconnectTimer) {
                    return;
                }
                reconnectTimer = setTimeout(() => {
                    reconnectTimer = null;
                    connectSse();
                }, 3000);
            };
        };

        connectSse();

        return () => {
            shouldReconnect = false;
            if (reconnectTimer) {
                clearTimeout(reconnectTimer);
            }
            eventSource?.close();
        };
    }, [token, connect, notificationsEnabled]);

    const requestPermission = async () => {
        if (!('Notification' in window)) {
            toast.error('This browser does not support notifications.');
            setNotificationsEnabled(false);
            return;
        }

        if (Notification.permission === 'denied') {
            setPermission('denied');
            setNotificationsEnabled(false);
            toast.error('Notifications are blocked by your browser settings.');
            return;
        }

        const perm = Notification.permission === 'granted'
            ? 'granted'
            : await Notification.requestPermission();
        setPermission(perm);

        if (perm === 'granted') {
            setNotificationsEnabled(true);
            toast.success('Notifications enabled.');
            new Notification('AI Coach', { body: 'I will proactively check in with you.' });
            return;
        }

        setNotificationsEnabled(false);
        toast.error('Notification permission was not granted.');
    };

    const disableNotifications = () => {
        setNotificationsEnabled(false);
        toast('Notifications muted.');
    };

    const toggleNotifications = async () => {
        if (notificationsEnabled) {
            disableNotifications();
            return;
        }

        await requestPermission();
    };

    return { requestPermission, permission, notificationsEnabled, toggleNotifications, disableNotifications };
};
