import { useEffect, useLayoutEffect, useMemo, useRef } from "react";
import * as THREE from "three";
import {
  BOUQUET_FLOWERS,
  BOUQUET_LEAVES,
  GEOMETRY,
  LEAF_COLOR,
  STEM_BASE,
  STEM_COLOR,
  WRAP,
  WRAP_COLOR,
  WRAP_TRIM_COLOR,
} from "./bouquet-layout";

/** Stems and the bouquet group are modelled along +Y; instances are aimed onto it. */
const UP = new THREE.Vector3(0, 1, 0);

/**
 * The bouquet itself: five draw calls, all procedural.
 *
 * Stems, heads and leaves are each one `InstancedMesh`, so the flower count does
 * not multiply draw calls. Head colour is per-instance (`setColorAt`), which
 * keeps eighteen differently coloured blooms on a single material. The wrap cone
 * and its trim ring are two plain meshes.
 *
 * Material colours come from `bouquet-layout`, which mirrors the landing design
 * tokens — the scene takes literal strings, so this is the seam where CSS
 * variables meet Three.
 *
 * Geometry and materials are created once in `useMemo` and disposed on unmount,
 * because R3F does not own objects handed to it through `args`.
 */
export function Bouquet() {
  const stems = useRef<THREE.InstancedMesh>(null);
  const heads = useRef<THREE.InstancedMesh>(null);
  const leaves = useRef<THREE.InstancedMesh>(null);

  const { geometries, materials } = useMemo(() => {
    const stemGeometry = new THREE.CylinderGeometry(
      0.018,
      0.026,
      1,
      GEOMETRY.stemRadialSegments,
      1,
      true,
    );
    const headGeometry = new THREE.IcosahedronGeometry(0.16, GEOMETRY.headDetail);
    const leafGeometry = new THREE.PlaneGeometry(0.2, 0.36);
    const wrapGeometry = new THREE.CylinderGeometry(
      WRAP.topRadius,
      WRAP.bottomRadius,
      WRAP.height,
      GEOMETRY.wrapRadialSegments,
      1,
      true,
    );
    const trimGeometry = new THREE.CylinderGeometry(
      WRAP.rimRadius,
      WRAP.rimRadius,
      WRAP.rimHeight,
      GEOMETRY.wrapRadialSegments,
      1,
      true,
    );

    return {
      geometries: { stemGeometry, headGeometry, leafGeometry, wrapGeometry, trimGeometry },
      materials: {
        stem: new THREE.MeshStandardMaterial({
          color: STEM_COLOR,
          roughness: 0.9,
          metalness: 0,
          flatShading: true,
        }),
        head: new THREE.MeshStandardMaterial({
          color: "#ffffff",
          roughness: 0.65,
          metalness: 0,
          flatShading: true,
        }),
        leaf: new THREE.MeshStandardMaterial({
          color: LEAF_COLOR,
          roughness: 0.85,
          metalness: 0,
          flatShading: true,
          side: THREE.DoubleSide,
        }),
        wrap: new THREE.MeshStandardMaterial({
          color: WRAP_COLOR,
          roughness: 0.95,
          metalness: 0,
          flatShading: true,
          side: THREE.DoubleSide,
        }),
        trim: new THREE.MeshStandardMaterial({
          color: WRAP_TRIM_COLOR,
          roughness: 0.8,
          metalness: 0,
          flatShading: true,
          side: THREE.DoubleSide,
        }),
      },
    };
  }, []);

  useEffect(
    () => () => {
      Object.values(geometries).forEach((geometry) => geometry.dispose());
      Object.values(materials).forEach((material) => material.dispose());
    },
    [geometries, materials],
  );

  // Instance transforms are written in a layout effect so the first painted
  // frame already shows the bouquet rather than a pile of untransformed meshes
  // stacked at the origin.
  useLayoutEffect(() => {
    const stemMesh = stems.current;
    const headMesh = heads.current;
    const leafMesh = leaves.current;
    if (!stemMesh || !headMesh || !leafMesh) {
      return;
    }

    const dummy = new THREE.Object3D();
    const base = new THREE.Vector3(...STEM_BASE);
    const head = new THREE.Vector3();
    const direction = new THREE.Vector3();

    BOUQUET_FLOWERS.forEach((flower, index) => {
      head.set(...flower.head);
      direction.subVectors(head, base);

      const length = direction.length();
      const midpoint = base.clone().addScaledVector(direction, 0.5);

      // The stem geometry is one unit tall along Y, so the instance is
      // positioned at the midpoint, aimed along the stem, and scaled to length.
      dummy.position.copy(midpoint);
      dummy.quaternion.setFromUnitVectors(UP, direction.clone().normalize());
      dummy.scale.set(1, length, 1);
      dummy.updateMatrix();
      stemMesh.setMatrixAt(index, dummy.matrix);

      dummy.position.copy(head);
      dummy.quaternion.identity();
      dummy.scale.setScalar(flower.scale);
      dummy.updateMatrix();
      headMesh.setMatrixAt(index, dummy.matrix);
      headMesh.setColorAt(index, new THREE.Color(flower.color));
    });

    BOUQUET_LEAVES.forEach((leaf, index) => {
      dummy.position.set(...leaf.position);
      dummy.rotation.set(...leaf.rotation);
      dummy.scale.setScalar(1);
      dummy.updateMatrix();
      leafMesh.setMatrixAt(index, dummy.matrix);
    });

    stemMesh.instanceMatrix.needsUpdate = true;
    headMesh.instanceMatrix.needsUpdate = true;
    leafMesh.instanceMatrix.needsUpdate = true;
    if (headMesh.instanceColor) {
      headMesh.instanceColor.needsUpdate = true;
    }
  }, []);

  return (
    <group>
      <instancedMesh
        ref={stems}
        args={[geometries.stemGeometry, materials.stem, BOUQUET_FLOWERS.length]}
      />
      <instancedMesh
        ref={heads}
        args={[geometries.headGeometry, materials.head, BOUQUET_FLOWERS.length]}
      />
      <instancedMesh
        ref={leaves}
        args={[geometries.leafGeometry, materials.leaf, BOUQUET_LEAVES.length]}
      />
      <mesh position={WRAP.position} geometry={geometries.wrapGeometry} material={materials.wrap} />
      {/* Trim ring around the mouth of the cone: two meshes rather than one so
          the wrap is not a single flat silhouette. */}
      <mesh
        position={[WRAP.position[0], WRAP.position[1] + WRAP.halfHeight, WRAP.position[2]]}
        geometry={geometries.trimGeometry}
        material={materials.trim}
      />
    </group>
  );
}