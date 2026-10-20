#version 150

uniform sampler2D HalfSampler;

uniform vec2 HalfTexel;
uniform float MaxCoc;

in vec2 texCoord;

out vec4 fragColor;

const float GOLDEN_ANGLE = 2.39996323;

const float STEP = 0.6;

void main() {
    vec4 centre = texture(HalfSampler, texCoord);
    float size = abs(centre.a);
    vec4 sum = vec4(centre.rgb, 0.0);
    float n = 1.0;
    float radius = STEP;
    float angle = 0.0;
    for (int i = 0; i < 400 && radius < MaxCoc; i++) {
        vec4 s = texture(HalfSampler, texCoord + vec2(cos(angle), sin(angle)) * HalfTexel * radius);
        bool behind = s.a > centre.a;
        float reach = behind ? min(abs(s.a), size * 2.0) : abs(s.a);
        float lend = smoothstep(radius - 0.5, radius + 0.5, reach);
        float front = step(s.a, centre.a - 1.0);
        sum += mix(sum / n, vec4(s.rgb, front), lend);
        n += 1.0;
        radius += STEP / radius;
        angle += GOLDEN_ANGLE;
    }
    fragColor = sum / n;
}
