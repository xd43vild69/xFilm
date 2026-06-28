package com.example.xfilm.rendering.gl

/**
 * GLSL ES 3.0 shader sources for the LUT preview pipeline.
 *
 * The vertex shader draws a full-screen quad and applies the SurfaceTexture
 * transform. The fragment shader samples the camera's external OES texture and
 * remaps each pixel through a 3D LUT (the baked H&D film curve).
 */
object GlShaders {

    const val VERTEX = """#version 300 es
in vec4 aPosition;
in vec4 aTexCoord;
uniform mat4 uTexMatrix;
out vec2 vTexCoord;
void main() {
    gl_Position = aPosition;
    vTexCoord = (uTexMatrix * aTexCoord).xy;
}
"""

    const val FRAGMENT = """#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision mediump float;
precision mediump sampler3D;
precision mediump sampler2D;

uniform samplerExternalOES uCameraTex;
uniform sampler3D uLut;
uniform sampler2D uGrainTex;
uniform float uLutSize;
uniform float uGrainIntensity;
uniform float uGrainSize;
uniform float uGrainLuminance;
uniform float uGrainTimeSeed;

// === PINHOLE CAMERA EFFECTS ===
uniform float uVignetteEnabled;
uniform float uVignetteIntensity;
uniform float uVignetteFalloff;

uniform float uChromaticEnabled;
uniform float uChromaticIntensity;
uniform float uChromaticDistance;

uniform float uSoftnessEnabled;
uniform float uSoftnessThreshold;
uniform float uSoftnessAmount;

in vec2 vTexCoord;
out vec4 fragColor;

// Vignette: radial darkening from center
vec3 applyVignette(vec3 color, vec2 uv) {
    vec2 centered = uv - 0.5;
    float dist = length(centered) * 1.414; // normalize diagonal
    float vignette = smoothstep(0.0, uVignetteFalloff, 1.0 - dist);
    vignette = mix(1.0, vignette, uVignetteIntensity);
    return color * vignette;
}

// Chromatic aberration: radial RGB shift
vec3 applyChromaticAberration(vec3 color, vec2 uv) {
    vec2 centered = (uv - 0.5) * uChromaticDistance;
    float dist = length(centered);

    // Sample each channel with slight radial offset
    float r = texture(uCameraTex, uv + centered * (uChromaticIntensity + 0.001)).r;
    float g = texture(uCameraTex, uv + centered * (uChromaticIntensity + 0.002)).g;
    float b = texture(uCameraTex, uv + centered * (uChromaticIntensity + 0.003)).b;

    vec3 ca = vec3(r, g, b);
    return mix(color, ca, uChromaticEnabled);
}

// Edge softness: blur and desaturate at corners
vec3 applySoftEdges(vec3 color, vec2 uv) {
    vec2 centered = abs(uv - 0.5);
    float edgeFactor = max(centered.x, centered.y) * 2.0;

    float softening = smoothstep(uSoftnessThreshold, 1.0, edgeFactor);

    // Desaturate + dim at edges
    float lum = dot(color, vec3(0.299, 0.587, 0.114));
    color = mix(color, vec3(lum), softening * uSoftnessAmount * 0.5);
    color *= 1.0 - (softening * uSoftnessAmount * 0.2);

    return color;
}

void main() {
    vec3 c = texture(uCameraTex, vTexCoord).rgb;
    // Map color into LUT texel-center coordinates to avoid edge clamp artifacts.
    vec3 coord = (c * (uLutSize - 1.0) + 0.5) / uLutSize;
    vec3 graded = texture(uLut, coord).rgb;

    // ========== PINHOLE EFFECTS PIPELINE ==========
    // 1. VIGNETTE (darkens edges, modulates grain)
    graded = applyVignette(graded, vTexCoord);

    // 2. CHROMATIC ABERRATION (radial shift, before grain)
    graded = applyChromaticAberration(graded, vTexCoord);

    // 3. GRAIN (with vignette modulation)
    // Sample grain texture with time-based seed (subtle evolution)
    vec2 grainCoord = vTexCoord * uGrainSize + uGrainTimeSeed;
    vec4 grainSample = texture(uGrainTex, grainCoord);
    vec3 grainNoise = grainSample.rgb * 2.0 - 1.0;  // Remap to [-1, 1]

    // Luminance-dependent grain intensity (more in shadows, less in highlights)
    float lum = dot(graded, vec3(0.299, 0.587, 0.114));

    // Shadow boost: grain strongest in dark areas (Tri-X toe compression)
    float shadowBoost = smoothstep(0.0, 0.3, lum) * 2.0;

    // Highlight suppression: grain nearly invisible in whites
    float highlightSuppression = smoothstep(0.75, 1.0, lum) * -0.8;

    // Total grain modulation
    float grainAmount = uGrainIntensity *
                       (1.0 + shadowBoost * uGrainLuminance +
                        highlightSuppression * uGrainLuminance);

    // Grain reduction at vignette edges
    grainAmount *= (1.0 - max(abs(vTexCoord.x - 0.5), abs(vTexCoord.y - 0.5)) *
                             uVignetteEnabled * uVignetteIntensity * 0.3);

    // Apply grain
    vec3 final = graded + grainNoise * grainAmount * 0.1;

    // 4. EDGE SOFTNESS (final overlay, post-grain)
    final = applySoftEdges(final, vTexCoord);

    fragColor = vec4(final, 1.0);
}
"""
}
