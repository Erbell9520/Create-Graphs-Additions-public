#version 150

uniform sampler2D DepthSampler;
uniform sampler2D PrevSampler;

uniform vec2 ProjParams;
uniform vec2 Spread;
uniform vec2 Lock;
uniform float Blend;

in vec2 texCoord;

out vec4 fragColor;

float blocks(vec2 uv) {
    float ndc = texture(DepthSampler, uv).r * 2.0 - 1.0;
    return ProjParams.y / (ndc + ProjParams.x);
}

void order(inout float a, inout float b) {
    float lo = min(a, b);
    b = max(a, b);
    a = lo;
}

void main() {
    float t0 = blocks(vec2(0.5, 0.5));
    float t1 = blocks(vec2(0.5 - Spread.x, 0.5));
    float t2 = blocks(vec2(0.5 + Spread.x, 0.5));
    float t3 = blocks(vec2(0.5, 0.5 - Spread.y));
    float t4 = blocks(vec2(0.5, 0.5 + Spread.y));

    order(t0, t1); order(t3, t4); order(t2, t4);
    order(t2, t3); order(t0, t3); order(t0, t2);
    order(t1, t4); order(t1, t3); order(t1, t2);
    float want = t2;
    if (Lock.x > 0.0 && abs(t2 - Lock.x) > Lock.y) want = Lock.x;
    float prev = texture(PrevSampler, vec2(0.5)).r;
    fragColor = vec4(mix(prev, 1.0 / max(want, 0.05), Blend), 0.0, 0.0, 1.0);
}
