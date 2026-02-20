import React from "react";
import { View, type ViewProps } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { theme } from "../theme";
import { HoloBackdrop } from "./HoloBackdrop";
import { useLayoutContext } from "../../src/state/layout";

export function Screen({ children, style, ...props }: ViewProps) {
  const { sidebarWidth } = useLayoutContext();
  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: theme.colors.base.background }}>
      <View style={{ flex: 1, paddingLeft: sidebarWidth }}>
        <HoloBackdrop subtle />
        <View {...props} style={[{ flex: 1 }, style]}>
          {children}
        </View>
      </View>
    </SafeAreaView>
  );
}
