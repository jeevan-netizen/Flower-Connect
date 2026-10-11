import { motion } from "framer-motion";
import { listContainerVariants, listItemVariants } from "@/motion/tokens";
import { usePrefersReducedMotion } from "@/motion/use-reduced-motion";

const SKELETON_CLASS = "animate-pulse flex flex-col h-full bg-white rounded-xl border border-slate-200 shadow-card p-4";
const LINE_CLASS = "h-4 bg-slate-200 rounded";
const LINE_SHORT_CLASS = "h-4 bg-slate-200 rounded w-3/4";
const LINE_VERY_SHORT_CLASS = "h-4 bg-slate-200 rounded w-1/2";
const BADGE_SKELETON_CLASS = "h-5 w-20 bg-slate-200 rounded-full";

/**
 * Skeleton loader for `VendorCard`.
 *
 * Matches the card's visual structure so no layout shift occurs when real
 * data arrives. Uses the same entry animation as `AnimatedList` children.
 */
export function VendorCardSkeleton() {
  const reduceMotion = usePrefersReducedMotion();

  return (
    <motion.article
      className={SKELETON_CLASS}
      variants={listItemVariants}
      initial={reduceMotion ? false : "hidden"}
      animate="visible"
    >
      <div className="flex items-start justify-between gap-3 mb-3">
        <div className="min-w-0 flex-1">
          <div className={`${LINE_CLASS} w-3/4`} aria-hidden="true" />
          <div className={`${LINE_SHORT_CLASS} mt-2`} aria-hidden="true" />
        </div>
        <div className="flex flex-col items-end gap-1 shrink-0">
          <div className={BADGE_SKELETON_CLASS} aria-hidden="true" />
          <div className={BADGE_SKELETON_CLASS} aria-hidden="true" />
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-3 text-sm mb-3">
        <div className={`${LINE_CLASS} w-28`} aria-hidden="true" />
        <div className={`${LINE_CLASS} w-28`} aria-hidden="true" />
      </div>

      <div className="flex flex-wrap items-center gap-3 text-sm mb-3">
        <div className={BADGE_SKELETON_CLASS} aria-hidden="true" />
        <div className={`${LINE_SHORT_CLASS}`} aria-hidden="true" />
      </div>

      <div className="mt-auto pt-3 border-t border-slate-100">
        <div className={`${LINE_VERY_SHORT_CLASS}`} aria-hidden="true" />
      </div>
    </motion.article>
  );
}

/**
 * A grid of vendor card skeletons matching the responsive layout.
 *
 * Shows 4 skeletons by default (matching the desktop grid), which collapse
 * to 2 on tablet and 1 on mobile via the same grid classes as the real cards.
 */
export function VendorCardSkeletonGrid({ count = 4 }: { count?: number }) {
  const reduceMotion = usePrefersReducedMotion();

  return (
    <motion.div
      className="grid gap-6 grid-cols-1 sm:grid-cols-2 lg:grid-cols-4"
      variants={listContainerVariants}
      initial={reduceMotion ? false : "hidden"}
      animate="visible"
      role="status"
      aria-label="Loading vendor cards"
    >
      {Array.from({ length: count }, (_, i) => (
        <VendorCardSkeleton key={i} />
      ))}
    </motion.div>
  );
}