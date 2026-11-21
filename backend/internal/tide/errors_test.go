package tide

import (
	"errors"
	"testing"
)

func TestNoaaAPIError_Error_WithWrappedError(t *testing.T) {
	// Create a wrapped error
	wrappedErr := errors.New("wrapped error")
	apiErr := NewNoaaAPIError("test message", wrappedErr)

	// Test Error() method with wrapped error
	expected := "NOAA API error: test message: wrapped error"
	if got := apiErr.Error(); got != expected {
		t.Errorf("NoaaAPIError.Error() = %v, want %v", got, expected)
	}
}

func TestNoaaAPIError_Unwrap(t *testing.T) {
	// Create original error to wrap
	originalErr := errors.New("original error")
	apiErr := NewNoaaAPIError("test message", originalErr)

	// Test Unwrap method
	if unwrapped := apiErr.Unwrap(); unwrapped != originalErr {
		t.Errorf("NoaaAPIError.Unwrap() = %v, want %v", unwrapped, originalErr)
	}
}
