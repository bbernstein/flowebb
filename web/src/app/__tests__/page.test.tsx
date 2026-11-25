import React from "react";
import { act, render, screen, waitFor } from "@testing-library/react";
import Home from "../page";
import { DEFAULT_LOCATION, GEOLOCATION_FALLBACK_MS } from "@/utils/initialLocation";
import { locationStorage, stationStorage } from "@/utils/storage";
import { useStationContext } from "@/context/StationContext";
import { Station } from "@/types";

jest.mock("next/dynamic", () => () => {
    const MockMap = (props: {
        userLocation: { lat: number; lon: number };
        selectedStationId: string | null;
    }) => (
        <div
            data-testid="map"
            data-lat={ props.userLocation.lat }
            data-lon={ props.userLocation.lon }
            data-selected={ props.selectedStationId ?? "" }
        />
    );
    return MockMap;
});

jest.mock("next/image", () => ({
    __esModule: true,
    default: (props: { alt: string }) => <span>{ props.alt }</span>,
}));

jest.mock("@/components/TideInfo", () => ({
    TideInfo: ({ stationId }: { stationId: string }) => (
        <div data-testid="tide-info">{ stationId }</div>
    ),
}));

jest.mock("@/context/TideContext", () => ({
    TideProvider: ({ children }: { children: React.ReactNode }) => <>{ children }</>,
}));

jest.mock("@/context/StationContext", () => ({
    useStationContext: jest.fn(),
}));

jest.mock("@/utils/storage", () => ({
    locationStorage: { get: jest.fn(), set: jest.fn() },
    stationStorage: { get: jest.fn(), set: jest.fn() },
}));

const nearest = {
    id: "9447130",
    name: "Seattle",
    state: "WA",
    timeZoneOffset: -28800,
} as unknown as Station;
const second = {
    id: "9446484",
    name: "Tacoma",
    state: "WA",
    timeZoneOffset: -28800,
} as unknown as Station;

const mockedUseStationContext = useStationContext as jest.Mock;
const mockedLocationGet = locationStorage.get as jest.Mock;
const mockedLocationSet = locationStorage.set as jest.Mock;
const mockedStationGet = stationStorage.get as jest.Mock;
const mockedStationSet = stationStorage.set as jest.Mock;

let fetchStations: jest.Mock;

function setupStations(result: Station[] | Error) {
    fetchStations = jest.fn(() =>
        result instanceof Error ? Promise.reject(result) : Promise.resolve(result)
    );
    mockedUseStationContext.mockReturnValue({
        stations: result instanceof Error ? [] : result,
        loading: false,
        error: null,
        fetchStations,
    });
}

type Success = (p: { coords: { latitude: number; longitude: number } }) => void;
type Failure = (e: { code: number; message: string }) => void;

function setGeolocation(impl: ((ok: Success, fail: Failure) => void) | undefined) {
    Object.defineProperty(global.navigator, "geolocation", {
        configurable: true,
        value: impl ? { getCurrentPosition: jest.fn(impl) } : undefined,
    });
}

beforeEach(() => {
    jest.clearAllMocks();
    jest.spyOn(console, "error").mockImplementation(() => {});
    mockedLocationGet.mockReturnValue(null);
    mockedStationGet.mockReturnValue(null);
    setupStations([nearest, second]);
});

afterEach(() => {
    jest.useRealTimers();
    (console.error as jest.Mock).mockRestore();
});

