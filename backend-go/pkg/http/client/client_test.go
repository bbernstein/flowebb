package client

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestClientCreation(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name        string
		baseURL     string
		timeout     time.Duration
		maxRetries  int
		wantTimeout time.Duration
	}{
		{
			name:        "default configuration",
			baseURL:     "https://api.example.com",
			timeout:     0,
			maxRetries:  0,
			wantTimeout: 30 * time.Second,
		},
		{
			name:        "custom configuration",
			baseURL:     "https://api.test.com",
			timeout:     5 * time.Second,
			maxRetries:  5,
			wantTimeout: 5 * time.Second,
		},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			client := New(Options{
				BaseURL:    tt.baseURL,
				Timeout:    tt.timeout,
				MaxRetries: tt.maxRetries,
			})

			assert.Equal(t, tt.baseURL, client.baseURL)
			assert.Equal(t, tt.wantTimeout, client.httpClient.Timeout)
		})
	}
}

func TestRequestFormatting(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name     string
		baseURL  string
		path     string
		wantURL  string
		wantCode int
	}{
		{
			name:     "absolute URL",
			baseURL:  "",
			path:     "https://api.example.com/test",
			wantURL:  "/test",
			wantCode: http.StatusOK,
		},
		{
			name:     "relative path with base URL",
			baseURL:  "https://api.example.com",
			path:     "/test",
			wantURL:  "/test",
			wantCode: http.StatusOK,
		},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				urlStr := r.URL.String()
				assert.Equal(t, tt.wantURL, urlStr)
				w.WriteHeader(tt.wantCode)
				// Optionally write some body data if needed for other tests
				_, err := w.Write([]byte("test response"))
				if err != nil {
					return
				}
			}))
			defer server.Close()

			if tt.baseURL == "" {
				tt.path = server.URL + "/test"
			} else {
				tt.baseURL = server.URL
			}

			client := New(Options{
				BaseURL: tt.baseURL,
				Timeout: 5 * time.Second,
			})

			resp, err := client.Get(context.Background(), tt.path)
			require.NoError(t, err)
			assert.Equal(t, tt.wantCode, resp.StatusCode)
			// Optionally verify the response body if needed
			assert.NotNil(t, resp.Body)
		})
	}
}

func TestTimeout(t *testing.T) {
	t.Parallel()

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		time.Sleep(200 * time.Millisecond)
		w.WriteHeader(http.StatusOK)
		_, err := w.Write([]byte("this should never be received due to timeout"))
		if err != nil {
			return
		}
	}))
	defer server.Close()

	client := New(Options{
		BaseURL: server.URL,
		Timeout: 100 * time.Millisecond,
	})

	ctx := context.Background()
	_, err := client.Get(ctx, "/test")
	require.Error(t, err)
	assert.Contains(t, err.Error(), "context deadline exceeded")
}

func BenchmarkHTTPClient(b *testing.B) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		_, err := w.Write([]byte("benchmark response"))
		if err != nil {
			return
		}
	}))
	defer server.Close()

	client := New(Options{
		BaseURL: server.URL,
		Timeout: 5 * time.Second,
	})

	ctx := context.Background()

	b.Run("Sequential Requests", func(b *testing.B) {
		for i := 0; i < b.N; i++ {
			resp, err := client.Get(ctx, "/test")
			require.NoError(b, err)
			require.NotNil(b, resp)
			require.Equal(b, http.StatusOK, resp.StatusCode)
		}
	})

	b.Run("Parallel Requests", func(b *testing.B) {
		b.RunParallel(func(pb *testing.PB) {
			for pb.Next() {
				resp, err := client.Get(ctx, "/test")
				require.NoError(b, err)
				require.NotNil(b, resp)
				require.Equal(b, http.StatusOK, resp.StatusCode)
			}
		})
	})
}

func TestGetFuncInjection(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name       string
		setupFunc  func() (*Client, context.Context)
		wantErr    bool
		errMessage string
	}{
		{
			name: "custom GetFunc returns success",
			setupFunc: func() (*Client, context.Context) {
				client := New(Options{})
				client.GetFunc = func(ctx context.Context, path string) (*Response, error) {
					return &Response{
						StatusCode: http.StatusOK,
						Body:       []byte("test response"),
					}, nil
				}
				return client, context.Background()
			},
			wantErr: false,
		},
		{
			name: "custom GetFunc returns error",
			setupFunc: func() (*Client, context.Context) {
				client := New(Options{})
				client.GetFunc = func(ctx context.Context, path string) (*Response, error) {
					return nil, errors.New("custom error")
				}
				return client, context.Background()
			},
			wantErr:    true,
			errMessage: "custom error",
		},
	}

	for _, tt := range tests {
		tt := tt // Capture range variable
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			client, ctx := tt.setupFunc()
			resp, err := client.Get(ctx, "/test")

			if tt.wantErr {
				require.Error(t, err)
				assert.Contains(t, err.Error(), tt.errMessage)
				assert.Nil(t, resp)
				return
			}

			require.NoError(t, err)
			require.NotNil(t, resp, "Response should not be nil")
			require.NotNil(t, resp.Body, "Response body should not be nil")
			require.Equal(t, http.StatusOK, resp.StatusCode)
			assert.Equal(t, "test response", string(resp.Body))
		})
	}
}

func TestGetRequestError(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name       string
		path       string
		wantErr    bool
		errMessage string
	}{
		{
			name:       "invalid URL",
			path:       ":\\invalid", // This will cause NewRequestWithContext to fail
			wantErr:    true,
			errMessage: "missing protocol scheme",
		},
	}

	for _, tt := range tests {
		tt := tt // Capture range variable
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			client := New(Options{})
			resp, err := client.Get(context.Background(), tt.path)

			if tt.wantErr {
				require.Error(t, err)
				assert.Contains(t, err.Error(), tt.errMessage)
				assert.Nil(t, resp)
				return
			}

			require.NoError(t, err)
			require.NotNil(t, resp)
			require.NotNil(t, resp.Body)
		})
	}
}
