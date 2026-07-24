package com.example.aiworkspace.service.analysis;

import org.springframework.stereotype.Component;

/** Converts WGS84 latitude/longitude to the KMA DFS 5 km grid deterministically. */
@Component
public class KmaGridConverter {

    private static final double EARTH_RADIUS_KM = 6371.00877;
    private static final double GRID_KM = 5.0;
    private static final double SLAT1 = Math.toRadians(30.0);
    private static final double SLAT2 = Math.toRadians(60.0);
    private static final double OLON = Math.toRadians(126.0);
    private static final double OLAT = Math.toRadians(38.0);
    private static final double XO = 43.0;
    private static final double YO = 136.0;

    public GridCoordinate toGrid(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90d || latitude > 90d || longitude < -180d || longitude > 180d) {
            throw new IllegalArgumentException("Coordinates are outside the WGS84 range");
        }

        double re = EARTH_RADIUS_KM / GRID_KM;
        double sn = Math.log(Math.cos(SLAT1) / Math.cos(SLAT2))
                / Math.log(Math.tan(Math.PI * 0.25 + SLAT2 * 0.5) / Math.tan(Math.PI * 0.25 + SLAT1 * 0.5));
        double sf = Math.tan(Math.PI * 0.25 + SLAT1 * 0.5);
        sf = Math.pow(sf, sn) * Math.cos(SLAT1) / sn;
        double ro = Math.tan(Math.PI * 0.25 + OLAT * 0.5);
        ro = re * sf / Math.pow(ro, sn);
        double ra = Math.tan(Math.PI * 0.25 + Math.toRadians(latitude) * 0.5);
        ra = re * sf / Math.pow(ra, sn);
        double theta = Math.toRadians(longitude) - OLON;
        if (theta > Math.PI) {
            theta -= 2.0 * Math.PI;
        } else if (theta < -Math.PI) {
            theta += 2.0 * Math.PI;
        }
        theta *= sn;

        int nx = (int) Math.floor(ra * Math.sin(theta) + XO + 0.5);
        int ny = (int) Math.floor(ro - ra * Math.cos(theta) + YO + 0.5);
        return new GridCoordinate(nx, ny);
    }

    public record GridCoordinate(int nx, int ny) {
    }
}
