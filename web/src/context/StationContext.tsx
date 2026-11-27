import React, { createContext, useCallback, useContext, useMemo, useState } from 'react';
import { gql, useQuery } from '@apollo/client';
import { Station } from '@/types';

const STATIONS_QUERY = gql`
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

interface StationContextType {
    stations: Station[];
    loading: boolean;
    error: string | null;
    fetchStations: (lat: number, lon: number, limit?: number) => Promise<Station[]>;
}

const StationContext = createContext<StationContextType | undefined>(undefined);

export const StationProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const [stations, setStations] = useState<Station[]>([]);
    const { loading, error, refetch } = useQuery(STATIONS_QUERY, {
        skip: true,
        onError: (error) => console.error('GraphQL error:', error),
        onCompleted: (data) => {
            setStations(data.stations);
        }
    });

    const fetchStations = useCallback(async (lat: number, lon: number, limit?: number): Promise<Station[]> => {
        try {
            const result = await refetch({ lat, lon, limit });
            const newStations: Station[] = result.data.stations;
            setStations(newStations);
            return newStations;
        } catch (err) {
            console.error('Error fetching stations:', err);
            // Rethrow so callers can tell a failed request from an empty result.
            throw err;
        }
    }, [refetch]);

    const errorMessage = error?.message;
    const value = useMemo<StationContextType>(() => ({
        stations,
        loading,
        error: errorMessage || null,
        fetchStations
    }), [stations, loading, errorMessage, fetchStations]);

    return (
        <StationContext.Provider value={value}>
            {children}
        </StationContext.Provider>
    );
};

export function useStationContext() {
    const context = useContext(StationContext);
    if (context === undefined) {
        throw new Error('useStationContext must be used within a StationProvider');
    }
    return context;
}
