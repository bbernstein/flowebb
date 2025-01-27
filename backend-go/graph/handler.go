package graph

import (
	"bytes"
	"context"
	"github.com/99designs/gqlgen/graphql/handler"
	"github.com/aws/aws-lambda-go/events"
	generated "github.com/bbernstein/flowebb/backend-go/graph/generated"
	"net/http"
)

type RequestCreator func(ctx context.Context, method, url string, body *bytes.Buffer) (*http.Request, error)

type Handler struct {
	srv            *handler.Server
	requestCreator RequestCreator
}

func defaultRequestCreator(ctx context.Context, method, url string, body *bytes.Buffer) (*http.Request, error) {
	req, err := http.NewRequestWithContext(ctx, method, url, body)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	return req, nil
}

func NewHandler(resolver *Resolver, requestCreator RequestCreator) *Handler {
	if requestCreator == nil {
		requestCreator = defaultRequestCreator
	}
	schema := generated.NewExecutableSchema(generated.Config{Resolvers: resolver})
	return &Handler{
		srv:            handler.NewDefaultServer(schema),
		requestCreator: requestCreator,
	}
}

func (h *Handler) HandleRequest(ctx context.Context, event events.APIGatewayProxyRequest) (events.APIGatewayProxyResponse, error) {
	if event.HTTPMethod == "" {
		event.HTTPMethod = "POST"
	}
	if event.HTTPMethod != "POST" {
		return events.APIGatewayProxyResponse{
			StatusCode: http.StatusMethodNotAllowed,
			Body:       "Only POST method is allowed",
		}, nil
	}
	req, err := h.requestCreator(ctx, event.HTTPMethod, "/graphql", bytes.NewBufferString(event.Body))
	if err != nil {
		return events.APIGatewayProxyResponse{
			StatusCode: 500,
			Body:       `{"errors":["Failed to create request"]}`,
		}, err
	}
	req.Header.Set("Content-Type", "application/json")

	// Create response writer to capture output
	w := &responseWriter{
		headers: make(http.Header),
		body:    &bytes.Buffer{},
		code:    http.StatusOK,
	}

	// Handle the request
	h.srv.ServeHTTP(w, req)

	return events.APIGatewayProxyResponse{
		StatusCode: w.code,
		Headers: map[string]string{
			"Content-Type": "application/json",
		},
		Body: w.body.String(),
	}, nil
}

// responseWriter implements http.ResponseWriter
type responseWriter struct {
	headers http.Header
	body    *bytes.Buffer
	code    int
}

func (w *responseWriter) Header() http.Header {
	return w.headers
}

func (w *responseWriter) Write(b []byte) (int, error) {
	return w.body.Write(b)
}

func (w *responseWriter) WriteHeader(statusCode int) {
	w.code = statusCode
}
