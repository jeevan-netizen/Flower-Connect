import { AnimatePresence, motion, type HTMLMotionProps } from "framer-motion";
import { statusChangeVariants } from "@/motion/tokens";
import { usePrefersReducedMotion } from "@/motion/use-reduced-motion";

interface StatusPillProps extends HTMLMotionProps<"span"> {
  /** Background/text classes for the status, from the owning screen's tone map. */
  tone: string;
  /** The label to show. A change to this string cross-fades the pill's text. */
  label: string;
}

/**
 * A compact status pill whose label swaps rather than snapping when the status
 * behind it changes — a pending vendor that is approved mid-session, a suspended
 * account that is reactivated.
 *
 * Two details keep it quiet. The pill transitions only its own colours, so the
 * tone crossfades; and the label's enter/exit states are dropped under
 * `prefers-reduced-motion`, so the text is simply replaced in the same tick
 * instead of being swapped through a fade.
 *
 * `initial={false}` on the `AnimatePresence` means a table of twenty pills
 * renders without twenty animations on first paint — only a status that changes
 * afterwards animates.
 */
export function StatusPill({ tone, label, ...rest }: StatusPillProps) {
  const reduceMotion = usePrefersReducedMotion();
  const enterState = reduceMotion ? false : "hidden";
  const exitState = reduceMotion ? undefined : "exit";

  return (
    <motion.span
      className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium transition-colors duration-micro ease-standard ${tone}`}
      {...rest}
    >
      <AnimatePresence initial={false} mode="wait">
        <motion.span
          key={label}
          variants={statusChangeVariants}
          initial={enterState}
          animate="visible"
          exit={exitState}
          className="inline-flex"
        >
          {label}
        </motion.span>
      </AnimatePresence>
    </motion.span>
  );
}
