import { Canvas, useFrame, useThree } from "@react-three/fiber";
import { useRef } from "react";
import type { Group } from "three";
import { Bouquet } from "./Bouquet";
import type { InteractionLevel } from "./useHeroInteractionLevel";
import { durations } from "@/motion/tokens";

export interface BouquetSceneProps {
  interaction: InteractionLevel;
  /** Reduced motion, or a device with no pointer interaction: no ambient motion. */
  staticMode: boolean;
  frameloop: "always" | "demand" | "never";
  onContextLost: () => void;
  onContextRestored: () => void;
}

/** Fixed camera: no camera animation, no controls, no reframing on scroll. */
const CAMERA = { position: [0, -0.05, 2.4] as [number, number, number], fov: 32 };

/** Lean amplitude per interaction level, in radians. */
const POINTER_LEAN = {
  full: 0.22,
  reduced: 0.08,
  none: 0,
} as const;

/** Ambient sway amplitude — a fraction of a radian either side of rest. */
const SWAY_AMPLITUDE = 0.06;

/**
 * How quickly the group catches up to its target rotation, per frame. Framer
 * independent (`delta`-scaled), so it is a rate rather than a duration and does
 * not need a motion token.
 */
const FOLLOW_RATE = 4;

function BouquetRig({ interaction, staticMode }: Pick<BouquetSceneProps, "interaction" | "staticMode">) {
  const group = useRef<Group>(null);
  const pointer = useThree((state) => state.pointer);

  useFrame((state, delta) => {
    const current = group.current;
    if (!current || staticMode) {
      return;
    }

    // One full sway per `durations.ambient`, the same slow loop period the
    // flower loader uses, so the two ambient motions on the page agree.
    const elapsed = state.clock.elapsedTime;
    const sway = Math.sin((elapsed / durations.ambient) * Math.PI * 2) * SWAY_AMPLITUDE;

    const lean = POINTER_LEAN[interaction];
    const targetX = pointer.y * lean;
    const targetY = pointer.x * lean + sway;
    const step = Math.min(1, delta * FOLLOW_RATE);

    current.rotation.x += (targetX - current.rotation.x) * step;
    current.rotation.y += (targetY - current.rotation.y) * step;
  });

  return (
    <group ref={group}>
      <Bouquet />
    </group>
  );
}

function Lights() {
  return (
    <>
      {/* Two lights, no shadow maps: `castShadow` stays off everywhere, so the
          scene costs no extra shadow passes. */}
      <ambientLight intensity={0.85} />
      <directionalLight position={[2, 3, 4]} intensity={0.8} />
    </>
  );
}

/**
 * The only component in the app that touches WebGL.
 *
 * Render settings are the performance contract: device pixel ratio capped at
 * 1.5, `antialias` off (flat-shaded low-poly geometry does not need MSAA and the
 * DPR cap already smooths edges), `powerPreference: "low-power"` so the browser
 * prefers the integrated GPU, and `touch-action: pan-y` so a vertical swipe over
 * the canvas scrolls the page instead of being swallowed as a drag.
 */
export function BouquetScene({
  interaction,
  staticMode,
  frameloop,
  onContextLost,
  onContextRestored,
}: BouquetSceneProps) {
  return (
    <Canvas
      aria-hidden="true"
      dpr={[1, 1.5]}
      frameloop={frameloop}
      flat
      camera={CAMERA}
      // R3F measures with `scroll: true` by default, which reads
      // `getBoundingClientRect()` and reconfigures the root on a 50ms scroll
      // tick. The frame's box is fixed by its Tailwind classes and its width only
      // changes on a window resize, which `ResizeObserver` already reports.
      resize={{ scroll: false }}
      // `pointerEvents` has to be set here, not only on the frame element: R3F
      // puts its own inline `pointerEvents` on the div it wraps the canvas in,
      // and a descendant that sets `auto` becomes a hit target even when an
      // ancestor says `none`. Our `style` is spread after R3F's, so this wins —
      // without it a `none` tier still raycasts on every pointer move.
      style={{ touchAction: "pan-y", pointerEvents: interaction === "none" ? "none" : "auto" }}
      gl={{
        antialias: false,
        alpha: true,
        depth: true,
        stencil: false,
        powerPreference: "low-power",
      }}
      onCreated={({ gl }) => {
        const canvas = gl.domElement;
        const handleLost = (event: Event) => {
          // Required, or the context is never restored and the event is not
          // cancelable, leaving the canvas permanently blank.
          event.preventDefault();
          onContextLost();
        };
        canvas.addEventListener("webglcontextlost", handleLost, false);
        canvas.addEventListener("webglcontextrestored", onContextRestored, false);
      }}
    >
      <Lights />
      <BouquetRig interaction={interaction} staticMode={staticMode} />
    </Canvas>
  );
}