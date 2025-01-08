package station

import (
	"context"
	"github.com/bbernstein/flowebb/backend-go/internal/models"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/bbernstein/flowebb/backend-go/internal/cache"
	"github.com/bbernstein/flowebb/backend-go/internal/config"
	"github.com/bbernstein/flowebb/backend-go/pkg/http/client"
)

func TestNOAAStationFinder_FindStation(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		response := []byte(`{
            "stationList": [{
                "stationId": "9447130",
                "name": "Seattle",
                "state": "WA",
                "region": "Puget Sound",
                "lat": 47.602638889,
                "lon": -122.339167,
                "timeZoneCorr": "-8",
                "level": "R",
                "stationType": "R"
            }]
        }`)
		w.Header().Set("Content-Type", "application/json")
		_, err := w.Write(response)
		if err != nil {
			return
		}
	}))
	defer srv.Close()

	// Create test cache
	testConfig := config.GetCacheConfig()
	testCache := cache.NewStationCache(testConfig)

	// Create HTTP client with the test server URL
	httpClient := client.New(client.Options{
		BaseURL: srv.URL,
		Timeout: 5 * time.Second,
	})

	finder, err := NewNOAAStationFinder(httpClient, testCache)
	require.NoError(t, err)
	require.NotNil(t, finder)

	tests := []struct {
		name      string
		stationID string
		want      *models.Station
		wantErr   bool
	}{
		{
			name:      "existing station",
			stationID: "9447130",
			want: &models.Station{
				ID:             "9447130",
				Name:           "Seattle",
				State:          stringPtr("WA"),
				Region:         stringPtr("Puget Sound"),
				Latitude:       47.602638889,
				Longitude:      -122.339167,
				Source:         models.SourceNOAA,
				Capabilities:   []string{"WATER_LEVEL"},
				TimeZoneOffset: -8 * 3600,
				Level:          stringPtr("R"),
				StationType:    stringPtr("R"),
			},
			wantErr: false,
		},
		{
			name:      "non-existent station",
			stationID: "invalid",
			want:      nil,
			wantErr:   true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := finder.FindStation(context.Background(), tt.stationID)

			if tt.wantErr {
				assert.Error(t, err)
				assert.Nil(t, got)
				return
			}

			require.NoError(t, err)
			if tt.want == nil {
				assert.Nil(t, got)
			} else {
				require.NotNil(t, got)
				assert.Equal(t, tt.want.ID, got.ID)
				assert.Equal(t, tt.want.Name, got.Name)
				assert.Equal(t, tt.want.State, got.State)
				assert.Equal(t, tt.want.Region, got.Region)
				assert.Equal(t, tt.want.Latitude, got.Latitude)
				assert.Equal(t, tt.want.Longitude, got.Longitude)
				assert.Equal(t, tt.want.Source, got.Source)
				assert.Equal(t, tt.want.Capabilities, got.Capabilities)
				assert.Equal(t, tt.want.TimeZoneOffset, got.TimeZoneOffset)
				assert.Equal(t, tt.want.Level, got.Level)
				assert.Equal(t, tt.want.StationType, got.StationType)
			}
		})
	}
}

// Helper function to create string pointers
func stringPtr(s string) *string {
	return &s
}
