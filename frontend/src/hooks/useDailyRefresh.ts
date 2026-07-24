import { useEffect, useRef } from 'react';

/**
 * Calls `callback` once at the next occurrence of `hour:00` local time,
 * then again every 24h after that. Used to refresh data that should only
 * change once per day (e.g. the farming report / today's task list),
 * independent of how often the component re-renders.
 */
export function useDailyRefresh(callback: () => void, hour = 6): void {
  const callbackRef = useRef(callback);
  callbackRef.current = callback;

  useEffect(() => {
    let timeoutId: ReturnType<typeof setTimeout>;

    const scheduleNext = () => {
      const now = new Date();
      const next = new Date(now.getFullYear(), now.getMonth(), now.getDate(), hour, 0, 0, 0);
      if (next <= now) next.setDate(next.getDate() + 1);

      timeoutId = setTimeout(() => {
        callbackRef.current();
        scheduleNext();
      }, next.getTime() - now.getTime());
    };

    scheduleNext();
    return () => clearTimeout(timeoutId);
  }, [hour]);
}
