import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/network/api_client.dart';
import '../../../models/order.dart';

/// Coupon được hệ thống đề xuất (đã lọc khả dụng cho tổng đơn) — giống web.
class CouponSuggestion {
  final String code;
  final String? description;
  final String discountDisplay; // "20%" hoặc "100,000₫"
  final double? minOrderAmount;
  final String? expiryDate;

  const CouponSuggestion({
    required this.code,
    this.description,
    required this.discountDisplay,
    this.minOrderAmount,
    this.expiryDate,
  });

  factory CouponSuggestion.fromJson(Map<String, dynamic> j) => CouponSuggestion(
        code: j['code'] as String,
        description: j['description'] as String?,
        discountDisplay: (j['discountDisplay'] as String?) ?? '',
        minOrderAmount: (j['minOrderAmount'] as num?)?.toDouble(),
        expiryDate: j['expiryDate'] as String?,
      );
}

class CheckoutState {
  final bool loading;
  final String? error;
  final Order? completedOrder;
  final String couponCode;
  final double? discount;
  final String? couponMessage;
  final List<CouponSuggestion> availableCoupons;

  const CheckoutState({
    this.loading = false,
    this.error,
    this.completedOrder,
    this.couponCode = '',
    this.discount,
    this.couponMessage,
    this.availableCoupons = const [],
  });

  CheckoutState copyWith({
    bool? loading,
    String? error,
    Order? completedOrder,
    String? couponCode,
    double? discount,
    String? couponMessage,
    List<CouponSuggestion>? availableCoupons,
  }) =>
      CheckoutState(
        loading: loading ?? this.loading,
        error: error,
        completedOrder: completedOrder ?? this.completedOrder,
        couponCode: couponCode ?? this.couponCode,
        discount: discount ?? this.discount,
        couponMessage: couponMessage,
        availableCoupons: availableCoupons ?? this.availableCoupons,
      );
}

class CheckoutNotifier extends StateNotifier<CheckoutState> {
  final ApiClient _api;

  CheckoutNotifier(this._api) : super(const CheckoutState());

  /// Tải danh sách coupon hệ thống đề xuất cho tổng đơn hiện tại (giống web).
  Future<void> loadAvailableCoupons(double orderTotal) async {
    try {
      final res = await _api.dio.get('/api/coupons/available', queryParameters: {'orderTotal': orderTotal});
      final list = (res.data as List)
          .map((e) => CouponSuggestion.fromJson(e as Map<String, dynamic>))
          .toList();
      state = state.copyWith(availableCoupons: list);
    } on DioException {
      state = state.copyWith(availableCoupons: const []);
    }
  }

  Future<bool> validateCoupon(String code, double orderTotal) async {
    try {
      final res = await _api.dio.post('/api/coupons/validate', data: {'code': code, 'orderTotal': orderTotal});
      final data = res.data as Map<String, dynamic>;
      if (data['valid'] == true) {
        state = state.copyWith(
          couponCode: code,
          discount: (data['savedAmount'] as num?)?.toDouble(),
          couponMessage: data['message'] as String?,
        );
        return true;
      } else {
        state = state.copyWith(couponMessage: data['message'] as String?);
        return false;
      }
    } on DioException {
      state = state.copyWith(couponMessage: 'Invalid coupon code');
      return false;
    }
  }

  Future<Order?> placeOrder({
    required String name,
    required String phone,
    required String address,
    String? note,
  }) async {
    state = state.copyWith(loading: true, error: null);
    try {
      final res = await _api.dio.post('/api/orders/checkout', data: {
        'customerName': name,
        'phone': phone,
        'address': address,
        if (state.couponCode.isNotEmpty) 'couponCode': state.couponCode,
        if (note != null && note.isNotEmpty) 'note': note,
      });
      final order = Order.fromJson(res.data as Map<String, dynamic>);
      state = state.copyWith(loading: false, completedOrder: order);
      return order;
    } on DioException catch (e) {
      final data = e.response?.data;
      final msg = (data is Map ? data['message'] : null) as String? ?? 'Order failed. Please try again.';
      state = state.copyWith(loading: false, error: msg);
      return null;
    }
  }

  void reset() => state = const CheckoutState();
}

final checkoutProvider = StateNotifierProvider<CheckoutNotifier, CheckoutState>((ref) {
  return CheckoutNotifier(ref.read(apiClientProvider));
});
