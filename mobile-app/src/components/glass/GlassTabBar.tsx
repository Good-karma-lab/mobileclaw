import React from "react";
import { View, Text, Pressable, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "../../theme/colors";

interface Tab {
  key: string;
  label: string;
  icon: keyof typeof Ionicons.glyphMap;
}

interface Props {
  tabs: Tab[];
  activeTab: string;
  onTabChange: (key: string) => void;
  accentColor?: string;
}

export function GlassTabBar({ tabs, activeTab, onTabChange, accentColor = colors.accent.cyan }: Props) {
  return (
    <View style={styles.bar}>
      {tabs.map((tab) => {
        const isActive = activeTab === tab.key;
        return (
          <Pressable
            key={tab.key}
            style={[
              styles.tab,
              isActive && { backgroundColor: `${accentColor}12`, borderWidth: 1, borderColor: `${accentColor}25` },
            ]}
            onPress={() => onTabChange(tab.key)}
          >
            <Ionicons name={tab.icon} size={18} color={isActive ? accentColor : colors.text.tertiary} />
            <Text style={[styles.label, isActive && { color: accentColor }]}>{tab.label}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  bar: { flexDirection: "row", paddingHorizontal: 12, gap: 4, marginBottom: 4 },
  tab: {
    flex: 1, flexDirection: "row", alignItems: "center", justifyContent: "center",
    gap: 6, paddingVertical: 10, borderRadius: 12, backgroundColor: "rgba(255,255,255,0.03)",
  },
  label: { color: colors.text.tertiary, fontSize: 12, fontWeight: "600" },
});
