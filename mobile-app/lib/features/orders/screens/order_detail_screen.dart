import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:image_picker/image_picker.dart';
import '../../../core/network/api_client.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/utils/format.dart';
import '../../../models/order.dart';
import '../../../shared/widgets/nova_app_bar.dart';
import '../../../shared/widgets/nova_button.dart';
import '../providers/orders_provider.dart';

class OrderDetailScreen extends ConsumerWidget {
  final int orderId;
  const OrderDetailScreen({super.key, required this.orderId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final ordersAsync = ref.watch(myOrdersProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: NovaAppBar(title: 'Order #$orderId'),
      body: ordersAsync.when(
        data: (orders) {
          final order = orders.where((o) => o.id == orderId).firstOrNull;
          if (order == null) {
            return const Center(child: Text('Order not found', style: TextStyle(fontFamily: 'DMSans', color: AppColors.textMuted2)));
          }
          return _Body(order: order, ref: ref);
        },
        loading: () => const Center(child: CircularProgressIndicator(color: AppColors.textMuted, strokeWidth: 2)),
        error: (_, _) => const Center(child: Text('Error loading order', style: TextStyle(fontFamily: 'DMSans', color: AppColors.error))),
      ),
    );
  }
}

class _Body extends StatefulWidget {
  final Order order;
  final WidgetRef ref;
  const _Body({required this.order, required this.ref});

  @override
  State<_Body> createState() => _BodyState();
}

class _BodyState extends State<_Body> {
  bool _cancelling = false;
  bool _showCancelForm = false;
  final _cancelReasonCtrl = TextEditingController();

  // Status labels/colors — mirrors desktop
  static const _statusColors = {
    'PENDING': AppColors.textPrimary,
    'PROCESSING': AppColors.textPrimary,
    'SHIPPED': AppColors.textPrimary,
    'DELIVERED': AppColors.textPrimary,
    'COMPLETED': AppColors.textPrimary,
    'CANCELLED': AppColors.textMuted2,
    'CANCEL_REQUESTED': AppColors.textPrimary,
  };
  static const _statusLabels = {
    'PENDING': 'Pending',
    'PROCESSING': 'Processing',
    'SHIPPED': 'Shipped',
    'DELIVERED': 'Delivered',
    'COMPLETED': 'Completed',
    'CANCELLED': 'Cancelled',
    'CANCEL_REQUESTED': 'Cancellation Requested',
  };

  @override
  void dispose() {
    _cancelReasonCtrl.dispose();
    super.dispose();
  }

