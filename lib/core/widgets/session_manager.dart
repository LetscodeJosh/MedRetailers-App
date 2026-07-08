import 'dart:async';
import 'dart:io';
import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../app_theme.dart';

// Global navigator key to trigger redirects from outside of BuildContext if needed
final GlobalKey<NavigatorState> globalNavigatorKey = GlobalKey<NavigatorState>();

class SessionAndConnectivityManager extends StatefulWidget {
  final Widget child;
  const SessionAndConnectivityManager({super.key, required this.child});

  @override
  State<SessionAndConnectivityManager> createState() => _SessionAndConnectivityManagerState();
}

class _SessionAndConnectivityManagerState extends State<SessionAndConnectivityManager> {
  Timer? _monitoringTimer;
  Timer? _countdownTimer;
  
  DateTime? _lastActivityTime;
  bool _isLoggedIn = false;
  bool _showNoInternetOverlay = false;
  int _countdown = 5;
  bool _checkingInternet = false;

  @override
  void initState() {
    super.initState();
    // Start periodic background checks
    _monitoringTimer = Timer.periodic(const Duration(seconds: 3), (_) => _performMonitoringCheck());
  }

  @override
  void dispose() {
    _monitoringTimer?.cancel();
    _countdownTimer?.cancel();
    super.dispose();
  }

  void _resetInactivityTimer() {
    if (_isLoggedIn) {
      _lastActivityTime = DateTime.now();
    }
  }

  Future<void> _performMonitoringCheck() async {
    final prefs = await SharedPreferences.getInstance();
    final role = prefs.getString("User_Role");
    final currentlyLoggedIn = role != null;

    if (currentlyLoggedIn != _isLoggedIn) {
      setState(() {
        _isLoggedIn = currentlyLoggedIn;
        if (_isLoggedIn) {
          _lastActivityTime = DateTime.now();
        } else {
          _lastActivityTime = null;
          _showNoInternetOverlay = false;
          _countdownTimer?.cancel();
        }
      });
    }

    if (!_isLoggedIn) return;

    // 1. Inactivity Check (3 Minutes Timeout)
    if (_lastActivityTime != null) {
      final diff = DateTime.now().difference(_lastActivityTime!);
      if (diff.inSeconds >= 180) { // 3 Minutes = 180 seconds
        _logout("Session Expired", "You have been logged out due to 3 minutes of inactivity.");
        return;
      }
    }

    // 2. Connectivity Check (Skip if already checking or count down is active)
    if (!_checkingInternet && !_showNoInternetOverlay) {
      _checkingInternet = true;
      final hasInternet = await _checkInternetConnection();
      _checkingInternet = false;

      if (!hasInternet && mounted) {
        _startInternetGracePeriod();
      }
    }
  }

  Future<bool> _checkInternetConnection() async {
    try {
      final result = await InternetAddress.lookup('google.com').timeout(const Duration(seconds: 2));
      return result.isNotEmpty && result[0].rawAddress.isNotEmpty;
    } catch (_) {
      try {
        // Fallback check to public DNS server
        final socket = await Socket.connect('8.8.8.8', 53, timeout: const Duration(seconds: 2));
        await socket.close();
        return true;
      } catch (_) {
        return false;
      }
    }
  }

  void _startInternetGracePeriod() {
    setState(() {
      _showNoInternetOverlay = true;
      _countdown = 5;
    });

    _countdownTimer?.cancel();
    _countdownTimer = Timer.periodic(const Duration(seconds: 1), (timer) async {
      if (!mounted || !_isLoggedIn) {
        timer.cancel();
        return;
      }

      // Check connection again on every countdown tick
      final hasInternet = await _checkInternetConnection();
      if (hasInternet && mounted) {
        timer.cancel();
        setState(() {
          _showNoInternetOverlay = false;
        });
        _resetInactivityTimer();
        return;
      }

      if (mounted) {
        setState(() {
          if (_countdown > 1) {
            _countdown--;
          } else {
            timer.cancel();
            _logout("Connection Lost", "No internet connection detected.");
          }
        });
      }
    });
  }

  Future<void> _logout(String reason, String message) async {
    _countdownTimer?.cancel();
    final prefs = await SharedPreferences.getInstance();
    await prefs.clear();

    if (mounted) {
      setState(() {
        _isLoggedIn = false;
        _showNoInternetOverlay = false;
        _lastActivityTime = null;
      });

      // Show notification to user
      ScaffoldMessenger.of(globalNavigatorKey.currentContext ?? context).showSnackBar(
        SnackBar(
          content: Text("$reason: $message"),
          backgroundColor: Colors.redAccent,
          duration: const Duration(seconds: 4),
        ),
      );

      // Redirect to login page and clear navigation history
      globalNavigatorKey.currentState?.pushNamedAndRemoveUntil('/login', (route) => false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Listener(
      onPointerDown: (_) => _resetInactivityTimer(),
      onPointerMove: (_) => _resetInactivityTimer(),
      onPointerHover: (_) => _resetInactivityTimer(),
      onPointerSignal: (_) => _resetInactivityTimer(),
      child: Stack(
        children: [
          widget.child,
          if (_showNoInternetOverlay)
            Positioned.fill(
              child: BackdropFilter(
                filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
                child: Container(
                  color: Colors.black.withOpacity(0.55),
                  child: Center(
                    child: Container(
                      margin: const EdgeInsets.symmetric(horizontal: 32),
                      padding: const EdgeInsets.all(24),
                      decoration: BoxDecoration(
                        color: Colors.white.withOpacity(0.9),
                        borderRadius: BorderRadius.circular(20),
                        border: Border.all(color: Colors.white.withOpacity(0.4), width: 1.5),
                        boxShadow: [
                          BoxShadow(
                            color: Colors.black.withOpacity(0.2),
                            blurRadius: 20,
                            offset: const Offset(0, 10),
                          )
                        ],
                      ),
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Icon(Icons.wifi_off, size: 64, color: AppTheme.primaryPurple),
                          const SizedBox(height: 16),
                          const Text(
                            "No Internet Connection",
                            style: TextStyle(
                              fontSize: 20,
                              fontWeight: FontWeight.bold,
                              color: Colors.black87,
                              decoration: TextDecoration.none,
                            ),
                          ),
                          const SizedBox(height: 12),
                          Text(
                            "Connection lost. You will be logged out in $_countdown seconds if your connection doesn't return.",
                            textAlign: TextAlign.center,
                            style: const TextStyle(
                              fontSize: 14,
                              color: Colors.black54,
                              fontWeight: FontWeight.normal,
                              decoration: TextDecoration.none,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}
