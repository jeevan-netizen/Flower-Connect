import { useEffect, useId, useState } from "react";
import { useForm, useWatch, type FieldErrors } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useUpdateVendorProfile, useVendorProfile } from "@/features/vendor/queries";
import {
  buildProfileUpdateRequest,
  WEEKDAYS,
  type VendorHours,
  type Weekday,
} from "@/features/vendor/types";
import { toApiTime, toTimeInputValue, weekdayLabel } from "@/features/vendor/format";
import { toApiError, type ApiErrorInfo } from "@/shared/lib/api-error";
import { Card, PageHeading } from "@/features/vendor/components/StatCard";
import {
  FormErrorSummary,
  SubmitButton,
  SuccessMessage,
} from "@/features/vendor/components/FormFields";
import { VendorErrorState } from "@/features/vendor/components/VendorErrorState";

const TIME_PATTERN = /^([01]\d|2[0-3]):[0-5]\d$/;

const daySchema = z.object({
  closed: z.boolean(),
  openTime: z.string(),
  closeTime: z.string(),
});

/**
 * The week is spelled out as a seven-key object rather than `z.record` or a field
 * array: the backend stores at most one row per weekday, so the set of rows never
 * changes, and fixed keys make every day a required value (so `reset` can refresh
 * each checkbox's checked state) while still giving typed paths to
 * `setValue` / `register` such as `days.MONDAY.closed`.
 */
const DAY_SHAPE = WEEKDAYS.reduce(
  (shape, weekday) => ({ ...shape, [weekday]: daySchema }),
  {} as { [K in Weekday]: typeof daySchema },
);

/**
 * The same three rules the backend enforces in `VendorService.validateHours`, so
 * the obvious mistakes never reach the network:
 *   a closed day carries no times;
 *   an open day carries both;
 *   an open day closes strictly after it opens.
 */
const hoursSchema = z.object({ days: z.object(DAY_SHAPE) }).superRefine((values, ctx) => {
  WEEKDAYS.forEach((weekday) => {
    const day = values.days[weekday];
    if (day.closed) {
      return;
    }
    if (!TIME_PATTERN.test(day.openTime)) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["days", weekday, "openTime"],
        message: "Opening time is required when the day is open",
      });
    }
    if (!TIME_PATTERN.test(day.closeTime)) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["days", weekday, "closeTime"],
        message: "Closing time is required when the day is open",
      });
    }
    if (TIME_PATTERN.test(day.openTime) && TIME_PATTERN.test(day.closeTime)) {
      if (day.closeTime <= day.openTime) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["days", weekday, "closeTime"],
          message: "Closing time must be after opening time",
        });
      }
    }
  });
});

type DayFormValue = z.infer<typeof daySchema>;
type DaysFormValues = { [K in Weekday]: DayFormValue };
type HoursFormValues = z.infer<typeof hoursSchema>;

function closedWeek(): DaysFormValues {
  return WEEKDAYS.reduce((week, weekday) => {
    week[weekday] = { closed: true, openTime: "", closeTime: "" };
    return week;
  }, {} as DaysFormValues);
}

function toFormDays(hours: VendorHours[] | undefined): DaysFormValues {
  const stored = new Map<Weekday, VendorHours>();
  (hours ?? []).forEach((day) => stored.set(day.weekday, day));

  return WEEKDAYS.reduce((week, weekday) => {
    const existing = stored.get(weekday);
    week[weekday] = {
      closed: existing ? existing.closed : true,
      openTime: existing ? toTimeInputValue(existing.openTime) : "",
      closeTime: existing ? toTimeInputValue(existing.closeTime) : "",
    };
    return week;
  }, {} as DaysFormValues);
}

function messageOf(value: unknown): string | undefined {
  if (typeof value !== "object" || value === null || !("message" in value)) {
    return undefined;
  }
  const message = (value as { message?: unknown }).message;
  return typeof message === "string" ? message : undefined;
}

/** Reads one field error out of `formState.errors.days[weekday]` without asserting its shape. */
function dayError(
  errors: FieldErrors<HoursFormValues>,
  weekday: Weekday,
  field: "openTime" | "closeTime",
): string | undefined {
  const days: unknown = errors.days;
  if (typeof days !== "object" || days === null) {
    return undefined;
  }
  const day: unknown = (days as Record<string, unknown>)[weekday];
  if (typeof day !== "object" || day === null) {
    return undefined;
  }
  return messageOf((day as Record<string, unknown>)[field]);
}

/**
 * Operating hours (plan tasks 2.4 / 2.9).
 *
 * Times are edited as `HH:mm` because that is what `<input type="time">` speaks,
 * and are converted to the backend's `HH:mm:ss` `LocalTime` on submit. All seven
 * weekdays are always rendered, including days with no stored row — the backend
 * treats a missing row as closed, so an editor that only showed stored rows would
 * hide days a vendor cannot reopen without another round trip.
 */
