# Native journal fixture

This synthetic fixture is emitted by the native Apple app's `NativeArchive3WriterTests.major3WriterImportsAndReopensWithoutLosingNativeOrSourceCustody` test. It contains no personal journal data. Its original JSON bytes are retained so Android verifies the actual native writer's nullability and exact integer representation.

The records distinguish unknown metadata from known empty text, false and epoch zero. They include both Int64 extrema and a native creation value outside the four-digit RFC3339 range. The Android component does not enable public or system major-3 archive imports.
