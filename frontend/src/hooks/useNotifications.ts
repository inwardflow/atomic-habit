import { useEffect, useState } from 'react';
import { useAuthStore } from '../store/authStore';
import { useNotificationStore } from '../store/notificationStore';
import { BACKEND_URL } from '../api/axios';
import toast from 'react-hot-toast';
import i18n from '../i18n';
import { readSse, SseHttpError } from '../utils/sse';

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

        const controller = new AbortController();
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

        const scheduleReconnect = () => {
            if (!shouldReconnect || reconnectTimer) return;
            reconnectTimer = setTimeout(() => {
                reconnectTimer = null;
                connectSse();
            }, 3000);
        };

        const connectSse = () => {
            readSse(`${BACKEND_URL}/api/notifications/subscribe`, {
                headers: { Authorization: `Bearer ${token}` },
                signal: controller.signal,
                onEvent: (event, data) => {
                    if (event === 'notification') handleNotification(data);
                },
            })
                .then(scheduleReconnect) // server closed the stream (e.g. timeout)
                .catch((error) => {
                    // 401: the access token expired; the effect re-runs once it is refreshed.
                    if (controller.signal.aborted || (error instanceof SseHttpError && error.status === 401)) return;
                    scheduleReconnect();
                });
        };

        connectSse();

        return () => {
            shouldReconnect = false;
            if (reconnectTimer) {
                clearTimeout(reconnectTimer);
            }
            controller.abort();
        };
    }, [token, connect, notificationsEnabled]);

    const requestPermission = async () => {
        if (!('Notification' in window)) {
            toast.error(i18n.t('notifications.unsupported'));
            setNotificationsEnabled(false);
            return;
        }

        if (Notification.permission === 'denied') {
            setPermission('denied');
            setNotificationsEnabled(false);
            toast.error(i18n.t('notifications.blocked'));
            return;
        }

        const perm = Notification.permission === 'granted'
            ? 'granted'
            : await Notification.requestPermission();
        setPermission(perm);

        if (perm === 'granted') {
            setNotificationsEnabled(true);
            toast.success(i18n.t('notifications.enabled'));
            new Notification(i18n.t('nav.coach'), { body: i18n.t('notifications.welcome_body') });
            return;
        }

        setNotificationsEnabled(false);
        toast.error(i18n.t('notifications.not_granted'));
    };

    const disableNotifications = () => {
        setNotificationsEnabled(false);
        toast(i18n.t('notifications.muted'));
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
