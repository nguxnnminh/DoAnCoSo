package com.shop.clothingstore.controller.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.shop.clothingstore.entity.Order;
import com.shop.clothingstore.entity.OrderStatus;
import com.shop.clothingstore.entity.Shipment;
import com.shop.clothingstore.exception.InvalidOrderStateException;
import com.shop.clothingstore.service.OrderService;
import com.shop.clothingstore.service.ShipmentService;

import jakarta.servlet.http.HttpServletRequest;

@Controller
public class AdminOrderController extends AdminBaseController {

    private final OrderService orderService;
    private final ShipmentService shipmentService;

    public AdminOrderController(OrderService orderService, ShipmentService shipmentService) {
        this.orderService = orderService;
        this.shipmentService = shipmentService;
    }

    // ─────────────────────────────────────────────────────────
    // ORDER LIST — with keyword search + status + date range
    // BUG FIX: status param was accepted by template but ignored
    //          by controller. Now wired to searchOrders().
    // ─────────────────────────────────────────────────────────
    @GetMapping("/admin/orders")
    public String orders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            Model model) {

        model.addAttribute("title", "Order Management");

        Pageable pageable = PageRequest.of(page, size);
        Page<Order> orders = orderService.searchOrders(keyword, status, dateFrom, dateTo, pageable);

        model.addAttribute("orders",    orders);
        model.addAttribute("statuses",  OrderStatus.values());
        model.addAttribute("keyword",   keyword);
        model.addAttribute("status",    status);
        model.addAttribute("dateFrom",  dateFrom);
        model.addAttribute("dateTo",    dateTo);
        addPageWindow(model, orders);

        return "admin/orders/index";
    }

    // ─────────────────────────────────────────────────────────
    // ORDER DETAIL — includes shipment info
    // ─────────────────────────────────────────────────────────
    @GetMapping("/admin/orders/{id}")
    public String orderDetail(
            @PathVariable Long id,
            Model model,
            RedirectAttributes redirectAttributes,
            HttpServletRequest request) {

        boolean ajax = isAjax(request);
        try {
            Order order = orderService.findByIdWithItems(id)
                    .orElseThrow(() -> new RuntimeException("Order not found"));

            Shipment shipment = shipmentService.findByOrder(order).orElse(null);

            model.addAttribute("title",        "Order #" + id + " Details");
            model.addAttribute("order",        order);
            model.addAttribute("shipment",     shipment);
            model.addAttribute("statuses",     OrderStatus.values());
            model.addAttribute("mainStatuses", new OrderStatus[]{
                OrderStatus.PENDING, OrderStatus.PROCESSING, OrderStatus.SHIPPING, OrderStatus.COMPLETED
            });

            return ajax
                    ? "admin/orders/_detail :: detail"
                    : "redirect:/admin/orders?modal=order&id=" + id + "&wide=1";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Order not found.");
            return "redirect:/admin/orders";
        }
    }

    // ─────────────────────────────────────────────────────────
    // UPDATE STATUS
    // ─────────────────────────────────────────────────────────
    @PostMapping("/admin/orders/{id}/status")
    public Object updateStatus(
            @PathVariable Long id,
            @RequestParam OrderStatus status,
            RedirectAttributes ra,
            HttpServletRequest request) {

        boolean ajax = isAjax(request);
        String back = "/admin/orders/" + id;
        try {
            orderService.updateOrderStatus(id, status);
        } catch (InvalidOrderStateException | IllegalStateException e) {
            return fail(ajax, ra, e.getMessage(), back);
        } catch (Exception e) {
            return fail(ajax, ra, "System error. Please try again.", back);
        }
        return ok(ajax, ra, "Order status updated successfully!", back);
    }

    // ─────────────────────────────────────────────────────────
    // ACCEPT CANCEL REQUEST → CANCELLED + stock restored
    // ─────────────────────────────────────────────────────────
    @PostMapping("/admin/orders/{id}/cancel-accept")
    public Object acceptCancel(
            @PathVariable Long id,
            RedirectAttributes ra,
            HttpServletRequest request) {

        boolean ajax = isAjax(request);
        String back = "/admin/orders/" + id;
        try {
            orderService.acceptCancelRequest(id);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return fail(ajax, ra, e.getMessage(), back);
        } catch (Exception e) {
            return fail(ajax, ra, "System error. Please try again.", back);
        }
        return ok(ajax, ra, "Cancellation request accepted. Order cancelled.", back);
    }

    // ─────────────────────────────────────────────────────────
    // DENY CANCEL REQUEST → back to PROCESSING
    // ─────────────────────────────────────────────────────────
    @PostMapping("/admin/orders/{id}/cancel-deny")
    public Object denyCancel(
            @PathVariable Long id,
            RedirectAttributes ra,
            HttpServletRequest request) {

        boolean ajax = isAjax(request);
        String back = "/admin/orders/" + id;
        try {
            orderService.denyCancelRequest(id);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return fail(ajax, ra, e.getMessage(), back);
        } catch (Exception e) {
            return fail(ajax, ra, "System error. Please try again.", back);
        }
        return ok(ajax, ra, "Cancellation request denied. Order continues processing.", back);
    }

    // ─────────────────────────────────────────────────────────
    // UPDATE SHIPMENT TRACKING
    // ─────────────────────────────────────────────────────────
    @PostMapping("/admin/orders/{id}/shipment")
    public String updateTracking(
            @PathVariable Long id,
            @RequestParam(required = false) String trackingNumber,
            @RequestParam(required = false) String carrier,
            RedirectAttributes redirectAttributes) {

        boolean updated = shipmentService.updateTracking(id, trackingNumber, carrier).isPresent();
        if (updated) {
            redirectAttributes.addFlashAttribute("success", "Shipment tracking info updated.");
        } else {
            redirectAttributes.addFlashAttribute("error", "No shipment record found for this order.");
        }
        return "redirect:/admin/orders/" + id;
    }
}
