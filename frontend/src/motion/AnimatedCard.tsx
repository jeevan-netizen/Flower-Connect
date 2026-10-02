import { motion, type HTMLMotionProps } from "framer-motion";
import { cardVariants, distances } from "@/motion/tokens";
import { usePrefersReducedMotion } from "@/motion/use-reduced-motion";

interface AnimatedCardProps extends HTMLMotionProps<"div"> {
  /** Set `false` for a card that is not interactive, to drop the hover lift. */
  hoverable?: boolean;
}

/**
 * A card that fades/slides in on mount and lifts a couple of pixels on hover.
 *
 * The lift is `transform`-only and never moves the element in the layout, so a
 * grid of cards does not reflow on pointer-over. Keyboard users get the same
 * emphasis only if the card is itself focusable — this component never adds
 * `tabIndex`, because only the caller knows whether the card is a link, a button
 * or neither.
 */
export function AnimatedCard({
  children,
  className,
  hoverable = true,
  ...rest
}: AnimatedCardProps) {
  const reduceMotion = usePrefersReducedMotion();
  const interactive = hoverable && !reduceMotion;

  return (
    <motion.div
      className={className}
      variants={cardVariants}
      initial={reduceMotion ? false : "hidden"}
      animate="visible"
      whileHover={interactive ? "hover" : undefined}
      whileTap={interactive ? { scale: distances.press } : undefined}
      {...rest}
    >
      {children}
    </motion.div>
  );
}
