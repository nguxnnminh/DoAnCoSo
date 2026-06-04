package com.shop.clothingstore.controller.admin;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;

public abstract class AdminBaseController {

    @ModelAttribute
    public void globalAttributes(Model model, HttpServletRequest request) {
        model.addAttribute("currentUri", request.getRequestURI());

        if (!model.containsAttribute("title")) {
            model.addAttribute("title", "Admin");
        }
    }

    /**
     * True when the request was issued by the admin SPA / modal layer
     * (fetch sets {@code X-Requested-With: XMLHttpRequest}). Used to decide
     * whether to return a modal fragment / JSON instead of a full page redirect.
     */
    protected boolean isAjax(HttpServletRequest request) {
        return "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
    }

    /**
     * Success outcome for a create/update handler.
     * AJAX (modal) → JSON the modal layer understands; otherwise the classic
     * flash-message + redirect-to-list flow.
     */
    protected Object ok(boolean ajax, RedirectAttributes ra, String message, String listUrl) {
        if (ajax) {
            return ResponseEntity.ok(Map.of("ok", true, "message", message));
        }
        ra.addFlashAttribute("success", message);
        return "redirect:" + listUrl;
    }

    /**
     * Failure outcome. AJAX → JSON {ok:false,error}; otherwise flash error and
     * redirect back to the form page so the legacy flow still works.
     */
    protected Object fail(boolean ajax, RedirectAttributes ra, String error, String backUrl) {
        if (ajax) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", error));
        }
        ra.addFlashAttribute("error", error);
        return "redirect:" + backUrl;
    }

    /**
     * Adds ±2 pagination window attributes so templates never render
     * more than 7 page buttons regardless of total page count.
     *
     * Adds to model:
     *   pageWindowStart      — first page number in the window
     *   pageWindowEnd        — last  page number in the window
     *   showStartEllipsis    — true when there are pages before the window
     *   showEndEllipsis      — true when there are pages after the window
     */
    protected void addPageWindow(Model model, Page<?> page) {
        int current     = page.getNumber();
        int total       = page.getTotalPages();
        int windowStart = Math.max(0, current - 2);
        int windowEnd   = Math.min(total - 1, current + 2);
        model.addAttribute("pageWindowStart",   windowStart);
        model.addAttribute("pageWindowEnd",     windowEnd);
        model.addAttribute("showStartEllipsis", windowStart > 1);
        model.addAttribute("showEndEllipsis",   windowEnd < total - 2);
    }
}