describe("Home first load", () => {
    it("returning visitor: shows the map and the stored station", async () => {
        mockedLocationGet.mockReturnValue({ lat: 47.2, lon: -122.4 });
        mockedStationGet.mockReturnValue({ id: second.id, name: second.name, timeZoneOffset: 0 });
        setGeolocation(jest.fn());

        render(<Home/>);

        const map = await screen.findByTestId("map");
        expect(map).toHaveAttribute("data-lat", "47.2");
        expect(map).toHaveAttribute("data-selected", second.id);
        expect(screen.getByTestId("tide-info")).toHaveTextContent(second.id);
        expect(fetchStations).toHaveBeenCalledWith(47.2, -122.4);
        expect(navigator.geolocation.getCurrentPosition).not.toHaveBeenCalled();
    });

    it("returning visitor with no stored station: selects the nearest", async () => {
        mockedLocationGet.mockReturnValue({ lat: 47.2, lon: -122.4 });

        render(<Home/>);

        expect(await screen.findByTestId("map")).toHaveAttribute("data-selected", nearest.id);
    });

    it("returning visitor whose station fetch fails: shows the map and an error", async () => {
        mockedLocationGet.mockReturnValue({ lat: 47.2, lon: -122.4 });
        setupStations(new Error("network down"));

        render(<Home/>);

        expect(await screen.findByTestId("map")).toBeInTheDocument();
        expect(screen.getByRole("alert")).toHaveTextContent("Failed to load stations");
        expect(screen.queryByRole("progressbar")).not.toBeInTheDocument();
    });

    it("station fetch returns nothing: shows the map and an error", async () => {
        mockedLocationGet.mockReturnValue({ lat: 47.2, lon: -122.4 });
        setupStations([]);

        render(<Home/>);

        expect(await screen.findByTestId("map")).toHaveAttribute("data-selected", "");
        expect(screen.getByRole("alert")).toHaveTextContent("No tide stations found");
    });

    it("first visit, geolocation allowed: shows the map and the nearest station's tide", async () => {
        setGeolocation((ok) => ok({ coords: { latitude: 40.7, longitude: -74.0 } }));

        render(<Home/>);

        const map = await screen.findByTestId("map");
        expect(map).toHaveAttribute("data-lat", "40.7");
        expect(map).toHaveAttribute("data-selected", nearest.id);
        expect(screen.getByTestId("tide-info")).toHaveTextContent(nearest.id);
        expect(mockedLocationSet).toHaveBeenCalledWith({ lat: 40.7, lon: -74.0 });
        expect(mockedStationSet).toHaveBeenCalledWith({
            id: nearest.id,
            name: nearest.name,
            timeZoneOffset: nearest.timeZoneOffset,
        });
        expect(screen.getByRole("button", { name: /use my location/i })).toBeInTheDocument();
    });

    it("first visit, geolocation denied: falls back to the default location", async () => {
        setGeolocation((_ok, fail) => fail({ code: 1, message: "denied" }));

        render(<Home/>);

        const map = await screen.findByTestId("map");
        expect(map).toHaveAttribute("data-lat", String(DEFAULT_LOCATION.lat));
        expect(map).toHaveAttribute("data-selected", nearest.id);
        expect(fetchStations).toHaveBeenCalledWith(DEFAULT_LOCATION.lat, DEFAULT_LOCATION.lon);
        expect(mockedLocationSet).toHaveBeenCalledWith(DEFAULT_LOCATION);
    });

    it("first visit, geolocation unavailable: falls back to the default location", async () => {
        setGeolocation(undefined);

        render(<Home/>);

        const map = await screen.findByTestId("map");
        expect(map).toHaveAttribute("data-lat", String(DEFAULT_LOCATION.lat));
        expect(map).toHaveAttribute("data-selected", nearest.id);
    });

    it("first visit, location prompt never answered: falls back after a timeout", async () => {
        jest.useFakeTimers();
        let lateSuccess: Success | undefined;
        setGeolocation((ok) => {
            lateSuccess = ok;
        });

        render(<Home/>);
        expect(screen.getByRole("progressbar")).toBeInTheDocument();

        await act(async () => {
            jest.advanceTimersByTime(GEOLOCATION_FALLBACK_MS);
        });

        const map = await screen.findByTestId("map");
        expect(map).toHaveAttribute("data-lat", String(DEFAULT_LOCATION.lat));
        // A fallback after a timeout is not saved, so the next visit asks again.
        expect(mockedLocationSet).not.toHaveBeenCalled();

        // An answer that arrives after the fallback is ignored.
        await act(async () => {
            lateSuccess?.({ coords: { latitude: 1, longitude: 2 } });
        });
        expect(fetchStations).toHaveBeenCalledTimes(1);
    });

    it("first visit, geolocation fetch fails: shows the map and an error", async () => {
        setGeolocation((ok) => ok({ coords: { latitude: 40.7, longitude: -74.0 } }));
        setupStations(new Error("boom"));

        render(<Home/>);

        expect(await screen.findByTestId("map")).toBeInTheDocument();
        expect(screen.getByRole("alert")).toHaveTextContent("Failed to load stations");
    });

    it("runs the first-load logic once even if fetchStations changes identity", async () => {
        mockedLocationGet.mockReturnValue({ lat: 47.2, lon: -122.4 });
        const { rerender } = render(<Home/>);
        await screen.findByTestId("map");

        setupStations([nearest, second]);
        rerender(<Home/>);

        await waitFor(() => expect(screen.getByTestId("map")).toBeInTheDocument());
        expect(mockedLocationGet).toHaveBeenCalledTimes(1);
    });
});
