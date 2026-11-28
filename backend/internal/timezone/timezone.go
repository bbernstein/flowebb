// Package timezone maps a station's coordinates to a DST-aware IANA location.
//
// NOAA reports only a whole-hour standard offset (timeZoneCorr) per station,
// which cannot express daylight saving time. This package looks the zone up
// from the station's latitude and longitude, and uses NOAA's offset as a
// safety check: whenever the lookup is missing, is an ocean (Etc/*) zone, or
// disagrees with NOAA's standard offset, it falls back to a fixed-offset zone.
package timezone

import (
	"math"
	"strings"
	"sync"
	"time"

	// Embed the IANA database so the Lambda does not need OS zoneinfo.
	_ "time/tzdata"

	"github.com/ringsaturn/tzf"
)

var (
	finderOnce sync.Once
	finder     tzf.F
)

// getFinder builds the (costly) tzf finder once. It returns nil if the
// embedded data cannot be loaded, in which case callers fall back.
func getFinder() tzf.F {
	finderOnce.Do(func() {
		f, err := tzf.NewDefaultFinder()
		if err == nil {
			finder = f
		}
	})
	return finder
}

// nearbyOffsetsDeg are probe distances (degrees, roughly 5 to 25 km) used to
// rescue coastal stations whose coordinates fall inside an ocean polygon.
var nearbyOffsetsDeg = []float64{0.05, 0.1, 0.2}

// Warm builds the zone finder so the cost is paid at start-up (for example
// Lambda init) rather than on the first request.
func Warm() { getFinder() }

type cacheKey struct {
	lat, lon  float64
	stdOffset int
}

// locCache memoizes ForStation results. A stations query can map thousands of
// stations, and each uncached lookup may probe the finder many times.
var locCache sync.Map // cacheKey -> *time.Location

// ForStation returns the IANA location for a station. stdOffsetSeconds is
// NOAA's standard (non-DST) offset from UTC and stays authoritative: if no
// land zone with that standard offset is found, a fixed zone is returned.
// Results are memoized per (lat, lon, stdOffsetSeconds).
func ForStation(lat, lon float64, stdOffsetSeconds int) *time.Location {
	// NaN never equals itself, so it cannot be a usable map key.
	if math.IsNaN(lat) || math.IsNaN(lon) {
		return time.FixedZone("", stdOffsetSeconds)
	}
	key := cacheKey{lat, lon, stdOffsetSeconds}
	if v, ok := locCache.Load(key); ok {
		return v.(*time.Location)
	}
	v, _ := locCache.LoadOrStore(key, lookup(lat, lon, stdOffsetSeconds))
	return v.(*time.Location)
}

func lookup(lat, lon float64, stdOffsetSeconds int) *time.Location {
	fallback := time.FixedZone("", stdOffsetSeconds)
	if lat < -90 || lat > 90 || lon < -180 || lon > 180 {
		return fallback
	}
	f := getFinder()
	if f == nil {
		return fallback
	}

	if loc := match(f, lat, lon, stdOffsetSeconds); loc != nil {
		return loc
	}
	// The point may sit just offshore. Probe a ring of nearby points for land.
	for _, d := range nearbyOffsetsDeg {
		for _, dir := range [][2]float64{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}} {
			plat, plon := lat+dir[0]*d, lon+dir[1]*d
			if plat < -90 || plat > 90 || plon < -180 || plon > 180 {
				continue
			}
			if loc := match(f, plat, plon, stdOffsetSeconds); loc != nil {
				return loc
			}
		}
	}
	return fallback
}

// match returns the land zone at the point if its standard offset equals
// stdOffsetSeconds, else nil.
func match(f tzf.F, lat, lon float64, stdOffsetSeconds int) *time.Location {
	name := f.GetTimezoneName(lon, lat)
	if name == "" || strings.HasPrefix(name, "Etc/") {
		return nil
	}
	loc, err := time.LoadLocation(name)
	if err != nil || standardOffset(loc) != stdOffsetSeconds {
		return nil
	}
	return loc
}

// standardOffset returns the zone's standard offset in seconds: the smaller
// of its offsets on 1 January and 1 July of the current year.
func standardOffset(loc *time.Location) int {
	year := time.Now().Year()
	_, jan := time.Date(year, time.January, 1, 0, 0, 0, 0, loc).Zone()
	_, jul := time.Date(year, time.July, 1, 0, 0, 0, 0, loc).Zone()
	return min(jan, jul)
}
