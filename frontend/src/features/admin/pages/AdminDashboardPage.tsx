import { Link } from "react-router-dom";
import { PageHeading, StatCard } from "@/features/vendor/components/StatCard";
import { AdminUserStatusBadge } from "@/features/admin/components/AdminStatusBadge";
import { useAdminUsers, useAdminVendors } from "@/features/admin/queries";
import { formatDateTime } from "@/features/admin/format";
import { AdminErrorState } from "@/features/admin/components/AdminErrorState";

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

  return (
    <div className="space-y-6">
      <PageHeading
        title="Admin dashboard"
        description="Review vendor applications and manage user account status."
      />

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
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
      </div>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="text-base font-semibold text-slate-900">Vendor management</h2>
          <p className="mt-1 text-sm text-slate-600">
            List vendors by approval status, then approve, reject, suspend or reinstate. Rejections
            and suspensions record a reason on the audit log.
          </p>
          <Link
            to="/admin/vendors"
            className="mt-4 inline-block text-sm font-medium text-brand-700 hover:text-brand-900"
          >
            Manage vendors
          </Link>
        </section>

        <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="text-base font-semibold text-slate-900">User management</h2>
          <p className="mt-1 text-sm text-slate-600">
            List accounts by role and status, then suspend, disable or reactivate. Every change
            records a reason and signs the account out everywhere.
          </p>
          <Link
            to="/admin/users"
            className="mt-4 inline-block text-sm font-medium text-brand-700 hover:text-brand-900"
          >
            Manage users
          </Link>
        </section>
      </div>

      {vendorError && <AdminErrorState error={vendorError} onRetry={() => void vendors.refetch()} />}

      {vendors.isPending ? (
        <div className="rounded-lg border border-slate-200 bg-white p-5 text-sm text-slate-600">
          Loading pending vendor applications...
        </div>
      ) : (
        vendors.data &&
        (vendors.data.empty ? (
          <div className="rounded-lg border border-slate-200 bg-white p-5 text-sm text-slate-600">
            No vendor applications are awaiting approval.
          </div>
        ) : (
          <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-base font-semibold text-slate-900">Awaiting approval</h2>
            <table className="mt-3 w-full text-left text-sm">
              <thead className="text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th scope="col" className="py-2 pr-4 font-medium">Business</th>
                  <th scope="col" className="py-2 pr-4 font-medium">Owner</th>
                  <th scope="col" className="py-2 pr-4 font-medium">Area</th>
                  <th scope="col" className="py-2 font-medium">Applied</th>
                </tr>
              </thead>
              <tbody>
                {vendors.data.content.map((vendor) => (
                  <tr key={vendor.id} className="border-t border-slate-100">
                    <td className="py-2 pr-4 font-medium text-slate-900">{vendor.businessName}</td>
                    <td className="py-2 pr-4 text-slate-700">{vendor.ownerEmail}</td>
                    <td className="py-2 pr-4 text-slate-700">
                      {vendor.area}, {vendor.city}
                    </td>
                    <td className="py-2 text-slate-600">{formatDateTime(vendor.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {vendors.data.totalElements > vendors.data.content.length && (
              <p className="mt-3 text-sm text-slate-600">
                <Link to="/admin/vendors?status=PENDING_APPROVAL" className="font-medium text-brand-700 hover:text-brand-900">
                  View all {vendors.data.totalElements} pending applications
                </Link>
              </p>
            )}
          </div>
        ))
      )}

      {userError && <AdminErrorState error={userError} onRetry={() => void users.refetch()} />}

      {users.isPending ? (
        <div className="rounded-lg border border-slate-200 bg-white p-5 text-sm text-slate-600">
          Loading suspended users...
        </div>
      ) : (
        users.data &&
        (users.data.empty ? (
          <div className="rounded-lg border border-slate-200 bg-white p-5 text-sm text-slate-600">
            No user accounts are suspended.
          </div>
        ) : (
          <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-base font-semibold text-slate-900">Suspended users</h2>
            <table className="mt-3 w-full text-left text-sm">
              <thead className="text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th scope="col" className="py-2 pr-4 font-medium">Name</th>
                  <th scope="col" className="py-2 pr-4 font-medium">Email</th>
                  <th scope="col" className="py-2 pr-4 font-medium">Role</th>
                  <th scope="col" className="py-2 font-medium">Status</th>
                </tr>
              </thead>
              <tbody>
                {users.data.content.map((user) => (
                  <tr key={user.id} className="border-t border-slate-100">
                    <td className="py-2 pr-4 font-medium text-slate-900">{user.fullName}</td>
                    <td className="py-2 pr-4 text-slate-700">{user.email}</td>
                    <td className="py-2 pr-4 text-slate-700">{user.role}</td>
                    <td className="py-2">
                      <AdminUserStatusBadge status={user.status} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {users.data.totalElements > users.data.content.length && (
              <p className="mt-3 text-sm text-slate-600">
                <Link to="/admin/users?status=SUSPENDED" className="font-medium text-brand-700 hover:text-brand-900">
                  View all {users.data.totalElements} suspended accounts
                </Link>
              </p>
            )}
          </div>
        ))
      )}
    </div>
  );
}