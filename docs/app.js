import * as THREE from "https://cdn.jsdelivr.net/npm/three@0.179.1/build/three.module.js";

const canvas = document.querySelector("#coreScene");
const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

const renderer = new THREE.WebGLRenderer({
  canvas,
  antialias: true,
  alpha: false,
  preserveDrawingBuffer: true,
  powerPreference: "high-performance",
});
renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.75));
renderer.setSize(window.innerWidth, window.innerHeight, false);
renderer.setClearColor(0x070b0c, 1);

const scene = new THREE.Scene();
const camera = new THREE.PerspectiveCamera(45, window.innerWidth / window.innerHeight, 0.1, 100);
camera.position.set(0, 1.2, 8);

const coreGroup = new THREE.Group();
scene.add(coreGroup);

const shellGeometry = new THREE.BoxGeometry(2.4, 2.4, 2.4, 8, 8, 8);
const shellMaterial = new THREE.MeshPhysicalMaterial({
  color: 0x143126,
  roughness: 0.38,
  metalness: 0.42,
  transmission: 0.08,
  thickness: 0.8,
  transparent: true,
  opacity: 0.86,
});
const shell = new THREE.Mesh(shellGeometry, shellMaterial);
coreGroup.add(shell);

const wireMaterial = new THREE.LineBasicMaterial({ color: 0x94f2d0, transparent: true, opacity: 0.55 });
const wire = new THREE.LineSegments(new THREE.EdgesGeometry(shellGeometry), wireMaterial);
wire.scale.setScalar(1.012);
coreGroup.add(wire);

const innerGeometry = new THREE.IcosahedronGeometry(0.92, 2);
const innerMaterial = new THREE.MeshStandardMaterial({
  color: 0x5ee08f,
  emissive: 0x1e8f54,
  emissiveIntensity: 1.35,
  roughness: 0.24,
  metalness: 0.2,
});
const inner = new THREE.Mesh(innerGeometry, innerMaterial);
coreGroup.add(inner);

const ringMaterial = new THREE.MeshBasicMaterial({ color: 0xe8c46b, transparent: true, opacity: 0.58 });
const rings = [];
for (let i = 0; i < 3; i += 1) {
  const ring = new THREE.Mesh(new THREE.TorusGeometry(2.15 + i * 0.35, 0.01, 8, 128), ringMaterial);
  ring.rotation.set(Math.PI / 2, i * 0.72, i * 0.44);
  coreGroup.add(ring);
  rings.push(ring);
}

const particleCount = 420;
const positions = new Float32Array(particleCount * 3);
for (let i = 0; i < particleCount; i += 1) {
  const radius = 4.2 + Math.random() * 5.5;
  const theta = Math.random() * Math.PI * 2;
  const y = (Math.random() - 0.5) * 5.2;
  positions[i * 3] = Math.cos(theta) * radius;
  positions[i * 3 + 1] = y;
  positions[i * 3 + 2] = Math.sin(theta) * radius;
}
const particlesGeometry = new THREE.BufferGeometry();
particlesGeometry.setAttribute("position", new THREE.BufferAttribute(positions, 3));
const particles = new THREE.Points(
  particlesGeometry,
  new THREE.PointsMaterial({
    color: 0x94f2d0,
    size: 0.018,
    transparent: true,
    opacity: 0.72,
  }),
);
scene.add(particles);

scene.add(new THREE.AmbientLight(0x8ceabf, 0.6));
const keyLight = new THREE.PointLight(0x5ee08f, 18, 12);
keyLight.position.set(2.2, 2.4, 3.5);
scene.add(keyLight);
const warmLight = new THREE.PointLight(0xe8c46b, 9, 10);
warmLight.position.set(-3.2, -1.6, 4);
scene.add(warmLight);

function resize() {
  const width = window.innerWidth;
  const height = window.innerHeight;
  camera.aspect = width / height;
  camera.updateProjectionMatrix();
  renderer.setSize(width, height, false);
}

window.addEventListener("resize", resize);

let frame = 0;
function animate() {
  frame += 1;
  const t = performance.now() * 0.001;
  coreGroup.position.x = window.innerWidth > 920 ? 2.25 : 0;
  coreGroup.position.y = window.innerWidth > 920 ? 0.2 : -0.4;
  coreGroup.rotation.y = t * 0.28;
  coreGroup.rotation.x = Math.sin(t * 0.42) * 0.12;
  inner.rotation.y = -t * 0.76;
  inner.rotation.z = t * 0.38;
  rings.forEach((ring, index) => {
    ring.rotation.z = t * (0.16 + index * 0.06);
    ring.rotation.x += 0.0015 * (index + 1);
  });
  particles.rotation.y = t * 0.025;
  renderer.render(scene, camera);
  if (!reducedMotion || frame < 2) {
    requestAnimationFrame(animate);
  }
}

animate();
