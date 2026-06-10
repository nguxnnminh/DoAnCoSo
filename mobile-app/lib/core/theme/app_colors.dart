import 'package:flutter/material.dart';

class AppColors {
  // Primary palette — mirrors CSS variables from desktop (Light Theme)
  static const background = Color(0xFFFFFFFF);
  static const backgroundDeep = Color(0xFFFFFFFF);
  static const backgroundDark = Color(0xFFF4F4F5);
  static const surface = Color(0xFFFFFFFF);
  static const surface2 = Color(0xFFFAFAFA);
  static const surface3 = Color(0xFFF4F4F5);

  static const white = Color(0xFF1C1B19); // Remapped white to black for primary buttons & shapes
  static const whiteSoft = Color(0xFF555555);
  static const whiteHover = Color(0xFF71717A);

  static const textPrimary = Color(0xFF1C1B19); // Remapped to black
  static const textMuted = Color(0xFF555555);
  static const textMuted2 = Color(0xFF71717A);
  static const textDim = Color(0xFF8E8E93);
  static const textDisabled = Color(0xFFA1A1AA);

  static const accent = Color(0xFF1C1B19); // Remapped gold to black
  static const accentLight = Color(0xFF555555);
  static const accentText = Color(0xFF1C1B19);
  static const accentSurface = Color(0x0F1C1B19); // 6% opacity black
  static const accentBorder = Color(0xFFE4E4E7);

  static const border = Color(0xFFE4E4E7); // Remapped to soft zinc border
  static const borderDark = Color(0xFFD4D4D8);
  static const borderDeep = Color(0xFFE4E4E7);

  static const success = Color(0xFF4F6B46); // Deep sage green
  static const error = Color(0xFF8A3E34); // Warm red

  static const shimmerBase = Color(0xFFF4F4F5);
  static const shimmerHighlight = Color(0xFFE4E4E7);
}
