#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform sampler2D BlurSampler;
uniform sampler2D FocusSampler;

uniform vec2 ProjParams;
uniform float CocScale;
uniform float MaxCoc;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    float ndc = texture(DepthSampler, texCoord).r * 2.0 - 1.0;
    float z = ProjParams.y / (ndc + ProjParams.x);
    float focus = texture(FocusSampler, vec2(0.5)).r;
    float circle = clamp((focus - 1.0 / max(z, 0.05)) * CocScale, -MaxCoc, MaxCoc);
    vec4 blur = texture(BlurSampler, texCoord);
    float k = max(smoothstep(0.6, 1.8, abs(circle)), clamp(blur.a * 2.0, 0.0, 1.0));
    fragColor = vec4(mix(texture(DiffuseSampler, texCoord).rgb, blur.rgb, k), 1.0);
}
