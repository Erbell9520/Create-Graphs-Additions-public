#version 150

uniform vec4 ColorModulator;
uniform float HoloTime;
uniform float HoloScan;
uniform float HoloDissolve;

in vec4 vertexColor;

out vec4 fragColor;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.x, p.y, p.x) * 0.1031);
    p3 += dot(p3, vec3(p3.y, p3.z, p3.x) + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

void main() {
    vec4 c = vertexColor * ColorModulator;
    if (c.a < 0.002) {
        discard;
    }

    float line = 0.5 + 0.5 * sin(gl_FragCoord.y * HoloScan - HoloTime * 6.0);
    c.a *= mix(1.0, 0.60 + 0.40 * line, 0.5);

    float grain = hash12(floor(gl_FragCoord.xy) + vec2(floor(HoloTime * 9.0)));
    c.rgb *= 0.86 + 0.28 * grain;

    if (HoloDissolve < 0.999) {
        float n = hash12(floor(gl_FragCoord.xy * 0.5) + vec2(7.0, 13.0));
        if (n > HoloDissolve) {
            discard;
        }
    }

    fragColor = c;
}
