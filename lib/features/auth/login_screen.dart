import 'dart:ui';
import 'package:flutter/material.dart';
import '../../core/app_theme.dart';
import 'auth_repository.dart';
import '../../core/widgets/glass_button.dart';
import 'credits_screen.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final TextEditingController _emailController = TextEditingController();
  final TextEditingController _passwordController = TextEditingController();
  final AuthRepository _authRepository = AuthRepository();
  
  bool _isLoading = false;
  String _loadingMessage = "Sign In";
  bool _obscurePassword = true;

  Future<void> _handleLogin() async {
    final email = _emailController.text.trim();
    final password = _passwordController.text.trim();

    if (email.isEmpty || password.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text("Please enter email and password"),
          backgroundColor: Colors.red,
        ),
      );
      return;
    }

    setState(() {
      _isLoading = true;
      _loadingMessage = "Authenticating...";
    });

    final result = await _authRepository.login(email, password);

    if (mounted) {
      if (result['success']) {
        setState(() {
          _loadingMessage = "Syncing Profile...";
        });
        
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text("Welcome! Role: ${result['role']}"),
            backgroundColor: Colors.green,
            duration: const Duration(seconds: 2),
          ),
        );
        
        Future.delayed(const Duration(milliseconds: 600), () {
          if (mounted) {
            Navigator.of(context).pushReplacementNamed('/dashboard');
          }
        });
      } else {
        setState(() {
          _isLoading = false;
          _loadingMessage = "Sign In";
        });
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(result['message'] ?? "Incorrect credentials, try again"),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Stack(
        children: [
          // 1. RESPONSIVE BACKGROUND
          Positioned.fill(
            child: Image.asset(
              'assets/images/dna_background.jpg',
              fit: BoxFit.cover,
            ),
          ),
          
          SafeArea(
            child: LayoutBuilder(
              builder: (context, constraints) {
                return SingleChildScrollView(
                  child: ConstrainedBox(
                    constraints: BoxConstraints(
                      minHeight: constraints.maxHeight,
                    ),
                    child: IntrinsicHeight(
                      child: Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 24.0),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const SizedBox(height: 16),
                            // 2. HEADER AREA
                            Row(
                              mainAxisAlignment: MainAxisAlignment.spaceBetween,
                              children: [
                                Image.asset(
                                  'assets/images/medretailer_official_logo.png',
                                  width: 140,
                                  height: 50,
                                  fit: BoxFit.contain,
                                ),
                                GestureDetector(
                                  onTap: () {
                                    Navigator.of(context).push(
                                      MaterialPageRoute(
                                        builder: (context) => const CreditsScreen(),
                                      ),
                                    );
                                  },
                                  child: const Icon(
                                    Icons.more_horiz,
                                    size: 40,
                                    color: Colors.grey,
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: 16),
                            Divider(
                              color: Colors.black.withOpacity(0.15),
                              height: 1,
                            ),
                            
                            // 3. CENTER CONTAINER
                            Expanded(
                              child: Center(
                                child: Container(
                                  constraints: const BoxConstraints(maxWidth: 500),
                                  padding: const EdgeInsets.symmetric(vertical: 24.0),
                                  child: Column(
                                    mainAxisSize: MainAxisSize.min,
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      Image.asset(
                                        'assets/images/welcometomedretailers.png',
                                        width: double.infinity,
                                        fit: BoxFit.contain,
                                      ),
                                      const SizedBox(height: 24),
                                      
                                      const Text(
                                        "Email",
                                        style: TextStyle(
                                          fontSize: 16,
                                          fontWeight: FontWeight.w600,
                                          color: Colors.black,
                                        ),
                                      ),
                                      const SizedBox(height: 8),
                                      Container(
                                        height: 50,
                                        decoration: BoxDecoration(
                                          color: Colors.white,
                                          borderRadius: BorderRadius.circular(8),
                                          border: Border.all(
                                            color: Colors.grey.shade300,
                                            width: 1.0,
                                          ),
                                        ),
                                        padding: const EdgeInsets.symmetric(horizontal: 16),
                                        alignment: Alignment.center,
                                        child: TextField(
                                          controller: _emailController,
                                          keyboardType: TextInputType.emailAddress,
                                          style: const TextStyle(
                                            fontFamily: 'monospace',
                                            color: Colors.black,
                                            fontSize: 14,
                                          ),
                                          decoration: const InputDecoration(
                                            border: InputBorder.none,
                                            enabledBorder: InputBorder.none,
                                            focusedBorder: InputBorder.none,
                                            hintText: "Type your email here...",
                                            hintStyle: TextStyle(
                                              fontStyle: FontStyle.italic,
                                              color: Colors.black38,
                                            ),
                                            contentPadding: EdgeInsets.zero,
                                          ),
                                        ),
                                      ),
                                      
                                      const SizedBox(height: 16),
                                      
                                      const Text(
                                        "Password",
                                        style: TextStyle(
                                          fontSize: 16,
                                          fontWeight: FontWeight.w600,
                                          color: Colors.black,
                                        ),
                                      ),
                                      const SizedBox(height: 8),
                                      Container(
                                        height: 50,
                                        decoration: BoxDecoration(
                                          color: Colors.white,
                                          borderRadius: BorderRadius.circular(8),
                                          border: Border.all(
                                            color: Colors.grey.shade300,
                                            width: 1.0,
                                          ),
                                        ),
                                        padding: const EdgeInsets.symmetric(horizontal: 16),
                                        alignment: Alignment.center,
                                        child: TextField(
                                          controller: _passwordController,
                                          obscureText: _obscurePassword,
                                          style: const TextStyle(
                                            fontFamily: 'monospace',
                                            color: Colors.black,
                                            fontSize: 14,
                                          ),
                                          decoration: InputDecoration(
                                            border: InputBorder.none,
                                            enabledBorder: InputBorder.none,
                                            focusedBorder: InputBorder.none,
                                            hintText: "Type your password here...",
                                            hintStyle: const TextStyle(
                                              fontStyle: FontStyle.italic,
                                              color: Colors.black38,
                                            ),
                                            suffixIcon: IconButton(
                                              icon: Icon(
                                                _obscurePassword
                                                    ? Icons.visibility
                                                    : Icons.visibility_off,
                                                color: const Color(0xFF835C9F),
                                              ),
                                              onPressed: () {
                                                setState(() {
                                                  _obscurePassword = !_obscurePassword;
                                                });
                                              },
                                            ),
                                            contentPadding: const EdgeInsets.only(top: 14),
                                          ),
                                        ),
                                      ),
                                      
                                      const SizedBox(height: 24),
                                      
                                      // Glass Interactive Login Button
                                      GlassButton(
                                        onPressed: _isLoading ? null : _handleLogin,
                                        color: AppTheme.primaryPurple,
                                        child: Text(
                                          _loadingMessage,
                                          style: const TextStyle(
                                            color: Colors.white,
                                            fontSize: 18,
                                            fontWeight: FontWeight.bold,
                                            fontFamily: 'monospace',
                                            letterSpacing: 1.2,
                                          ),
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                              ),
                            ),
                            
                            // 4. VERSION INFO
                            Center(
                              child: Padding(
                                padding: const EdgeInsets.only(bottom: 12.0),
                                child: Text(
                                  "Version 2.0.1-beta",
                                  style: TextStyle(
                                    fontSize: 12,
                                    color: AppTheme.primaryPurple.withOpacity(0.8),
                                    fontWeight: FontWeight.w500,
                                  ),
                                ),
                              ),
                            ),
                            
                            Divider(
                              color: Colors.black.withOpacity(0.15),
                              height: 1,
                            ),
                            
                            // 5. FOOTER ICONS
                            Padding(
                              padding: const EdgeInsets.symmetric(vertical: 12.0),
                              child: Row(
                                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                children: [
                                  Image.asset(
                                    'assets/images/medicine_icon.png',
                                    width: 60,
                                    height: 60,
                                    fit: BoxFit.contain,
                                  ),
                                  Image.asset(
                                    'assets/images/medicine_icon.png',
                                    width: 60,
                                    height: 60,
                                    fit: BoxFit.contain,
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}
