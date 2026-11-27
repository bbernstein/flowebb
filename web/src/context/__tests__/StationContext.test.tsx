import React from "react";
import { act, renderHook } from "@testing-library/react";
import { MockedProvider, MockedResponse } from "@apollo/client/testing";
import { gql } from "@apollo/client";
import { StationProvider, useStationContext } from "../StationContext";

const QUERY = gql`
    query GetStations($lat: Float!, $lon: Float!, $limit: Int) {
        stations(lat: $lat, lon: $lon, limit: $limit) {
            id
            name
            state
            region
            distance
            latitude
            longitude
            source
            capabilities
            timeZoneOffset
        }
    }
`;

const station = {
    id: "9447130",
    name: "Seattle",
    state: "WA",
    region: "Puget Sound",
    distance: 1.2,
    latitude: 47.6,
    longitude: -122.3,
    source: "NOAA",
    capabilities: ["WATER_LEVEL"],
    timeZoneOffset: -28800,
};

function renderWith(mocks: MockedResponse[]) {
    const wrapper = ({ children }: { children: React.ReactNode }) => (
        <MockedProvider mocks={ mocks } addTypename={ false }>
            <StationProvider>{ children }</StationProvider>
        </MockedProvider>
    );
    return renderHook(() => useStationContext(), { wrapper });
}

beforeEach(() => {
    jest.spyOn(console, "error").mockImplementation(() => {});
});

afterEach(() => {
    (console.error as jest.Mock).mockRestore();
});

describe("StationContext.fetchStations", () => {
    it("returns the stations and stores them in context", async () => {
        const { result } = renderWith([
            {
                request: { query: QUERY, variables: { lat: 47.6, lon: -122.3 } },
                result: { data: { stations: [station] } },
            },
        ]);

        let returned: unknown;
        await act(async () => {
            returned = await result.current.fetchStations(47.6, -122.3);
        });

        expect(returned).toEqual([station]);
        expect(result.current.stations).toEqual([station]);
    });

    it("rejects when the request fails, so callers can show an error", async () => {
        const { result } = renderWith([
            {
                request: { query: QUERY, variables: { lat: 47.6, lon: -122.3 } },
                error: new Error("network down"),
            },
        ]);

        await act(async () => {
            await expect(result.current.fetchStations(47.6, -122.3)).rejects.toThrow(
                "network down"
            );
        });
    });

    it("keeps fetchStations and the context value stable across provider re-renders", async () => {
        const { result, rerender } = renderWith([
            {
                request: { query: QUERY, variables: { lat: 47.6, lon: -122.3 } },
                result: { data: { stations: [station] } },
            },
        ]);

        const initialFetchStations = result.current.fetchStations;

        await act(async () => {
            await result.current.fetchStations(47.6, -122.3);
        });

        // Fetching updated the stations state, which re-rendered the provider.
        expect(result.current.stations).toEqual([station]);
        expect(result.current.fetchStations).toBe(initialFetchStations);

        const valueAfterFetch = result.current;
        rerender();
        expect(result.current.fetchStations).toBe(initialFetchStations);
        expect(result.current).toBe(valueAfterFetch);
    });

    it("throws when used outside a StationProvider", () => {
        expect(() => renderHook(() => useStationContext())).toThrow(
            "useStationContext must be used within a StationProvider"
        );
    });
});
