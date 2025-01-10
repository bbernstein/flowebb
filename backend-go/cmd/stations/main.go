package main

import (
	"context"
	"github.com/aws/aws-lambda-go/events"
	"github.com/aws/aws-lambda-go/lambda"
	"github.com/bbernstein/flowebb/backend-go/internal/handler"
	"github.com/bbernstein/flowebb/backend-go/internal/station"
	"github.com/bbernstein/flowebb/backend-go/pkg/http/client"
	"github.com/rs/zerolog"
	"github.com/rs/zerolog/log"
	"os"
	"sync"
	"time"
)

var (
	lambdaStart     = lambda.Start // Allow mocking of lambda.Start in tests
	stationsHandler *handler.StationsHandler
	setupOnce       sync.Once
)

func init() {
	setupOnce.Do(func() {
		// Initialize logger
		zerolog.TimeFieldFormat = zerolog.TimeFormatUnix
		levelStr := os.Getenv("LOG_LEVEL")
		if levelStr == "" {
			levelStr = "info"
		}
		level, err := zerolog.ParseLevel(levelStr)
		if err != nil {
			level = zerolog.InfoLevel
		}
		zerolog.SetGlobalLevel(level)

		// Setup console logger for development
		if env := os.Getenv("ENV"); env == "local" || env == "development" {
			log.Logger = log.Output(zerolog.ConsoleWriter{Out: os.Stdout})
		}

		// Initialize HTTP client
		httpClient := client.New(client.Options{
			BaseURL: "https://api.tidesandcurrents.noaa.gov",
			Timeout: 30 * time.Second,
		})

		// Initialize station finder with cache
		stationFinder, _ := station.NewNOAAStationFinder(httpClient, nil)

		// Initialize handler
		stationsHandler = handler.NewStationsHandler(stationFinder)
	})
}

func handleRequest(ctx context.Context, request events.APIGatewayProxyRequest) (events.APIGatewayProxyResponse, error) {
	return stationsHandler.HandleRequest(ctx, request)
}

func main() {
	lambdaStart(handleRequest)
}
