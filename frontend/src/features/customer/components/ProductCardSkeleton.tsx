import { motion } from "framer-motion";
import { cardVariants } from "@/motion/tokens";
import { usePrefersReducedMotion } from "@/motion/use-reduced-motion";

const CARD_CLASS = "flex flex-col h-full bg-white rounded-xl border border-slate-200 shadow-card p-4";

const SKELETON_CLASS = "bg-slate-200 animate-pulse rounded";

/**
 * Product card skeleton for the customer search page loading state.
 */
export function ProductCardSkeleton() {
  const reduceMotion = usePrefersReducedMotion();

  return (
    <motion.article
      className={CARD_CLASS}
      variants={cardVariants}
      initial={reduceMotion ? false : "hidden"}
      animate="visible"
    >
      <div className="flex flex-col flex-1">
        <div className="flex items-start justify-between gap-3 mb-3">
          <div className="min-w-0 flex-1">
            <div className={`${SKELETON_CLASS} h-5 w-3/4`} aria-hidden="true" />
            <div className={`${SKELETON_CLASS} h-4 w-1/2 mt-1`} aria-hidden="true" />
            <div className={`${SKELETON_CLASS} h-3 w-1/3 mt-1`} aria-hidden="true" />
          </div>
          <div className="flex flex-col items-end gap-1 shrink-0">
            <div className={`${SKELETON_CLASS} h-5 w-20`} aria-hidden="true" />
            <div className={`${SKELETON_CLASS} h-5 w-16`} aria-hidden="true" />
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-3 text-sm mb-3">
          <div className={`${SKELETON_CLASS} h-6 w-24`} aria-hidden="true" />
        </div>

        <div className="flex flex-wrap items-center gap-3 text-sm mb-3">
          <div className={`${SKELETON_CLASS} h-6 w-20`} aria-hidden="true" />
        </div>

        <div className="mt-auto pt-3 border-t border-slate-100">
          <div className={`${SKELETON_CLASS} h-3 w-1/2`} aria-hidden="true" />
        </div>
      </div>
    </motion.article>
  );
}

/**
 * Grid of product card skeletons.
 */
interface ProductCardSkeletonGridProps {
  count?: number;
}

export function ProductCardSkeletonGrid({ count = 4 }: ProductCardSkeletonGridProps) {
  return (
    <motion.div
      className="grid gap-6 grid-cols-1 sm:grid-cols-2 lg:grid-cols-4"
      variants={cardVariants}
      initial="hidden"
      animate="visible"
    >
      {Array.from({ length: count }).map((_, index) => (
        <ProductCardSkeleton key={index} />
      ))}
    </motion.div>
  );
}