import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/utils/format.dart';
import '../../../shared/widgets/nova_app_bar.dart';
import '../../../shared/widgets/nova_button.dart';
import '../../../shared/widgets/nova_input.dart';
import '../../cart/providers/cart_provider.dart';
import '../../profile/providers/profile_provider.dart';
import '../providers/checkout_provider.dart';

class CheckoutScreen extends ConsumerStatefulWidget {
  const CheckoutScreen({super.key});

  @override
  ConsumerState<CheckoutScreen> createState() => _CheckoutScreenState();
}

class _CheckoutScreenState extends ConsumerState<CheckoutScreen> {
  final _nameCtrl = TextEditingController();
  final _phoneCtrl = TextEditingController();
  final _addressCtrl = TextEditingController();
  final _noteCtrl = TextEditingController();
  final _couponCtrl = TextEditingController();

  bool _couponsLoaded = false;

  @override
  void initState() {
    super.initState();
    ref.listenManual(profileProvider, (_, next) {
      next.whenData((p) {
        if (_nameCtrl.text.isEmpty) _nameCtrl.text = p.fullName;
        if (_phoneCtrl.text.isEmpty && p.phone != null) _phoneCtrl.text = p.phone!;
        if (_addressCtrl.text.isEmpty && p.address != null) _addressCtrl.text = p.address!;
      });
    }, fireImmediately: true);

    // Tải coupon hệ thống đề xuất (giống web) ngay khi có tổng giỏ hàng.
    ref.listenManual(cartProvider, (_, next) {
      next.whenData((cart) {
        if (!_couponsLoaded && cart.total > 0) {
          _couponsLoaded = true;
          ref.read(checkoutProvider.notifier).loadAvailableCoupons(cart.total);
        }
      });
    }, fireImmediately: true);
  }

  Future<void> _applySuggested(String code) async {
    _couponCtrl.text = code;
    final cart = ref.read(cartProvider).valueOrNull;
    if (cart == null) return;
    await ref.read(checkoutProvider.notifier).validateCoupon(code, cart.total);
  }

  @override
  void dispose() {
    _nameCtrl.dispose();
    _phoneCtrl.dispose();
    _addressCtrl.dispose();
    _noteCtrl.dispose();
    _couponCtrl.dispose();
    super.dispose();
  }

  Future<void> _placeOrder() async {
    final order = await ref.read(checkoutProvider.notifier).placeOrder(
          name: _nameCtrl.text.trim(),
          phone: _phoneCtrl.text.trim(),
          address: _addressCtrl.text.trim(),
          note: _noteCtrl.text.trim(),
        );
    if (order != null && mounted) {
      ref.read(cartProvider.notifier).fetch();
      context.go('/checkout/success/${order.id}');
    }
  }

  Future<void> _validateCoupon() async {
    final cart = ref.read(cartProvider).valueOrNull;
    if (cart == null) return;
    await ref.read(checkoutProvider.notifier).validateCoupon(_couponCtrl.text.trim(), cart.total);
  }

