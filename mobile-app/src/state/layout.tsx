import React, { createContext, useContext } from "react";
import { useLayout } from "../hooks/useLayout";

type LayoutContextType = {
  useSidebar: boolean;
  sidebarWidth: number;
  isAutomotive: boolean;
  isTablet: boolean;
  isLandscape: boolean;
  width: number;
  height: number;
};

const LayoutContext = createContext<LayoutContextType>({
  useSidebar: false,
  sidebarWidth: 0,
  isAutomotive: false,
  isTablet: false,
  isLandscape: false,
  width: 0,
  height: 0,
});

export function LayoutProvider({ children }: { children: React.ReactNode }) {
  const layout = useLayout();
  return <LayoutContext.Provider value={layout}>{children}</LayoutContext.Provider>;
}

export function useLayoutContext() {
  return useContext(LayoutContext);
}
