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
uniform samplerExternalOES uCameraTex;
uniform sampler3D uLut;
uniform float uLutSize;
in vec2 vTexCoord;
out vec4 fragColor;
void main() {
    vec3 c = texture(uCameraTex, vTexCoord).rgb;
    // Map color into LUT texel-center coordinates to avoid edge clamp artifacts.
    vec3 coord = (c * (uLutSize - 1.0) + 0.5) / uLutSize;
    vec3 graded = texture(uLut, coord).rgb;
    fragColor = vec4(graded, 1.0);
}
"""
}
