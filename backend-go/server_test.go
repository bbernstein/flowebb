package main

import (
	"context"
	"encoding/json"
	"github.com/99designs/gqlgen/graphql/handler"
	"github.com/bbernstein/flowebb/backend-go/graph"
	"github.com/bbernstein/flowebb/backend-go/graph/generated"
	"github.com/bbernstein/flowebb/backend-go/internal/cache"
	"github.com/bbernstein/flowebb/backend-go/internal/config"
	"github.com/rs/zerolog"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"
)

func TestGraphQLServer(t *testing.T) {
	// Save original environment
	originalEnv := os.Getenv("ENV")
	originalPort := os.Getenv("PORT")
	defer func() {
		err := os.Setenv("ENV", originalEnv)
		if err != nil {
			return
		}
		err = os.Setenv("PORT", originalPort)
		if err != nil {
			return
		}
	}()

	tests := []struct {
		name          string
		query         string
		env           string
		expectedCode  int
		responseCheck func(*testing.T, *httptest.ResponseRecorder)
	}{
		{
			name: "valid query",
			query: `{
				"query": "{ __schema { types { name } } }"
			}`,
			env:          "development",
			expectedCode: http.StatusOK,
			responseCheck: func(t *testing.T, w *httptest.ResponseRecorder) {
				var response map[string]interface{}
				err := json.Unmarshal(w.Body.Bytes(), &response)
				require.NoError(t, err)
				require.Contains(t, response, "data")
			},
		},
		{
			name:         "invalid query",
			query:        `{"query": "invalid graphql query"}`,
			env:          "development",
			expectedCode: http.StatusUnprocessableEntity,
			responseCheck: func(t *testing.T, w *httptest.ResponseRecorder) {
				var response map[string]interface{}
				err := json.Unmarshal(w.Body.Bytes(), &response)
				require.NoError(t, err)
				require.Contains(t, response, "errors")
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// Set test environment
			err := os.Setenv("ENV", tt.env)
			if err != nil {
				return
			}
			err = os.Setenv("PORT", "8080")
			if err != nil {
				return
			}

			// Create test resolver
			resolver := &graph.Resolver{}

			// Create test server
			schema := generated.NewExecutableSchema(generated.Config{Resolvers: resolver})
			srv := handler.NewDefaultServer(schema)

			// Create test request
			req := httptest.NewRequest("POST", "/query", strings.NewReader(tt.query))
			req.Header.Set("Content-Type", "application/json")
			w := httptest.NewRecorder()

			// Handle request
			srv.ServeHTTP(w, req)

			// Check response
			assert.Equal(t, tt.expectedCode, w.Code)
			if tt.responseCheck != nil {
				tt.responseCheck(t, w)
			}
		})
	}
}

func TestNewCacheService(t *testing.T) {
	cfg := &config.CacheConfig{
		TidePredictionLRUSize:       1000,
		TidePredictionLRUTTLMinutes: 15,
	}

	cacheService, err := cache.NewCacheService(context.TODO(), cfg)
	require.NoError(t, err)
	require.NotNil(t, cacheService)
}

func TestServerConfiguration(t *testing.T) {
	// Save original environment
	originalEnv := os.Getenv("ENV")
	originalPort := os.Getenv("PORT")
	defer func() {
		err := os.Setenv("ENV", originalEnv)
		if err != nil {
			return
		}
		err = os.Setenv("PORT", originalPort)
		if err != nil {
			return
		}
	}()

	tests := []struct {
		name     string
		env      string
		port     string
		wantPort string
	}{
		{
			name:     "development environment",
			env:      "development",
			port:     "3000",
			wantPort: "3000",
		},
		{
			name:     "production environment",
			env:      "production",
			port:     "",
			wantPort: "8080", // default port
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := os.Setenv("ENV", tt.env)
			if err != nil {
				return
			}
			err = os.Setenv("PORT", tt.port)
			if err != nil {
				return
			}

			// Reset log level for each test
			zerolog.SetGlobalLevel(zerolog.InfoLevel)

			// Create test server
			schema := generated.NewExecutableSchema(generated.Config{Resolvers: &graph.Resolver{}})
			srv := handler.NewDefaultServer(schema)

			// Verify server configuration
			require.NotNil(t, srv)
			assert.IsType(t, &handler.Server{}, srv)

			// Verify expected port
			port := os.Getenv("PORT")
			if port == "" {
				port = "8080"
			}
			assert.Equal(t, tt.wantPort, port)
		})
	}
}
