import 'dart:ui';
import 'package:flutter/material.dart';

class GlassButton extends StatefulWidget {
  final VoidCallback? onPressed;
  final Widget child;
  final Color? color;
  final double borderRadius;
  final double height;
  final double? width;
  final bool hasBlur;
  final EdgeInsetsGeometry padding;

  const GlassButton({
    super.key,
    required this.onPressed,
    required this.child,
    this.color,
    this.borderRadius = 12.0,
    this.height = 50.0,
    this.width,
    this.hasBlur = true,
    this.padding = const EdgeInsets.symmetric(horizontal: 16.0),
  });

  @override
  State<GlassButton> createState() => _GlassButtonState();
}

class _GlassButtonState extends State<GlassButton> {
  bool _isPressed = false;

  @override
  Widget build(BuildContext context) {
    final themeColor = widget.color ?? const Color(0xFF835C9F);
    
    return GestureDetector(
      onTapDown: widget.onPressed == null ? null : (_) => setState(() => _isPressed = true),
      onTapUp: widget.onPressed == null ? null : (_) {
        setState(() => _isPressed = false);
        widget.onPressed!();
      },
      onTapCancel: widget.onPressed == null ? null : () => setState(() => _isPressed = false),
      child: AnimatedScale(
        scale: _isPressed ? 0.95 : 1.0,
        duration: const Duration(milliseconds: 100),
        child: AnimatedOpacity(
          opacity: widget.onPressed == null ? 0.5 : (_isPressed ? 0.8 : 1.0),
          duration: const Duration(milliseconds: 100),
          child: Container(
            height: widget.height,
            width: widget.width,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(widget.borderRadius),
              boxShadow: [
                BoxShadow(
                  color: themeColor.withOpacity(_isPressed ? 0.15 : 0.25),
                  blurRadius: _isPressed ? 6.0 : 12.0,
                  offset: const Offset(0, 4),
                ),
              ],
            ),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(widget.borderRadius),
              child: BackdropFilter(
                filter: ImageFilter.blur(
                  sigmaX: widget.hasBlur ? 15.0 : 0.0,
                  sigmaY: widget.hasBlur ? 15.0 : 0.0,
                ),
                child: Container(
                  padding: widget.padding,
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(widget.borderRadius),
                    border: Border.all(
                      color: Colors.white.withOpacity(0.35),
                      width: 1.2,
                    ),
                    gradient: LinearGradient(
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                      colors: [
                        themeColor.withOpacity(0.85),
                        themeColor.withOpacity(0.55),
                      ],
                    ),
                  ),
                  child: Center(child: widget.child),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
