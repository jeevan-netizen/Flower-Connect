import { Children, type ReactNode } from "react";
import { motion } from "framer-motion";
import { listContainerVariants, listItemVariants } from "@/motion/tokens";
import { usePrefersReducedMotion } from "@/motion/use-reduced-motion";

interface AnimatedListProps {
  children: ReactNode;
  className?: string;
  /** Class applied to the wrapper generated around each child. */
  itemClassName?: string;
}

/**
 * Staggered entry for a collection: the container schedules its children one
 * `staggerInterval` apart. Every child is wrapped in a plain `div` — pass an
 * `itemClassName` that carries the layout (a grid item, a flex child) if the
 * wrapper needs to participate in one.
 *
 * All children render immediately as far as the DOM and the accessibility tree
 * are concerned; the stagger is decoration on top of content that is already
 * present and focusable.
 */
export function AnimatedList({ children, className, itemClassName }: AnimatedListProps) {
  const reduceMotion = usePrefersReducedMotion();

  return (
    <motion.div
      className={className}
      variants={listContainerVariants}
      initial={reduceMotion ? false : "hidden"}
      animate="visible"
    >
      {Children.map(children, (child, index) => (
        // eslint-disable-next-line react/no-array-index-key -- children arrive
        // in a fixed, authored order and carry no stable identity of their own.
        <motion.div key={index} className={itemClassName} variants={listItemVariants}>
          {child}
        </motion.div>
      ))}
    </motion.div>
  );
}
