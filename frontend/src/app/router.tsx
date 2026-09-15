import { createBrowserRouter, Outlet, Link, RouterProvider } from "react-router-dom";
import { useInitAuth, RequireAuth, RequireUnauth } from "@/features/auth/hooks/useAuth";
import { LoginPage } from "@/features/auth/pages/LoginPage";
import { RegisterPage } from "@/features/auth/pages/RegisterPage";

function Layout() {
  return (
    <div className="min-h-screen flex flex-col">
      <header className="bg-brand-600 text-white">
        <nav className="max-w-7xl mx-auto px-4 py-3 flex items-center justify-between">
          <Link to="/" className="text-xl font-bold">
            FlowerConnect
          </Link>
          <div className="flex gap-4 text-sm">
            <Link to="/browse">Browse</Link>
            <Link to="/cart">Cart</Link>
            <Link to="/orders">Orders</Link>
          </div>
        </nav>
      </header>
      <main className="flex-1">
        <Outlet />
      </main>
      <footer className="bg-slate-100 text-center text-sm py-4">
        &copy; 2026 FlowerConnect
      </footer>
    </div>
  );
}

function HomePage() {
  return (
    <div className="max-w-7xl mx-auto px-4 py-12 text-center">
      <h1 className="text-4xl font-bold text-brand-700">FlowerConnect</h1>
      <p className="mt-3 text-slate-600">Hyperlocal flower marketplace</p>
    </div>
  );
}

function Placeholder({ title }: { title: string }) {
  return (
    <div className="max-w-7xl mx-auto px-4 py-12">
      <h2 className="text-2xl font-semibold">{title}</h2>
      <p className="mt-2 text-slate-600">Phase 0 placeholder page.</p>
    </div>
  );
}

export const router = createBrowserRouter([
  {
    path: "/",
    element: <LayoutWithAuth />,
    children: [
      { index: true, element: <HomePage /> },
      {
        path: "browse",
        element: (
          <RequireAuth>
            <Placeholder title="Browse" />
          </RequireAuth>
        ),
      },
      {
        path: "cart",
        element: (
          <RequireAuth>
            <Placeholder title="Cart" />
          </RequireAuth>
        ),
      },
      {
        path: "orders",
        element: (
          <RequireAuth>
            <Placeholder title="Orders" />
          </RequireAuth>
        ),
      },
      { path: "login", element: <RequireUnauth><LoginPage /></RequireUnauth> },
      { path: "register", element: <RequireUnauth><RegisterPage /></RequireUnauth> },
    ],
  },
]);

function LayoutWithAuth() {
  useInitAuth();
  return <Layout />;
}

export function AppRouter() {
  return <RouterProvider router={router} />;
}
