package tide

import (
	"context"
	"fmt"
	"net/url"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/bbernstein/flowebb/backend/internal/models"
	"github.com/bbernstein/flowebb/backend/pkg/http/client"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// fakeNoaaEvent is one high or low tide at a true UTC instant.
type fakeNoaaEvent struct {
	at     time.Time
	kind   string
	height string
}

// fakeNoaa emulates the NOAA datagetter endpoint. With time_zone=gmt it
// honors time-qualified GMT begin/end bounds and renders times in GMT. With
// time_zone=lst_ldt it renders local (daylight-aware) times in ldt, as the
// real API does, so the old request style can be shown to be wrong.
type fakeNoaa struct {
	ldt    *time.Location
	events []fakeNoaaEvent

	mu    sync.Mutex
	paths []string
}

func (f *fakeNoaa) client() *client.Client {
	return &client.Client{GetFunc: f.get}
}

func (f *fakeNoaa) requests() []string {
	f.mu.Lock()
	defer f.mu.Unlock()
	return append([]string(nil), f.paths...)
}

func (f *fakeNoaa) get(_ context.Context, path string) (*client.Response, error) {
	f.mu.Lock()
	f.paths = append(f.paths, path)
	f.mu.Unlock()

	u, err := url.Parse(path)
	if err != nil {
		return nil, err
	}
	q := u.Query()

	render := time.UTC
	var begin, end time.Time
	if q.Get("time_zone") == "gmt" {
		begin, err = time.ParseInLocation("20060102 15:04", q.Get("begin_date"), time.UTC)
		if err != nil {
			return nil, err
		}
		end, err = time.ParseInLocation("20060102 15:04", q.Get("end_date"), time.UTC)
		if err != nil {
			return nil, err
		}
	} else {
		render = f.ldt
		begin, err = time.ParseInLocation("20060102", q.Get("begin_date"), f.ldt)
		if err != nil {
			return nil, err
		}
		end, err = time.ParseInLocation("20060102", q.Get("end_date"), f.ldt)
		if err != nil {
			return nil, err
		}
		end = end.AddDate(0, 0, 1).Add(-time.Minute)
	}

	var items []string
	if q.Get("interval") == "hilo" {
		for _, e := range f.events {
			if e.at.Before(begin) || e.at.After(end) {
				continue
			}
			items = append(items, fmt.Sprintf(`{"t":%q,"v":%q,"type":%q}`,
				e.at.In(render).Format("2006-01-02 15:04"), e.height, e.kind))
		}
	} else {
		for t := begin; !t.After(end); t = t.Add(6 * time.Minute) {
			items = append(items, fmt.Sprintf(`{"t":%q,"v":"1.000"}`,
				t.In(render).Format("2006-01-02 15:04")))
		}
	}
	body := `{"predictions":[` + strings.Join(items, ",") + `]}`
	return &client.Response{StatusCode: 200, Body: []byte(body)}, nil
}

// recordingCache always misses and hands saved batches to a channel.
type recordingCache struct {
	saved chan []models.TidePredictionRecord
}

func newRecordingCache() *recordingCache {
	return &recordingCache{saved: make(chan []models.TidePredictionRecord, 4)}
}

func (c *recordingCache) GetPredictions(_ context.Context, _ string, _ time.Time) (*models.TidePredictionRecord, error) {
	return nil, nil
}

func (c *recordingCache) SavePredictionsBatch(_ context.Context, records []models.TidePredictionRecord) error {
	c.saved <- records
	return nil
}

func (c *recordingCache) waitSaved(t *testing.T) []models.TidePredictionRecord {
	t.Helper()
	select {
	case r := <-c.saved:
		return r
	case <-time.After(5 * time.Second):
		t.Fatal("timed out waiting for cache save")
		return nil
	}
}

type dstStation struct {
	id      string
	lat     float64
	lon     float64
	stdCorr int // NOAA timeZoneCorr in seconds
	stype   string
}

var (
	bostonStation   = dstStation{"8443970", 42.3548, -71.0534, -5 * 3600, "R"}
	honoluluStation = dstStation{"1612340", 21.3067, -157.867, -10 * 3600, "R"}
	guamStation     = dstStation{"1630000", 13.4389, 144.6533, 10 * 3600, "R"}
)

func (d dstStation) finder() *mockStationFinder2 {
	return &mockStationFinder2{
		findStationFn: func(_ context.Context, _ string) (*models.Station, error) {
			st := d.stype
			return &models.Station{
				ID:             d.id,
				Name:           "Station " + d.id,
				Latitude:       d.lat,
				Longitude:      d.lon,
				TimeZoneOffset: d.stdCorr,
				StationType:    &st,
			}, nil
		},
	}
}

// Boston 8443970 on 2026-07-01, from NOAA with time_zone=gmt.
var bostonJuly1Events = []fakeNoaaEvent{
	{time.Date(2026, 7, 1, 4, 38, 0, 0, time.UTC), "H", "10.062"},
	{time.Date(2026, 7, 1, 11, 3, 0, 0, time.UTC), "L", "0.499"},
	{time.Date(2026, 7, 1, 17, 19, 0, 0, time.UTC), "H", "8.75"},
	{time.Date(2026, 7, 1, 23, 6, 0, 0, time.UTC), "L", "1.606"},
}

type dstCase struct {
	station    dstStation
	ldt        string // IANA zone the fake uses to render lst_ldt times
	now        time.Time
	events     []fakeNoaaEvent
	wantOffset int
	wantBegin  string // raw (URL-encoded) begin_date
	wantEnd    string // raw (URL-encoded) end_date
	wantLocal  string // response localTime for now
	wantFirst  time.Time
	wantCount  int // predictions in the response (one local day)
	wantDays   map[string]int
}

func runDSTCase(t *testing.T, tc dstCase) *models.ExtendedTideResponse {
	t.Helper()
	ldt, err := time.LoadLocation(tc.ldt)
	require.NoError(t, err)

	fake := &fakeNoaa{ldt: ldt, events: tc.events}
	cache := newRecordingCache()
	svc := &Service{
		HttpClient:      fake.client(),
		StationFinder:   tc.station.finder(),
		PredictionCache: cache,
		Now:             func() time.Time { return tc.now },
	}

	resp, err := svc.GetCurrentTideForStation(context.Background(), tc.station.id, nil, nil)
	require.NoError(t, err)
	require.NotNil(t, resp)

	reqs := fake.requests()
	require.Len(t, reqs, 2, "expected one predictions and one hilo request")
	for _, p := range reqs {
		assert.Contains(t, p, "time_zone=gmt")
		assert.Contains(t, p, "begin_date="+tc.wantBegin+"&")
		assert.Contains(t, p, "end_date="+tc.wantEnd+"&")
	}

	require.NotNil(t, resp.TimeZoneOffsetSeconds)
	assert.Equal(t, tc.wantOffset, *resp.TimeZoneOffsetSeconds)
	assert.Equal(t, tc.now.UnixMilli(), resp.Timestamp)
	assert.Equal(t, tc.wantLocal, resp.LocalTime)

	require.Len(t, resp.Predictions, tc.wantCount)
	first := resp.Predictions[0]
	assert.Equal(t, tc.wantFirst.UnixMilli(), first.Timestamp, "first prediction should be local midnight")
	assert.True(t, strings.HasSuffix(first.LocalTime, "T00:00:00"), "first localTime %s", first.LocalTime)

	saved := cache.waitSaved(t)
	got := map[string]int{}
	for _, rec := range saved {
		got[rec.Date] = len(rec.Predictions)
		for _, p := range rec.Predictions {
			assert.True(t, strings.HasPrefix(p.LocalTime, rec.Date),
				"prediction %s grouped under day %s", p.LocalTime, rec.Date)
		}
		for _, e := range rec.Extremes {
			assert.True(t, strings.HasPrefix(e.LocalTime, rec.Date),
				"extreme %s grouped under day %s", e.LocalTime, rec.Date)
		}
	}
	assert.Equal(t, tc.wantDays, got)
	return resp
}

func TestDST_BostonSummerDay(t *testing.T) {
	resp := runDSTCase(t, dstCase{
		station:    bostonStation,
		ldt:        "America/New_York",
		now:        time.Date(2026, 7, 1, 16, 0, 0, 0, time.UTC),
		events:     bostonJuly1Events,
		wantOffset: -14400,
		wantBegin:  "20260701%2004:00",
		wantEnd:    "20260703%2003:59",
		wantLocal:  "2026-07-01T12:00:00",
		wantFirst:  time.Date(2026, 7, 1, 4, 0, 0, 0, time.UTC),
		wantCount:  240,
		wantDays:   map[string]int{"2026-07-01": 240, "2026-07-02": 240},
	})

	require.Len(t, resp.Extremes, 4)
	high := resp.Extremes[0]
	assert.Equal(t, models.TideTypeHigh, high.Type)
	assert.Equal(t, time.Date(2026, 7, 1, 4, 38, 0, 0, time.UTC).UnixMilli(), high.Timestamp,
		"00:38 EDT high tide must be 04:38Z")
	assert.Equal(t, "2026-07-01T00:38:00", high.LocalTime)
	assert.InDelta(t, 10.062, high.Height, 1e-9)
	assert.Equal(t, "2026-07-01T19:06:00", resp.Extremes[3].LocalTime)
}

func TestDST_BostonWinterDay(t *testing.T) {
	runDSTCase(t, dstCase{
		station:    bostonStation,
		ldt:        "America/New_York",
		now:        time.Date(2026, 1, 15, 17, 0, 0, 0, time.UTC),
		wantOffset: -18000,
		wantBegin:  "20260115%2005:00",
		wantEnd:    "20260117%2004:59",
		wantLocal:  "2026-01-15T12:00:00",
		wantFirst:  time.Date(2026, 1, 15, 5, 0, 0, 0, time.UTC),
		wantCount:  240,
		wantDays:   map[string]int{"2026-01-15": 240, "2026-01-16": 240},
	})
}

func TestDST_BostonSpringForwardDay(t *testing.T) {
	runDSTCase(t, dstCase{
		station:    bostonStation,
		ldt:        "America/New_York",
		now:        time.Date(2026, 3, 8, 17, 0, 0, 0, time.UTC), // 13:00 EDT
		wantOffset: -14400,
		wantBegin:  "20260308%2005:00", // midnight EST
		wantEnd:    "20260310%2003:59", // next-next midnight EDT, minus 1 minute
		wantLocal:  "2026-03-08T13:00:00",
		wantFirst:  time.Date(2026, 3, 8, 5, 0, 0, 0, time.UTC),
		wantCount:  230, // 23-hour day
		wantDays:   map[string]int{"2026-03-08": 230, "2026-03-09": 240},
	})
}

func TestDST_BostonFallBackDay(t *testing.T) {
	runDSTCase(t, dstCase{
		station:    bostonStation,
		ldt:        "America/New_York",
		now:        time.Date(2026, 11, 1, 18, 0, 0, 0, time.UTC), // 13:00 EST
		wantOffset: -18000,
		wantBegin:  "20261101%2004:00", // midnight EDT
		wantEnd:    "20261103%2004:59", // next-next midnight EST, minus 1 minute
		wantLocal:  "2026-11-01T13:00:00",
		wantFirst:  time.Date(2026, 11, 1, 4, 0, 0, 0, time.UTC),
		wantCount:  250, // 25-hour day
		wantDays:   map[string]int{"2026-11-01": 250, "2026-11-02": 240},
	})
}

func TestDST_HonoluluNoDST(t *testing.T) {
	runDSTCase(t, dstCase{
		station:    honoluluStation,
		ldt:        "Pacific/Honolulu",
		now:        time.Date(2026, 7, 1, 22, 0, 0, 0, time.UTC),
		wantOffset: -36000,
		wantBegin:  "20260701%2010:00",
		wantEnd:    "20260703%2009:59",
		wantLocal:  "2026-07-01T12:00:00",
		wantFirst:  time.Date(2026, 7, 1, 10, 0, 0, 0, time.UTC),
		wantCount:  240,
		wantDays:   map[string]int{"2026-07-01": 240, "2026-07-02": 240},
	})
}

func TestDST_GuamPositiveOffset(t *testing.T) {
	runDSTCase(t, dstCase{
		station:    guamStation,
		ldt:        "Pacific/Guam",
		now:        time.Date(2026, 7, 1, 2, 0, 0, 0, time.UTC), // 12:00 ChST
		wantOffset: 36000,
		wantBegin:  "20260630%2014:00",
		wantEnd:    "20260702%2013:59",
		wantLocal:  "2026-07-01T12:00:00",
		wantFirst:  time.Date(2026, 6, 30, 14, 0, 0, 0, time.UTC),
		wantCount:  240,
		wantDays:   map[string]int{"2026-07-01": 240, "2026-07-02": 240},
	})
}

// Subordinate (type S) stations also fetch the previous local day so the
// extremes interpolation has a point before local midnight.
func TestDST_SubordinateStationFetchesPreviousLocalDay(t *testing.T) {
	st := bostonStation
	st.stype = "S"
	runDSTCase(t, dstCase{
		station:    st,
		ldt:        "America/New_York",
		now:        time.Date(2026, 7, 1, 16, 0, 0, 0, time.UTC),
		events:     bostonJuly1Events,
		wantOffset: -14400,
		wantBegin:  "20260630%2004:00",
		wantEnd:    "20260703%2003:59",
		wantLocal:  "2026-07-01T12:00:00",
		wantFirst:  time.Date(2026, 7, 1, 4, 0, 0, 0, time.UTC),
		wantCount:  240,
		wantDays:   map[string]int{"2026-06-30": 240, "2026-07-01": 240, "2026-07-02": 240},
	})
}

func TestParseNoaaTime_IsUTC(t *testing.T) {
	ms, err := parseNoaaTime("2026-07-01 04:38")
	require.NoError(t, err)
	assert.Equal(t, time.Date(2026, 7, 1, 4, 38, 0, 0, time.UTC).UnixMilli(), ms)

	_, err = parseNoaaTime("not a time")
	require.Error(t, err)
}

func TestNoaaGMTBound(t *testing.T) {
	ny, err := time.LoadLocation("America/New_York")
	require.NoError(t, err)
	assert.Equal(t, "20260701%2004:00", noaaGMTBound(time.Date(2026, 7, 1, 0, 0, 0, 0, ny)))
}
