#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float GameTime;

in float vertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec2 texCoord0;
in vec3 viewPos;
in vec3 viewNormal;

out vec4 fragColor;

void main() {
    vec4 modulatedColor = vertexColor * ColorModulator;
    float progress = clamp(modulatedColor.a, 0.0, 1.0);
    vec3 normal = normalize(viewNormal);
    vec3 viewDirection = normalize(-viewPos);
    float fresnel = clamp(1.0 - abs(dot(normal, viewDirection)), 0.0, 1.0);

    vec2 animatedUv = fract(texCoord0 * 5.0 + viewPos.xy * 0.08 + vec2(GameTime * 0.0085, GameTime * -0.0065));
    vec4 noise = texture(Sampler0, animatedUv);
    float shimmer = smoothstep(0.42, 0.86, noise.r);
    float alpha = progress * shimmer * (0.2 + fresnel * 0.8);
    vec3 shimmerColor = modulatedColor.rgb * lightMapColor.rgb;
    vec4 color = vec4(shimmerColor, alpha);
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
