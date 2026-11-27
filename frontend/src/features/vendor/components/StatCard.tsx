import { AnimatedCard } from "@/motion/AnimatedCard";
import { CARD, PAGE_DESCRIPTION, PAGE_TITLE } from "@/shared/ui/classes";

interface StatCardProps {
  label: string;
  value: React.ReactNode;
  hint?: string;
}

/**
 * One dashboard statistic. Plain presentational component — no data fetching.
 *
 * `hoverable={false}`: a statistic is not clickable, so it gets the entry
 * animation but no hover lift, which would otherwise suggest an action.
 */
export function StatCard({ label, value, hint }: StatCardProps) {
  return (
    <AnimatedCard hoverable={false} className={`${CARD} p-4 sm:p-5`}>
      <p className="text-xs font-medium text-bolder-muted">{label}</p>
      <p className="mt-2 font-display text-2xl leading-none text-bolder-text">{value}</p>
      {hint && <p className="mt-2 text-xs text-bolder-muted">{hint}</p>}
    </AnimatedCard>
  );
}

export function PageHeading({ title, description }: { title: string; description?: string }) {
  return (
    <div>
      <h1 className={PAGE_TITLE}>{title}</h1>
      {description && <p className={PAGE_DESCRIPTION}>{description}</p>}
    </div>
  );
}

export function Card({ title, children }: { title?: string; children: React.ReactNode }) {
  return (
    <AnimatedCard hoverable={false} className={`${CARD} p-5 sm:p-6`}>
      <section className="h-full">
        {title && <h2 className="font-display text-xl text-bolder-text">{title}</h2>}
        <div className={title ? "mt-4" : ""}>{children}</div>
      </section>
    </AnimatedCard>
  );
}