  Future<void> _cancel() async {
    setState(() => _cancelling = true);
    try {
      await widget.ref.read(apiClientProvider).dio.post('/api/orders/${widget.order.id}/cancel');
      widget.ref.invalidate(myOrdersProvider);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: const Row(children: [
              Icon(Icons.check_circle_outline, color: AppColors.success, size: 18),
              SizedBox(width: 10),
              Text('Order cancelled successfully', style: TextStyle(fontFamily: 'DMSans', fontSize: 12, letterSpacing: 0.08)),
            ]),
            backgroundColor: AppColors.surface,
            behavior: SnackBarBehavior.floating,
            duration: const Duration(seconds: 3),
          ),
        );
        await Future.delayed(const Duration(milliseconds: 800));
        if (mounted) {
          final router = GoRouter.of(context);
          if (router.canPop()) { router.pop(); } else { router.go('/orders'); }
        }
      }
    } catch (_) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Could not cancel order')));
    } finally {
      if (mounted) setState(() => _cancelling = false);
    }
  }

  Future<void> _requestCancel() async {
    final reason = _cancelReasonCtrl.text.trim();
    setState(() => _cancelling = true);
    try {
      await widget.ref.read(apiClientProvider).dio.post(
        '/api/orders/${widget.order.id}/cancel-request',
        queryParameters: {'reason': reason.isEmpty ? 'No reason provided' : reason},
      );
      widget.ref.invalidate(myOrdersProvider);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Cancellation requested')));
        final router = GoRouter.of(context);
        if (router.canPop()) { router.pop(); } else { router.go('/orders'); }
      }
    } catch (_) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Could not request cancellation')));
    } finally {
      if (mounted) setState(() => _cancelling = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final order = widget.order;
    final status = order.status;
    final statusColor = _statusColors[status] ?? AppColors.textMuted;
    final statusLabel = _statusLabels[status] ?? status;

    return SingleChildScrollView(
      padding: const EdgeInsets.all(20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Status badge
          Row(
            children: [
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 5),
                decoration: BoxDecoration(border: Border.all(color: statusColor.withAlpha(100)), color: statusColor.withAlpha(25)),
                child: Text(statusLabel.toUpperCase(), style: TextStyle(fontFamily: 'DMSans', fontSize: 9, letterSpacing: 0.2, color: statusColor, fontWeight: FontWeight.w600)),
              ),
            ],
          ),
          const SizedBox(height: 16),

          _Row('Date', formatDateTime(order.createdAt)),
          _Row('Ship to', order.address),
          _Row('Phone', order.phone),

          // Items
          if (order.items.isNotEmpty) ...[
            const SizedBox(height: 24),
            const Text('ITEMS', style: TextStyle(fontFamily: 'DMSans', fontSize: 9, letterSpacing: 0.4, color: AppColors.textMuted2, fontWeight: FontWeight.w500)),
            const SizedBox(height: 12),
            ...order.items.map((item) => Padding(
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(item.productName, style: const TextStyle(fontFamily: 'DMSans', fontSize: 13, color: AppColors.textPrimary)),
                            Text('${item.size} / ${item.color} × ${item.quantity}', style: const TextStyle(fontFamily: 'DMSans', fontSize: 11, color: AppColors.textMuted2)),
                          ],
                        ),
                      ),
                      Text(formatPrice(item.price * item.quantity), style: const TextStyle(fontFamily: 'DMSans', fontSize: 13, color: AppColors.textPrimary)),
                    ],
                  ),
                )),
            const Divider(color: AppColors.borderDark, height: 24),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text('TOTAL', style: TextStyle(fontFamily: 'DMSans', fontSize: 10, letterSpacing: 0.2, color: AppColors.textMuted2, fontWeight: FontWeight.w500)),
                Text(formatPrice(order.total), style: const TextStyle(fontFamily: 'DMSans', fontSize: 18, color: AppColors.textPrimary, fontWeight: FontWeight.w500)),
              ],
            ),
          ],

          if (status == 'COMPLETED' || status == 'DELIVERED') ...[
            const SizedBox(height: 32),
            const Divider(color: AppColors.borderDark),
            const SizedBox(height: 20),
            const Text('REVIEWS', style: TextStyle(fontFamily: 'DMSans', fontSize: 9, letterSpacing: 0.4, color: AppColors.textMuted2, fontWeight: FontWeight.w500)),
            const SizedBox(height: 12),
            ...order.items.map((item) => _ReviewRow(item: item, orderId: order.id)),
          ],

          if (order.canCancel) ...[
            const SizedBox(height: 32),
            NovaOutlineButton(
              label: 'Cancel Order',
              loading: _cancelling,
              onPressed: _cancel,
              borderColor: AppColors.error,
              textColor: AppColors.error,
            ),
          ],

          if (order.canRequestCancel) ...[
            const SizedBox(height: 32),
            GestureDetector(
              onTap: () => setState(() => _showCancelForm = !_showCancelForm),
              child: Container(
                padding: const EdgeInsets.symmetric(vertical: 14),
                width: double.infinity,
                decoration: BoxDecoration(border: Border.all(color: AppColors.border)),
                alignment: Alignment.center,
                child: Text(
                  _showCancelForm ? 'HIDE CANCEL REQUEST' : 'REQUEST CANCELLATION',
                  style: const TextStyle(fontFamily: 'DMSans', fontSize: 11, letterSpacing: 0.16, color: AppColors.textMuted),
                ),
              ),
            ),
            if (_showCancelForm) ...[
              const SizedBox(height: 16),
              TextField(
                controller: _cancelReasonCtrl,
                maxLines: 3,
                style: const TextStyle(fontFamily: 'DMSans', fontSize: 13, color: AppColors.whiteSoft),
                decoration: const InputDecoration(
                  hintText: 'Reason for cancellation (optional)',
                  hintStyle: TextStyle(fontFamily: 'DMSans', fontSize: 12, color: AppColors.textDisabled),
                  border: OutlineInputBorder(borderSide: BorderSide(color: AppColors.border), borderRadius: BorderRadius.zero),
                  enabledBorder: OutlineInputBorder(borderSide: BorderSide(color: AppColors.border), borderRadius: BorderRadius.zero),
                  focusedBorder: OutlineInputBorder(borderSide: BorderSide(color: AppColors.textDim), borderRadius: BorderRadius.zero),
                  contentPadding: EdgeInsets.all(12),
                ),
              ),
              const SizedBox(height: 12),
              NovaOutlineButton(
                label: 'Submit Request',
                loading: _cancelling,
                onPressed: _requestCancel,
                borderColor: AppColors.error,
                textColor: AppColors.error,
              ),
            ],
          ],
          const SizedBox(height: 40),
        ],
      ),
    );
  }
}

