import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'core/api_client.dart';
import 'core/app_theme.dart';
import 'features/auth/auth_repository.dart';
import 'features/auth/login_screen.dart';
import 'features/sales_order/sales_order_list_screen.dart';
import 'features/order_entry/order_entry_screen.dart';
import 'core/widgets/glass_button.dart';
import 'core/widgets/session_manager.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  
  try {
    // Initialize Core API Client
    final apiClient = ApiClient();
    await apiClient.init();
  } catch (e) {
    debugPrint("Failed to initialize API client: $e");
  }
  
  runApp(const MedRetailerApp());
}

class MedRetailerApp extends StatelessWidget {
  const MedRetailerApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      navigatorKey: globalNavigatorKey,
      title: 'MedRetailers',
      theme: AppTheme.lightTheme,
      debugShowCheckedModeBanner: false,
      home: const InitializerScreen(),
      routes: {
        '/login': (context) => const LoginScreen(),
        '/dashboard': (context) => const SalesOrderListScreen(),
        '/order_entry': (context) => const OrderEntryScreen(),
      },
      builder: (context, child) => SessionAndConnectivityManager(child: child!),
    );
  }
}

class InitializerScreen extends StatefulWidget {
  const InitializerScreen({super.key});

  @override
  State<InitializerScreen> createState() => _InitializerScreenState();
}

class _InitializerScreenState extends State<InitializerScreen> {
  bool _isLoading = true;
  String _btnText = "LOGIN";

  @override
  void initState() {
    super.initState();
    _checkLoginStatus();
  }

  Future<void> _checkLoginStatus() async {
    final prefs = await SharedPreferences.getInstance();
    final role = prefs.getString("User_Role");
    
    // Small delay to simulate native splash loading feel
    await Future.delayed(const Duration(seconds: 1));
    
    if (role != null) {
      if (mounted) {
        Navigator.of(context).pushReplacementNamed('/dashboard');
      }
    } else {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
      }
    }
  }

  void _onLoginClick() {
    setState(() {
      _btnText = "Proceeding to Login...";
    });
    
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text("Proceeding to Login..."),
        backgroundColor: AppTheme.primaryPurple,
        duration: Duration(milliseconds: 500),
      ),
    );

    Future.delayed(const Duration(milliseconds: 300), () {
      if (mounted) {
        Navigator.of(context).pushReplacementNamed('/login');
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return const Scaffold(
        backgroundColor: Colors.white,
        body: Center(
          child: CircularProgressIndicator(color: AppTheme.primaryPurple),
        ),
      );
    }

    return Scaffold(
      body: Stack(
        children: [
          // 1. RESPONSIVE BACKGROUND
          Positioned.fill(
            child: Opacity(
              opacity: 0.35,
              child: Image.asset(
                'assets/images/dna_background.jpg',
                fit: BoxFit.cover,
              ),
            ),
          ),
          
          SafeArea(
            child: Column(
              children: [
                // 2. HEADER AREA
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 12.0),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Image.asset(
                        'assets/images/medretailer_official_logo.png',
                        width: 140,
                        height: 50,
                        fit: BoxFit.contain,
                        filterQuality: FilterQuality.high,
                      ),
                      const Icon(
                        Icons.more_horiz,
                        size: 40,
                        color: Colors.grey,
                      ),
                    ],
                  ),
                ),
                
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 24.0),
                  child: Divider(
                    color: Colors.black.withOpacity(0.15),
                    height: 1,
                  ),
                ),
                
                // 3. CENTER BRANDING (Frosted glass container removed, using high-definition vector typography for Booking & Fulfillment)
                Expanded(
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 24.0),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Image.asset(
                          'assets/images/medre_1.png',
                          width: double.infinity,
                          height: 70,
                          fit: BoxFit.contain,
                          filterQuality: FilterQuality.high,
                        ),
                        const SizedBox(height: 16),
                        Image.asset(
                          'assets/images/distribution.png',
                          width: double.infinity,
                          height: 60,
                          fit: BoxFit.contain,
                          filterQuality: FilterQuality.high,
                        ),
                        const SizedBox(height: 16),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Text(
                              "BOOKING",
                              style: GoogleFonts.montserrat(
                                fontSize: 24,
                                fontWeight: FontWeight.w900,
                                letterSpacing: 2.0,
                                color: Colors.black,
                                shadows: [
                                  Shadow(
                                    color: Colors.black.withOpacity(0.12),
                                    blurRadius: 4,
                                    offset: const Offset(0, 2),
                                  ),
                                ],
                              ),
                            ),
                            const SizedBox(width: 16),
                            Container(
                              width: 10,
                              height: 10,
                              decoration: const BoxDecoration(
                                color: Color(0xFF835C9F),
                                shape: BoxShape.circle,
                              ),
                            ),
                            const SizedBox(width: 16),
                            Text(
                              "FULFILLMENT",
                              style: GoogleFonts.montserrat(
                                fontSize: 24,
                                fontWeight: FontWeight.w900,
                                letterSpacing: 2.0,
                                color: Colors.black,
                                shadows: [
                                  Shadow(
                                    color: Colors.black.withOpacity(0.12),
                                    blurRadius: 4,
                                    offset: const Offset(0, 2),
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
                
                // 4. GLASS INTERACTIVE LOGIN BUTTON matching theme color palette
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 48.0, vertical: 16.0),
                  child: GlassButton(
                    onPressed: _onLoginClick,
                    color: AppTheme.primaryPurple,
                    child: Text(
                      _btnText,
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                        fontFamily: 'monospace',
                        letterSpacing: 1.2,
                      ),
                    ),
                  ),
                ),
                
                const SizedBox(height: 12),
                
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 24.0),
                  child: Divider(
                    color: Colors.black.withOpacity(0.15),
                    height: 1,
                  ),
                ),
                
                // 5. FOOTER ELEMENTS
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 24.0, vertical: 12.0),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Image.asset(
                        'assets/images/medicine_icon.png',
                        width: 55,
                        height: 55,
                        fit: BoxFit.contain,
                        filterQuality: FilterQuality.high,
                      ),
                      Image.asset(
                        'assets/images/medicine_icon.png',
                        width: 55,
                        height: 55,
                        fit: BoxFit.contain,
                        filterQuality: FilterQuality.high,
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}


