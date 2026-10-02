import type { ReactNode } from "react";
import { motion } from "framer-motion";
import { pageVariants } from "@/motion/tokens";
import { usePrefersReducedMotion } from "@/motion/use-reduced-motion";

interface AnimatedPageProps {
  children: ReactNode;
  className?: string;
}

/**
 * The route-level transition wrapper.
 *
 * It is rendered once inside the app shell's `<Outlet />` and keyed by pathname
 * in `AnimatePresence`, so `initial`/`animate` plays on entry and `exit` plays
 * when the next route takes over. Route guards are *outside* this component (they
 * sit in the route config), so a redirect caused by a guard never double-renders
 * this element or leaves stale page content behind it.
 *
 * It adds one wrapper `div` around the routed page. Layout is unaffected
 * because the wrapper is a plain block that fills its parent.
 *
 * With reduced motion there is neither an entry nor an exit animation, which
 * also means `AnimatePresence mode="wait"` has nothing to wait for: the outgoing
 * route unmounts in the same tick as the navigation.
 */
export function AnimatedPage({ children, className }: AnimatedPageProps) {
  const reduceMotion = usePrefersReducedMotion();

  return (
    <motion.div
      className={className}
      variants={pageVariants}
      initial={reduceMotion ? false : "initial"}
      animate="animate"
      exit={reduceMotion ? undefined : "exit"}
    >
      {children}
    </motion.div>
  );
}
