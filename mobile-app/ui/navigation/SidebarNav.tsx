import React from "react";
import { View, Pressable, Text } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import type { BottomTabBarProps } from "@react-navigation/bottom-tabs";

import { theme } from "../theme";

const ICONS: Record<string, keyof typeof Ionicons.glyphMap> = {
  chat: "chatbubbles",
  activity: "notifications",
  settings: "hardware-chip",
  integrations: "link",
  device: "phone-portrait",
};

const LABELS: Record<string, string> = {
  chat: "Talk",
  activity: "Activity",
  settings: "Settings",
  integrations: "Integrations",
  device: "Status",
};

export function SidebarNav({ state, descriptors, navigation }: BottomTabBarProps) {
  const insets = useSafeAreaInsets();

  return (
    <View
      style={{
        position: "absolute",
        left: 0,
        top: 0,
        bottom: 0,
        width: 180,
        zIndex: 100,
        paddingTop: insets.top + 12,
        paddingBottom: insets.bottom + 12,
        backgroundColor: theme.colors.surface.dock,
        borderRightWidth: 1,
        borderRightColor: theme.colors.stroke.subtle,
      }}
    >
      <View
        style={{
          paddingHorizontal: 16,
          paddingBottom: 12,
          flexDirection: "row",
          alignItems: "center",
          gap: 8,
        }}
      >
        <View
          style={{
            width: 8,
            height: 8,
            borderRadius: 4,
            backgroundColor: theme.colors.base.secondary,
          }}
        />
        <Text
          style={{
            fontFamily: theme.typography.bodyMedium,
            fontSize: 13,
            color: theme.colors.base.textMuted,
            letterSpacing: 0.2,
            textTransform: "uppercase",
          }}
        >
          MobileClaw
        </Text>
      </View>

      {state.routes.map((route, index) => {
        const isFocused = state.index === index;
        const options = descriptors[route.key].options;
        const label =
          options.tabBarLabel !== undefined
            ? options.tabBarLabel
            : options.title !== undefined
              ? options.title
              : route.name;

        const onPress = () => {
          if (!isFocused) navigation.navigate(route.name);
        };

        const onLongPress = () => {
          navigation.emit({ type: "tabLongPress", target: route.key });
        };

        const iconName = ICONS[route.name] ?? "ellipse";

        return (
          <Pressable
            key={route.key}
            onPress={onPress}
            onLongPress={onLongPress}
            testID={`dock-tab-${route.name}`}
            accessibilityRole="button"
            accessibilityState={isFocused ? { selected: true } : {}}
            accessibilityLabel={typeof label === "string" ? label : route.name}
            style={({ pressed }) => [
              {
                flexDirection: "row",
                alignItems: "center",
                gap: 12,
                minHeight: 72,
                marginHorizontal: 8,
                paddingHorizontal: 12,
                borderRadius: 16,
                opacity: pressed ? 0.75 : 1,
                backgroundColor: isFocused ? theme.colors.alpha.userBubbleBg : "transparent",
              },
            ]}
          >
            <Ionicons
              name={iconName}
              size={32}
              color={isFocused ? theme.colors.base.primary : theme.colors.overlay.dockIconIdle}
            />
            <Text
              style={{
                fontFamily: theme.typography.bodyMedium,
                fontSize: 13,
                color: isFocused ? theme.colors.base.primary : theme.colors.overlay.dockIconIdle,
              }}
            >
              {LABELS[route.name] || String(label)}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}
