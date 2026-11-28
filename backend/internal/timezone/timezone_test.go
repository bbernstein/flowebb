package timezone

import (
	"testing"
	"time"
)

const hour = 3600

func TestForStation_DSTAwareZones(t *testing.T) {
	tests := []struct {
		name      string
		lat, lon  float64
		stdOffset int
		wantZone  string
		dst       bool
	}{
		{"Boston", 42.3601, -71.0589, -5 * hour, "America/New_York", true},
		{"San Francisco", 37.8063, -122.4659, -8 * hour, "America/Los_Angeles", true},
		{"Honolulu", 21.3067, -157.8670, -10 * hour, "Pacific/Honolulu", false},
		{"Guam", 13.4443, 144.7937, 10 * hour, "Pacific/Guam", false},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			loc := ForStation(tt.lat, tt.lon, tt.stdOffset)
			if loc.String() != tt.wantZone {
				t.Fatalf("zone = %q, want %q", loc.String(), tt.wantZone)
			}
			_, winter := time.Date(2025, 1, 15, 12, 0, 0, 0, loc).Zone()
			_, summer := time.Date(2025, 7, 15, 12, 0, 0, 0, loc).Zone()
			if got := min(winter, summer); got != tt.stdOffset {
				t.Errorf("standard offset = %d, want %d", got, tt.stdOffset)
			}
			if (winter != summer) != tt.dst {
				t.Errorf("observes DST = %v, want %v", winter != summer, tt.dst)
			}
		})
	}
}

func TestForStation_FallbackOffshore(t *testing.T) {
	// Mid North Pacific: only Etc/* ocean zones exist there.
	loc := ForStation(30.0, -150.0, -10*hour)
	assertFixed(t, loc, -10*hour)
}

func TestForStation_FallbackOnMismatchedCorrection(t *testing.T) {
	// Boston coordinates but NOAA says UTC-8: NOAA stays authoritative.
	loc := ForStation(42.3601, -71.0589, -8*hour)
	assertFixed(t, loc, -8*hour)
}

func TestForStation_CoastalOceanPointRescued(t *testing.T) {
	// Massachusetts Bay point that tzf places in an Etc/GMT+5 ocean polygon; a nearby land probe rescues it.
	loc := ForStation(42.30, -70.00, -5*hour)
	if loc.String() != "America/New_York" {
		t.Errorf("zone = %q, want America/New_York", loc.String())
	}
}

func TestForStation_InvalidCoordinates(t *testing.T) {
	loc := ForStation(999, 999, 3*hour)
	assertFixed(t, loc, 3*hour)
}

func TestStandardOffset(t *testing.T) {
	ny, _ := time.LoadLocation("America/New_York")
	if got := standardOffset(ny); got != -5*hour {
		t.Errorf("New York = %d", got)
	}
	syd, _ := time.LoadLocation("Australia/Sydney") // southern-hemisphere DST
	if got := standardOffset(syd); got != 10*hour {
		t.Errorf("Sydney = %d", got)
	}
}

func assertFixed(t *testing.T, loc *time.Location, offset int) {
	t.Helper()
	if loc == nil {
		t.Fatal("nil location")
	}
	_, got := time.Date(2025, 7, 1, 0, 0, 0, 0, loc).Zone()
	_, got2 := time.Date(2025, 1, 1, 0, 0, 0, 0, loc).Zone()
	if got != offset || got2 != offset {
		t.Errorf("offsets = %d/%d, want fixed %d (zone %q)", got2, got, offset, loc.String())
	}
}
