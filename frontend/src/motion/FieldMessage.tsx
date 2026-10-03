import { AnimatePresence, motion } from "framer-motion";
import { fieldErrorVariants } from "@/motion/tokens";
import { usePrefersReducedMotion } from "@/motion/use-reduced-motion";

interface FieldMessageProps {
  /**
   * The control's `aria-describedby` target. One element serves both the hint
   * and the error, so the description a screen reader announces changes with
   * the message instead of pointing at a node that has just been replaced.
   */
  id?: string;
  /** Validation message. Wins over `hint` while present. */
  message?: string;
  /** Steady-state explanation, shown while there is no validation message. */
  hint?: string;
  /**
   * Keep one line of height even with nothing to say.
   *
   * Without it a message appearing grows the field's block and shoves every
   * control below it down, which moves the thing the operator was aiming at
   * mid-submit. One reserved line is the trade: a small constant gap in the
   * form's steady state in exchange for a form that never jumps under a
   * validation error.
   */
  reserve?: boolean;
}

/** One line of `text-sm`, which is the tallest message this renders. */
const RESERVED_LINE = "min-h-5";

/**
 * The single line of text under a form control: a hint, a validation message, or
 * both reserved space.
 *
 * Every field in the app rendered its own `<p className="mt-1 text-sm
 * text-red-600">` inside an `errors.x && ...` guard, which meant three copies
 * of the same markup, three copies of the same reveal, and no reserved space
 * anywhere. This is that markup, once, with the reveal wired to the tokens.
 *
 * The message animates in and out with `AnimatePresence` while the wrapper stays
 * mounted, so the exit has somewhere to happen and the reserved height holds
 * throughout. The `key` is constant, which is deliberate: only the presence of
 * a message animates — a message whose *text* changes is replaced instantly,
 * because cross-fading every keystroke of a re-validated field would flicker.
 */
export function FieldMessage({ id, message, hint, reserve = false }: FieldMessageProps) {
  const reduceMotion = usePrefersReducedMotion();
  const content = message ?? hint;

  if (!content && !reserve) {
    return null;
  }

  return (
    <p
      id={id}
      className={`mt-1 ${reserve ? RESERVED_LINE : ""} ${
        message ? "text-sm text-red-600" : "text-xs text-slate-500"
      }`}
    >
      <AnimatePresence>
        {content && (
          <motion.span
            key="message"
            variants={fieldErrorVariants}
            initial={reduceMotion ? false : "hidden"}
            animate="visible"
            exit={reduceMotion ? undefined : "exit"}
            className="block"
          >
            {content}
          </motion.span>
        )}
      </AnimatePresence>
    </p>
  );
}
