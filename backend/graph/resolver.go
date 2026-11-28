package graph

import (
	"time"

	"github.com/bbernstein/flowebb/backend/graph/generated"
	"github.com/bbernstein/flowebb/backend/internal/models"
	"github.com/bbernstein/flowebb/backend/internal/tide"
	"github.com/bbernstein/flowebb/backend/internal/timezone"
)

type Resolver struct {
	TideService   tide.TideService
	StationFinder models.StationFinder
	// Now returns the current time; nil means time.Now. Tests set it.
	Now func() time.Time
}

func (r *Resolver) now() time.Time {
	if r.Now != nil {
		return r.Now()
	}
	return time.Now()
}

// currentOffsetSeconds returns the station's UTC offset in effect now,
// including daylight saving time, derived from its coordinates. The NOAA
// standard offset is the fallback when no matching zone is found.
func (r *Resolver) currentOffsetSeconds(s models.Station) int {
	loc := timezone.ForStation(s.Latitude, s.Longitude, s.TimeZoneOffset)
	_, off := r.now().In(loc).Zone()
	return off
}

// Ensure Resolver implements the ResolverRoot interface
var _ generated.ResolverRoot = &Resolver{}
