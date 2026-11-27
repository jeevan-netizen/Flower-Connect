import { Link } from "react-router-dom";
import { Card, PageHeading, StatCard } from "@/features/vendor/components/StatCard";
import { AdminUserStatusBadge } from "@/features/admin/components/AdminStatusBadge";
import { useAdminUsers, useAdminVendors } from "@/features/admin/queries";
import { formatDateTime } from "@/features/admin/format";
import { AdminErrorState } from "@/features/admin/components/AdminErrorState";
import { AnimatedList } from "@/motion/AnimatedList";
import { FadeIn } from "@/motion/FadeIn";

/** The two "manage" affordances read as one set. */
const CARD_LINK_CLASS =
  "mt-4 inline-block text-sm font-medium text-bolder-rose-strong transition-colors duration-micro ease-standard hover:text-bolder-rose-deep";

/**
 * Admin dashboard (plan task 2.10: "admin dashboard shell").
 *
 * There is no dashboard or statistics endpoint in Phase 2, so this screen is
 * navigation plus a small preview of each list, composed entirely from the two
 * listings that already exist. Nothing here is invented analytics: the counts are
 * `totalElements` as reported by `GET /api/v1/admin/vendors` and
 * `GET /api/v1/admin/users`, and the tables are the first page of each.
 *
 * Only one status filter is shown per listing — `PENDING_APPROVAL` vendors and
 * `SUSPENDED` users — because those are the two queues an administrator's own
 * work produces. The remaining statuses are reachable from the full listings.
 */
export function AdminDashboardPage() {
  const vendors = useAdminVendors({ status: "PENDING_APPROVAL", page: 0 });
  const users = useAdminUsers({ role: null, status: "SUSPENDED", page: 0 });

  const vendorError = vendors.error;
  const userError = users.error;

  /**
   * Both preview tables replay their entry animation only when their rows change,
   * not on every refetch — see the same key in the two listings.
   */
  const vendorRowsKey = vendors.data?.content.map((vendor) => vendor.id).join(",") ?? "";
  const userRowsKey = users.data?.content.map((user) => user.id).join(",") ?? "";

  return (
    <div className="space-y-6">
      <PageHeading
        title="Admin dashboard"
        description="Review vendor applications and manage user account status."
      />

      <AnimatedList className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <StatCard
          label="Vendors awaiting approval"
          value={vendors.isPending ? "…" : (vendors.data?.totalElements ?? 0)}
          hint="Applications needing a decision"
        />
        <StatCard
          label="Suspended users"
          value={users.isPending ? "…" : (users.data?.totalElements ?? 0)}
          hint="Accounts currently not able to sign in"
        />
      </AnimatedList>

      <AnimatedList className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <Card title="Vendor management">
          <p className="text-sm text-bolder-muted">
            List vendors by approval status, then approve, reject, suspend or reinstate. Rejections
            and suspensions record a reason on the audit log.
          </p>
          <Link to="/admin/vendors" className={CARD_LINK_CLASS}>
            Manage vendors
          </Link>
        </Card>

        <Card title="User management">
          <p className="text-sm text-bolder-muted">
            List accounts by role and status, then suspend, disable or reactivate. Every change
            records a reason and signs the account out everywhere.
          </p>
          <Link to="/admin/users" className={CARD_LINK_CLASS}>
            Manage users
          </Link>
        </Card>
      </AnimatedList>

      {vendorError && <AdminErrorState error={vendorError} onRetry={() => void vendors.refetch()} />}
      {vendors.isPending ? (
        <div className="rounded-lg border border-bolder-line bg-bolder-surface p-5 text-sm text-bolder-muted">
          Loading pending vendor applications...
        </div>
      ) : (
        vendors.data &&
        (vendors.data.empty ? (
          <div className="rounded-lg border border-bolder-line bg-bolder-surface p-5 text-sm text-bolder-muted">
            No vendor applications are awaiting approval.
          </div>
        ) : (
          <FadeIn
            key={vendorRowsKey}
            className="overflow-x-auto rounded-lg border border-bolder-line bg-bolder-surface p-5 shadow-sm"
          >
            <h2 className="text-base font-semibold text-bolder-text">Awaiting approval</h2>
            <table className="mt-3 w-full text-left text-sm">
              <thead className="text-xs uppercase tracking-wide text-bolder-muted">
                <tr>
                  <th scope="col" className="py-2 pr-4 font-medium">Business</th>
                  <th scope="col" className="py-2 pr-4 font-medium">Owner</th>
                  <th scope="col" className="py-2 pr-4 font-medium">Area</th>
                  <th scope="col" className="py-2 font-medium">Applied</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-bolder-line">
                {vendors.data.content.map((vendor) => (
                  <tr key={vendor.id} className="align-top">
                    <td className="py-2 pr-4 font-medium text-bolder-text">{vendor.businessName}</td>
                    <td className="py-2 pr-4 text-bolder-text">{vendor.ownerEmail}</td>
                    <td className="py-2 pr-4 text-bolder-text">
                      {vendor.area}, {vendor.city}
                    </td>
                    <td className="py-2 text-bolder-muted">{formatDateTime(vendor.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {vendors.data.totalElements > vendors.data.content.length && (
              <p className="mt-3 text-sm text-bolder-muted">
                <Link
                  to="/admin/vendors?status=PENDING_APPROVAL"
                  className="font-medium text-bolder-rose-strong hover:text-bolder-rose-deep transition-colors duration-micro ease-standard"
                >
                  View all {vendors.data.totalElements} pending applications
                </Link>
              </p>
            )}
          </FadeIn>
        ))
      )}
      {userError && <AdminErrorState error={userError} onRetry={() => void users.refetch()} />}
      {users.isPending ? (
        <div className="rounded-lg border border-bolder-line bg-bolder-surface p-5 text-sm text-bolder-muted">
          Loading suspended users...
        </div>
      ) : (
        users.data &&
        (users.data.empty ? (
          <div className="rounded-lg border border-bolder-line bg-bolder-surface p-5 text-sm text-bolder-muted">
            No user accounts are suspended.
          </div>
        ) : (
          <FadeIn
            key={userRowsKey}
            className="overflow-x-auto rounded-lg border border-bolder-line bg-bolder-surface p-5 shadow-sm"
          >
            <h2 className="text-base font-semibold text-bolder-text">Suspended users</h2>
            <table className="mt-3 w-full text-left text-sm">
              <thead className="text-xs uppercase tracking-wide text-bolder-muted">
                <tr>
                  <th scope="col" className="py-2 pr-4 font-medium">Name</th>
                  <th scope="col" className="py-2 pr-4 font-medium">Email</th>
                  <th scope="col" className="py-2 pr-4 font-medium">Role</th>
                  <th scope="col" className="py-2 font-medium">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-bolder-line">
                {users.data.content.map((user) => (
                  <tr key={user.id} className="align-top">
                    <td className="py-2 pr-4 font-medium text-bolder-text">{user.fullName}</td>
                    <td className="py-2 pr-4 text-bolder-text">{user.email}</td>
                    <td className="py-2 pr-4 text-bolder-text">{user.role}</td>
                    <td className="py-2">
                      <AdminUserStatusBadge status={user.status} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {users.data.totalElements > users.data.content.length && (
              <p className="mt-3 text-sm text-bolder-muted">
                <Link
                  to="/admin/users?status=SUSPENDED"
                  className="font-medium text-bolder-rose-strong hover:text-bolder-rose-deep transition-colors duration-micro ease-standard"
                >
                  View all {users.data.totalElements} suspended accounts
                </Link>
              </p>
            )}
          </FadeIn>
        ))
      )}
    </div>
  );
}
