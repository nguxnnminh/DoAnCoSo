package com.shop.clothingstore.controller;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import com.shop.clothingstore.service.UserService;

/**
 * MockMvc tests for the profile UC: view, update (validation), change password.
 * The controller uses {@code @AuthenticationPrincipal UserDetails}, so the
 * AuthenticationPrincipalArgumentResolver is registered and a UserDetails
 * principal is supplied via {@code .principal()}.
 */
@SuppressWarnings("null")
class ProfileControllerTest {

    private MockMvc mockMvc;

    @Mock private UserService userService;
    @Mock private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ProfileController(userService, passwordEncoder))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setViewResolvers(new InternalResourceViewResolver())
                .build();
        // @AuthenticationPrincipal resolves from the SecurityContext, not the
        // request principal — seed it for the test thread.
        UserDetails principal = User.withUsername("user@test.com")
                .password("x").authorities(List.of()).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private com.shop.clothingstore.entity.User domainUser() {
        com.shop.clothingstore.entity.User u = new com.shop.clothingstore.entity.User();
        u.setEmail("user@test.com");
        u.setFullName("Old Name");
        u.setPassword("$2a$hashed");
        return u;
    }

    // ── VIEW ─────────────────────────────────────────────────
    @Test
    @DisplayName("GET /profile renders profile for the user")
    void profilePage_RendersView() throws Exception {
        when(userService.findByEmail("user@test.com")).thenReturn(Optional.of(domainUser()));

        mockMvc.perform(get("/profile"))
                .andExpect(status().isOk())
                .andExpect(view().name("shop/profile"))
                .andExpect(model().attributeExists("user"));
    }

    // ── UPDATE ───────────────────────────────────────────────
    @Test
    @DisplayName("POST /profile/update valid → saves and flashes success")
    void updateProfile_Valid_Success() throws Exception {
        when(userService.findByEmail("user@test.com")).thenReturn(Optional.of(domainUser()));

        mockMvc.perform(post("/profile/update")
                        .param("fullName", "New Name")
                        .param("phone", "0901234567")
                        .param("address", "123 Street")
                        )
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attribute("success", "Information updated successfully"));

        verify(userService).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("POST /profile/update blank name → flash error")
    void updateProfile_BlankName_Rejected() throws Exception {
        mockMvc.perform(post("/profile/update").param("fullName", ""))
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    @DisplayName("POST /profile/update invalid phone → flash error")
    void updateProfile_InvalidPhone_Rejected() throws Exception {
        mockMvc.perform(post("/profile/update")
                        .param("fullName", "New Name").param("phone", "abc")
                        )
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attributeExists("error"));
    }

    // ── CHANGE PASSWORD ──────────────────────────────────────
    @Test
    @DisplayName("POST /profile/change-password valid → success")
    void changePassword_Valid_Success() throws Exception {
        when(userService.findByEmail("user@test.com")).thenReturn(Optional.of(domainUser()));
        when(passwordEncoder.matches("oldpass12", "$2a$hashed")).thenReturn(true);
        when(passwordEncoder.encode("newpass12")).thenReturn("$2a$new");

        mockMvc.perform(post("/profile/change-password")
                        .param("oldPassword", "oldpass12")
                        .param("newPassword", "newpass12")
                        .param("confirmPassword", "newpass12")
                        )
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attribute("success", "Password changed successfully"));
    }

    @Test
    @DisplayName("POST /profile/change-password wrong current → flash error")
    void changePassword_WrongOld_Rejected() throws Exception {
        when(userService.findByEmail("user@test.com")).thenReturn(Optional.of(domainUser()));
        when(passwordEncoder.matches("wrong", "$2a$hashed")).thenReturn(false);

        mockMvc.perform(post("/profile/change-password")
                        .param("oldPassword", "wrong")
                        .param("newPassword", "newpass12")
                        .param("confirmPassword", "newpass12")
                        )
                .andExpect(flash().attribute("error", "Incorrect current password"));
    }

    @Test
    @DisplayName("POST /profile/change-password mismatch confirm → flash error")
    void changePassword_Mismatch_Rejected() throws Exception {
        when(userService.findByEmail("user@test.com")).thenReturn(Optional.of(domainUser()));
        when(passwordEncoder.matches("oldpass12", "$2a$hashed")).thenReturn(true);

        mockMvc.perform(post("/profile/change-password")
                        .param("oldPassword", "oldpass12")
                        .param("newPassword", "newpass12")
                        .param("confirmPassword", "different")
                        )
                .andExpect(flash().attribute("error", "Passwords do not match"));
    }
}
