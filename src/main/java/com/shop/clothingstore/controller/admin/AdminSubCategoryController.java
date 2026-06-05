package com.shop.clothingstore.controller.admin;

import java.text.Normalizer;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.shop.clothingstore.entity.Category;
import com.shop.clothingstore.entity.SubCategory;
import com.shop.clothingstore.service.CategoryService;
import com.shop.clothingstore.service.SubCategoryService;

import jakarta.servlet.http.HttpServletRequest;

@Controller
@RequestMapping("/admin/subcategories")
public class AdminSubCategoryController extends AdminBaseController {

    private final SubCategoryService subCategoryService;
    private final CategoryService categoryService;

    public AdminSubCategoryController(SubCategoryService subCategoryService,
                                      CategoryService categoryService) {
        this.subCategoryService = subCategoryService;
        this.categoryService = categoryService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) Long categoryId,
                       Model model) {
        model.addAttribute("title", "Subcategories");
        model.addAttribute("currentPage", "categories");
        List<SubCategory> subs = (categoryId != null)
                ? subCategoryService.getByCategoryId(categoryId)
                : subCategoryService.getAllSubCategories();
        model.addAttribute("subCategories", subs);
        model.addAttribute("categories", categoryService.getAllCategories());
        model.addAttribute("filterCategoryId", categoryId);
        return "admin/subcategories/index";
    }

    @GetMapping("/create")
    public String createForm(@RequestParam(required = false) Long categoryId,
                             Model model, HttpServletRequest request) {
        model.addAttribute("title", "Add Subcategory");
        model.addAttribute("currentPage", "categories");
        model.addAttribute("categories", categoryService.getAllCategories());
        model.addAttribute("preselectedCategoryId", categoryId);
        model.addAttribute("sizeTypes", com.shop.clothingstore.entity.SizeType.values());
        model.addAttribute("isEdit", false);
        return isAjax(request)
                ? "admin/subcategories/_form :: form"
                : "redirect:/admin/subcategories?modal=create";
    }

    @PostMapping("/create")
    public Object create(@RequestParam String name,
                         @RequestParam Long categoryId,
                         @RequestParam(required = false) String sizeType,
                         @RequestParam(required = false) String slug,
                         RedirectAttributes ra,
                         HttpServletRequest request) {
        boolean ajax = isAjax(request);
        String back = "/admin/subcategories/create";

        if (name == null || name.isBlank()) {
            return fail(ajax, ra, "Subcategory name cannot be empty.", back);
        }
        Category category = categoryService.getCategoryById(categoryId).orElse(null);
        if (category == null) {
            return fail(ajax, ra, "Invalid main category.", back);
        }

        String finalSlug = (slug != null && !slug.isBlank())
                ? slug.trim().toLowerCase()
                : generateUniqueSlug(name);

        if (subCategoryService.getBySlug(finalSlug).isPresent()) {
            return fail(ajax, ra, "Slug '" + finalSlug + "' already exists.", back);
        }

        try {
            SubCategory sc = new SubCategory();
            sc.setName(name.trim());
            sc.setCategory(category);
            sc.setSlug(finalSlug);
            if (sizeType != null && !sizeType.isBlank()) {
                sc.setSizeType(com.shop.clothingstore.entity.SizeType.valueOf(sizeType));
            }
            subCategoryService.saveSubCategory(sc);
        } catch (Exception e) {
            return fail(ajax, ra, "Error: " + e.getMessage(), back);
        }
        return ok(ajax, ra, "Subcategory '" + name.trim() + "' created successfully!", "/admin/subcategories");
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes ra,
                           HttpServletRequest request) {
        boolean ajax = isAjax(request);
        return subCategoryService.getSubCategoryById(id).map(sc -> {
            model.addAttribute("title", "Edit Subcategory");
            model.addAttribute("currentPage", "categories");
            model.addAttribute("subCategory", sc);
            model.addAttribute("categories", categoryService.getAllCategories());
            model.addAttribute("sizeTypes", com.shop.clothingstore.entity.SizeType.values());
            model.addAttribute("isEdit", true);
            return ajax
                    ? "admin/subcategories/_form :: form"
                    : "redirect:/admin/subcategories?modal=edit&id=" + id;
        }).orElseGet(() -> {
            ra.addFlashAttribute("error", "Subcategory not found.");
            return "redirect:/admin/subcategories";
        });
    }

    @PostMapping("/{id}")
    public Object update(@PathVariable Long id,
                         @RequestParam String name,
                         @RequestParam Long categoryId,
                         @RequestParam(required = false) String sizeType,
                         @RequestParam(required = false) String slug,
                         RedirectAttributes ra,
                         HttpServletRequest request) {
        boolean ajax = isAjax(request);
        String back = "/admin/subcategories/" + id + "/edit";

        SubCategory existing = subCategoryService.getSubCategoryById(id).orElse(null);
        if (existing == null) {
            return fail(ajax, ra, "Subcategory not found.", "/admin/subcategories");
        }
        Category category = categoryService.getCategoryById(categoryId).orElse(null);
        if (category == null) {
            return fail(ajax, ra, "Invalid main category.", back);
        }
        if (name == null || name.isBlank()) {
            return fail(ajax, ra, "Subcategory name cannot be empty.", back);
        }

        String finalSlug = (slug != null && !slug.isBlank())
                ? slug.trim().toLowerCase()
                : toSlug(name);

        if (!finalSlug.equals(existing.getSlug())
                && subCategoryService.getBySlug(finalSlug).isPresent()) {
            return fail(ajax, ra, "Slug '" + finalSlug + "' already exists.", back);
        }

        try {
            existing.setName(name.trim());
            existing.setCategory(category);
            existing.setSlug(finalSlug);
            existing.setSizeType(
                    (sizeType != null && !sizeType.isBlank())
                            ? com.shop.clothingstore.entity.SizeType.valueOf(sizeType)
                            : null);
            subCategoryService.saveSubCategory(existing);
        } catch (Exception e) {
            return fail(ajax, ra, "Error: " + e.getMessage(), back);
        }
        return ok(ajax, ra, "Subcategory updated successfully!", "/admin/subcategories");
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            subCategoryService.deleteSubCategory(id);
            ra.addFlashAttribute("success", "Subcategory deleted.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Cannot delete: subcategory still has linked products.");
        }
        return "redirect:/admin/subcategories";
    }

    private String toSlug(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        String s = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        s = s.toLowerCase().replaceAll("[^a-z0-9\\s-]", "").replaceAll("\\s+", "-");
        return s;
    }

    private String generateUniqueSlug(String name) {
        String base = toSlug(name);
        String slug = base;
        int i = 1;
        while (subCategoryService.getBySlug(slug).isPresent()) {
            slug = base + "-" + i++;
        }
        return slug;
    }
}
