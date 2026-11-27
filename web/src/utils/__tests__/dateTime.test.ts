import { execFileSync } from 'child_process';
import fs from 'fs';
import path from 'path';
import {
    formatDisplayDateTime,
    formatDisplayTime,
    formatStationDateTime,
    formatStationTime,
    formatTime,
    getStationDayBounds
} from '../dateTime';

describe('datetime utilities', () => {
    // Helper function to create a fixed timestamp
    const createTimestamp = (date: string): number => new Date(date).getTime();

    describe('formatTime', () => {
        it('formats time with timezone offset', () => {
            const timestamp = createTimestamp('2024-01-01T12:00:00Z');
            const result = formatTime(timestamp, -28800); // -8 hours (PST)
            expect(result).toBe('04:00 AM');
        });
    });

    describe('formatStationTime', () => {
        it('formats time with offset', () => {
            const timestamp = createTimestamp('2024-01-01T12:00:00Z');
            const result = formatStationTime(timestamp, -28800);
            expect(result).toBe('04:00 AM');
        });

        it('handles null offset', () => {
            const timestamp = createTimestamp('2024-01-01T12:00:00Z');
            const result = formatStationTime(timestamp, null);
            expect(result).toBeDefined();
        });
    });

    describe('formatStationDateTime', () => {
        it('formats date and time with offset', () => {
            const timestamp = createTimestamp('2024-01-01T12:00:00Z');
            const result = formatStationDateTime(timestamp, -28800);
            expect(result).toBe('Mon, Jan 01, 04:00 AM');
        });

        it('handles null offset', () => {
            const timestamp = createTimestamp('2024-01-01T12:00:00Z');
            const result = formatStationDateTime(timestamp, null);
            expect(result).toBeDefined();
        });
    });

    describe('getStationDayBounds', () => {
        it('returns correct day bounds for PST', () => {
            const timestamp = createTimestamp('2024-01-01T12:00:00Z');
            const offsetSeconds = -28800; // PST (-8 hours)
            const bounds = getStationDayBounds(timestamp, offsetSeconds);

            expect(bounds.startOfDay).toBeDefined();
            expect(bounds.endOfDay).toBeDefined();
            expect(bounds.startDateTime).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/);
            expect(bounds.endDateTime).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/);
        });
    });

    describe('getStationDayBounds is independent of the browser timezone', () => {
        // Jest hands tests a copy of process.env, so assigning process.env.TZ in a
        // test never reaches the JS engine. Instead, run the real module source in a
        // child Node process whose TZ is set, and read the results back as JSON.
        const sourcePath = path.resolve(__dirname, '../dateTime.tsx');
        const runUnderZone = (zone: string, inputs: Array<[number, number]>) => {
            const script = `
                const ts = require('typescript');
                const src = require('fs').readFileSync(process.argv[1], 'utf8');
                const out = ts.transpileModule(src, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } });
                const m = { exports: {} };
                new Function('module', 'exports', out.outputText)(m, m.exports);
                const inputs = JSON.parse(process.argv[2]);
                console.log(JSON.stringify({
                    tzOffset: new Date('2024-01-01T12:00:00Z').getTimezoneOffset(),
                    results: inputs.map(([t, o]) => {
                        const b = m.exports.getStationDayBounds(t, o);
                        return {
                            startDateTime: b.startDateTime,
                            endDateTime: b.endDateTime,
                            startOfDay: b.startOfDay.toISOString(),
                            spanMs: b.endOfDay.getTime() - b.startOfDay.getTime(),
                        };
                    }),
                }));
            `;
            const stdout = execFileSync(
                process.execPath,
                ['-e', script, sourcePath, JSON.stringify(inputs)],
                { env: { ...process.env, TZ: zone }, cwd: path.resolve(__dirname, '../../..'), encoding: 'utf8' }
            );
            return JSON.parse(stdout) as {
                tzOffset: number;
                results: Array<{ startDateTime: string; endDateTime: string; startOfDay: string; spanMs: number }>;
            };
        };

        const zones: Array<[string, number]> = [
            ['UTC', 0],
            ['America/Los_Angeles', 480],
            ['Asia/Kolkata', -330],
            ['Pacific/Kiritimati', -840],
        ];

        // [label, timestamp, offsetSeconds, startDateTime, endDateTime, startOfDay ISO]
        const cases: Array<[string, string, number, string, string, string]> = [
            ['a: PST midday', '2024-01-01T12:00:00Z', -28800, '2023-12-31T12:00:00', '2024-01-02T12:00:00', '2023-12-31T20:00:00.000Z'],
            ['b: station day differs from UTC day', '2024-01-01T03:00:00Z', -28800, '2023-12-30T12:00:00', '2024-01-01T12:00:00', '2023-12-30T20:00:00.000Z'],
            ['c: half-hour offset (India)', '2024-01-01T20:00:00Z', 19800, '2024-01-01T12:00:00', '2024-01-03T12:00:00', '2024-01-01T06:30:00.000Z'],
            ['d: US DST-change date', '2024-03-10T12:00:00Z', -18000, '2024-03-09T12:00:00', '2024-03-11T12:00:00', '2024-03-09T17:00:00.000Z'],
            ['e: exactly station midnight', '2024-01-01T08:00:00Z', -28800, '2023-12-31T12:00:00', '2024-01-02T12:00:00', '2023-12-31T20:00:00.000Z'],
        ];

        it('has the source file the child process loads', () => {
            expect(fs.existsSync(sourcePath)).toBe(true);
        });

        describe.each(zones)('browser zone %s', (zone, expectedOffsetMinutes) => {
            const run = runUnderZone(zone, cases.map(([, ts, offset]) => [new Date(ts).getTime(), offset]));

            it('actually applied the browser zone', () => {
                expect(run.tzOffset).toBe(expectedOffsetMinutes);
            });

            it.each(cases.map((c, i) => [c[0], c, i] as const))('%s', (_name, [label, , , start, end, startIso], i) => {
                const r = run.results[i];
                expect({ label, startDateTime: r.startDateTime, endDateTime: r.endDateTime, startOfDay: r.startOfDay })
                    .toEqual({ label, startDateTime: start, endDateTime: end, startOfDay: startIso });
                expect(r.spanMs).toBe(48 * 3600 * 1000);
            });
        });
    });

    describe('formatDisplayTime', () => {
        it('formats ISO time string', () => {
            const result = formatDisplayTime('2024-01-01T12:00:00');
            expect(result).toBe('12:00 PM');
        });
    });

    describe('formatDisplayDateTime', () => {
        it('formats ISO datetime string', () => {
            const result = formatDisplayDateTime('2024-01-01T12:00:00');
            expect(result).toBe('Mon, Jan 01, 12:00 PM');
        });
    });
});
