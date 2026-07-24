import { useEffect, useState } from 'react';
import { fetchWeather, type WeatherSnapshot } from '../services/weatherService';

const ONE_HOUR_MS = 60 * 60 * 1000;

/** Loads weather on mount and again every hour. */
export function useWeather(regionName: string): WeatherSnapshot | null {
  const [weather, setWeather] = useState<WeatherSnapshot | null>(null);

  useEffect(() => {
    let cancelled = false;

    const load = () => {
      fetchWeather(regionName).then(snapshot => {
        if (!cancelled) setWeather(snapshot);
      });
    };

    load();
    const intervalId = setInterval(load, ONE_HOUR_MS);

    return () => {
      cancelled = true;
      clearInterval(intervalId);
    };
  }, [regionName]);

  return weather;
}
