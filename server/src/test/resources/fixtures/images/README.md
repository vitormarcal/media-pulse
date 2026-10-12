# WebP fixtures

`static.webp` is a 2 × 2 VP8 image. `animated.webp` is a small animated WebP. Both are copied from the sibling Visual Library project's test fixtures (`tiny.webp` and `animated.webp`) to keep tests self-contained. Tests verify original bytes, detected MIME, static/animated decoding, local cleanup, truncated data and declared dimensions above the pixel limit.
