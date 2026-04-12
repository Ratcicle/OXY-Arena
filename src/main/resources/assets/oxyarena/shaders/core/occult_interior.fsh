#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec2 texCoord0;
in vec3 viewPos;
in vec3 viewNormal;

out vec4 fragColor;

void main() {
    vec4 mask = texture(Sampler0, texCoord0);
    if (mask.a < 0.1) {
        discard;
    }

    vec4 modulatedColor = vertexColor * ColorModulator;
    float progress = clamp(modulatedColor.a, 0.0, 1.0);
    vec3 normal = normalize(viewNormal);
    vec3 viewDirection = normalize(-viewPos);
    float fresnel = pow(clamp(1.0 - abs(dot(normal, viewDirection)), 0.0, 1.0), 1.8);
    float alpha = progress * mix(0.18, 1.0, fresnel);
    vec3 neutralColor = modulatedColor.rgb * lightMapColor.rgb;
    vec4 color = vec4(neutralColor, alpha);
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
