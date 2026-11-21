package graph

import (
	"bytes"
	"context"
	"errors"
	"net/http"
	"testing"

	"github.com/aws/aws-lambda-go/events"
	"github.com/bbernstein/flowebb/backend/internal/models"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

type mockTideService struct {
	getCurrentTideForStationFn func(ctx context.Context, stationID string, startTimeStr, endTimeStr *string) (*models.ExtendedTideResponse, error)
}

func (m *mockTideService) GetCurrentTide(_ context.Context, _, _ float64, _, _ *string) (*models.ExtendedTideResponse, error) {
	waterLevel := 1.5
	return &models.ExtendedTideResponse{
		NearestStation: "TEST001",
		WaterLevel:     &waterLevel,
	}, nil
}

func (m *mockTideService) GetCurrentTideForStation(ctx context.Context, stationID string, startTimeStr, endTimeStr *string) (*models.ExtendedTideResponse, error) {
	if m.getCurrentTideForStationFn != nil {
		return m.getCurrentTideForStationFn(ctx, stationID, startTimeStr, endTimeStr)
	}
	return nil, nil
}

type mockStationFinder struct {
	findStationFn         func(ctx context.Context, stationID string) (*models.Station, error)
	findNearestStationsFn func(ctx context.Context, lat, lon float64, limit int) ([]models.Station, error)
}

func (m *mockStationFinder) FindStation(ctx context.Context, stationID string) (*models.Station, error) {
	if m.findStationFn != nil {
		return m.findStationFn(ctx, stationID)
	}
	return nil, nil
}

func (m *mockStationFinder) FindNearestStations(ctx context.Context, lat, lon float64, limit int) ([]models.Station, error) {
	if m.findNearestStationsFn != nil {
		return m.findNearestStationsFn(ctx, lat, lon, limit)
	}
	return nil, nil
}

func TestHandler_HandleRequest(t *testing.T) {
	tests := []struct {
		name         string
		query        string
		httpMethod   string
		setupMock    func() *Resolver
		wantCode     int
		wantResponse string
		wantErr      bool
	}{
		{
			name:       "valid stations query",
			query:      `{"query": "query { stations(lat: 47.6062, lon: -122.3321, limit: 2) { id name latitude longitude } }"}`,
			httpMethod: "POST",
			setupMock: func() *Resolver {
				return &Resolver{
					StationFinder: &mockStationFinder{
						findNearestStationsFn: func(ctx context.Context, lat, lon float64, limit int) ([]models.Station, error) {
							return []models.Station{
								{
									ID:        "TEST001",
									Name:      "Test Station 1",
									Latitude:  lat,
									Longitude: lon,
								},
								{
									ID:        "TEST002",
									Name:      "Test Station 2",
									Latitude:  lat + 0.1,
									Longitude: lon + 0.1,
								},
							}, nil
						},
					},
				}
			},
			wantCode:     200,
			wantResponse: `{"data":{"stations":[{"id":"TEST001","name":"Test Station 1","latitude":47.6062,"longitude":-122.3321},{"id":"TEST002","name":"Test Station 2","latitude":47.7062,"longitude":-122.2321}]}}`,
			wantErr:      false,
		},
		{
			name:       "valid stations query null method",
			query:      `{"query": "query { stations(lat: 47.6062, lon: -122.3321, limit: 1) { id name latitude longitude } }"}`,
			httpMethod: "",
			setupMock: func() *Resolver {
				return &Resolver{
					StationFinder: &mockStationFinder{
						findNearestStationsFn: func(ctx context.Context, lat, lon float64, limit int) ([]models.Station, error) {
							return []models.Station{
								{
									ID:        "TEST001",
									Name:      "Test Station 1",
									Latitude:  lat,
									Longitude: lon,
								},
							}, nil
						},
					},
				}
			},
			wantCode:     200,
			wantResponse: `{"data":{"stations":[{"id":"TEST001","name":"Test Station 1","latitude":47.6062,"longitude":-122.3321}]}}`,
			wantErr:      false,
		},
		{
			name:       "non-POST request",
			query:      `{"query": "query { stations(lat: 47.6062, lon: -122.3321, limit: 2) { id name latitude longitude } }"}`,
			httpMethod: "GET",
			setupMock: func() *Resolver {
				return &Resolver{}
			},
			wantCode:     405,
			wantResponse: "Only POST method is allowed",
			wantErr:      false,
		},
		{
			name:       "error creating request",
			query:      `{"query": "query { stations(lat: 47.6062, lon: -122.3321, limit: 2) { id name latitude longitude } }"}`,
			httpMethod: "POST",
			setupMock: func() *Resolver {
				return &Resolver{
					StationFinder: &mockStationFinder{
						findNearestStationsFn: func(ctx context.Context, lat, lon float64, limit int) ([]models.Station, error) {
							return nil, errors.New("mock error")
						},
					},
				}
			},
			wantCode:     200,
			wantResponse: `{"errors":[{"message":"mock error","path":["stations"]}],"data":null}`,
			wantErr:      false,
		},
		{
			name:       "error creating request",
			query:      `{"query": "query { stations(lat: 47.6062, lon: -122.3321, limit: 2) { id name latitude longitude } }"}`,
			httpMethod: "POST",
			setupMock: func() *Resolver {
				return &Resolver{
					StationFinder: &mockStationFinder{
						findNearestStationsFn: func(ctx context.Context, lat, lon float64, limit int) ([]models.Station, error) {
							return nil, errors.New("mock error")
						},
					},
				}
			},
			wantCode:     200,
			wantResponse: `{"errors":[{"message":"mock error","path":["stations"]}],"data":null}`,
			wantErr:      false,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			resolver := tt.setupMock()
			handler := NewHandler(resolver, nil)

			event := events.APIGatewayProxyRequest{
				Body:       tt.query,
				HTTPMethod: tt.httpMethod,
			}

			response, err := handler.HandleRequest(context.Background(), event)

			if tt.wantErr {
				require.Error(t, err)
				return
			}

			require.NoError(t, err)
			assert.Equal(t, tt.wantCode, response.StatusCode)
			assert.Equal(t, tt.wantResponse, response.Body)
		})
	}
}

