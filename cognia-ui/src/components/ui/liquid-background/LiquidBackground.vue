<script lang="ts" setup>
import type { OGLRenderingContext } from "ogl";
import type { HTMLAttributes } from "vue";
import { cn } from "@inspira-ui/plugins";
import { Mesh, Program, Renderer, Triangle } from "ogl";
import { onMounted, onUnmounted, ref } from "vue";

const props = defineProps<{ class?: HTMLAttributes["class"] }>();

const ctnDom = ref<HTMLDivElement | null>(null);

let animateId: number;
let renderer: Renderer;
let gl: OGLRenderingContext;
let mesh: Mesh;
let darkModeObserver: MutationObserver | null = null;

// Vertex Shader
const vert = `
  attribute vec2 uv;
  attribute vec2 position;
  
  varying vec2 vUv;
  
  void main() {
      vUv = uv;
      gl_Position = vec4(position, 0, 1);
  }
  `;

// Fragment Shader
const frag = `
  precision highp float;
  
  uniform float uTime;
  uniform vec3 uResolution;
  uniform float uIsDark;
  
  varying vec2 vUv;
  
  void main() {
      float mr = min(uResolution.x, uResolution.y);
      vec2 uv = (vUv.xy * 2.0 - 1.0) * uResolution.xy / mr;
  
      float d = -uTime * 1.2;
      float a = 0.0;
      for (float i = 0.0; i < 8.0; ++i) {
          a += cos(i - d - a * uv.x);
          d += sin(uv.y * i + a);
      }
      d += uTime * 1.0;
      vec3 col = vec3(cos(uv * vec2(d, a)) * 0.6 + 0.4, cos(a + d) * 0.5 + 0.5);
      col = cos(col * cos(vec3(d, a, 2.5)) * 0.5 + 0.5);
      
      // Convert to grayscale
      float gray = dot(col, vec3(0.299, 0.587, 0.114));
      
      // Light mode (uIsDark = 0.0): white background, dark fluid -> invert gray
      // Dark mode (uIsDark = 1.0): black background, light fluid -> keep gray
      float finalGray = mix(1.0 - gray, gray, uIsDark);
      
      gl_FragColor = vec4(vec3(finalGray), 1.0);
  }
  `;

function resize() {
  if (!ctnDom.value) return;
  const scale = 1;
  renderer.setSize(ctnDom.value.offsetWidth * scale, ctnDom.value.offsetHeight * scale);
  if (mesh) {
    mesh.program.uniforms.uResolution.value = [
      gl.canvas.width,
      gl.canvas.height,
      gl.canvas.width / gl.canvas.height,
    ];
  }
}

function update(t: number) {
  animateId = requestAnimationFrame(update);
  if (mesh) {
    mesh.program.uniforms.uTime.value = t * 0.001;
    renderer.render({ scene: mesh });
  }
}

function updateDarkMode() {
  if (!gl || !mesh) return;
  const isDark = !document.documentElement.classList.contains('dark');
  mesh.program.uniforms.uIsDark.value = isDark ? 1.0 : 0.0;
  gl.clearColor(isDark ? 0 : 1, isDark ? 0 : 1, isDark ? 0 : 1, 1);
}

onMounted(() => {
  if (!ctnDom.value) return;

  renderer = new Renderer();
  gl = renderer.gl;
  
  // Initial clear color will be handled by updateDarkMode
  gl.clearColor(1, 1, 1, 1);

  window.addEventListener("resize", resize, false);
  
  const geometry = new Triangle(gl);

  const program = new Program(gl, {
    vertex: vert,
    fragment: frag,
    uniforms: {
      uTime: { value: 0 },
      uResolution: {
        value: [gl.canvas.width, gl.canvas.height, gl.canvas.width / gl.canvas.height],
      },
      uIsDark: { value: 0.0 },
    },
  });

  mesh = new Mesh(gl, { geometry, program });
  
  // Set initial dark/light mode
  updateDarkMode();
  
  // Listen for class changes on <html> to toggle dark/light mode dynamically
  darkModeObserver = new MutationObserver((mutations) => {
    mutations.forEach((mutation) => {
      if (mutation.attributeName === 'class') {
        updateDarkMode();
      }
    });
  });
  darkModeObserver.observe(document.documentElement, { attributes: true });

  resize();
  animateId = requestAnimationFrame(update);

  ctnDom.value.appendChild(gl.canvas);
});

onUnmounted(() => {
  cancelAnimationFrame(animateId);
  window.removeEventListener("resize", resize);
  if (darkModeObserver) {
    darkModeObserver.disconnect();
  }
  if (ctnDom.value && gl?.canvas) {
    ctnDom.value.removeChild(gl.canvas);
  }
  gl?.getExtension("WEBGL_lose_context")?.loseContext();
});
</script>

<template>
  <div
    ref="ctnDom"
    :class="cn(`block size-full`, props.class)"
  />
</template>