import React from "react";
import type { BottomTabBarProps } from "@react-navigation/bottom-tabs";

import { FloatingDock } from "./FloatingDock";
import { SidebarNav } from "./SidebarNav";
import { useLayoutContext } from "../../src/state/layout";

export function NavigationShell(props: BottomTabBarProps) {
  const { useSidebar } = useLayoutContext();
  if (useSidebar) return <SidebarNav {...props} />;
  return <FloatingDock {...props} />;
}