  @override
  Widget build(BuildContext context) {
    final checkout = ref.watch(checkoutProvider);
    final cart = ref.watch(cartProvider).valueOrNull;

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: const NovaAppBar(title: 'Checkout'),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _SectionLabel('Shipping Information'),
            const SizedBox(height: 16),
            NovaInput(controller: _nameCtrl, hint: 'Full Name', textInputAction: TextInputAction.next),
            const SizedBox(height: 20),
            NovaInput(controller: _phoneCtrl, hint: 'Phone Number', keyboardType: TextInputType.phone, textInputAction: TextInputAction.next),
            const SizedBox(height: 20),
            NovaInput(controller: _addressCtrl, hint: 'Shipping Address', textInputAction: TextInputAction.next),
            const SizedBox(height: 20),
            NovaInput(controller: _noteCtrl, hint: 'Order Notes (optional)', textInputAction: TextInputAction.done),

            const SizedBox(height: 32),
            _SectionLabel('Coupon Code'),
            const SizedBox(height: 12),

            // Coupon đề xuất (giống web): danh sách khả dụng, cái đầu = "Recommended".
            if (checkout.availableCoupons.isNotEmpty) ...[
              ...checkout.availableCoupons.asMap().entries.map((e) {
                final i = e.key;
                final c = e.value;
                final selected = checkout.couponCode == c.code && checkout.discount != null;
                return GestureDetector(
                  onTap: () => _applySuggested(c.code),
                  child: Container(
                    margin: const EdgeInsets.only(bottom: 8),
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: AppColors.backgroundDark,
                      border: Border.all(color: selected ? AppColors.accent : AppColors.borderDark),
                    ),
                    child: Row(
                      children: [
                        Icon(selected ? Icons.radio_button_checked : Icons.radio_button_off, size: 18, color: selected ? AppColors.accent : AppColors.textDim),
                        const SizedBox(width: 10),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                children: [
                                  Text(c.discountDisplay, style: const TextStyle(fontFamily: 'BebasNeue', fontSize: 18, letterSpacing: 0.04, color: AppColors.textPrimary)),
                                  const SizedBox(width: 8),
                                  Text(c.code, style: const TextStyle(fontFamily: 'DMSans', fontSize: 11, color: AppColors.textMuted, fontWeight: FontWeight.w600, letterSpacing: 0.1)),
                                  if (i == 0) ...[
                                    const SizedBox(width: 8),
                                    Container(
                                      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                      decoration: BoxDecoration(border: Border.all(color: AppColors.accent.withAlpha(120)), color: AppColors.accent.withAlpha(20)),
                                      child: const Text('RECOMMENDED', style: TextStyle(fontFamily: 'DMSans', fontSize: 7, letterSpacing: 0.2, color: AppColors.accent, fontWeight: FontWeight.w600)),
                                    ),
                                  ],
                                ],
                              ),
                              if (c.minOrderAmount != null)
                                Padding(
                                  padding: const EdgeInsets.only(top: 2),
                                  child: Text('Min order: ${formatPrice(c.minOrderAmount!)}', style: const TextStyle(fontFamily: 'DMSans', fontSize: 10, color: AppColors.textDim)),
                                ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                );
              }),
              const SizedBox(height: 4),
            ],

            Row(
              children: [
                Expanded(child: NovaInput(controller: _couponCtrl, hint: 'Enter coupon code', textInputAction: TextInputAction.done, onSubmitted: _validateCoupon)),
                const SizedBox(width: 12),
                GestureDetector(
                  onTap: _validateCoupon,
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                    color: AppColors.surface2,
                    child: Text('Apply', style: TextStyle(fontFamily: 'DMSans', fontSize: 11, letterSpacing: 0.14, color: AppColors.textMuted)),
                  ),
                ),
              ],
            ),
            AnimatedSize(
              duration: const Duration(milliseconds: 200),
              child: checkout.couponMessage != null
                  ? Padding(
                      padding: const EdgeInsets.only(top: 8),
                      child: Text(checkout.couponMessage!, style: TextStyle(fontFamily: 'DMSans', fontSize: 12, color: checkout.discount != null ? AppColors.success : AppColors.error)),
                    )
                  : const SizedBox.shrink(),
            ),

            const SizedBox(height: 32),
            _SectionLabel('Order Summary'),
            const SizedBox(height: 12),
            if (cart != null) ...[
              ...cart.items.map((item) => Padding(
                    padding: const EdgeInsets.symmetric(vertical: 6),
                    child: Row(
                      children: [
                        Expanded(child: Text('${item.productName} (${item.size}/${item.color}) x${item.quantity}', style: TextStyle(fontFamily: 'DMSans', fontSize: 12, color: AppColors.textMuted))),
                        Text(formatPrice(item.subtotal), style: TextStyle(fontFamily: 'DMSans', fontSize: 12, color: AppColors.textPrimary)),
                      ],
                    ),
                  )),
              const Divider(color: AppColors.borderDark, height: 24),
              if (checkout.discount != null)
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text('Discount', style: TextStyle(fontFamily: 'DMSans', fontSize: 12, color: AppColors.accent)),
                    Text('- ${formatPrice(checkout.discount)}', style: TextStyle(fontFamily: 'DMSans', fontSize: 12, color: AppColors.accent)),
                  ],
                ),
              const SizedBox(height: 8),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text('TOTAL', style: TextStyle(fontFamily: 'DMSans', fontSize: 12, letterSpacing: 0.18, color: AppColors.textMuted2, fontWeight: FontWeight.w500)),
                  Text(formatPrice(cart.total - (checkout.discount ?? 0)), style: TextStyle(fontFamily: 'DMSans', fontSize: 18, color: AppColors.textPrimary, fontWeight: FontWeight.w500)),
                ],
              ),
            ],

            if (checkout.error != null)
              Padding(
                padding: const EdgeInsets.only(top: 16),
                child: Text(checkout.error!, style: TextStyle(fontFamily: 'DMSans', fontSize: 12, color: AppColors.error)),
              ),

            const SizedBox(height: 32),
            NovaPrimaryButton(label: 'Place Order', loading: checkout.loading, onPressed: _placeOrder, height: 52),
            const SizedBox(height: 24),
          ],
        ),
      ),
    );
  }
}

class _SectionLabel extends StatelessWidget {
  final String text;
  const _SectionLabel(this.text);

  @override
  Widget build(BuildContext context) => Text(text.toUpperCase(), style: TextStyle(fontFamily: 'DMSans', fontSize: 9, letterSpacing: 0.4, color: AppColors.textMuted2, fontWeight: FontWeight.w600));
}
