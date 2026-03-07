import React from "react";
import { View, Text, StyleSheet } from "react-native";
import { LinearGradient } from "expo-linear-gradient";

import { colors } from "../../theme/colors";

export function ConfigScreen() {
  return (
    <LinearGradient
      colors={[colors.base.spaceBlack, colors.base.midnightBlue]}
      style={styles.container}
      testID="config-screen"
    >
      <View style={styles.center}>
        <Text style={styles.title}>Configuration</Text>
      </View>
    </LinearGradient>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  center: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
  },
  title: {
    color: colors.text.primary,
    fontSize: 28,
    fontWeight: "bold",
  },
});
