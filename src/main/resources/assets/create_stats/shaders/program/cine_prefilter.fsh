#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform sampler2D FocusSampler;

uniform vec2 ProjParams;
uniform vec2 FullTexel;
uniform float CocScale;
uniform float MaxCoc;

in vec2 texCoord;

out vec4 fragColor;

float circle(vec2 uv, float focus) {
    float ndc = texture(DepthSampler, uv).r * 2.0 - 1.0;
    float z = ProjParams.y / (ndc + ProjParams.x);
    return clamp((focus - 1.0 / max(z, 0.05)) * CocScale, -MaxCoc, MaxCoc);
}

void main() {
    float focus = texture(FocusSampler, vec2(0.5)).r;
    vec2 o = FullTexel * 0.5;
    vec2 uv0 = texCoord + vec2(-o.x, -o.y);
    vec2 uv1 = texCoord + vec2(o.x, -o.y);
    vec2 uv2 = texCoord + vec2(-o.x, o.y);
    vec2 uv3 = texCoord + vec2(o.x, o.y);
    vec4 c = vec4(circle(uv0, focus), circle(uv1, focus), circle(uv2, focus), circle(uv3, focus));
    float nearest = min(min(c.x, c.y), min(c.z, c.w));
    vec4 w = step(c, vec4(nearest + 1.0));
    vec3 colour = texture(DiffuseSampler, uv0).rgb * w.x + texture(DiffuseSampler, uv1).rgb * w.y
            + texture(DiffuseSampler, uv2).rgb * w.z + texture(DiffuseSampler, uv3).rgb * w.w;
    fragColor = vec4(colour / (w.x + w.y + w.z + w.w), nearest);
}