class _ReviewRow extends StatefulWidget {
  final OrderItem item;
  final int orderId;
  const _ReviewRow({required this.item, required this.orderId});

  @override
  State<_ReviewRow> createState() => _ReviewRowState();
}

class _ReviewRowState extends State<_ReviewRow> {
  bool _showForm = false;
  bool _submitted = false;
  bool _submitting = false;
  int _rating = 5;
  final _commentCtrl = TextEditingController();
  final List<XFile> _images = [];
  final _picker = ImagePicker();

  static const int _maxImages = 5;

  @override
  void dispose() {
    _commentCtrl.dispose();
    super.dispose();
  }

  Future<void> _pickImages() async {
    final remaining = _maxImages - _images.length;
    if (remaining <= 0) return;
    final picked = await _picker.pickMultiImage(imageQuality: 85, limit: remaining);
    if (picked.isNotEmpty && mounted) {
      setState(() => _images.addAll(picked));
    }
  }

  void _removeImage(int index) => setState(() => _images.removeAt(index));

  Future<void> _submit(ApiClient api) async {
    if (_rating == 0) return;
    final itemId = widget.item.id;
    if (itemId == null) return;
    setState(() => _submitting = true);
    try {
      final formData = FormData.fromMap({
        'rating': _rating.toString(),
        'comment': _commentCtrl.text.trim(),
        if (_images.isNotEmpty)
          'images': await Future.wait(
            _images.map((f) async => await MultipartFile.fromFile(f.path, filename: f.name)),
          ),
      });
      await api.dio.post('/api/reviews/$itemId', data: formData);
      if (mounted) setState(() { _submitting = false; _submitted = true; _showForm = false; });
    } on DioException {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(border: Border.all(color: AppColors.borderDark), color: AppColors.backgroundDark),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(widget.item.productName, style: const TextStyle(fontFamily: 'DMSans', fontSize: 12, color: AppColors.textPrimary, fontWeight: FontWeight.w500)),
          Text('${widget.item.size} / ${widget.item.color}', style: const TextStyle(fontFamily: 'DMSans', fontSize: 11, color: AppColors.textMuted2)),
          const SizedBox(height: 10),
          if (_submitted || widget.item.reviewed) ...[
            Row(children: [
              const Icon(Icons.check_circle_outline, size: 16, color: AppColors.success),
              const SizedBox(width: 6),
              Text(_submitted ? 'Review submitted!' : 'You reviewed this item',
                  style: const TextStyle(fontFamily: 'DMSans', fontSize: 12, color: AppColors.success)),
            ]),
          ] else if (!_showForm) ...[
            GestureDetector(
              onTap: () => setState(() => _showForm = true),
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                decoration: BoxDecoration(border: Border.all(color: AppColors.border)),
                child: const Text('Leave a Review', style: TextStyle(fontFamily: 'DMSans', fontSize: 11, letterSpacing: 0.12, color: AppColors.textMuted)),
              ),
            ),
          ] else ...[
            // Star picker
            Row(
              children: List.generate(5, (i) => GestureDetector(
                onTap: () => setState(() => _rating = i + 1),
                child: Padding(
                  padding: const EdgeInsets.only(right: 4),
                  child: Icon(i < _rating ? Icons.star_rounded : Icons.star_outline_rounded,
                      size: 28, color: AppColors.accent),
                ),
              )),
            ),
            const SizedBox(height: 12),
            // Comment
            TextField(
              controller: _commentCtrl,
              maxLines: 3,
              style: const TextStyle(fontFamily: 'DMSans', fontSize: 13, color: AppColors.whiteSoft),
              decoration: const InputDecoration(
                hintText: 'Share your experience...',
                hintStyle: TextStyle(fontFamily: 'DMSans', fontSize: 12, color: AppColors.textDisabled),
                border: OutlineInputBorder(borderSide: BorderSide(color: AppColors.border), borderRadius: BorderRadius.zero),
                enabledBorder: OutlineInputBorder(borderSide: BorderSide(color: AppColors.border), borderRadius: BorderRadius.zero),
                focusedBorder: OutlineInputBorder(borderSide: BorderSide(color: AppColors.textDim), borderRadius: BorderRadius.zero),
                contentPadding: EdgeInsets.all(12),
              ),
            ),
            const SizedBox(height: 12),
            // Image attach row
            Row(children: [
              const Text('PHOTOS', style: TextStyle(fontFamily: 'DMSans', fontSize: 9, letterSpacing: 0.3, color: AppColors.textMuted2, fontWeight: FontWeight.w500)),
              const SizedBox(width: 6),
              Text('(${_images.length}/$_maxImages)', style: const TextStyle(fontFamily: 'DMSans', fontSize: 9, color: AppColors.textDim)),
            ]),
            const SizedBox(height: 8),
            SizedBox(
              height: 72,
              child: ListView(
                scrollDirection: Axis.horizontal,
                children: [
                  // Thumbnails đã chọn
                  ..._images.asMap().entries.map((e) => _ImageThumb(
                    file: File(e.value.path),
                    onRemove: () => _removeImage(e.key),
                  )),
                  // Nút thêm ảnh
                  if (_images.length < _maxImages)
                    GestureDetector(
                      onTap: _pickImages,
                      child: Container(
                        width: 72, height: 72,
                        margin: const EdgeInsets.only(right: 6),
                        decoration: BoxDecoration(
                          border: Border.all(color: AppColors.border, style: BorderStyle.solid),
                          color: AppColors.surface,
                        ),
                        child: const Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Icon(Icons.add_photo_alternate_outlined, size: 22, color: AppColors.textDim),
                            SizedBox(height: 3),
                            Text('Add', style: TextStyle(fontFamily: 'DMSans', fontSize: 9, color: AppColors.textDim)),
                          ],
                        ),
                      ),
                    ),
                ],
              ),
            ),
            const SizedBox(height: 12),
            Row(children: [
              Expanded(child: NovaOutlineButton(
                label: 'Cancel',
                onPressed: () => setState(() { _showForm = false; _images.clear(); }),
                height: 40,
              )),
              const SizedBox(width: 10),
              Expanded(child: Consumer(builder: (ctx, ref, _) => NovaPrimaryButton(
                label: 'Submit',
                loading: _submitting,
                onPressed: () => _submit(ref.read(apiClientProvider)),
                height: 40,
              ))),
            ]),
          ],
        ],
      ),
    );
  }
}

