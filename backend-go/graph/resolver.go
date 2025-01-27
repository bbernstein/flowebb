package graph

import (
	"github.com/bbernstein/flowebb/backend-go/internal/models"
	"github.com/bbernstein/flowebb/backend-go/internal/tide"
)

type Resolver struct {
	TideService   tide.TideService
	StationFinder models.StationFinder
}
