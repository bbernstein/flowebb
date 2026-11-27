
/**
 * Formats a time in a timezone-aware way, handling Node version compatibility
 */
const formatTimeWithTimezone = (date: Date, offsetSeconds: number): string => {
    // Create a new date adjusted for the timezone offset
    const localDate = new Date(date.getTime() + (date.getTimezoneOffset() * 60 * 1000) + (offsetSeconds * 1000));

    return localDate.toLocaleTimeString('en-US', {
        hour: '2-digit',
        minute: '2-digit',
        hour12: true
    });
};

export const formatTime = (timestamp: number, timeZoneOffsetSeconds: number): string => {
    const date = new Date(timestamp);
    return formatTimeWithTimezone(date, timeZoneOffsetSeconds);
};

/**
 * Formats a time in the station's timezone
 * @param timestamp Unix timestamp in milliseconds
 * @param offsetSeconds Timezone offset in seconds
 * @returns Formatted time string in station's local timezone
 */
export const formatStationTime = (timestamp: number, offsetSeconds: number | null): string => {
    if (offsetSeconds !== null) {
        const date = new Date(timestamp);
        return formatTimeWithTimezone(date, offsetSeconds);
    }
    const date = new Date(timestamp);
    return date.toLocaleTimeString();
};

/**
 * Formats a full date and time in the station's timezone
 * @param timestamp Unix timestamp in milliseconds
 * @param offsetSeconds Timezone offset in seconds
 * @returns Formatted date and time string in station's local timezone
 */
export const formatStationDateTime = (timestamp: number, offsetSeconds: number | null): string => {
    const date = new Date(timestamp);
    if (offsetSeconds !== null) {
        // Convert to station's timezone
        const localDate = new Date(date.getTime() + (date.getTimezoneOffset() * 60 * 1000) + (offsetSeconds * 1000));
        return localDate.toLocaleString('en-US', {
            weekday: 'short',
            day: '2-digit',
            month: 'short',
            hour: '2-digit',
            minute: '2-digit',
            hour12: true
        });
    }
    return date.toLocaleString();
};

export interface DayBounds {
    startOfDay: Date;
    endOfDay: Date;
    startDateTime: string;
    endDateTime: string;
}

const MS_PER_HOUR = 60 * 60 * 1000;

/**
 * Gets the 48-hour window around a station's local day: from 12:00 (noon) on
 * the day before to 12:00 on the day after, in the station's local time.
 *
 * Uses pure UTC arithmetic, so the browser's timezone (and its DST rules) never
 * affects the result, and half-hour offsets are preserved.
 *
 * @param timestamp Unix timestamp in milliseconds
 * @param offsetSeconds Station timezone offset from UTC in seconds
 * @returns `startOfDay` / `endOfDay` are the real instants of the window edges;
 *   `startDateTime` / `endDateTime` are station-local wall-clock strings
 *   (YYYY-MM-DDTHH:mm:ss, no zone) as the API expects.
 */
export const getStationDayBounds = (timestamp: number, offsetSeconds: number): DayBounds => {
    const offsetMs = offsetSeconds * 1000;
    // Station wall-clock time encoded as a UTC instant, so the UTC getters read
    // the station's calendar fields and the browser's own zone never applies.
    const stationWall = new Date(timestamp + offsetMs);
    const midnightWallMs = Date.UTC(
        stationWall.getUTCFullYear(),
        stationWall.getUTCMonth(),
        stationWall.getUTCDate()
    );
    const startWallMs = midnightWallMs - 12 * MS_PER_HOUR;
    const endWallMs = startWallMs + 48 * MS_PER_HOUR;

    // YYYY-MM-DDTHH:mm:ss, station-local, no zone (what the API expects).
    const formatForApi = (wallMs: number) => new Date(wallMs).toISOString().slice(0, 19);

    return {
        startOfDay: new Date(startWallMs - offsetMs),
        endOfDay: new Date(endWallMs - offsetMs),
        startDateTime: formatForApi(startWallMs),
        endDateTime: formatForApi(endWallMs),
    };
};

/**
 * Formats a local time string from the API for display
 * @param localTime ISO formatted local time string from API
 * @returns Formatted time string
 */
export const formatDisplayTime = (localTime: string): string => {
    const date = new Date(localTime);
    return date.toLocaleTimeString('en-US', {
        hour: '2-digit',
        minute: '2-digit',
        hour12: true
    });
};

/**
 * Formats a full date and time for display
 * @param localTime ISO formatted local time string from API
 * @returns Formatted date and time string
 */
export const formatDisplayDateTime = (localTime: string): string => {
    const date = new Date(localTime);
    return date.toLocaleString('en-US', {
        weekday: 'short',
        day: '2-digit',
        month: 'short',
        hour: '2-digit',
        minute: '2-digit',
        hour12: true
    });
};
