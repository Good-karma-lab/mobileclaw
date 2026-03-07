import React from "react";
import { View, Text, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { GlassButton } from "../glass";
import { colors } from "../../theme/colors";
import { typography } from "../../theme/typography";
import { spacing } from "../../theme/spacing";

type Props = {
  onNext: () => void;
};

const FEATURES: { icon: keyof typeof Ionicons.glyphMap; text: string }[] = [
  { icon: "chatbubbles-outline", text: "Chat naturally with any AI model" },
  { icon: "mic-outline", text: "Control your device with voice" },
  { icon: "globe-outline", text: "Connect to the World Wide Swarm" },
];

export function WelcomeStep({ onNext }: Props) {
  return (
    <View style={styles.container}>
      <View style={styles.hero}>
        <Text style={styles.title}>GUAPPA</Text>
        <Text style={styles.subtitle}>Your AI companion</Text>
      </View>

      <View style={styles.features}>
        {FEATURES.map((feature) => (
          <View key={feature.text} style={styles.featureRow}>
            <Ionicons
              name={feature.icon}
              size={24}
              color={colors.accent.cyan}
              style={styles.featureIcon}
            />
            <Text style={styles.featureText}>{feature.text}</Text>
          </View>
        ))}
      </View>

      <View style={styles.bottom}>
        <GlassButton
          title="Get Started"
          onPress={onNext}
          icon="arrow-forward"
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: "space-between",
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.xxl,
  },
  hero: {
    alignItems: "center",
    marginTop: spacing.xxl,
  },
  title: {
    fontFamily: typography.display.fontFamily,
    fontSize: 48,
    color: colors.accent.cyan,
    letterSpacing: 6,
    marginBottom: spacing.sm,
  },
  subtitle: {
    fontFamily: typography.body.fontFamily,
    fontSize: 18,
    color: colors.text.secondary,
  },
  features: {
    gap: spacing.lg,
  },
  featureRow: {
    flexDirection: "row",
    alignItems: "center",
  },
  featureIcon: {
    marginRight: spacing.md,
    width: 32,
    textAlign: "center",
  },
  featureText: {
    fontFamily: typography.bodyMedium.fontFamily,
    fontSize: 16,
    color: colors.text.primary,
    flex: 1,
  },
  bottom: {
    marginTop: spacing.xl,
  },
});
