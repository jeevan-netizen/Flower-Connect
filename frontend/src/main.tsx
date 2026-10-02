import React from "react";
import { createRoot } from "react-dom/client";
import { MotionConfig } from "framer-motion";
import { QueryClientProvider } from "@tanstack/react-query";
import { queryClient } from "./app/providers/query-client";
import { ThemeProvider } from "./app/providers/theme";
import { AppRouter } from "./app/router";
import "./app/styles/index.css";

createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <ThemeProvider>
      <QueryClientProvider client={queryClient}>
        {/*
          `reducedMotion="user"` makes every transform and layout animation below
          this point honour `prefers-reduced-motion`, without each component
          having to opt out. Opacity and colour animations are kept, so a fade
          is still possible as a non-essential enhancement.
        */}
        <MotionConfig reducedMotion="user">
          <AppRouter />
        </MotionConfig>
      </QueryClientProvider>
    </ThemeProvider>
  </React.StrictMode>,
);
