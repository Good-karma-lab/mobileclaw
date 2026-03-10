import React, { useState, useCallback, useMemo } from "react";
import {
  View,
  Text,
  TextInput,
  Pressable,
  FlatList,
  LayoutAnimation,
  Platform,
  UIManager,
  StyleSheet,
  type ViewStyle,
  type StyleProp,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { colors } from "../../theme/colors";
import { typography } from "../../theme/typography";
import { spacing } from "../../theme/spacing";

if (
  Platform.OS === "android" &&
  UIManager.setLayoutAnimationEnabledExperimental
) {
  UIManager.setLayoutAnimationEnabledExperimental(true);
}

type Option = {
  label: string;
  value: string;
};

type Props = {
  label?: string;
  value: string;
  options: Option[];
  onValueChange: (value: string) => void;
  placeholder?: string;
  style?: StyleProp<ViewStyle>;
  testID?: string;
};

const SEARCH_THRESHOLD = 10; // Show search box when more than 10 options

export function GlassDropdown({
  label,
  value,
  options,
  onValueChange,
  placeholder = "Select...",
  style,
  testID,
}: Props) {
  const [expanded, setExpanded] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");

  const selectedOption = options.find((o) => o.value === value);
  const displayText = selectedOption?.label ?? placeholder;

  const filteredOptions = useMemo(() => {
    if (!searchQuery.trim()) return options;
    const q = searchQuery.toLowerCase();
    return options.filter(
      (o) =>
        o.label.toLowerCase().includes(q) ||
        o.value.toLowerCase().includes(q),
    );
  }, [options, searchQuery]);

  const handleToggle = useCallback(() => {
    LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
    setExpanded((prev) => {
      if (prev) setSearchQuery(""); // Clear search on close
      return !prev;
    });
  }, []);

  const handleSelect = useCallback(
    (optionValue: string) => {
      LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
      onValueChange(optionValue);
      setExpanded(false);
      setSearchQuery("");
    },
    [onValueChange],
  );

  const showSearch = options.length > SEARCH_THRESHOLD;

  const renderItem = useCallback(
    ({ item }: { item: Option }) => {
      const isSelected = item.value === value;
      return (
        <Pressable
          onPress={() => handleSelect(item.value)}
          style={[
            styles.optionItem,
            isSelected && styles.optionItemSelected,
          ]}
        >
          <Text
            style={[
              styles.optionText,
              isSelected && styles.optionTextSelected,
            ]}
            numberOfLines={1}
          >
            {item.label}
          </Text>
          {isSelected && (
            <Ionicons
              name="checkmark"
              size={16}
              color={colors.accent.cyan}
            />
          )}
        </Pressable>
      );
    },
    [value, handleSelect],
  );

  return (
    <View style={[styles.container, style]}>
      {label && <Text style={styles.label}>{label}</Text>}
      <Pressable onPress={handleToggle} style={styles.pill} testID={testID}>
        <Text
          style={[
            styles.pillText,
            !selectedOption && styles.placeholderText,
          ]}
          numberOfLines={1}
        >
          {displayText}
        </Text>
        <Ionicons
          name={expanded ? "chevron-up" : "chevron-down"}
          size={16}
          color={colors.text.secondary}
        />
      </Pressable>
      {expanded && (
        <View style={styles.optionsList}>
          {showSearch && (
            <View style={styles.searchContainer}>
              <Ionicons
                name="search"
                size={16}
                color={colors.text.tertiary}
                style={styles.searchIcon}
              />
              <TextInput
                style={styles.searchInput}
                value={searchQuery}
                onChangeText={setSearchQuery}
                placeholder="Search models..."
                placeholderTextColor={colors.text.tertiary}
                autoCapitalize="none"
                autoCorrect={false}
                testID="model-search-input"
              />
              {searchQuery.length > 0 && (
                <Pressable onPress={() => setSearchQuery("")}>
                  <Ionicons
                    name="close-circle"
                    size={16}
                    color={colors.text.tertiary}
                  />
                </Pressable>
              )}
            </View>
          )}
          <FlatList
            data={filteredOptions}
            renderItem={renderItem}
            keyExtractor={(item) => item.value}
            style={styles.flatList}
            keyboardShouldPersistTaps="handled"
            initialNumToRender={20}
            maxToRenderPerBatch={30}
            windowSize={5}
          />
          {filteredOptions.length === 0 && (
            <Text style={styles.noResults}>No models found</Text>
          )}
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    marginBottom: spacing.sm,
  },
  label: {
    color: colors.text.secondary,
    fontSize: 12,
    fontFamily: typography.bodySemiBold.fontFamily,
    marginBottom: spacing.xs,
    textTransform: "uppercase",
    letterSpacing: 0.5,
  },
  pill: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    backgroundColor: "rgba(10, 20, 28, 0.4)",
    borderWidth: 1,
    borderColor: "rgba(40, 70, 90, 0.12)",
    borderRadius: 12,
    paddingHorizontal: 14,
    paddingVertical: 12,
  },
  pillText: {
    color: colors.text.primary,
    fontSize: 15,
    fontFamily: typography.body.fontFamily,
    flex: 1,
    marginRight: spacing.sm,
  },
  placeholderText: {
    color: colors.text.tertiary,
  },
  optionsList: {
    marginTop: spacing.xs,
    backgroundColor: "rgba(10, 22, 30, 0.4)",
    borderWidth: 1,
    borderColor: "rgba(40, 70, 90, 0.12)",
    borderRadius: 12,
    overflow: "hidden",
    maxHeight: 350,
  },
  searchContainer: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderBottomWidth: 1,
    borderBottomColor: "rgba(40, 70, 90, 0.2)",
  },
  searchIcon: {
    marginRight: 8,
  },
  searchInput: {
    flex: 1,
    color: colors.text.primary,
    fontSize: 14,
    fontFamily: typography.body.fontFamily,
    paddingVertical: 4,
  },
  flatList: {
    maxHeight: 300,
  },
  optionItem: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: 14,
    paddingVertical: 12,
  },
  optionItemSelected: {
    backgroundColor: "rgba(20, 60, 80, 0.15)",
  },
  optionText: {
    color: colors.text.primary,
    fontSize: 15,
    fontFamily: typography.body.fontFamily,
    flex: 1,
  },
  optionTextSelected: {
    color: colors.accent.cyan,
  },
  noResults: {
    color: colors.text.tertiary,
    fontSize: 14,
    fontFamily: typography.body.fontFamily,
    textAlign: "center",
    paddingVertical: 16,
  },
});
