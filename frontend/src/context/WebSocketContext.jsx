import { Client } from '@stomp/stompjs';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import SockJS from 'sockjs-client';
import { useAuth } from './useAuth';
import { WebSocketContext } from './useWebSocket';

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '');

function resolveWsEndpointUrl() {
  if (API_BASE_URL) {
    return `${API_BASE_URL}/ws`;
  }
  return `${window.location.origin}/ws`;
}

export function WebSocketProvider({ children }) {
  const { isAuthenticated, token } = useAuth();

  const [connectionStatus, setConnectionStatus] = useState('DISCONNECTED');
  const [connectCount, setConnectCount] = useState(0);

  const clientRef = useRef(null);
  const subscriptionsRef = useRef(new Map());
  const nextSubIdRef = useRef(1);

  const attachStompSubscription = useCallback((client, entry) => {
    if (!client?.connected) {
      return;
    }
    try {
      if (entry.stompSub) {
        entry.stompSub.unsubscribe();
      }
      entry.stompSub = client.subscribe(entry.destination, (frame) => {
        let parsedBody = frame.body;
        if (frame.body) {
          try {
            parsedBody = JSON.parse(frame.body);
          } catch {
            parsedBody = frame.body;
          }
        }
        entry.handler(parsedBody, frame);
      });
    } catch {
      entry.stompSub = null;
    }
  }, []);

  useEffect(() => {
    if (!isAuthenticated || !token) {
      if (clientRef.current) {
        clientRef.current.deactivate();
        clientRef.current = null;
      }
      setConnectionStatus('DISCONNECTED');
      setConnectCount(0);
      return undefined;
    }

    let isDisposed = false;
    let hasConnectedOnce = false;
    const activeSubscriptions = subscriptionsRef.current;
    setConnectionStatus('CONNECTING');

    const stompClient = new Client({
      webSocketFactory: () => new SockJS(resolveWsEndpointUrl()),
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: () => {},
      onConnect: () => {
        if (isDisposed) {
          return;
        }
        hasConnectedOnce = true;
        setConnectionStatus('CONNECTED');
        setConnectCount((prev) => prev + 1);

        activeSubscriptions.forEach((entry) => {
          attachStompSubscription(stompClient, entry);
        });
      },
      onDisconnect: () => {
        if (!isDisposed) {
          setConnectionStatus('DISCONNECTED');
        }
      },
      onWebSocketClose: () => {
        if (!isDisposed) {
          setConnectionStatus(hasConnectedOnce ? 'RECONNECTING' : 'CONNECTING');
        }
      },
      onStompError: () => {
        if (!isDisposed) {
          setConnectionStatus('RECONNECTING');
        }
      },
    });

    clientRef.current = stompClient;
    stompClient.activate();

    return () => {
      isDisposed = true;
      activeSubscriptions.forEach((entry) => {
        if (entry.stompSub) {
          try {
            entry.stompSub.unsubscribe();
          } catch {
            // Ignore teardown errors when socket is already closing
          }
          entry.stompSub = null;
        }
      });
      stompClient.deactivate();
      if (clientRef.current === stompClient) {
        clientRef.current = null;
      }
    };
  }, [isAuthenticated, token, attachStompSubscription]);

  const subscribe = useCallback(
    (destination, handler) => {
      const id = nextSubIdRef.current++;
      const entry = { destination, handler, stompSub: null };
      subscriptionsRef.current.set(id, entry);

      if (clientRef.current?.connected) {
        attachStompSubscription(clientRef.current, entry);
      }

      return () => {
        const current = subscriptionsRef.current.get(id);
        if (current?.stompSub) {
          try {
            current.stompSub.unsubscribe();
          } catch {
            // Ignore unsubscribe errors on closed sockets
          }
        }
        subscriptionsRef.current.delete(id);
      };
    },
    [attachStompSubscription]
  );

  const publish = useCallback((destination, payload) => {
    const client = clientRef.current;
    if (!client?.connected) {
      return false;
    }
    try {
      client.publish({
        destination,
        body: JSON.stringify(payload),
      });
      return true;
    } catch {
      return false;
    }
  }, []);

  const value = useMemo(
    () => ({
      connectionStatus,
      isConnected: connectionStatus === 'CONNECTED',
      connectCount,
      subscribe,
      publish,
    }),
    [connectionStatus, connectCount, subscribe, publish]
  );

  return <WebSocketContext.Provider value={value}>{children}</WebSocketContext.Provider>;
}
