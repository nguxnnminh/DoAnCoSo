import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_fonts/google_fonts.dart';
import 'app_colors.dart';

class AppTheme {
  static ThemeData get dark {
    final base = ThemeData.light(); // Changed base to light theme
    return base.copyWith(
      scaffoldBackgroundColor: AppColors.background,
      primaryColor: AppColors.textPrimary,
      colorScheme: const ColorScheme.light(
        primary: AppColors.textPrimary,
        secondary: AppColors.accent,
        surface: AppColors.surface,
        error: AppColors.error,
        onPrimary: AppColors.background,
        onSecondary: AppColors.background,
        onSurface: AppColors.textPrimary,
      ),
      appBarTheme: AppBarTheme(
        backgroundColor: AppColors.background,
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: true,
        systemOverlayStyle: SystemUiOverlayStyle.dark, // Dark status bar icons for light background
        titleTextStyle: _displayStyle(fontSize: 22, letterSpacing: 0.14),
        iconTheme: const IconThemeData(color: AppColors.textPrimary),
      ),
      textTheme: _buildTextTheme(),
      inputDecorationTheme: _buildInputTheme(),
      elevatedButtonTheme: _buildElevatedButtonTheme(),
      outlinedButtonTheme: _buildOutlinedButtonTheme(),
      dividerTheme: const DividerThemeData(
        color: AppColors.border,
        thickness: 1,
        space: 0,
      ),
      bottomNavigationBarTheme: const BottomNavigationBarThemeData(
        backgroundColor: AppColors.background,
        selectedItemColor: AppColors.textPrimary,
        unselectedItemColor: AppColors.textDim,
        showSelectedLabels: true,
        showUnselectedLabels: true,
        type: BottomNavigationBarType.fixed,
        elevation: 0,
      ),
      snackBarTheme: SnackBarThemeData(
        backgroundColor: AppColors.surface,
        contentTextStyle: _bodyStyle(fontSize: 13),
        shape: const Border(top: BorderSide(color: AppColors.border)),
        behavior: SnackBarBehavior.floating,
      ),
    );
  }

  static TextStyle _displayStyle({double fontSize = 16, double letterSpacing = 0.12}) {
    return GoogleFonts.bebasNeue(
      fontSize: fontSize,
      color: AppColors.textPrimary,
      letterSpacing: letterSpacing,
    );
  }

  static TextStyle _bodyStyle({double fontSize = 14, FontWeight weight = FontWeight.w400}) {
    return GoogleFonts.beVietnamPro(
      fontSize: fontSize,
      color: AppColors.textPrimary,
      fontWeight: weight,
    );
  }

  static TextTheme _buildTextTheme() {
    return TextTheme(
      displayLarge: _displayStyle(fontSize: 64, letterSpacing: 0.04),
      displayMedium: _displayStyle(fontSize: 48, letterSpacing: 0.04),
      displaySmall: _displayStyle(fontSize: 36, letterSpacing: 0.06),
      headlineLarge: _displayStyle(fontSize: 28, letterSpacing: 0.10),
      headlineMedium: _displayStyle(fontSize: 22, letterSpacing: 0.12),
      headlineSmall: _displayStyle(fontSize: 18, letterSpacing: 0.14),
      titleLarge: _bodyStyle(fontSize: 16, weight: FontWeight.w600),
      titleMedium: _bodyStyle(fontSize: 14, weight: FontWeight.w500),
      titleSmall: _bodyStyle(fontSize: 12, weight: FontWeight.w500),
      bodyLarge: _bodyStyle(fontSize: 15),
      bodyMedium: _bodyStyle(fontSize: 13),
      bodySmall: _bodyStyle(fontSize: 11),
      labelLarge: _bodyStyle(fontSize: 12, weight: FontWeight.w500),
      labelMedium: _bodyStyle(fontSize: 11, weight: FontWeight.w500),
      labelSmall: _bodyStyle(fontSize: 10, weight: FontWeight.w500),
    );
  }

  static InputDecorationTheme _buildInputTheme() {
    return InputDecorationTheme(
      filled: false,
      border: const UnderlineInputBorder(
        borderSide: BorderSide(color: AppColors.border),
      ),
      enabledBorder: const UnderlineInputBorder(
        borderSide: BorderSide(color: AppColors.border),
      ),
      focusedBorder: const UnderlineInputBorder(
        borderSide: BorderSide(color: AppColors.textPrimary),
      ),
      errorBorder: const UnderlineInputBorder(
        borderSide: BorderSide(color: AppColors.error),
      ),
      hintStyle: GoogleFonts.beVietnamPro(
        fontSize: 12,
        color: AppColors.textDisabled,
        letterSpacing: 0.1,
      ),
      labelStyle: GoogleFonts.beVietnamPro(
        fontSize: 11,
        color: AppColors.textMuted2,
        letterSpacing: 0.14,
      ),
      contentPadding: const EdgeInsets.symmetric(vertical: 12),
    );
  }

  static ElevatedButtonThemeData _buildElevatedButtonTheme() {
    return ElevatedButtonThemeData(
      style: ElevatedButton.styleFrom(
        backgroundColor: AppColors.white,
        foregroundColor: AppColors.backgroundDeep,
        minimumSize: const Size(double.infinity, 48),
        shape: const RoundedRectangleBorder(),
        elevation: 0,
        textStyle: GoogleFonts.beVietnamPro(
          fontSize: 12,
          fontWeight: FontWeight.w500,
          letterSpacing: 0.22,
        ),
      ),
    );
  }

  static OutlinedButtonThemeData _buildOutlinedButtonTheme() {
    return OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        foregroundColor: AppColors.textMuted,
        side: const BorderSide(color: AppColors.border),
        minimumSize: const Size(double.infinity, 48),
        shape: const RoundedRectangleBorder(),
        textStyle: GoogleFonts.beVietnamPro(
          fontSize: 12,
          fontWeight: FontWeight.w500,
          letterSpacing: 0.16,
        ),
      ),
    );
  }
}
