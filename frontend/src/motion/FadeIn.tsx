import { motion, type HTMLMotionProps } from "framer-motion";
import { fadeVariants } from "@/motion/tokens";
import { usePrefersReducedMotion } from "@/motion/use-reduced-motion";

/**
 * Fades its children in once on mount. Transform/opacity only, duration and
 * easing from `src/motion/tokens.ts`.
 *
 * Under `prefers-reduced-motion: reduce` the initial hidden state is never
 * applied, so the content is simply there — no animation and no opacity left
 * mid-flight for a reader to inherit.
 */
export function FadeIn({ children, ...rest }: HTMLMotionProps<"div">) {
  const reduceMotion = usePrefersReducedMotion();

  return (
    <motion.div
      variants={fadeVariants}
      initial={reduceMotion ? false : "hidden"}
      animate="visible"
      {...rest}
    >
      {children}
    </motion.div>
  );
}