func TestHandler_NewRequestWithContextError(t *testing.T) {
	mockRequestCreator := func(ctx context.Context, method, url string, body *bytes.Buffer) (*http.Request, error) {
		return nil, errors.New("mock error")
	}

	resolver := &Resolver{}
	handler := NewHandler(resolver, mockRequestCreator)

	event := events.APIGatewayProxyRequest{
		Body:       `{"query": "query { stations(lat: 47.6062, lon: -122.3321, limit: 2) { id name latitude longitude } }"}`,
		HTTPMethod: "POST",
	}

	response, err := handler.HandleRequest(context.Background(), event)

	require.NoError(t, err)
	assert.Equal(t, 200, response.StatusCode)
	assert.Contains(t, response.Body, "internal system error")
}

func TestDefaultRequestCreator(t *testing.T) {
	ctx := context.Background()
	method := "POST"
	url := "http://example.com"
	body := bytes.NewBuffer([]byte(`{"query": "test"}`))

	req, err := defaultRequestCreator(ctx, method, url, body)

	// Check no error occurred
	if err != nil {
		t.Errorf("defaultRequestCreator returned unexpected error: %v", err)
	}

	// Verify the request was created with correct properties
	if req.Method != method {
		t.Errorf("expected method %s, got %s", method, req.Method)
	}

	if req.URL.String() != url {
		t.Errorf("expected URL %s, got %s", url, req.URL.String())
	}

	contentType := req.Header.Get("Content-Type")
	if contentType != "application/json" {
		t.Errorf("expected Content-Type application/json, got %s", contentType)
	}

	// Verify the context was set
	if req.Context() != ctx {
		t.Error("context was not set correctly")
	}
}

// You might also want to test the error case with an invalid URL
func TestDefaultRequestCreatorWithInvalidURL(t *testing.T) {
	ctx := context.Background()
	method := "POST"
	url := "://invalid-url" // Invalid URL format
	body := bytes.NewBuffer([]byte(`{"query": "test"}`))

	_, err := defaultRequestCreator(ctx, method, url, body)

	if err == nil {
		t.Error("expected error for invalid URL, got nil")
	}
}