export function VendorHoursPage() {
  const { data: profile, isPending, error, refetch } = useVendorProfile();
  const updateProfile = useUpdateVendorProfile();
  const [success, setSuccess] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<ApiErrorInfo | null>(null);
  const idPrefix = useId();

  const {
    control,
    register,
    handleSubmit,
    reset,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<HoursFormValues>({
    resolver: zodResolver(hoursSchema),
    defaultValues: { days: closedWeek() },
  });

  const watchedDays = useWatch({ control, name: "days" });

  // The profile is the form's source of truth: re-seed whenever a refetch or a
  // successful save produces a new week.
  useEffect(() => {
    if (profile) {
      reset({ days: toFormDays(profile.hours) });
    }
  }, [profile, reset]);

  if (isPending) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-6 text-sm text-slate-600">
        Loading your operating hours...
      </div>
    );
  }

  if (error || !profile) {
    return <VendorErrorState error={error} onRetry={() => void refetch()} />;
  }

  const onSubmit = handleSubmit(async (values) => {
    setSuccess(null);
    setSubmitError(null);

    const hours: VendorHours[] = WEEKDAYS.map((weekday) => {
      const day = values.days[weekday];
      return {
        weekday,
        closed: day.closed,
        openTime: day.closed ? null : toApiTime(day.openTime),
        closeTime: day.closed ? null : toApiTime(day.closeTime),
      };
    });

    try {
      await updateProfile.mutateAsync(buildProfileUpdateRequest(profile, {}, hours));
      setSuccess("Operating hours saved.");
    } catch (mutationError) {
      setSubmitError(toApiError(mutationError));
    }
  });

  return (
    <div className="space-y-6">
      <PageHeading
        title="Operating hours"
        description="When your shop accepts delivery slots. Saving replaces the whole week."
      />

      <Card>
        <form onSubmit={onSubmit} noValidate>
          <FormErrorSummary message={submitError?.message ?? null} />
          <SuccessMessage message={success} />

          <ul className="mt-4 divide-y divide-slate-100">
            {WEEKDAYS.map((weekday) => {
              const day = watchedDays?.[weekday];
              const closed = day ? day.closed : true;
              const openError = dayError(errors, weekday, "openTime");
              const closeError = dayError(errors, weekday, "closeTime");
              const checkboxId = `${idPrefix}-closed-${weekday}`;
              const openId = `${idPrefix}-open-${weekday}`;
              const closeId = `${idPrefix}-close-${weekday}`;

              return (
                <li
                  key={weekday}
                  className="flex flex-col gap-3 py-4 sm:flex-row sm:items-center sm:gap-6"
                >
                  <div className="sm:w-40 sm:shrink-0">
                    <label
                      htmlFor={checkboxId}
                      className="flex items-center gap-2 text-sm font-medium text-slate-700"
                    >
                      {/* The "day is open" checkbox is driven from the watched form
                          value rather than registered as an uncontrolled input:
                          react-hook-form leaves checkbox state in the DOM, so a
                          `reset` from a refetch or a save would otherwise leave a
                          stale week checked while the times showed the new one. */}
                      <input
                        id={checkboxId}
                        type="checkbox"
                        className="h-4 w-4 rounded border-slate-300 text-brand-600 focus:ring-brand-500"
                        checked={!closed}
                        onChange={(event) =>
                          setValue(`days.${weekday}.closed`, !event.target.checked, {
                            shouldDirty: true,
                            shouldValidate: true,
                          })
                        }
                      />
                      {weekdayLabel(weekday)}
                    </label>
                  </div>

                  <div className="flex flex-1 flex-col gap-3 sm:flex-row">
                    <div className="sm:w-40">
                      <label
                        htmlFor={openId}
                        className="block text-xs font-medium uppercase tracking-wide text-slate-500"
                      >
                        Opens
                      </label>
                      <input
                        id={openId}
                        type="time"
                        disabled={closed}
                        aria-invalid={openError ? true : undefined}
                        className={`mt-1 block w-full rounded-md border-slate-300 shadow-sm focus:border-brand-500 focus:ring-brand-500 disabled:bg-slate-100 sm:text-sm ${openError ? "border-red-500" : ""}`}
                        {...register(`days.${weekday}.openTime`)}
                      />
                      {openError && <p className="mt-1 text-sm text-red-600">{openError}</p>}
                    </div>

                    <div className="sm:w-40">
                      <label
                        htmlFor={closeId}
                        className="block text-xs font-medium uppercase tracking-wide text-slate-500"
                      >
                        Closes
                      </label>
                      <input
                        id={closeId}
                        type="time"
                        disabled={closed}
                        aria-invalid={closeError ? true : undefined}
                        className={`mt-1 block w-full rounded-md border-slate-300 shadow-sm focus:border-brand-500 focus:ring-brand-500 disabled:bg-slate-100 sm:text-sm ${closeError ? "border-red-500" : ""}`}
                        {...register(`days.${weekday}.closeTime`)}
                      />
                      {closeError && <p className="mt-1 text-sm text-red-600">{closeError}</p>}
                    </div>
                  </div>
                </li>
              );
            })}
          </ul>

          <div className="mt-4">
            <SubmitButton
              isSubmitting={isSubmitting || updateProfile.isPending}
              idleLabel="Save operating hours"
              busyLabel="Saving..."
            />
          </div>
        </form>
      </Card>
    </div>
  );
}
