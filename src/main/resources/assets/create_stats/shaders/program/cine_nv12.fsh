#version 150

uniform sampler2D DiffuseSampler;

uniform vec2 SrcSize;
uniform vec4 Crop;
uniform vec2 OutSize;

in vec2 texCoord;

out vec4 fragColor;

vec3 tap(vec2 src) {
    return texture(DiffuseSampler, vec2(src.x, SrcSize.y - src.y) / SrcSize).rgb;
}

vec3 at(vec2 film) {
    vec2 scale = Crop.zw / OutSize;
    vec2 src = Crop.xy + film * scale;
    if (scale.x < 1.01 && scale.y < 1.01) return tap(src);
    vec2 o = 0.25 * scale;
    return 0.25 * (tap(src + vec2(-o.x, -o.y)) + tap(src + vec2(o.x, -o.y))
            + tap(src + vec2(-o.x, o.y)) + tap(src + o));
}

void main() {
    vec2 p = floor(gl_FragCoord.xy);
    float v;
    if (p.y < OutSize.y) {
        vec3 c = at(p + 0.5);
        v = 16.0 / 255.0 + dot(c, vec3(0.2126, 0.7152, 0.0722)) * (219.0 / 255.0);
    } else {
        float bx = floor(p.x * 0.5) * 2.0;
        float by = (p.y - OutSize.y) * 2.0;
        vec3 c = 0.25 * (at(vec2(bx + 0.5, by + 0.5)) + at(vec2(bx + 1.5, by + 0.5))
                + at(vec2(bx + 0.5, by + 1.5)) + at(vec2(bx + 1.5, by + 1.5)));
        v = mod(p.x, 2.0) < 0.5
                ? 128.0 / 255.0 + dot(c, vec3(-0.114572, -0.385428, 0.5)) * (224.0 / 255.0)
                : 128.0 / 255.0 + dot(c, vec3(0.5, -0.454153, -0.045847)) * (224.0 / 255.0);
    }
    fragColor = vec4(v, 0.0, 0.0, 1.0);
}
