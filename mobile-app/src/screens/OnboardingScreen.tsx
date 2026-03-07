import React, { useState, useCallback } from "react";
import { View, StyleSheet } from "react-native";
import { LinearGradient } from "expo-linear-gradient";
import AsyncStorage from "@react-native-async-storage/async-storage";

import { WelcomeStep } from "../components/onboarding/WelcomeStep";
import { ProviderSetupStep } from "../components/onboarding/ProviderSetupStep";
import { ModelDownloadStep } from "../components/onboarding/ModelDownloadStep";
import { PermissionsStep } from "../components/onboarding/PermissionsStep";
import { colors } from "../theme/colors";
import { spacing } from "../theme/spacing";

export const ONBOARDING_COMPLETE_KEY = "guappa:onboarding:completed:v1";

const STEP_COUNT = 4;

type Props = {
  onComplete: () => void;
};

export function OnboardingScreen({ onComplete }: Props) {
  const [step, setStep] = useState(0);

  const goNext = useCallback(() => {
    setStep((prev) => Math.min(prev + 1, STEP_COUNT - 1));
  }, []);

  const handleComplete = useCallback(async () => {
    await AsyncStorage.setItem(ONBOARDING_COMPLETE_KEY, "true");
    onComplete();
  }, [onComplete]);

  return (
    <LinearGradient
      colors={[colors.base.spaceBlack, colors.base.midnightBlue]}
      style={styles.container}
    >
      {/* Step indicator dots */}
      <View style={styles.indicator}>
        {Array.from({ length: STEP_COUNT }).map((_, i) => (
          <View
            key={i}
            style={[
              styles.dot,
              i === step ? styles.dotActive : styles.dotInactive,
            ]}
          />
        ))}
      </View>

      {/* Step content */}
      <View style={styles.content}>
        {step === 0 && <WelcomeStep onNext={goNext} />}
        {step === 1 && <ProviderSetupStep onNext={goNext} onSkip={goNext} />}
        {step === 2 && <ModelDownloadStep onNext={goNext} onSkip={goNext} />}
        {step === 3 && <PermissionsStep onComplete={handleComplete} />}
      </View>
    </LinearGradient>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  indicator: {
    flexDirection: "row",
    justifyContent: "center",
    alignItems: "center",
    paddingTop: spacing.xxl + spacing.md,
    gap: spacing.sm,
  },
  dot: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  dotActive: {
    backgroundColor: colors.accent.cyan,
    width: 24,
  },
  dotInactive: {
    backgroundColor: "rgba(255, 255, 255, 0.20)",
  },
  content: {
    flex: 1,
  },
});
