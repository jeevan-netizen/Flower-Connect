import type { StorefrontHour } from "@/features/customer/types";
import { formatOpeningHours, formatWeekday } from "@/features/customer/format";

interface StorefrontHoursProps {
  hours: StorefrontHour[];
}

/**
 * A florist's opening week.
 *
 * The backend sends the week in weekday order and a closed day carries null
 * times, so a closed day is worded rather than rendered as a blank row. A
 * florist who has not published any hours says so instead of showing an empty
 * table.
 */
export function StorefrontHours({ hours }: StorefrontHoursProps) {
  if (hours.length === 0) {
    return <p className="text-sm text-slate-600">Opening hours have not been published.</p>;
  }

  return (
    <ul className="divide-y divide-slate-100">
      {hours.map((hour) => (
        <li
          key={hour.weekday}
          className="flex items-center justify-between gap-4 py-2 text-sm"
        >
          <span className="font-medium text-slate-700">{formatWeekday(hour.weekday)}</span>
          <span className={hour.closed ? "text-slate-500" : "text-slate-900"}>
            {formatOpeningHours(hour.openTime, hour.closeTime, hour.closed)}
          </span>
        </li>
      ))}
    </ul>
  );
}
