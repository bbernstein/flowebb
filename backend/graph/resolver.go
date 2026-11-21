package graph

import (
	"github.com/bbernstein/flowebb/backend/graph/generated"
	"github.com/bbernstein/flowebb/backend/internal/models"
	"github.com/bbernstein/flowebb/backend/internal/tide"
)

type Resolver struct {
	TideService   tide.TideService
	StationFinder models.StationFinder
}

// Ensure Resolver implements the ResolverRoot interface
var _ generated.ResolverRoot = &Resolver{}
