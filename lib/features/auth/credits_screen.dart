import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';
import '../../core/app_theme.dart';

class CreditsScreen extends StatelessWidget {
  const CreditsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final List<Map<String, String>> teamMembers = [
      {
        'name': 'Mr. Allen Paul Miole',
        'position': 'PIMS SFE-IT Head',
        'contribution': 'Project Initiator, facilitated system requirements and business logic alignment. Bridged the gap between Sales Force Effectiveness operations and technical system implementation.',
        'imagePath': 'assets/images/placeholder_allen.png',
      },
      {
        'name': 'Mr. Dexter Huinda',
        'position': 'PIMS-IT Manager',
        'contribution': 'Provided project oversight, strategic direction, and resource management. Championed the initiative to ensure the successful delivery and deployment of the application.',
        'imagePath': 'assets/images/placeholder_dexter.png', 
      },
      {
        'name': 'Joshua Tan',
        'position': 'Lead Dev/DevOps',
        'contribution': 'Developer of MedRetailers App. Handled the UI/UX design, and the backend integration with the ERPNext v15 API.',
        'imagePath': 'assets/images/placeholder_joshua.png',
      },
    ];

    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        title: const Text(
          "MedRetailers App",
          style: TextStyle(
            fontWeight: FontWeight.w700,
            letterSpacing: 0.8,
            fontSize: 20,
          ),
        ),
        elevation: 0,
        backgroundColor: Colors.transparent,
        foregroundColor: AppTheme.primaryPurple,
        centerTitle: true,
      ),
      body: SafeArea(
        child: CustomScrollView(
          physics: const BouncingScrollPhysics(),
          slivers: [
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 24.0, vertical: 32.0),
                child: Column(
                  children: [
                    Container(
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        color: AppTheme.primaryPurple.withOpacity(0.06),
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(
                        Icons.auto_awesome_outlined,
                        size: 36,
                        color: AppTheme.primaryPurple,
                      ),
                    ),
                    const SizedBox(height: 20),
                    const Text(
                      "The Teams",
                      style: TextStyle(
                        fontSize: 26,
                        fontWeight: FontWeight.w800,
                        color: Color(0xFF2D2D2D),
                        letterSpacing: 0.5,
                      ),
                    ),
                    const SizedBox(height: 10),
                    const Padding(
                      padding: EdgeInsets.symmetric(horizontal: 16.0),
                      child: Text(
                        "Acknowledging the dedicated minds behind the design, architecture, and deployment of the MedRetailers App.",
                        textAlign: TextAlign.center,
                        style: TextStyle(
                          fontSize: 14,
                          color: Colors.grey,
                          height: 1.5,
                          fontWeight: FontWeight.w400,
                        ),
                      ),
                    ),
                    const SizedBox(height: 16),
                    Container(
                      width: 60,
                      height: 3,
                      decoration: BoxDecoration(
                        color: AppTheme.primaryPurple.withOpacity(0.3),
                        borderRadius: BorderRadius.circular(2),
                      ),
                    ),
                  ],
                ),
              ),
            ),
            SliverPadding(
              padding: const EdgeInsets.symmetric(horizontal: 24.0),
              sliver: SliverList(
                delegate: SliverChildBuilderDelegate(
                  (context, index) {
                    final member = teamMembers[index];
                    return _MinimalistMemberTile(
                      name: member['name']!,
                      position: member['position']!,
                      contribution: member['contribution']!,
                      imagePath: member['imagePath']!,
                      isLast: index == teamMembers.length - 1,
                    );
                  },
                  childCount: teamMembers.length,
                ),
              ),
            ),
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(24.0, 16.0, 24.0, 48.0),
                child: Column(
                  children: [
                    Container(
                      width: 50,
                      height: 1,
                      color: Colors.grey.withOpacity(0.2),
                    ),
                    const SizedBox(height: 32),
                    const Text(
                      "Have a problem with the app?",
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.w600,
                        color: Color(0xFF2D2D2D),
                      ),
                    ),
                    const SizedBox(height: 4),
                    const Text(
                      "Feel free to reach out",
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontSize: 12,
                        color: Colors.grey,
                      ),
                    ),
                    const SizedBox(height: 24),
                    _buildCenteredContactButton(
                      icon: const Icon(Icons.email_outlined, color: AppTheme.primaryPurple, size: 18),
                      label: "Contact/Email Us",
                      value: "jptan@profinsights.biz",
                      onTap: () => _launchURL("mailto:jptan@profinsights.biz"),
                    ),
                    const SizedBox(height: 12),
                    _buildCenteredContactButton(
                      icon: Container(
                        width: 18,
                        height: 18,
                        decoration: BoxDecoration(
                          borderRadius: BorderRadius.circular(4.5),
                          gradient: const RadialGradient(
                            colors: [
                              Color(0xFFFFDD55),
                              Color(0xFFFF543F),
                              Color(0xFFC837AB),
                              Color(0xFF3757D3),
                            ],
                            center: Alignment(-0.6, 0.9),
                            radius: 1.2,
                          ),
                        ),
                        child: Center(
                          child: Container(
                            width: 11,
                            height: 11,
                            decoration: BoxDecoration(
                              border: Border.all(color: Colors.white, width: 1.0),
                              borderRadius: BorderRadius.circular(2.5),
                            ),
                            child: Center(
                              child: Container(
                                width: 3.5,
                                height: 3.5,
                                decoration: BoxDecoration(
                                  border: Border.all(color: Colors.white, width: 1.0),
                                  shape: BoxShape.circle,
                                ),
                              ),
                            ),
                          ),
                        ),
                      ),
                      label: "Follow Us",
                      value: "https://www.instagram.com/pmiicareers",
                      onTap: () => _launchURL("https://www.instagram.com/pmiicareers"),
                    ),
                    const SizedBox(height: 12),
                    _buildCenteredContactButton(
                      icon: const Icon(Icons.facebook, color: Color(0xFF1877F2), size: 20),
                      label: "Visit our page",
                      value: "https://www.facebook.com/pmiimarketing/",
                      onTap: () => _launchURL("https://www.facebook.com/pmiimarketing/"),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _launchURL(String url) async {
    final uri = Uri.parse(url);
    if (await canLaunchUrl(uri)) {
      await launchUrl(uri, mode: LaunchMode.externalApplication);
    }
  }

  Widget _buildCenteredContactButton({
    required Widget icon,
    required String label,
    required String value,
    required VoidCallback onTap,
  }) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(30),
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 10.0, horizontal: 20.0),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(30),
          border: Border.all(color: Colors.black.withOpacity(0.04), width: 1),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.02),
              blurRadius: 8,
              offset: const Offset(0, 2),
            ),
          ],
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            icon,
            const SizedBox(width: 10),
            Text(
              "$label: ",
              style: const TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w500,
                color: Colors.grey,
              ),
            ),
            Flexible(
              child: Text(
                value,
                style: const TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  color: AppTheme.primaryPurple,
                  decoration: TextDecoration.underline,
                ),
                overflow: TextOverflow.ellipsis,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _MinimalistMemberTile extends StatelessWidget {
  final String name;
  final String position;
  final String contribution;
  final String imagePath;
  final bool isLast;

  const _MinimalistMemberTile({
    required this.name,
    required this.position,
    required this.contribution,
    required this.imagePath,
    this.isLast = false,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 24),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(
          color: Colors.black.withOpacity(0.03),
          width: 1,
        ),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.02),
            blurRadius: 10,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(20),
        child: Padding(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(
                    width: 64,
                    height: 64,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      gradient: LinearGradient(
                        colors: [
                          AppTheme.primaryPurple.withOpacity(0.1),
                          AppTheme.accentPurple.withOpacity(0.05),
                        ],
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                      ),
                      border: Border.all(
                        color: AppTheme.primaryPurple.withOpacity(0.15),
                        width: 1.5,
                      ),
                    ),
                    child: ClipOval(
                      child: Image.asset(
                        imagePath,
                        fit: BoxFit.cover,
                        errorBuilder: (context, error, stackTrace) {
                          return Center(
                            child: Icon(
                              Icons.person_outline_rounded,
                              size: 32,
                              color: AppTheme.primaryPurple.withOpacity(0.8),
                            ),
                          );
                        },
                      ),
                    ),
                  ),
                  const SizedBox(width: 18),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          name,
                          style: const TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.w700,
                            color: Color(0xFF2D2D2D),
                            letterSpacing: -0.3,
                          ),
                        ),
                        const SizedBox(height: 5),
                        Text(
                          position,
                          style: const TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.w600,
                            color: AppTheme.primaryPurple,
                            letterSpacing: 0.2,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 20),
              Container(
                height: 1,
                color: Colors.grey.withOpacity(0.08),
              ),
              const SizedBox(height: 16),
              const Text(
                "KEY CONTRIBUTION",
                style: TextStyle(
                  fontSize: 10,
                  fontWeight: FontWeight.w700,
                  color: Colors.grey,
                  letterSpacing: 1.2,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                contribution,
                style: const TextStyle(
                  fontSize: 13.5,
                  color: Color(0xFF555555),
                  height: 1.5,
                  fontWeight: FontWeight.w400,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
