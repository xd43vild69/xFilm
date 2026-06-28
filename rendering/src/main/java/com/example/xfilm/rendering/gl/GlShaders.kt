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

in vec2 vTexCoord;
out vec4 fragColor;

void main() {
    vec3 c = texture(uCameraTex, vTexCoord).rgb;
    // Map color into LUT texel-center coordinates to avoid edge clamp artifacts.
    vec3 coord = (c * (uLutSize - 1.0) + 0.5) / uLutSize;
    vec3 graded = texture(uLut, coord).rgb;

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

    // Apply grain
    vec3 final = graded + grainNoise * grainAmount * 0.1;
    fragColor = vec4(final, 1.0);
}
"""
}
