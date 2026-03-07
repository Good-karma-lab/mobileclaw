import React, { useEffect, useRef } from "react";
import { View, Text, Pressable, Animated, Modal, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "../../theme/colors";

interface Props {
  visible: boolean;
  onClose: () => void;
  title?: string;
  children: React.ReactNode;
}

export function GlassModal({ visible, onClose, title, children }: Props) {
  const fadeAnim = useRef(new Animated.Value(0)).current;
  const scaleAnim = useRef(new Animated.Value(0.9)).current;

  useEffect(() => {
    if (visible) {
      Animated.parallel([
        Animated.timing(fadeAnim, { toValue: 1, duration: 200, useNativeDriver: true }),
        Animated.spring(scaleAnim, { toValue: 1, tension: 80, friction: 10, useNativeDriver: true }),
      ]).start();
    }
  }, [visible, fadeAnim, scaleAnim]);

  return (
    <Modal visible={visible} transparent animationType="none" onRequestClose={onClose}>
      <Animated.View style={[styles.overlay, { opacity: fadeAnim }]}>
        <Pressable style={styles.backdrop} onPress={onClose} />
        <Animated.View style={[styles.content, { transform: [{ scale: scaleAnim }] }]}>
          <View style={styles.header}>
            {title && <Text style={styles.title}>{title}</Text>}
            <Pressable onPress={onClose} style={styles.closeButton}>
              <Ionicons name="close" size={22} color={colors.text.secondary} />
            </Pressable>
          </View>
          {children}
        </Animated.View>
      </Animated.View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: { flex: 1, justifyContent: "center", alignItems: "center" },
  backdrop: { ...StyleSheet.absoluteFillObject, backgroundColor: "rgba(0,0,0,0.6)" },
  content: {
    width: "88%",
    maxHeight: "80%",
    backgroundColor: "rgba(15, 15, 30, 0.95)",
    borderRadius: 24,
    borderWidth: 1,
    borderColor: "rgba(255,255,255,0.1)",
    padding: 20,
  },
  header: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 16,
  },
  title: { color: colors.text.primary, fontSize: 18, fontWeight: "700", flex: 1 },
  closeButton: {
    width: 32, height: 32, borderRadius: 16,
    backgroundColor: "rgba(255,255,255,0.05)",
    alignItems: "center", justifyContent: "center",
  },
});