class _ImageThumb extends StatelessWidget {
  final File file;
  final VoidCallback onRemove;
  const _ImageThumb({required this.file, required this.onRemove});

  @override
  Widget build(BuildContext context) => Stack(
        children: [
          Container(
            width: 72, height: 72,
            margin: const EdgeInsets.only(right: 6),
            decoration: BoxDecoration(border: Border.all(color: AppColors.borderDark)),
            child: Image.file(file, fit: BoxFit.cover),
          ),
          Positioned(
            top: 2, right: 8,
            child: GestureDetector(
              onTap: onRemove,
              child: Container(
                width: 18, height: 18,
                decoration: const BoxDecoration(color: AppColors.background, shape: BoxShape.circle),
                child: const Icon(Icons.close, size: 12, color: AppColors.textMuted),
              ),
            ),
          ),
        ],
      );
}

class _Row extends StatelessWidget {
  final String label;
  final String value;
  const _Row(this.label, this.value);

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SizedBox(width: 72, child: Text(label, style: const TextStyle(fontFamily: 'DMSans', fontSize: 11, color: AppColors.textMuted2))),
            Expanded(child: Text(value, style: const TextStyle(fontFamily: 'DMSans', fontSize: 12, color: AppColors.textPrimary))),
          ],
        ),
      );
}
