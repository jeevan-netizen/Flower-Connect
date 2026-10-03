import { motion, type HTMLMotionProps } from "framer-motion";
import { slideUpVariants } from "@/motion/tokens";
import { usePrefersReducedMotion } from "@/motion/use-reduced-motion";

/**
 * Slides its children up into place while fading in. The travel distance and
 * timings come from `src/motion/tokens.ts`; nothing here is hardcoded.
 *
 * Accessibility notes, shared by every primitive in this folder:
 *  - only `transform` and `opacity` are animated, so no layout thrash;
 *  - nothing is hidden from the accessibility tree, no focus trap is created,
 *    and no focusable element is mounted while transparent;
 *  - the animation is decorative, so it never gates an action: the content is
 *    interactive from the first frame.
 */
export function SlideUp({ children, ...rest }: HTMLMotionProps<"div">) {
  const reduceMotion = usePrefersReducedMotion();

  return (
    <motion.div
      variants={slideUpVariants}
      initial={reduceMotion ? false : "hidden"}
      animate="visible"
      {...rest}
    >
      {children}
    </motion.div>
  );
}
