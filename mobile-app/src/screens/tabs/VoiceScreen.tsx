import React from "react";
import { View, Text, StyleSheet } from "react-native";
import { LinearGradient } from "expo-linear-gradient";

import { colors } from "../../theme/colors";

export function VoiceScreen() {
  return (
    <LinearGradient
      colors={[colors.base.spaceBlack, colors.base.midnightBlue]}
      style={styles.container}
      testID="voice-screen"
    >
      <View style={styles.center}>
        <Text style={styles.title}>GUAPPA</Text>
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
    fontSize: 42,
    fontWeight: "bold",
    letterSpacing: 4,
  },
});
