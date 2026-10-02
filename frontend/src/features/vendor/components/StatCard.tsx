import { AnimatedCard } from "@/motion/AnimatedCard";

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
    <AnimatedCard
      hoverable={false}
      className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm"
    >
      <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</p>
      <p className="mt-1 text-xl font-semibold text-slate-900">{value}</p>
      {hint && <p className="mt-1 text-xs text-slate-500">{hint}</p>}
    </AnimatedCard>
  );
}

export function PageHeading({ title, description }: { title: string; description?: string }) {
  return (
    <div>
      <h1 className="text-2xl font-bold text-slate-900">{title}</h1>
      {description && <p className="mt-1 text-sm text-slate-600">{description}</p>}
    </div>
  );
}

export function Card({ title, children }: { title?: string; children: React.ReactNode }) {
  return (
    <AnimatedCard
      hoverable={false}
      className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm"
    >
      <section className="h-full">
        {title && <h2 className="text-base font-semibold text-slate-900">{title}</h2>}
        <div className={title ? "mt-4" : ""}>{children}</div>
      </section>
    </AnimatedCard>
  );
}
