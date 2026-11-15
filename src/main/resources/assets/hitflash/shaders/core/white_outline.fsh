#version 330

in vec4 vertexColor;
out vec4 fragColor;

void main() {
    if (gl_FrontFacing || vertexColor.a <= 0.0) {
        discard;
    }
    fragColor = vertexColor;
}
