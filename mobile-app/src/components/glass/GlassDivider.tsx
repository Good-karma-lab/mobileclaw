import React from "react";
import { View, StyleSheet, type ViewStyle, type StyleProp } from "react-native";

import { colors } from "../../theme/colors";
import { spacing } from "../../theme/spacing";

type Props = {
  style?: StyleProp<ViewStyle>;
  /** Vertical spacing above and below the divider */
  vertical?: number;
  /** Override opacity (0-1) */
  opacity?: number;
  /** Orientation */
  direction?: "horizontal" | "vertical";
};

export function GlassDivider({
  style,
  vertical,
  opacity = 0.15,
  direction = "horizontal",
}: Props) {
  const isVertical = direction === "vertical";

  return (
    <View
      style={[
        isVertical ? styles.vertical : styles.horizontal,
        {
          borderColor: `rgba(60, 90, 110, ${opacity})`,
          ...(vertical !== undefined
            ? isVertical
              ? { marginHorizontal: vertical }
              : { marginVertical: vertical }
            : {}),
        },
        style,
      ]}
    />
  );
}

const styles = StyleSheet.create({
  horizontal: {
    height: 1,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderColor: colors.glass.borderSubtle,
    marginVertical: spacing.sm,
    alignSelf: "stretch",
  },
  vertical: {
    width: 1,
    borderRightWidth: StyleSheet.hairlineWidth,
    borderColor: colors.glass.borderSubtle,
    marginHorizontal: spacing.sm,
    alignSelf: "stretch",
  },
});
