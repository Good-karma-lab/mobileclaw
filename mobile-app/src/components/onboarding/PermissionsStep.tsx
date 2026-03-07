import React, { useState, useCallback } from "react";
import {
  View,
  Text,
  ScrollView,
  Platform,
  PermissionsAndroid,
  StyleSheet,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { GlassCard, GlassButton } from "../glass";
import { colors } from "../../theme/colors";
import { typography } from "../../theme/typography";
import { spacing } from "../../theme/spacing";

type Props = {
  onComplete: () => void;
};

type PermissionItem = {
  key: string;
  icon: keyof typeof Ionicons.glyphMap;
  name: string;
  reason: string;
  androidPermission?: string;
};

type PermissionGroup = {
  label: string;
  dotColor: string;
  items: PermissionItem[];
};

const PERMISSION_GROUPS: PermissionGroup[] = [
  {
    label: "Essential",
    dotColor: colors.semantic.success,
    items: [
      {
        key: "notifications",
        icon: "notifications-outline",
        name: "Notifications",
        reason: "Stay updated with agent responses",
        androidPermission: "android.permission.POST_NOTIFICATIONS",
      },
    ],
  },
  {
    label: "Recommended",
    dotColor: colors.semantic.warning,
    items: [
      {
        key: "microphone",
        icon: "mic-outline",
        name: "Microphone",
        reason: "Voice commands and dictation",
        androidPermission: PermissionsAndroid.PERMISSIONS.RECORD_AUDIO,
      },
      {
        key: "contacts",
        icon: "people-outline",
        name: "Contacts",
        reason: "Find and call contacts by name",
        androidPermission: PermissionsAndroid.PERMISSIONS.READ_CONTACTS,
      },
      {
        key: "calendar",
        icon: "calendar-outline",
        name: "Calendar",
        reason: "Create and manage events",
        androidPermission: PermissionsAndroid.PERMISSIONS.READ_CALENDAR,
      },
    ],
  },
  {
    label: "Optional",
    dotColor: colors.text.tertiary,
    items: [
      {
        key: "camera",
        icon: "camera-outline",
        name: "Camera",
        reason: "Photo capture and QR scanning",
        androidPermission: PermissionsAndroid.PERMISSIONS.CAMERA,
      },
      {
        key: "sms",
        icon: "chatbox-outline",
        name: "SMS",
        reason: "Send and read text messages",
        androidPermission: PermissionsAndroid.PERMISSIONS.READ_SMS,
      },
      {
        key: "callLog",
        icon: "call-outline",
        name: "Call Log",
        reason: "View recent call history",
        androidPermission: PermissionsAndroid.PERMISSIONS.READ_CALL_LOG,
      },
      {
        key: "location",
        icon: "location-outline",
        name: "Location",
        reason: "Location-aware commands",
        androidPermission:
          PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION,
      },
    ],
  },
];

export function PermissionsStep({ onComplete }: Props) {
  const [granted, setGranted] = useState<Record<string, boolean>>({});

  const requestPermission = useCallback(
    async (item: PermissionItem) => {
      if (Platform.OS !== "android" || !item.androidPermission) {
        setGranted((prev) => ({ ...prev, [item.key]: true }));
        return;
      }

      try {
        const result = await PermissionsAndroid.request(
          item.androidPermission as any,
          {
            title: `${item.name} Permission`,
            message: item.reason,
            buttonPositive: "Grant",
            buttonNegative: "Deny",
          },
        );
        const isGranted = result === PermissionsAndroid.RESULTS.GRANTED;
        setGranted((prev) => ({ ...prev, [item.key]: isGranted }));
      } catch {
        // Permission request failed — mark as not granted
      }
    },
    [],
  );

  const handleGrantAllEssential = useCallback(async () => {
    const essential = PERMISSION_GROUPS[0].items;
    for (const item of essential) {
      await requestPermission(item);
    }
  }, [requestPermission]);

  return (
    <ScrollView
      style={styles.scroll}
      contentContainerStyle={styles.container}
    >
      <Text style={styles.heading}>Permissions GUAPPA needs</Text>

      <GlassButton
        title="Grant All Essential"
        onPress={handleGrantAllEssential}
        icon="shield-checkmark-outline"
        style={styles.grantAllButton}
      />

      {PERMISSION_GROUPS.map((group) => (
        <View key={group.label} style={styles.group}>
          <View style={styles.groupHeader}>
            <View
              style={[styles.dot, { backgroundColor: group.dotColor }]}
            />
            <Text style={styles.groupLabel}>{group.label}</Text>
          </View>

          {group.items.map((item) => {
            const isGranted = granted[item.key] === true;

            return (
              <GlassCard key={item.key} style={styles.permCard}>
                <View style={styles.permRow}>
                  <Ionicons
                    name={item.icon}
                    size={22}
                    color={
                      isGranted
                        ? colors.semantic.success
                        : colors.text.secondary
                    }
                    style={styles.permIcon}
                  />
                  <View style={styles.permTextWrap}>
                    <Text style={styles.permName}>{item.name}</Text>
                    <Text style={styles.permReason}>{item.reason}</Text>
                  </View>
                  {isGranted ? (
                    <Ionicons
                      name="checkmark-circle"
                      size={24}
                      color={colors.semantic.success}
                    />
                  ) : (
                    <GlassButton
                      title="Grant"
                      onPress={() => requestPermission(item)}
                      variant="secondary"
                      style={styles.grantBtn}
                    />
                  )}
                </View>
              </GlassCard>
            );
          })}
        </View>
      ))}

      <GlassButton
        title="Continue"
        onPress={onComplete}
        icon="arrow-forward"
        style={styles.continueButton}
      />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scroll: {
    flex: 1,
  },
  container: {
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.xxl,
    paddingBottom: spacing.xxl + spacing.xl,
  },
  heading: {
    fontFamily: typography.display.fontFamily,
    fontSize: 22,
    color: colors.text.primary,
    textAlign: "center",
    marginBottom: spacing.lg,
  },
  grantAllButton: {
    marginBottom: spacing.lg,
  },
  group: {
    marginBottom: spacing.lg,
  },
  groupHeader: {
    flexDirection: "row",
    alignItems: "center",
    marginBottom: spacing.sm,
  },
  dot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    marginRight: spacing.sm,
  },
  groupLabel: {
    fontFamily: typography.bodySemiBold.fontFamily,
    fontSize: 13,
    color: colors.text.secondary,
    textTransform: "uppercase",
    letterSpacing: 0.5,
  },
  permCard: {
    marginBottom: spacing.sm,
  },
  permRow: {
    flexDirection: "row",
    alignItems: "center",
  },
  permIcon: {
    marginRight: spacing.md,
    width: 28,
    textAlign: "center",
  },
  permTextWrap: {
    flex: 1,
    marginRight: spacing.sm,
  },
  permName: {
    fontFamily: typography.bodySemiBold.fontFamily,
    fontSize: 15,
    color: colors.text.primary,
  },
  permReason: {
    fontFamily: typography.body.fontFamily,
    fontSize: 12,
    color: colors.text.tertiary,
    marginTop: 2,
  },
  grantBtn: {
    paddingVertical: 8,
    paddingHorizontal: spacing.md,
    minHeight: 36,
  },
  continueButton: {
    marginTop: spacing.md,
  },
});
