import { useWindowDimensions } from "react-native";

export type LayoutType = "phone" | "tablet" | "automotive";

export function useLayout() {
  const { width, height } = useWindowDimensions();
  const isLandscape = width > height;
  const shortSide = Math.min(width, height);
  const isTablet = shortSide >= 600;
  const useSidebar = isLandscape && width >= 520;
  const sidebarWidth = useSidebar ? 180 : 0;
  const isAutomotive = useSidebar && height <= 600;
  return { isLandscape, isTablet, useSidebar, sidebarWidth, isAutomotive, width, height };
}
