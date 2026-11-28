# Agent C: Raster and lossy-codec red team condensed report
Raster (Pillow/FreeType re-implementation of quran-image-generator layout; 1080x2610 and 1440x3480; p1,p3,p300,p500,p604):
- p300 at 1080 px: PNG-L 259,760 B; WebP lossless RGBA 232,858; WebP lossy alpha60 145,990; AVIF q50 129,356; 1-bit WebP 47,700. At 1440 px: 366,243 / 324,056 / 206,824 / 176,278 / 68,100.
- Extrapolated to 604 pages (all pages like p300/p500): 1080 px: PNG 156 MB, WebP lossless 140, lossy a80 126, lossy a60 88, AVIF q50 78, AVIF q20 43 (visibly degraded), 1-bit 29. 1440 px: 221 / 196 / 177 / 124 / 107 / 57 / 41.
- Zoom 3x: upscaled 1080 px vs true 3x re-render PSNR 19.4 dB, SSIM 0.917; soft-edge pixels 4.3% vs 1.03% (3-4x blurrier). True 3x images would be 250-480 MB; a 3240x7830 A8 bitmap is 25 MB RAM.
- Cross-page glyph sharing: 32 of 701 glyphs (573 unique outlines), so no dedup gain. Hinting programs are 270-302 KB in p1/p3 only.
Codec (q = quantisation grid in font units, upem 2500; max error per axis = q/2): p300 TTF 346,212; glyf+brotli 203,800; WOFF2 202,512; naive delta+brotli q1 223,825, q4 166,086, q8 138,149; better entropy q8 111.6 KB, q16 88.1 KB, q24 75.7 KB, q32 66.6 KB. Extrapolated 604-page totals: lossless WOFF2 about 123 MB; q8 67-83 MB; q16 53 MB; q24 46 MB (0.19 px at em 40, 0.77 px at 4x); q32 40 MB. 50 MB needs about 5.3 bits/point. Android Path cost not measured (no device, skia-python failed on libEGL).
Verdicts: raster not viable under 30 MB with usable zoom; codec not viable under 50 MB at conservative tolerance.
