package com.shop.clothingstore.controller.admin;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.shop.clothingstore.entity.OrderStatus;
import com.shop.clothingstore.repository.OrderRepository;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Injects model attributes available to every admin template.
 *
 * pendingCount — number of PENDING orders, shown as badge on the sidebar Orders link.
 *               Computed once per request for all admin pages, not per controller.
 */
@ControllerAdvice(basePackages = "com.shop.clothingstore.controller.admin")
public class AdminControllerAdvice {

    private final OrderRepository orderRepository;

    public AdminControllerAdvice(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @ModelAttribute
    public void adminGlobal(Model model) {
        try {
            long pending = orderRepository.countByStatus(OrderStatus.PENDING);
            model.addAttribute("pendingCount", pending);
        } catch (Exception ignored) {
            model.addAttribute("pendingCount", 0L);
        }
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxUploadSize(
            MaxUploadSizeExceededException ex,
            HttpServletRequest request,
            RedirectAttributes ra) {

        ra.addFlashAttribute("error",
                "Upload exceeds the allowed size limit (max 20 MB per file, 50 MB per request)");

        String referer = request.getHeader("Referer");
        return "redirect:" + (referer != null ? referer : "/admin/products");
    }
}
